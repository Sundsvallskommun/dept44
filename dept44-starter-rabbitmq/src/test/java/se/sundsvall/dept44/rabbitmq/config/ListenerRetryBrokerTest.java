package se.sundsvall.dept44.rabbitmq.config;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.ListenerContainerConsumerFailedEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;
import se.sundsvall.dept44.rabbitmq.OutgoingMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The listener retry the starter sets up, against a real broker: what is retried, what goes back in the queue and what
 * is dead-lettered. The delays are cut to milliseconds, and each queue allows one redelivery before it dead-letters.
 */
class ListenerRetryBrokerTest {

	private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4.3.6-management-alpine");

	private static final String PASSES = "dept44.retry.passes";
	private static final String PERMANENT = "dept44.retry.permanent";
	private static final String STUCK = "dept44.retry.stuck";
	private static final String SERVER_ERROR = "dept44.retry.server-error";
	private static final String ACKNOWLEDGED = "dept44.retry.acknowledged";
	private static final String BATCH = "dept44.retry.batch";
	private static final int MAX_RETRIES = 2;

	@BeforeAll
	static void startBroker() {
		BROKER.start();

		final var connectionFactory = new CachingConnectionFactory(BROKER.getHost(), BROKER.getAmqpPort());
		connectionFactory.setUsername(BROKER.getAdminUsername());
		connectionFactory.setPassword(BROKER.getAdminPassword());
		final var admin = new RabbitAdmin(connectionFactory);
		for (final var name : new String[] {
			PASSES, PERMANENT, STUCK, SERVER_ERROR, ACKNOWLEDGED, BATCH
		}) {
			// As the topology for a consumer looks: a delivery limit, and a dead letter queue through the default exchange
			admin.declareQueue(QueueBuilder.durable(name).quorum().withArguments(Map.of(
				"x-delivery-limit", 1,
				"x-dead-letter-exchange", "",
				"x-dead-letter-routing-key", name + ".dlq")).build());
			admin.declareQueue(QueueBuilder.durable(name + ".dlq").quorum().build());
		}
		connectionFactory.destroy();
	}

	@AfterAll
	static void stopBroker() {
		BROKER.stop();
	}

	@Test
	void aTransientFailureThatPassesIsRetriedUntilTheMessageIsHandled() {
		run(Listeners.class, context -> {
			publish(context, PASSES);

			await().atMost(Duration.ofSeconds(10)).until(() -> Listeners.HANDLED.get() == 1);
			assertThat(Listeners.PASSES_ATTEMPTS.get()).isEqualTo(3);
			assertThat(context.getBean(RabbitTemplate.class).receive(PASSES + ".dlq", 500)).isNull();
		});
	}

	@Test
	void anyOtherFailureIsDeadLetteredAtOnce() {
		run(Listeners.class, context -> {
			publish(context, PERMANENT);

			assertThat(context.getBean(RabbitTemplate.class).receive(PERMANENT + ".dlq", 10_000)).isNotNull();
			assertThat(Listeners.PERMANENT_ATTEMPTS.get()).isEqualTo(1);
		});
	}

	@Test
	void aTransientFailureThatLastsGoesBackInTheQueueUntilItsDeliveryLimit() {
		run(Listeners.class, context -> {
			publish(context, STUCK);

			assertThat(context.getBean(RabbitTemplate.class).receive(STUCK + ".dlq", 10_000)).isNotNull();
			// Retried on each delivery, and delivered again once after the retries of the first
			assertThat(Listeners.STUCK_ATTEMPTS.get()).isEqualTo(2 * (MAX_RETRIES + 1));
		});
	}

	@Test
	void aServerErrorThatLastsIsDeadLetteredOnceItsRetriesAreOver() {
		run(Listeners.class, context -> {
			publish(context, SERVER_ERROR);

			assertThat(context.getBean(RabbitTemplate.class).receive(SERVER_ERROR + ".dlq", 10_000)).isNotNull();
			// Retried on the first delivery, and not put back in the queue for another
			assertThat(Listeners.SERVER_ERROR_ATTEMPTS.get()).isEqualTo(MAX_RETRIES + 1);
		});
	}

	@Test
	void aMessageTheListenerAcknowledgesIsNeitherRetriedNorDeadLettered() {
		run(Listeners.class, context -> {
			publish(context, ACKNOWLEDGED);

			await().atMost(Duration.ofSeconds(10)).until(() -> Listeners.ACKNOWLEDGED_ATTEMPTS.get() == 1);
			assertThat(context.getBean(RabbitTemplate.class).receive(ACKNOWLEDGED + ".dlq", 1_000)).isNull();
			assertThat(Listeners.ACKNOWLEDGED_ATTEMPTS.get()).isEqualTo(1);
		});
	}

