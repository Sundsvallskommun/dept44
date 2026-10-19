package se.sundsvall.dept44.rabbitmq.config;

import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;
import se.sundsvall.dept44.rabbitmq.OutgoingMessage;
import se.sundsvall.dept44.rabbitmq.RabbitPublishException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.NACKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.RETURNED;

/**
 * The starter against a real broker, since confirms, returns and nacks are what the publisher is for.
 */
class RabbitMqBrokerTest {

	private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4-management-alpine");

	private static final String EXCHANGE = "dept44.test";
	private static final String QUEUE = "dept44.test.work";
	private static final String ROUTING_KEY = "work";

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

	private static void run(final Consumer<ApplicationContext> test) {
		new ApplicationContextRunner()
			.withInitializer(context -> new RabbitMqDefaultPropertiesEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class, RabbitMqConfiguration.class))
			.withPropertyValues(
				"spring.rabbitmq.host=" + BROKER.getHost(),
				"spring.rabbitmq.port=" + BROKER.getAmqpPort(),
				"spring.rabbitmq.username=" + BROKER.getAdminUsername(),
				"spring.rabbitmq.password=" + BROKER.getAdminPassword())
			.run(context -> {
				assertThat(context).hasNotFailed();
				test.accept(context);
			});
	}

	record Payload(String value) {
	}
}
