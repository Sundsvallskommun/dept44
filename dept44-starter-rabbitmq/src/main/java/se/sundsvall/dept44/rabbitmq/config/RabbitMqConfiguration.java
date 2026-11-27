package se.sundsvall.dept44.rabbitmq.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.health.RabbitHealthContributorAutoConfiguration;
import org.springframework.boot.amqp.health.RabbitHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.autoconfigure.contributor.CompositeHealthContributorConfiguration;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;
import se.sundsvall.dept44.rabbitmq.TransientFailures;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Objects.requireNonNullElse;

/**
 * Only loaded when {@code dept44.rabbitmq.enabled} is true, see {@link RabbitMqAutoConfigurationImportFilter}.
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class, before = RabbitHealthContributorAutoConfiguration.class)
@EnableConfigurationProperties(RabbitMqProperties.class)
class RabbitMqConfiguration {

	/**
	 * JSON written and read with the service's own {@link JsonMapper}, so that dates and null handling are the same as in
	 * its REST API. Spring Boot hands it to both the template and the listener containers. Not indented, as dept44-starter
	 * has the REST API's JSON: whitespace in every message is size and nothing else.
	 * <p>
	 * The producer's class name is not sent in {@code __TypeId__}: the contract is the JSON, not a Java class. A consumer
	 * gets its type from the parameter of its listener method.
	 */
	@Bean
	@ConditionalOnMissingBean(MessageConverter.class)
	MessageConverter dept44RabbitMessageConverter(final JsonMapper jsonMapper) {
		final var converter = new JacksonJsonMessageConverter(jsonMapper.rebuild().disable(SerializationFeature.INDENT_OUTPUT).build());
		converter.setJavaTypeMapper(new DefaultJacksonJavaTypeMapper() {
			@Override
			public void fromJavaType(final JavaType javaType, final MessageProperties properties) {
				// No type headers, see above
			}
		});
		return converter;
	}

	/**
	 * Applies to every {@link RabbitTemplate}, Spring Boot's and the service's own, which Spring Boot's
	 * {@code RabbitTemplateCustomizer} does not reach.
	 * <p>
	 * It publishes on a connection of its own. The broker blocks a connection that publishes during a memory or disk
	 * alarm, and on a connection shared with the listeners their acks would then go unread too: the memory is never
	 * freed, and the alarm never clears. A transacted template is left on the listener's channel, so that what it
	 * publishes from a listener commits or rolls back with that listener's transaction. It fails the startup while the
	 * publisher confirms are on, see {@link #requireNoTransactionWithConfirms}. And it sends the request id along, see
	 * {@link RequestIdHeaders}.
	 */
	@Bean
	static BeanPostProcessor dept44RabbitTemplateAdvisor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(final Object bean, final String beanName) {
				if (bean instanceof final RabbitTemplate rabbitTemplate) {
					final var description = "RabbitTemplate '%s'".formatted(beanName);
					requireNoTransactionWithConfirms(rabbitTemplate.isChannelTransacted(), rabbitTemplate.getConnectionFactory(), description);
					rabbitTemplate.setUsePublisherConnection(!rabbitTemplate.isChannelTransacted());
					rabbitTemplate.addBeforePublishPostProcessors(new RequestIdHeaders());
				}
				return bean;
			}
		};
	}

	/**
	 * Gives every connection factory, Spring Boot's and the service's own, an executor that runs the deliveries of each
	 * channel on virtual threads, so that one consumer never waits for another. An executor the service has set itself is
	 * replaced.
	 * <p>
	 * Without it, the RabbitMQ client hands the deliveries of a connection's consumers to a pool of one thread per CPU,
	 * one thread in a pod with a single CPU. The direct listener container runs the listener on that thread, and its retry
	 * waits there between attempts. One message retried for up to 15 minutes would then hold up every other direct
	 * consumer on the connection. The client still hands a channel's deliveries over one at a time, in order.
	 * <p>
	 * The threads are named after the connection factory's bean, for the thread in the log lines. The executor also runs
	 * what the factory would run on its own executor, such as the wait for a publish's confirm. At shutdown the factory
	 * does not wait for such work on an executor it was given, so a publish not yet confirmed then is nacked as the
	 * connection closes, where it would otherwise get up to 30 seconds.
	 */
	@Bean
	static BeanPostProcessor dept44RabbitConsumerThreads() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(final Object bean, final String beanName) {
				if (bean instanceof final AbstractConnectionFactory connectionFactory) {
					final var threadFactory = Thread.ofVirtual().name(beanName + "-", 0).factory();
					// Also the publisher connection factory's
					connectionFactory.setExecutor(Executors.newThreadPerTaskExecutor(threadFactory));
				}
				return bean;
			}
		};
	}

	/**
	 * Puts {@link RequestIdListenerAdvice} first in the advice chain of every listener container factory, Spring Boot's
	 * and the service's own, so that whatever else is in the chain, such as Spring Boot's retry, already logs under the
	 * message's request id. {@link ListenerLogAdvice} comes next, before the retry, so that it logs each delivery once.
	 * It is left out where the container does not settle each message as the listener ends, since it would log what
	 * becomes of a message wrongly there.
	 * <p>
	 * Fails the startup for a simple container that would settle several messages together, see
	 * {@link #requireEachMessageSettledOnItsOwn}.
	 * <p>
	 * Whether the log advice goes in depends on the acknowledge mode, and the startup check on the batch size. Both are
	 * read from {@code spring.rabbitmq.listener.*}, since a factory has no getters for them. A factory the service sets up
	 * in code with another acknowledge mode, or a batch size above one, is not seen. Its listeners still get
	 * {@link ListenerLogAdvice}, whose lines may then be wrong, and the startup does not fail for the batch size.
	 */
	@Bean
	static BeanPostProcessor dept44RequestIdListenerAdvisor(final Environment environment) {
		final var rabbitProperties = Binder.get(environment).bindOrCreate("spring.rabbitmq", RabbitProperties.class);
		final var listener = rabbitProperties.getListener();
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(final Object bean, final String beanName) {
				if (bean instanceof final AbstractRabbitListenerContainerFactory<?> factory) {
					if (factory instanceof SimpleRabbitListenerContainerFactory) {
						requireEachMessageSettledOnItsOwn(listener.getSimple());
					}
					final var adviceChain = new ArrayList<Advice>();
					adviceChain.add(new RequestIdListenerAdvice());
					if (settlesEachMessageAsTheListenerEnds(factory, listener)) {
						adviceChain.add(new ListenerLogAdvice());
					}
					adviceChain.addAll(adviceOfOthers(factory.getAdviceChain()));
					factory.setAdviceChain(adviceChain.toArray(Advice[]::new));
				}
				return bean;
			}
		};
	}

	/**
	 * The advice in a factory's chain that is not the starter's. The starter's own is there in a chain taken from a factory
	 * the advisor has already been through, and goes in once each.
	 */
	private static List<Advice> adviceOfOthers(final Advice @Nullable [] chain) {
		if (chain == null) {
			return List.of();
		}
		final var others = new ArrayList<Advice>();
		for (final var advice : chain) {
			final var isTheStartersOwn = advice instanceof RequestIdListenerAdvice || advice instanceof ListenerLogAdvice;
			if (!isTheStartersOwn) {
				others.add(advice);
			}
		}
		return others;
	}

	/**
	 * Above a batch size of one, the simple container settles the messages it has taken together, unless they are a batch
	 * listener's batch. When one fails it puts back those handled before it as well, to be handled again, with a
	 * {@code basic.nack} that RabbitMQ does not count against {@code x-delivery-limit}.
	 */
	private static void requireEachMessageSettledOnItsOwn(final RabbitProperties.SimpleContainer simple) {
		if (!simple.isConsumerBatchEnabled() && requireNonNullElse(simple.getBatchSize(), 1) > 1) {
			throw new IllegalStateException("spring.rabbitmq.listener.simple.batch-size above 1 requires spring.rabbitmq.listener.simple.consumer-batch-enabled=true. "
				+ "Without it, a failed message puts back the messages handled before it, with a basic.nack that RabbitMQ does not count against x-delivery-limit");
		}
	}

	/**
	 * RabbitMQ closes a channel that is asked for both a transaction and publisher confirms, which the starter turns on.
	 * Every publish on it would fail as if the broker could not be reached, and a listener would retry that as an outage.
	 */
	static void requireNoTransactionWithConfirms(final boolean channelTransacted, final ConnectionFactory connectionFactory, final String description) {
		if (channelTransacted && (connectionFactory.isPublisherConfirms() || connectionFactory.isSimplePublisherConfirms())) {
			throw new IllegalStateException(description + " is channel-transacted, which RabbitMQ does not allow on a connection with publisher confirms. "
				+ "Leave out the transaction, since Dept44RabbitPublisher depends on the confirms");
		}
	}

	/**
	 * Whether the container acknowledges or rejects each message as the listener returns or throws, which is what
	 * {@link ListenerLogAdvice} logs. Read from Spring Boot's properties, since a factory has no getters for them.
	 */
	private static boolean settlesEachMessageAsTheListenerEnds(final AbstractRabbitListenerContainerFactory<?> factory, final RabbitProperties.Listener listener) {
		return switch (factory) {
			case final SimpleRabbitListenerContainerFactory _ -> isAuto(listener.getSimple());
			case final DirectRabbitListenerContainerFactory _ -> isAuto(listener.getDirect());
			default -> true;
		};
	}

	private static boolean isAuto(final RabbitProperties.AmqpContainer container) {
		return requireNonNullElse(container.getAcknowledgeMode(), AcknowledgeMode.AUTO) == AcknowledgeMode.AUTO;
	}

	/**
	 * The split between the failures that may pass and those that do not, which both the retry and the recoverer follow.
	 * A service that knows more failures declares its own, built on {@link TransientFailures#defaults()}.
	 */
	@Bean
	@ConditionalOnMissingBean
	TransientFailures dept44TransientFailures() {
		return TransientFailures.defaults();
	}

	/**
	 * Retries only a transient failure, see {@link TransientFailures}. Any other goes to the recoverer at once, so that a
	 * message that can never be handled is not tried again for minutes. Stops after 15 minutes, whatever the attempts
	 * take: the broker's {@code consumer_timeout}, 30 minutes by default, counts from the delivery, and closes the channel
	 * of a message held longer. Ordered first, so that a customizer of the service's own runs after it.
	 */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	RabbitListenerRetrySettingsCustomizer dept44TransientFailureRetry(final TransientFailures transientFailures) {
		return settings -> {
			settings.setExceptionPredicate(transientFailures::isTransient);
			settings.setFactory(builder -> builder.timeout(Duration.ofMinutes(15)).build());
		};
	}

	@Bean
	@ConditionalOnMissingBean
	MessageRecoverer dept44TransientFailureRecoverer(final TransientFailures transientFailures) {
		return new TransientFailureRecoverer(transientFailures);
	}

	/**
	 * Gets the registry through a provider: a bean that depends on the registry is started after it, whatever its phase.
	 */
	@Bean
	ListenerContainerInstaller dept44ListenerContainerInstaller(final ObjectProvider<RabbitListenerEndpointRegistry> registry) {
		return new ListenerContainerInstaller(registry);
	}

	/**
	 * The listener containers' health, as {@code rabbitListeners}. Turned off as any other health indicator.
	 */
	@Bean
	@ConditionalOnEnabledHealthIndicator("rabbit-listeners")
	RabbitListenersHealthIndicator rabbitListenersHealthIndicator(final ApplicationContext context, final RabbitListenerEndpointRegistry registry) {
		return new RabbitListenersHealthIndicator(context, registry);
	}

	@Bean
	UndeclaredTopologyWarning dept44UndeclaredTopologyWarning(final ConfigurableListableBeanFactory beanFactory) {
		return new UndeclaredTopologyWarning(beanFactory);
	}

	/**
	 * Left out where the service has more than one template and none of them is primary. It then declares its own, with
	 * the template it wants.
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnSingleCandidate(RabbitTemplate.class)
	Dept44RabbitPublisher dept44RabbitPublisher(final RabbitTemplate rabbitTemplate, final RabbitMqProperties properties) {
		return new Dept44RabbitPublisher(rabbitTemplate, properties.publishConfirmTimeout());
	}

	/**
	 * Spring Boot's {@code rabbit} health check, one for each {@link RabbitTemplate} bean as Spring Boot's, but on the
	 * listeners' connection. Each template bean publishes on a connection of its own, see
	 * {@link #dept44RabbitTemplateAdvisor()}, which the broker blocks during a memory or disk alarm. The check then waits
	 * in the socket for a channel until the alarm clears, and so do {@code /actuator/health} and a readiness probe that
	 * includes it. The template the check runs on is not a bean, so the advisor leaves it on the listeners' connection.
	 * The broker blocks that one too once a listener returns a reply, which Spring AMQP publishes on the consumer's
	 * channel.
	 * <p>
	 * Replaces Spring Boot's, which backs off for a bean of this name.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(RabbitTemplate.class)
	@ConditionalOnEnabledHealthIndicator("rabbit")
	static class RabbitHealthConfiguration extends CompositeHealthContributorConfiguration<RabbitHealthIndicator, RabbitTemplate> {

		RabbitHealthConfiguration() {
			super(template -> new RabbitHealthIndicator(onTheListenersConnection(template.getConnectionFactory())));
		}

		@Bean
		@ConditionalOnMissingBean(name = {
			"rabbitHealthIndicator", "rabbitHealthContributor"
		})
		HealthContributor rabbitHealthContributor(final ConfigurableListableBeanFactory beanFactory) {
			return createContributor(beanFactory, RabbitTemplate.class);
		}

		private static RabbitTemplate onTheListenersConnection(final ConnectionFactory connectionFactory) {
			final var template = new RabbitTemplate(connectionFactory);
			// A ThreadChannelConnectionFactory sets it to true
			template.setUsePublisherConnection(false);
			return template;
		}
	}
}