	@Test
	void aBatchThatFailsIsDeadLetteredRatherThanDropped() {
		run(BatchListener.class, context -> {
			publish(context, BATCH);
			publish(context, BATCH);

			final var rabbitTemplate = context.getBean(RabbitTemplate.class);
			assertThat(rabbitTemplate.receive(BATCH + ".dlq", 10_000)).isNotNull();
			assertThat(rabbitTemplate.receive(BATCH + ".dlq", 10_000)).isNotNull();
		}, "spring.rabbitmq.listener.simple.consumer-batch-enabled=true", "spring.rabbitmq.listener.simple.batch-size=2");
	}

	private static void publish(final ApplicationContext context, final String queue) {
		context.getBean(Dept44RabbitPublisher.class).publish(OutgoingMessage.to("", queue).withPayload(new RabbitMqBrokerTest.Payload("value")));
	}

	private static void run(final Class<?> listeners, final ContextTest test, final String... properties) {
		Listeners.reset();
		new ApplicationContextRunner()
			.withInitializer(context -> new RabbitMqDefaultPropertiesEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class, RabbitMqConfiguration.class))
			.withUserConfiguration(listeners, ConsumerFailures.class)
			.withPropertyValues(
				"spring.rabbitmq.host=" + BROKER.getHost(),
				"spring.rabbitmq.port=" + BROKER.getAmqpPort(),
				"spring.rabbitmq.username=" + BROKER.getAdminUsername(),
				"spring.rabbitmq.password=" + BROKER.getAdminPassword(),
				"spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
				"spring.rabbitmq.listener.simple.retry.max-interval=20ms",
				"spring.rabbitmq.listener.simple.retry.max-retries=" + MAX_RETRIES)
			.withPropertyValues(properties)
			.run(context -> {
				assertThat(context).hasNotFailed();
				test.accept(context);
				// However a message ends, the consumer carries on with the next one rather than being restarted
				assertThat(ConsumerFailures.EVENTS).isEmpty();
			});
	}

	@FunctionalInterface
	private interface ContextTest {
		void accept(final ApplicationContext context) throws Exception;
	}

	static class ConsumerFailures {

		static final List<ListenerContainerConsumerFailedEvent> EVENTS = new CopyOnWriteArrayList<>();

		@EventListener
		void consumerFailed(final ListenerContainerConsumerFailedEvent event) {
			EVENTS.add(event);
		}
	}

	static class Listeners {

		static final AtomicInteger PASSES_ATTEMPTS = new AtomicInteger();
		static final AtomicInteger HANDLED = new AtomicInteger();
		static final AtomicInteger PERMANENT_ATTEMPTS = new AtomicInteger();
		static final AtomicInteger STUCK_ATTEMPTS = new AtomicInteger();
		static final AtomicInteger SERVER_ERROR_ATTEMPTS = new AtomicInteger();
		static final AtomicInteger ACKNOWLEDGED_ATTEMPTS = new AtomicInteger();

		static void reset() {
			PASSES_ATTEMPTS.set(0);
			HANDLED.set(0);
			PERMANENT_ATTEMPTS.set(0);
			STUCK_ATTEMPTS.set(0);
			SERVER_ERROR_ATTEMPTS.set(0);
			ACKNOWLEDGED_ATTEMPTS.set(0);
			ConsumerFailures.EVENTS.clear();
		}

		/** As a database that is back after two attempts */
		@RabbitListener(queues = PASSES)
		void passes(final RabbitMqBrokerTest.Payload payload) {
			if (PASSES_ATTEMPTS.incrementAndGet() <= 2) {
				throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction", new ConnectException("Connection refused"));
			}
			HANDLED.incrementAndGet();
		}

		@RabbitListener(queues = PERMANENT)
		void permanent(final RabbitMqBrokerTest.Payload payload) {
			PERMANENT_ATTEMPTS.incrementAndGet();
			throw new IllegalStateException("A bug, the same every time");
		}

		/** As a database that does not come back */
		@RabbitListener(queues = STUCK)
		void stuck(final RabbitMqBrokerTest.Payload payload) {
			STUCK_ATTEMPTS.incrementAndGet();
			throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction", new ConnectException("Connection refused"));
		}

		/** As a called service that answers 500 to this message every time */
		@RabbitListener(queues = SERVER_ERROR)
		void serverError(final RabbitMqBrokerTest.Payload payload) {
			SERVER_ERROR_ATTEMPTS.incrementAndGet();
			throw new ServerProblem(HttpStatus.BAD_GATEWAY, "the called service failed");
		}

		/** As a listener that has nothing to do with the message, and says so */
		@RabbitListener(queues = ACKNOWLEDGED)
		void acknowledged(final RabbitMqBrokerTest.Payload payload) {
			ACKNOWLEDGED_ATTEMPTS.incrementAndGet();
			throw new ImmediateAcknowledgeAmqpException("Nothing to do for this message");
		}
	}

	static class BatchListener {

		@RabbitListener(queues = BATCH)
		void batch(final List<Message> messages) {
			throw new IllegalStateException("A bug, the same for the whole batch");
		}
	}
}
