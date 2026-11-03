package se.sundsvall.dept44.rabbitmq.config;

import java.time.Duration;
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
import static org.awaitility.Awaitility.await;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.BLOCKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.NACKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.RETURNED;

/**
 * The starter against a real broker, since confirms, returns and nacks are what the publisher is for.
 */
class RabbitMqBrokerTest {

	// The version in TEST and production
	private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4.3.6-management-alpine");

	private static final String EXCHANGE = "dept44.test";
	private static final String QUEUE = "dept44.test.work";
	private static final String ROUTING_KEY = "work";
	private static final String LISTENED_QUEUE = "dept44.test.listened";
	private static final String LISTENED_ROUTING_KEY = "listened";
	private static final String FAILING_QUEUE = "dept44.test.failing";
	private static final String FAILING_ROUTING_KEY = "failing";
	private static final String ALARM_QUEUE = "dept44.test.alarm";
	private static final String ALARM_ROUTING_KEY = "alarm";
	private static final String REQUEST_QUEUE = "dept44.test.requests";
	private static final String REQUEST_ROUTING_KEY = "requests";
	private static final String REPLY_QUEUE = "dept44.test.replies";

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
		final var alarmQueue = QueueBuilder.durable(ALARM_QUEUE).quorum().build();
		admin.declareQueue(alarmQueue);
		admin.declareBinding(BindingBuilder.bind(alarmQueue).to(exchange).with(ALARM_ROUTING_KEY));
		final var requestQueue = QueueBuilder.durable(REQUEST_QUEUE).quorum().build();
		admin.declareQueue(requestQueue);
		admin.declareBinding(BindingBuilder.bind(requestQueue).to(exchange).with(REQUEST_ROUTING_KEY));
		admin.declareQueue(QueueBuilder.durable(REPLY_QUEUE).quorum().build());
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

	@Test
	void aReplyFromAListenerCarriesTheSendersRequestIdAndIdentifier() {
		contextRunner().withUserConfiguration(Responder.class).run(context -> {
			assertThat(context).hasNotFailed();
			RequestId.init("sender-request-id");
			Identifier.set(Identifier.parse("joe01doe; type=adAccount"));
			try {
				context.getBean(Dept44RabbitPublisher.class).request(OutgoingMessage.to(EXCHANGE, REQUEST_ROUTING_KEY)
					.withPayload(new Payload("value"))
					.withMessageId("message-id")
					.withCorrelationId("correlation-id")
					.withReplyTo("", REPLY_QUEUE));
			} finally {
				RequestId.reset();
				Identifier.remove();
			}

			final var reply = context.getBean(RabbitTemplate.class).receive(REPLY_QUEUE, 10_000);

			assertThat(reply).isNotNull();
			assertThatJson(new String(reply.getBody(), UTF_8)).isEqualTo("{\"value\":\"reply to value\"}");
			assertThat(reply.getMessageProperties().<String>getHeader(RequestId.HEADER_NAME)).isEqualTo("sender-request-id");
			assertThat(reply.getMessageProperties().<String>getHeader(Identifier.HEADER_NAME)).isEqualTo("joe01doe; type=adAccount");
		});
	}

	@Test
	void whileTheBrokerBlocksPublishingAPublishFailsAtOnce() {
		contextRunner().withPropertyValues("dept44.rabbitmq.publish-confirm-timeout=1s").run(context -> {
			assertThat(context).hasNotFailed();
			final var publisher = context.getBean(Dept44RabbitPublisher.class);
			// Opens the publisher connection before the alarm
			publisher.publish(OutgoingMessage.to(EXCHANGE, ALARM_ROUTING_KEY).withPayload(new Payload("before the alarm")));

			memoryAlarm(true);
			try {
				// The broker blocks the connection once it publishes during the alarm, so the first publish times out.
				// Without the starter's check, the ones after it would wait in the socket until the alarm clears
				await().atMost(Duration.ofSeconds(15)).until(() -> reasonOfAlarmPublish(publisher) == BLOCKED);
				assertThatExceptionOfType(RabbitPublishException.class)
					.isThrownBy(() -> publisher.publish(OutgoingMessage.to(EXCHANGE, ALARM_ROUTING_KEY).withPayload(new Payload("during the alarm"))))
					.satisfies(e -> assertThat(e.getReason()).isEqualTo(BLOCKED))
					.withMessageEndingWith("was not sent, the broker blocks publishing: low on memory");
			} finally {
				memoryAlarm(false);
			}

			await().atMost(Duration.ofSeconds(15)).until(() -> reasonOfAlarmPublish(publisher) == null);
		});
	}

	/**
	 * @return why a publish failed, or null when the broker took the message
	 */
	private static RabbitPublishException.Reason reasonOfAlarmPublish(final Dept44RabbitPublisher publisher) {
		try {
			publisher.publish(OutgoingMessage.to(EXCHANGE, ALARM_ROUTING_KEY).withPayload(new Payload("during the alarm")));
			return null;
		} catch (final RabbitPublishException e) {
			return e.getReason();
		}
	}

	/**
	 * A memory alarm, as when the broker runs low on memory: a high watermark of one byte. Off sets the default back.
	 */
	private static void memoryAlarm(final boolean on) throws Exception {
		final var result = on
			? BROKER.execInContainer("rabbitmqctl", "set_vm_memory_high_watermark", "absolute", "1")
			: BROKER.execInContainer("rabbitmqctl", "set_vm_memory_high_watermark", "0.6");
		assertThat(result.getExitCode()).isZero();
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

	static class Responder {

		@RabbitListener(queues = REQUEST_QUEUE)
		Payload respond(final Payload request) {
			return new Payload("reply to " + request.value());
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
