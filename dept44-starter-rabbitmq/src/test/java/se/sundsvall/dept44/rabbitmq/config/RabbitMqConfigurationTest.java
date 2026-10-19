package se.sundsvall.dept44.rabbitmq.config;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMqConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withInitializer(context -> new RabbitMqDefaultPropertiesEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class, RabbitMqConfiguration.class));

	@Test
	void templateAndListenersUseTheJsonConverter() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(Dept44RabbitPublisher.class).hasSingleBean(MessageConverter.class);
			assertThat(context.getBean(RabbitTemplate.class).getMessageConverter()).isSameAs(context.getBean(MessageConverter.class));
		});
	}

	@Test
	void publishesOnAConnectionOfItsOwn() {
		contextRunner.run(context -> assertThat(context.getBean(RabbitTemplate.class).isUsePublisherConnection()).isTrue());
	}

	@Test
	void jsonIsWrittenWithTheServicesMapperAndWithoutTypeHeaders() {
		contextRunner.withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE").run(context -> {
			final var converter = context.getBean(MessageConverter.class);

			final var message = converter.toMessage(new Payload("value", LocalDate.of(2026, 10, 19)), new MessageProperties());

			assertThatJson(new String(message.getBody(), UTF_8)).isEqualTo("{\"some_value\":\"value\",\"some_date\":\"2026-10-19\"}");
			assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
			assertThat(message.getMessageProperties().getHeaders()).isEmpty();
		});
	}

	@Test
	void jsonIsReadAsTheListenersParameterType() {
		contextRunner.run(context -> {
			final var converter = context.getBean(MessageConverter.class);
			final var message = converter.toMessage(new Payload("value", LocalDate.of(2026, 10, 19)), new MessageProperties());
			message.getMessageProperties().setInferredArgumentType(Payload.class);

			assertThat(converter.fromMessage(message)).isEqualTo(new Payload("value", LocalDate.of(2026, 10, 19)));
		});
	}

	@Test
	void aServicesOwnConverterWins() {
		contextRunner.withBean(MessageConverter.class, SimpleMessageConverter::new).run(context -> {
			assertThat(context).hasSingleBean(MessageConverter.class).getBean(MessageConverter.class).isInstanceOf(SimpleMessageConverter.class);
			assertThat(context.getBean(RabbitTemplate.class).getMessageConverter()).isInstanceOf(SimpleMessageConverter.class);
		});
	}

	@Test
	void aServicesOwnPublisherWins() {
		final var publisher = mock(Dept44RabbitPublisher.class);

		contextRunner.withBean(Dept44RabbitPublisher.class, () -> publisher)
			.run(context -> assertThat(context).getBean(Dept44RabbitPublisher.class).isSameAs(publisher));
	}

	@Test
	void noRabbitAdminDeclaresAnything() {
		contextRunner.run(context -> assertThat(context).doesNotHaveBean(AmqpAdmin.class));
	}

	@Test
	void publishConfirmTimeout() {
		contextRunner.run(context -> assertThat(context.getBean(RabbitMqProperties.class).publishConfirmTimeout()).isEqualTo(Duration.ofSeconds(5)));
		contextRunner.withPropertyValues("dept44.rabbitmq.publish-confirm-timeout=750ms")
			.run(context -> assertThat(context.getBean(RabbitMqProperties.class).publishConfirmTimeout()).isEqualTo(Duration.ofMillis(750)));
	}

	@Test
	void publishConfirmTimeoutThatIsNotPositiveFailsTheStartup() {
		contextRunner.withPropertyValues("dept44.rabbitmq.publish-confirm-timeout=0").run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("The confirm timeout must be positive, was PT0S"));
	}

	@Test
	void overridingPublisherConfirmsFailsTheStartup() {
		contextRunner.withPropertyValues("spring.rabbitmq.publisher-confirm-type=simple").run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Dept44RabbitPublisher requires spring.rabbitmq.publisher-confirm-type=correlated"));
	}

	@Test
	void overridingPublisherReturnsFailsTheStartup() {
		contextRunner.withPropertyValues("spring.rabbitmq.publisher-returns=false").run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Dept44RabbitPublisher requires spring.rabbitmq.publisher-returns=true"));
	}

	@Test
	void overridingMandatoryFailsTheStartup() {
		contextRunner.withPropertyValues("spring.rabbitmq.template.mandatory=false").run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Dept44RabbitPublisher requires spring.rabbitmq.template.mandatory=true"));
	}

	record Payload(String someValue, LocalDate someDate) {
	}
}
