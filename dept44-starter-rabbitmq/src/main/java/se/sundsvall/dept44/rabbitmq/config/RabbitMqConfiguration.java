package se.sundsvall.dept44.rabbitmq.config;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

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
	 * Publishes on a connection of its own. The broker blocks a connection that publishes during a memory or disk alarm,
	 * and on a connection shared with the listeners their acks would then go unread too: the memory is never freed, and
	 * the alarm never clears.
	 */
	@Bean
	RabbitTemplateCustomizer dept44PublisherConnectionCustomizer() {
		return rabbitTemplate -> rabbitTemplate.setUsePublisherConnection(true);
	}

	@Bean
	@ConditionalOnMissingBean
	Dept44RabbitPublisher dept44RabbitPublisher(final RabbitTemplate rabbitTemplate, final RabbitMqProperties properties) {
		return new Dept44RabbitPublisher(rabbitTemplate, properties.publishConfirmTimeout());
	}
}
