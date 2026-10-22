package se.sundsvall.dept44.rabbitmq.config;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;
import se.sundsvall.dept44.rabbitmq.OutgoingMessage;
import se.sundsvall.dept44.rabbitmq.RabbitPublishException;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.NACKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.RETURNED;

/**
 * The starter against a real broker, since confirms, returns and nacks are what the publisher is for.
 */
class RabbitMqBrokerTest {

	// The version in TEST
	private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4.2.4-management-alpine");

	private static final String EXCHANGE = "dept44.test";
	private static final String QUEUE = "dept44.test.work";
	private static final String ROUTING_KEY = "work";
	private static final String LISTENED_QUEUE = "dept44.test.listened";
	private static final String LISTENED_ROUTING_KEY = "listened";
	private static final String FAILING_QUEUE = "dept44.test.failing";
	private static final String FAILING_ROUTING_KEY = "failing";

	@BeforeAll
	static void startBroker() {
		BROKER.start();

		// The starter never declares anything, so the test does it as rabbitmq-topology would
		final var connectionFactory = new CachingConnectionFactory(BROKER.getHost(), BROKER.getAmqpPort());
		connectionFactory.setUsername(BROKER.getAdminUsername());
		connectionFactory.setPassword(BROKER.getAdminPassword());
		final var admin = new RabbitAdmin(connectionFactory);
		final var exchange = new DirectExchange(EXCHANGE);
		final var queue = QueueBuilder.durable(QUEUE).quorum().build();
		admin.declareExchange(exchange);
		admin.declareQueue(queue);
		admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY));
		final var listenedQueue = QueueBuilder.durable(LISTENED_QUEUE).quorum().build();
		admin.declareQueue(listenedQueue);
		admin.declareBinding(BindingBuilder.bind(listenedQueue).to(exchange).with(LISTENED_ROUTING_KEY));
		final var failingQueue = QueueBuilder.durable(FAILING_QUEUE).quorum().build();
		admin.declareQueue(failingQueue);
		admin.declareBinding(BindingBuilder.bind(failingQueue).to(exchange).with(FAILING_ROUTING_KEY));
		connectionFactory.destroy();
	}

	@AfterAll
	static void stopBroker() {
		BROKER.stop();
	}

	@Test
	void confirmedMessageReachesTheQueue() {
		run(context -> {
			context.getBean(Dept44RabbitPublisher.class).publish(OutgoingMessage.to(EXCHANGE, ROUTING_KEY)
				.withPayload(new Payload("value"))
				.withMessageId("message-id")
				.withCorrelationId("correlation-id")
				.withReplyTo("dept44.responder.reply", "dept44.test.replies")
				.withHeader("x-custom", "custom"));

			final var received = context.getBean(RabbitTemplate.class).receive(QUEUE, 5000);

			assertThat(received).isNotNull();
			assertThatJson(new String(received.getBody(), UTF_8)).isEqualTo("{\"value\":\"value\"}");
			final var properties = received.getMessageProperties();
			assertThat(properties.getMessageId()).isEqualTo("message-id");
			assertThat(properties.getCorrelationId()).isEqualTo("correlation-id");
			assertThat(properties.getReplyTo()).isEqualTo("dept44.responder.reply/dept44.test.replies");
			assertThat(properties.<String>getHeader("x-custom")).isEqualTo("custom");
			assertThat(properties.getHeaders()).doesNotContainKey("__TypeId__");
		});
	}

	@Test
	void unroutableMessageIsReturned() {
		run(context -> assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> context.getBean(Dept44RabbitPublisher.class).publish(OutgoingMessage.to(EXCHANGE, "nobody-binds-this").withPayload(new Payload("value"))))
			.satisfies(e -> assertThat(e.getReason()).isEqualTo(RETURNED))
			.withMessageEndingWith("was returned by the broker: 312 NO_ROUTE"));
	}

	@Test
	void messageToAMissingExchangeIsNacked() {
		run(context -> assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> context.getBean(Dept44RabbitPublisher.class).publish(OutgoingMessage.to("dept44.missing", ROUTING_KEY).withPayload(new Payload("value"))))
			.satisfies(e -> assertThat(e.getReason()).isEqualTo(NACKED))
			.withMessageContaining("NOT_FOUND"));
	}

	@Test
	void listenerHandlesTheMessageUnderTheSendersRequestIdAndIdentifier() {
		contextRunner().withUserConfiguration(Listener.class).run(context -> {
			assertThat(context).hasNotFailed();
			RequestId.init("sender-request-id");
			Identifier.set(Identifier.parse("joe01doe; type=adAccount"));
			try {
				context.getBean(Dept44RabbitPublisher.class).publish(OutgoingMessage.to(EXCHANGE, LISTENED_ROUTING_KEY).withPayload(new Payload("value")));
			} finally {
				RequestId.reset();
				Identifier.remove();
			}

			assertThat(Listener.SEEN.poll(10, SECONDS)).isEqualTo(new Seen(new Payload("value"), "sender-request-id", Identifier.parse("joe01doe; type=adAccount")));
		});
	}

	@Test
	void aFailedListenerIsHandledUnderTheSendersRequestIdByTheServicesOwnErrorHandler() {
		contextRunner().withUserConfiguration(FailingListener.class).run(context -> {
			assertThat(context).hasNotFailed();
			RequestId.init("sender-request-id");
			try {
				context.getBean(Dept44RabbitPublisher.class).publish(OutgoingMessage.to(EXCHANGE, FAILING_ROUTING_KEY).withPayload(new Payload("value")));
			} finally {
				RequestId.reset();
			}

			assertThat(FailingListener.ERROR_HANDLER_SAW.poll(10, SECONDS)).isEqualTo("sender-request-id");
		});
	}

	private static void run(final Consumer<ApplicationContext> test) {
		contextRunner().run(context -> {
			assertThat(context).hasNotFailed();
			test.accept(context);
		});
	}

	private static ApplicationContextRunner contextRunner() {
		return new ApplicationContextRunner()
			.withInitializer(context -> new RabbitMqDefaultPropertiesEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class, RabbitMqConfiguration.class))
			.withPropertyValues(
				"spring.rabbitmq.host=" + BROKER.getHost(),
				"spring.rabbitmq.port=" + BROKER.getAmqpPort(),
				"spring.rabbitmq.username=" + BROKER.getAdminUsername(),
				"spring.rabbitmq.password=" + BROKER.getAdminPassword());
	}

	record Payload(String value) {
	}

	record Seen(Payload payload, String requestId, Identifier identifier) {
	}

	static class Listener {

		static final BlockingQueue<Seen> SEEN = new LinkedBlockingQueue<>();

		@RabbitListener(queues = LISTENED_QUEUE)
		void receive(final Payload payload) {
			SEEN.add(new Seen(payload, RequestId.get(), Identifier.get()));
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class FailingListener {

		static final BlockingQueue<String> ERROR_HANDLER_SAW = new LinkedBlockingQueue<>();

		/**
		 * Spring Boot's factory, with an error handler of the service's own. It records the request id it runs under, and
		 * rejects the message without requeue, so that it is not delivered again.
		 */
		@Bean
		SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(final SimpleRabbitListenerContainerFactoryConfigurer configurer, final ConnectionFactory connectionFactory) {
			final var factory = new SimpleRabbitListenerContainerFactory();
			configurer.configure(factory, connectionFactory);
			factory.setErrorHandler(throwable -> {
				ERROR_HANDLER_SAW.add(RequestId.get());
				throw new AmqpRejectAndDontRequeueException(throwable);
			});
			return factory;
		}

		@RabbitListener(queues = FAILING_QUEUE)
		void receive(final Payload payload) {
			throw new IllegalStateException("The listener fails");
		}
	}
}
