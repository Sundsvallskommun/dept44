package se.sundsvall.dept44.rabbitmq.config;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;
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
			final var converter = context.getBean(MessageConverter.class);
			assertThat(context.getBean(RabbitTemplate.class).getMessageConverter()).isSameAs(converter);
			// The container factory has no getter for its converter
			assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class)).hasFieldOrPropertyWithValue("messageConverter", converter);
		});
	}

	@Test
	void requestIdAdviceComesFirstInTheListenerAdviceChain() {
		contextRunner.run(context -> {
			final var adviceChain = context.getBean(SimpleRabbitListenerContainerFactory.class).getAdviceChain();

			assertThat(adviceChain).hasSize(2);
			assertThat(adviceChain[0]).isInstanceOf(RequestIdListenerAdvice.class);
			assertThat(adviceChain[1]).isInstanceOf(StatelessRetryOperationsInterceptor.class);
		});
		contextRunner.withPropertyValues("spring.rabbitmq.listener.simple.retry.enabled=false")
			.run(context -> assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class).getAdviceChain())
				.singleElement()
				.isInstanceOf(RequestIdListenerAdvice.class));
	}

	@Test
	void listenersPrefetchOneMessage() {
		contextRunner.run(context -> assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class))
			.extracting("prefetchCount")
			.isEqualTo(1));
	}

	@Test
	void errorHandlersAreWrappedBeforeTheContainersStart() {
		RecordingContainers.ERROR_HANDLERS_AT_START.clear();

		contextRunner.withUserConfiguration(RecordingContainers.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(RecordingContainers.ERROR_HANDLERS_AT_START).singleElement().isInstanceOf(RequestIdErrorHandler.class);
		});
	}

	@Test
	void transientFailuresAreRetriedAndRecovered() {
		contextRunner.run(context -> {
			assertThat(context).getBean(MessageRecoverer.class).isInstanceOf(TransientFailureRecoverer.class);
			assertThat(context).getBean(RabbitListenerRetrySettingsCustomizer.class).isNotNull();
		});
	}

	@Test
	void aServicesOwnRecovererWins() {
		final var recoverer = mock(MessageRecoverer.class);

		contextRunner.withBean(MessageRecoverer.class, () -> recoverer)
			.run(context -> assertThat(context).getBean(MessageRecoverer.class).isSameAs(recoverer));
	}

	@Test
	void publishesOnAConnectionOfItsOwnWithTheRequestId() {
		contextRunner.run(context -> assertPublisherConnectionAndRequestId(context.getBean(RabbitTemplate.class)));
	}

	@Test
	void aServicesOwnTemplatePublishesOnAConnectionOfItsOwnWithTheRequestId() {
		contextRunner.withUserConfiguration(OwnTemplate.class).run(context -> {
			assertThat(context).hasSingleBean(RabbitTemplate.class);
			assertPublisherConnectionAndRequestId(context.getBean(RabbitTemplate.class));
		});
	}

	private static void assertPublisherConnectionAndRequestId(final RabbitTemplate rabbitTemplate) {
		assertThat(rabbitTemplate.isUsePublisherConnection()).isTrue();
		assertThat(rabbitTemplate.getBeforePublishPostProcessors()).singleElement().isInstanceOf(RequestIdHeaders.class);
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

	@Configuration(proxyBeanMethods = false)
	static class OwnTemplate {

		@Bean
		RabbitTemplate rabbitTemplate(final RabbitTemplateConfigurer configurer, final ConnectionFactory connectionFactory) {
			final var rabbitTemplate = new RabbitTemplate();
			configurer.configure(rabbitTemplate, connectionFactory);
			return rabbitTemplate;
		}
	}

	/**
	 * Containers that record the error handler they start with, instead of connecting to a broker.
	 */
	@Configuration(proxyBeanMethods = false)
	static class RecordingContainers {

		static final List<ErrorHandler> ERROR_HANDLERS_AT_START = new CopyOnWriteArrayList<>();

		@Bean
		SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(final SimpleRabbitListenerContainerFactoryConfigurer configurer, final ConnectionFactory connectionFactory) {
			final var factory = new SimpleRabbitListenerContainerFactory() {
				@Override
				protected SimpleMessageListenerContainer createContainerInstance() {
					return new SimpleMessageListenerContainer() {
						@Override
						protected void doStart() {
							ERROR_HANDLERS_AT_START.add(getErrorHandler());
						}
					};
				}
			};
			configurer.configure(factory, connectionFactory);
			return factory;
		}

		@RabbitListener(queues = "dept44.recorded")
		void listen(final String payload) {
			// Never started
		}
	}
}
