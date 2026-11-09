package se.sundsvall.dept44.rabbitmq.config;

import com.rabbitmq.client.Channel;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.AbstractAdaptableMessageListener;
import org.springframework.amqp.rabbit.listener.adapter.BatchMessagingMessageListenerAdapter;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
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
	void requestIdAndLogAdviceComeFirstInTheListenerAdviceChain() {
		contextRunner.run(context -> {
			final var adviceChain = context.getBean(SimpleRabbitListenerContainerFactory.class).getAdviceChain();

			assertThat(adviceChain).hasSize(3);
			assertThat(adviceChain[0]).isInstanceOf(RequestIdListenerAdvice.class);
			assertThat(adviceChain[1]).isInstanceOf(ListenerLogAdvice.class);
			assertThat(adviceChain[2]).isInstanceOf(StatelessRetryOperationsInterceptor.class);
		});
		contextRunner.withPropertyValues("spring.rabbitmq.listener.simple.retry.enabled=false")
			.run(context -> assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class).getAdviceChain())
				.hasExactlyElementsOfTypes(RequestIdListenerAdvice.class, ListenerLogAdvice.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"spring.rabbitmq.listener.simple.acknowledge-mode=manual",
		"spring.rabbitmq.listener.simple.acknowledge-mode=none",
		// Acknowledged together, and a failure rejects those already handled as well
		"spring.rabbitmq.listener.simple.batch-size=2"
	})
	void noLineForEachMessageWhenTheContainerDoesNotSettleEachOneAsTheListenerEnds(final String property) {
		contextRunner.withPropertyValues(property)
			.run(context -> assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class).getAdviceChain())
				.hasExactlyElementsOfTypes(RequestIdListenerAdvice.class, StatelessRetryOperationsInterceptor.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"spring.rabbitmq.listener.direct.acknowledge-mode=manual",
		"spring.rabbitmq.listener.direct.acknowledge-mode=none"
	})
	void noLineForEachMessageWhenADirectContainerDoesNotSettleEachOneAsTheListenerEnds(final String property) {
		contextRunner.withPropertyValues("spring.rabbitmq.listener.type=direct", property)
			.run(context -> assertThat(context.getBean(DirectRabbitListenerContainerFactory.class).getAdviceChain())
				.hasExactlyElementsOfTypes(RequestIdListenerAdvice.class, StatelessRetryOperationsInterceptor.class));
	}

	@Test
	void aLineForEachMessageWhenSeveralAreSettledTogetherAsTheListenerEnds() {
		// A batch listener's batch is settled as one
		contextRunner.withPropertyValues("spring.rabbitmq.listener.simple.consumer-batch-enabled=true", "spring.rabbitmq.listener.simple.batch-size=2")
			.run(context -> assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class).getAdviceChain())
				.hasExactlyElementsOfTypes(RequestIdListenerAdvice.class, ListenerLogAdvice.class, StatelessRetryOperationsInterceptor.class));
		// A direct container acknowledges those already handled before it rejects one
		contextRunner.withPropertyValues("spring.rabbitmq.listener.type=direct", "spring.rabbitmq.listener.direct.messages-per-ack=2")
			.run(context -> assertThat(context.getBean(DirectRabbitListenerContainerFactory.class).getAdviceChain())
				.hasExactlyElementsOfTypes(RequestIdListenerAdvice.class, ListenerLogAdvice.class, StatelessRetryOperationsInterceptor.class));
	}

	@Test
	void listenersPrefetchOneMessage() {
		contextRunner.run(context -> assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class))
			.extracting("prefetchCount")
			.isEqualTo(1));
	}

	@Test
	void errorHandlersAndRepliesCarryTheRequestIdBeforeTheContainersStart() {
		RecordingContainers.ERROR_HANDLERS_AT_START.clear();
		RecordingContainers.REPLY_POST_PROCESSORS_AT_START.clear();

		contextRunner.withUserConfiguration(RecordingContainers.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(RecordingContainers.ERROR_HANDLERS_AT_START).singleElement().isInstanceOf(RequestIdErrorHandler.class);
			assertThat(RecordingContainers.REPLY_POST_PROCESSORS_AT_START).singleElement()
				.satisfies(postProcessors -> assertThat(postProcessors).singleElement().isInstanceOf(RequestIdHeaders.class));
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

	@Test
	void aTransactedTemplatePublishesOnTheListenersChannelWithTheRequestId() {
		contextRunner.withUserConfiguration(TransactedTemplate.class).run(context -> {
			final var rabbitTemplate = context.getBean(RabbitTemplate.class);
			assertThat(rabbitTemplate.isUsePublisherConnection()).isFalse();
			assertThat(rabbitTemplate.getBeforePublishPostProcessors()).singleElement().isInstanceOf(RequestIdHeaders.class);
		});
	}

	@Test
	void aFactoryGivenAnotherFactorysAdviceChainGetsTheAdviceOnce() {
		contextRunner.withUserConfiguration(CopiedAdviceChain.class).run(context -> assertThat(context.getBean("copiedFactory", SimpleRabbitListenerContainerFactory.class).getAdviceChain())
			.hasExactlyElementsOfTypes(RequestIdListenerAdvice.class, ListenerLogAdvice.class, StatelessRetryOperationsInterceptor.class));
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

	@Test
	void warnsAboutAListenerWhoseMessagesSpringAmqpPutsBackUncounted(final CapturedOutput output) {
		contextRunner.withUserConfiguration(SettlingListeners.class)
			.withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(output.getOut())
					.contains("on queues [dept44.channel] may settle a message itself")
					.contains("on queues [dept44.error-handler] may settle a message itself")
					// Spring AMQP rejects an async listener's failed message, one at a time
					.doesNotContain("[dept44.async] may settle")
					.doesNotContain("[dept44.plain] may settle");
			});
	}

	@Test
	void doesNotWarnAboutABatchListenerWhoseBatchIsDeadLetteredInstead(final CapturedOutput output) {
		contextRunner.withUserConfiguration(BatchListener.class)
			.withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.listener.simple.consumer-batch-enabled=true",
				"spring.rabbitmq.listener.simple.batch-size=2")
			.run(context -> {
				assertThat(context).hasNotFailed();
				// A batch listener may settle a message itself, as far as Spring AMQP knows
				assertThat(context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers()).singleElement()
					.satisfies(container -> assertThat(((AbstractMessageListenerContainer) container).getMessageListener())
						.isInstanceOfSatisfying(BatchMessagingMessageListenerAdapter.class, listener -> assertThat(listener.maySettleDelivery()).isTrue()));
				assertThat(output.getOut()).doesNotContain("[dept44.batch] may settle");
			});
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

	@Configuration(proxyBeanMethods = false)
	static class TransactedTemplate {

		@Bean
		RabbitTemplate rabbitTemplate(final RabbitTemplateConfigurer configurer, final ConnectionFactory connectionFactory) {
			final var rabbitTemplate = new RabbitTemplate();
			configurer.configure(rabbitTemplate, connectionFactory);
			rabbitTemplate.setChannelTransacted(true);
			return rabbitTemplate;
		}
	}

	/**
	 * A second factory set up from Spring Boot's, which the advisor has already been through
	 */
	@Configuration(proxyBeanMethods = false)
	static class CopiedAdviceChain {

		@Bean
		SimpleRabbitListenerContainerFactory copiedFactory(final SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory) {
			final var factory = new SimpleRabbitListenerContainerFactory();
			factory.setAdviceChain(rabbitListenerContainerFactory.getAdviceChain());
			return factory;
		}
	}

	/**
	 * Containers that record the error handler and the reply post-processors they start with, instead of connecting to a
	 * broker.
	 */
	@Configuration(proxyBeanMethods = false)
	static class RecordingContainers {

		static final List<ErrorHandler> ERROR_HANDLERS_AT_START = new CopyOnWriteArrayList<>();
		static final List<MessagePostProcessor[]> REPLY_POST_PROCESSORS_AT_START = new CopyOnWriteArrayList<>();

		@Bean
		SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(final SimpleRabbitListenerContainerFactoryConfigurer configurer, final ConnectionFactory connectionFactory) {
			final var factory = new SimpleRabbitListenerContainerFactory() {
				@Override
				protected SimpleMessageListenerContainer createContainerInstance() {
					return new SimpleMessageListenerContainer() {
						@Override
						protected void doStart() {
							ERROR_HANDLERS_AT_START.add(getErrorHandler());
							REPLY_POST_PROCESSORS_AT_START.add(((AbstractAdaptableMessageListener) getMessageListener()).getBeforeSendReplyPostProcessors());
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

	/**
	 * Never started
	 */
	static class SettlingListeners {

		@RabbitListener(queues = "dept44.channel")
		void channel(final String payload, final Channel channel) {
			// Never started
		}

		@RabbitListener(queues = "dept44.error-handler", errorHandler = "rethrow")
		void errorHandler(final String payload) {
			// Never started
		}

		@RabbitListener(queues = "dept44.async")
		CompletableFuture<Void> async(final String payload) {
			return CompletableFuture.completedFuture(null);
		}

		@RabbitListener(queues = "dept44.plain")
		void plain(final String payload) {
			// Never started
		}

		@Bean
		static RabbitListenerErrorHandler rethrow() {
			return (amqpMessage, channel, message, exception) -> {
				throw exception;
			};
		}
	}

	static class BatchListener {

		@RabbitListener(queues = "dept44.batch")
		void batch(final List<Message> messages) {
			// Never started
		}
	}
}
