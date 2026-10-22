package se.sundsvall.dept44.rabbitmq.config;

import java.util.ArrayList;
import java.util.Arrays;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;
import se.sundsvall.dept44.rabbitmq.TransientFailures;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

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
	 * freed, and the alarm never clears. And it sends the request id along, see {@link RequestIdHeaders}.
	 */
	@Bean
	static BeanPostProcessor dept44RabbitTemplateAdvisor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(final Object bean, final String beanName) {
				if (bean instanceof final RabbitTemplate rabbitTemplate) {
					rabbitTemplate.setUsePublisherConnection(true);
					rabbitTemplate.addBeforePublishPostProcessors(new RequestIdHeaders());
				}
				return bean;
			}
		};
	}

	/**
	 * Puts {@link RequestIdListenerAdvice} first in the advice chain of every listener container factory, Spring Boot's
	 * and the service's own, so that whatever else is in the chain, such as Spring Boot's retry, already logs under the
	 * message's request id.
	 */
	@Bean
	static BeanPostProcessor dept44RequestIdListenerAdvisor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(final Object bean, final String beanName) {
				if (bean instanceof final AbstractRabbitListenerContainerFactory<?> factory) {
					final var adviceChain = new ArrayList<Advice>();
					adviceChain.add(new RequestIdListenerAdvice());
					ofNullable(factory.getAdviceChain()).map(Arrays::asList).ifPresent(adviceChain::addAll);
					factory.setAdviceChain(adviceChain.toArray(Advice[]::new));
				}
				return bean;
			}
		};
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
	RequestIdErrorHandlerInstaller dept44RequestIdErrorHandlerInstaller(final ObjectProvider<RabbitListenerEndpointRegistry> registry) {
		return new RequestIdErrorHandlerInstaller(registry);
	}

	@Bean
	@ConditionalOnMissingBean
	Dept44RabbitPublisher dept44RabbitPublisher(final RabbitTemplate rabbitTemplate, final RabbitMqProperties properties) {
		return new Dept44RabbitPublisher(rabbitTemplate, properties.publishConfirmTimeout());
	}
}
