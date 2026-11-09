package se.sundsvall.dept44.rabbitmq.config;

import java.util.ArrayList;
import java.util.Arrays;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;
import se.sundsvall.dept44.rabbitmq.TransientFailures;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

/**
 * Only loaded when {@code dept44.rabbitmq.enabled} is true, see {@link RabbitMqAutoConfigurationImportFilter}.
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@EnableConfigurationProperties(RabbitMqProperties.class)
class RabbitMqConfiguration {

	/**
	 * JSON written and read with the service's own {@link JsonMapper}, so that dates and null handling are the same as in
	 * its REST API. Spring Boot hands it to both the template and the listener containers.
	 * <p>
	 * The producer's class name is not sent in {@code __TypeId__}: the contract is the JSON, not a Java class. A consumer
	 * gets its type from the parameter of its listener method.
	 */
	@Bean
	@ConditionalOnMissingBean(MessageConverter.class)
	MessageConverter dept44RabbitMessageConverter(final JsonMapper jsonMapper) {
		final var converter = new JacksonJsonMessageConverter(jsonMapper);
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
	 * publishes from a listener commits or rolls back with that listener's transaction. And it sends the request id
	 * along, see {@link RequestIdHeaders}.
	 */
	@Bean
	static BeanPostProcessor dept44RabbitTemplateAdvisor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(final Object bean, final String beanName) {
				if (bean instanceof final RabbitTemplate rabbitTemplate) {
					rabbitTemplate.setUsePublisherConnection(!rabbitTemplate.isChannelTransacted());
					rabbitTemplate.addBeforePublishPostProcessors(new RequestIdHeaders());
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
	 */
	@Bean
	static BeanPostProcessor dept44RequestIdListenerAdvisor(final Environment environment) {
		final var listener = Binder.get(environment).bindOrCreate("spring.rabbitmq", RabbitProperties.class).getListener();
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(final Object bean, final String beanName) {
				if (bean instanceof final AbstractRabbitListenerContainerFactory<?> factory) {
					final var adviceChain = new ArrayList<Advice>();
					adviceChain.add(new RequestIdListenerAdvice());
					if (settlesEachMessageAsTheListenerEnds(factory, listener)) {
						adviceChain.add(new ListenerLogAdvice());
					}
					// Once each, also in a chain taken from a factory the advisor has already been through
					ofNullable(factory.getAdviceChain()).map(Arrays::stream)
						.ifPresent(advice -> advice.filter(a -> !(a instanceof RequestIdListenerAdvice || a instanceof ListenerLogAdvice)).forEach(adviceChain::add));
					factory.setAdviceChain(adviceChain.toArray(Advice[]::new));
				}
				return bean;
			}
		};
	}

	/**
	 * Whether the container acknowledges or rejects each message as the listener returns or throws, which is what
	 * {@link ListenerLogAdvice} logs. Read from Spring Boot's properties, since a factory has no getters for them.
	 */
	private static boolean settlesEachMessageAsTheListenerEnds(final AbstractRabbitListenerContainerFactory<?> factory, final RabbitProperties.Listener listener) {
		return switch (factory) {
			// Above one, a failure also rejects the messages handled before it, unless they are a batch listener's batch
			case final SimpleRabbitListenerContainerFactory _ -> isAuto(listener.getSimple())
				&& (listener.getSimple().isConsumerBatchEnabled() || requireNonNullElse(listener.getSimple().getBatchSize(), 1) <= 1);
			case final DirectRabbitListenerContainerFactory _ -> isAuto(listener.getDirect());
			default -> true;
		};
	}

	private static boolean isAuto(final RabbitProperties.AmqpContainer container) {
		return requireNonNullElse(container.getAcknowledgeMode(), AcknowledgeMode.AUTO) == AcknowledgeMode.AUTO;
	}

	/**
	 * Retries only a transient failure, see {@link TransientFailures}. Any other goes to the recoverer at once, so that a
	 * message that can never be handled is not tried again for minutes. Ordered first, so that a customizer of the
	 * service's own runs after it and can change the split.
	 */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	RabbitListenerRetrySettingsCustomizer dept44TransientFailureRetry() {
		return settings -> settings.setExceptionPredicate(TransientFailures::isTransient);
	}

	@Bean
	@ConditionalOnMissingBean
	MessageRecoverer dept44TransientFailureRecoverer() {
		return new TransientFailureRecoverer();
	}

	/**
	 * Gets the registry through a provider: a bean that depends on the registry is started after it, whatever its phase.
	 */
	@Bean
	RequestIdContainerInstaller dept44RequestIdContainerInstaller(final ObjectProvider<RabbitListenerEndpointRegistry> registry) {
		return new RequestIdContainerInstaller(registry);
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
}
