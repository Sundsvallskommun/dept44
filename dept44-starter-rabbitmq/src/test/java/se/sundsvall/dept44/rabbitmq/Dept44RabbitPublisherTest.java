package se.sundsvall.dept44.rabbitmq;

import com.rabbitmq.client.BlockedListener;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Address;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.BLOCKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.INTERRUPTED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.NACKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.RETURNED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.TIMEOUT;

@ExtendWith({
	MockitoExtension.class, OutputCaptureExtension.class
})
@NullUnmarked
class Dept44RabbitPublisherTest {

	private static final String EXCHANGE = "exchange";
	private static final String ROUTING_KEY = "routing-key";
	private static final String PAYLOAD = "payload";

	@Mock
	private RabbitTemplate rabbitTemplate;

	@Mock
	private ConnectionFactory connectionFactory;

	@Mock
	private ConnectionFactory publisherConnectionFactory;

	@Mock
	private Connection connection;

	private Dept44RabbitPublisher publisher;

	@BeforeEach
	void setUp() {
		// As with Spring Boot's CachingConnectionFactory, the template publishes on the publisher connection factory
		when(rabbitTemplate.isUsePublisherConnection()).thenReturn(true);
		when(rabbitTemplate.getConnectionFactory()).thenReturn(connectionFactory);
		when(connectionFactory.getPublisherConnectionFactory()).thenReturn(publisherConnectionFactory);
		when(publisherConnectionFactory.isPublisherConfirms()).thenReturn(true);
		when(publisherConnectionFactory.isPublisherReturns()).thenReturn(true);
		when(rabbitTemplate.isMandatoryFor(any())).thenReturn(true);
		publisher = new Dept44RabbitPublisher(rabbitTemplate, Duration.ofMillis(200));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"PT0S", "PT-1S"
	})
	void confirmTimeoutMustBePositive(final Duration confirmTimeout) {
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> new Dept44RabbitPublisher(rabbitTemplate, confirmTimeout))
			.withMessage("The confirm timeout must be positive, was " + confirmTimeout);
	}

	@Test
	void aConfirmTimeoutUnderAMillisecondIsWaitedOut() {
		final var confirmTimeout = Duration.ofNanos(900_000);
		final var sentAt = new AtomicLong();
		// The broker never confirms
		brokerAnswers(_ -> sentAt.set(System.nanoTime()));
		final var subMillisecondPublisher = new Dept44RabbitPublisher(rabbitTemplate, confirmTimeout);

		assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> subMillisecondPublisher.publish(message()))
			.satisfies(e -> assertThat(e.getReason()).isEqualTo(TIMEOUT));
		assertThat(System.nanoTime() - sentAt.get()).isGreaterThanOrEqualTo(confirmTimeout.toNanos());
	}

	@Test
	void publishSetsThePropertiesAndReturnsOnAck(final CapturedOutput output) {
		final var sent = brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

		publisher.publish(message()
			.withMessageId("message-id")
			.withCorrelationId("correlation-id")
			.withReplyTo("reply-exchange", "reply-key")
			.withHeader("x-custom", "value"));

		final var properties = sent.get().getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo("message-id");
		assertThat(properties.getCorrelationId()).isEqualTo("correlation-id");
		assertThat(properties.getReplyToAddress()).isEqualTo(new Address("reply-exchange", "reply-key"));
		assertThat(properties.getHeaders()).containsEntry("x-custom", "value");
		assertThat(output.getOut()).contains("Message message-id to exchange 'exchange' with routing key 'routing-key' was confirmed by the broker");
	}

	@Test
	void publishGeneratesAMessageIdWhenAbsent() {
		final var correlation = new AtomicReference<CorrelationData>();
		final var sent = brokerAnswers(correlationData -> {
			correlation.set(correlationData);
			correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
		});

		publisher.publish(message());

		final var properties = sent.get().getMessageProperties();
		assertThat(properties.getMessageId()).isNotBlank().isNotEqualTo(correlation.get().getId());
		assertThat(properties.getCorrelationId()).isNull();
		assertThat(properties.getReplyTo()).isNull();
	}

	@Test
	void nackedMessage(final CapturedOutput output) {
		brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(false, "Channel closed by application")));

		assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> publisher.publish(message().withMessageId("message-id")))
			.withMessage("Message message-id to exchange 'exchange' with routing key 'routing-key' was nacked by the broker: Channel closed by application")
			.satisfies(e -> assertThat(e).extracting(RabbitPublishException::getReason, RabbitPublishException::getMessageId).containsExactly(NACKED, "message-id"));
		// The exception says it, and the service logs it
		assertThat(output.getOut()).doesNotContain("was confirmed by the broker");
	}

	@Test
	void returnedMessage() {
		brokerAnswers(correlationData -> {
			correlationData.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", EXCHANGE, ROUTING_KEY));
			correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
		});

		assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> publisher.publish(message().withMessageId("message-id")))
			.withMessage("Message message-id to exchange 'exchange' with routing key 'routing-key' was returned by the broker: 312 NO_ROUTE")
			.satisfies(e -> assertThat(e).extracting(RabbitPublishException::getReason, RabbitPublishException::getMessageId).containsExactly(RETURNED, "message-id"));
	}

	@Test
	void noConfirmInTime() {
		brokerAnswers(_ -> {});

		assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> publisher.publish(message().withMessageId("message-id")))
			.withMessage("Message message-id to exchange 'exchange' with routing key 'routing-key' was not confirmed within PT0.2S")
			.satisfies(e -> assertThat(e).extracting(RabbitPublishException::getReason, RabbitPublishException::getMessageId).containsExactly(TIMEOUT, "message-id"));
	}

	@Test
	void interruptedWhileWaiting() {
		brokerAnswers(_ -> Thread.currentThread().interrupt());

		try {
			assertThatExceptionOfType(RabbitPublishException.class)
				.isThrownBy(() -> publisher.publish(message().withMessageId("message-id")))
				.withMessage("Interrupted while waiting for the confirm of Message message-id to exchange 'exchange' with routing key 'routing-key'")
				.satisfies(e -> assertThat(e).extracting(RabbitPublishException::getReason, RabbitPublishException::getMessageId).containsExactly(INTERRUPTED, "message-id"))
				.withCauseInstanceOf(InterruptedException.class);
			// The publisher keeps the thread interrupted
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			// Clears the flag also when an assertion fails, so that it does not leak into the next test
			Thread.interrupted();
		}
	}

	@Test
	void confirmCompletedExceptionally() {
		final var cause = new IllegalStateException("broken");
		brokerAnswers(correlationData -> correlationData.getFuture().completeExceptionally(cause));

		assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> publisher.publish(message().withMessageId("message-id")))
			.withMessage("Message message-id to exchange 'exchange' with routing key 'routing-key' was not confirmed")
			.satisfies(e -> assertThat(e).extracting(RabbitPublishException::getReason, RabbitPublishException::getMessageId).containsExactly(NACKED, "message-id"))
			.withCause(cause);
	}

	@Test
	void aBlockedConnectionFailsThePublishAtOnceUntilTheBrokerUnblocksIt() throws Exception {
		final var blockedListener = blockedListener(connectionListener());
		when(connection.isOpen()).thenReturn(true);

		blockedListener.handleBlocked("low on memory");

		assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> publisher.publish(message().withMessageId("message-id")))
			.withMessage("Message message-id to exchange 'exchange' with routing key 'routing-key' was not sent, the broker blocks publishing: low on memory")
			.satisfies(e -> assertThat(e).extracting(RabbitPublishException::getReason, RabbitPublishException::getMessageId).containsExactly(BLOCKED, "message-id"));
		verifyNothingSent();

		blockedListener.handleUnblocked();
		brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

		assertThatNoException().isThrownBy(() -> publisher.publish(message()));
	}

	@Test
	void aBlockedConnectionThatClosesNoLongerFailsThePublish() throws Exception {
		final var connectionListener = connectionListener();
		blockedListener(connectionListener).handleBlocked("low on disk");
		// Open, as the proxy is once the factory has opened the connection again, so that only onClose lets the publish through
		lenient().when(connection.isOpen()).thenReturn(true);

		connectionListener.onClose(connection);
		brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

		assertThatNoException().isThrownBy(() -> publisher.publish(message()));
	}

	@Test
	void aBlockedConnectionThatDiesNoLongerFailsThePublish() throws Exception {
		blockedListener(connectionListener()).handleBlocked("low on memory");

		// The broker restarts to clear the alarm. The connection dies without an unblock, and the factory tells no listener
		when(connection.isOpen()).thenReturn(false);
		brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

		assertThatNoException().isThrownBy(() -> publisher.publish(message()));
	}

	@Test
	void aBlockedConnectionThatTheFactoryOpensAgainNoLongerFailsThePublish() throws Exception {
		final var connectionListener = connectionListener();
		blockedListener(connectionListener).handleBlocked("low on memory");

		// The factory opens a dead connection again behind the same proxy, and tells its listeners only that it opened it
		lenient().when(connection.isOpen()).thenReturn(true);
		connectionListener.onCreate(connection);
		brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

		assertThatNoException().isThrownBy(() -> publisher.publish(message()));
	}

	@Test
	void aCreatedConnectionThatIsNullIsIgnored() {
		final var connectionListener = connectionListener();

		assertThatNoException().isThrownBy(() -> connectionListener.onCreate(null));
	}

	@Test
	void watchesThePublisherConnectionTheTemplatePublishesOn() {
		verify(publisherConnectionFactory).addConnectionListener(any(BlockedConnections.class));
		verify(connectionFactory, never()).addConnectionListener(any());
	}

	@Test
	void confirmsAreRequiredOnTheConnectionTheTemplatePublishesOn() {
		// The template's own factory may confirm, but the template publishes on its publisher connection factory
		lenient().when(connectionFactory.isPublisherConfirms()).thenReturn(true);
		when(publisherConnectionFactory.isPublisherConfirms()).thenReturn(false);

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> new Dept44RabbitPublisher(rabbitTemplate, Duration.ofSeconds(1)))
			.withMessage("Dept44RabbitPublisher requires spring.rabbitmq.publisher-confirm-type=correlated");
	}

	@Test
	void returnsAreRequiredOnTheConnectionTheTemplatePublishesOn() {
		when(publisherConnectionFactory.isPublisherReturns()).thenReturn(false);

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> new Dept44RabbitPublisher(rabbitTemplate, Duration.ofSeconds(1)))
			.withMessage("Dept44RabbitPublisher requires spring.rabbitmq.publisher-returns=true");
	}

	@Test
	void mandatoryIsRequired() {
		when(rabbitTemplate.isMandatoryFor(any())).thenReturn(false);

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> new Dept44RabbitPublisher(rabbitTemplate, Duration.ofSeconds(1)))
			.withMessage("Dept44RabbitPublisher requires spring.rabbitmq.template.mandatory=true");
	}

	@Test
	void aTemplateThatPublishesOnTheListenersConnectionIsRefused() {
		// As a template created with new outside a bean, which the starter never sees
		when(rabbitTemplate.isUsePublisherConnection()).thenReturn(false);

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> new Dept44RabbitPublisher(rabbitTemplate, Duration.ofSeconds(1)))
			.withMessageStartingWith("Dept44RabbitPublisher requires a RabbitTemplate that publishes on a connection of its own");
	}

	@Test
	void aTemplateWhoseConnectionFactoryHasNoPublisherConnectionFactoryIsRefused() {
		// RabbitTemplate then publishes on its connection factory, which the listeners use too
		when(connectionFactory.getPublisherConnectionFactory()).thenReturn(null);

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> new Dept44RabbitPublisher(rabbitTemplate, Duration.ofSeconds(1)))
			.withMessageStartingWith("Dept44RabbitPublisher requires a RabbitTemplate that publishes on a connection of its own")
			.withMessageContaining("has no publisher connection factory");
	}

	@Test
	void exceptionCarriesTheGeneratedMessageId() {
		final var sent = brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nacked")));

		assertThatExceptionOfType(RabbitPublishException.class)
			.isThrownBy(() -> publisher.publish(message()))
			.satisfies(e -> assertThat(e.getMessageId()).isNotBlank().isEqualTo(sent.get().getMessageProperties().getMessageId()));
	}

	@Test
	void publishRequiresAPayload() {
		final var message = OutgoingMessage.to(EXCHANGE, ROUTING_KEY);

		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> publisher.publish(message))
			.withMessage("A message to exchange 'exchange' has no payload");
		verifyNothingSent();
	}

	@Test
	void requestIsPublishedWithItsProperties() {
		final var sent = brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

		publisher.request(message()
			.withMessageId("message-id")
			.withCorrelationId("correlation-id")
			.withReplyTo("reply-exchange", "reply-key"));

		final var properties = sent.get().getMessageProperties();
		assertThat(properties.getMessageId()).isEqualTo("message-id");
		assertThat(properties.getCorrelationId()).isEqualTo("correlation-id");
		assertThat(properties.getReplyTo()).isEqualTo("reply-exchange/reply-key");
	}

	@Test
	void requestRequiresMessageIdCorrelationIdAndReplyTo() {
		final var message = message();

		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> publisher.request(message))
			.withMessage("A request to exchange 'exchange' lacks message id, correlation id, reply-to");
		verifyNothingSent();
	}

	@Test
	void requestNamesOnlyWhatIsMissing() {
		final var message = message().withMessageId("message-id").withCorrelationId("correlation-id");

		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> publisher.request(message))
			.withMessage("A request to exchange 'exchange' lacks reply-to");
	}

	private void verifyNothingSent() {
		verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
	}

	private static OutgoingMessage message() {
		return OutgoingMessage.to(EXCHANGE, ROUTING_KEY).withPayload(PAYLOAD);
	}

	/**
	 * The listener the publisher added to the publisher connection factory, in setUp.
	 */
	private ConnectionListener connectionListener() {
		final var captor = ArgumentCaptor.forClass(ConnectionListener.class);
		verify(publisherConnectionFactory).addConnectionListener(captor.capture());
		return captor.getValue();
	}

	/**
	 * Opens the connection, as the factory does, and returns what the broker's blocked notifications reach.
	 */
	private BlockedListener blockedListener(final ConnectionListener connectionListener) {
		connectionListener.onCreate(connection);
		final var captor = ArgumentCaptor.forClass(BlockedListener.class);
		verify(connection).addBlockedListener(captor.capture());
		return captor.getValue();
	}

	/**
	 * Runs the publisher's post-processor on a message, as the template does, and lets the broker act on the confirm.
	 */
	private AtomicReference<Message> brokerAnswers(final Consumer<CorrelationData> broker) {
		final var sent = new AtomicReference<Message>();
		doAnswer(invocation -> {
			final MessagePostProcessor postProcessor = invocation.getArgument(3);
			sent.set(postProcessor.postProcessMessage(new Message(new byte[0])));
			broker.accept(invocation.getArgument(4));
			return null;
		}).when(rabbitTemplate).convertAndSend(eq(EXCHANGE), anyString(), eq(PAYLOAD), any(MessagePostProcessor.class), any(CorrelationData.class));
		return sent;
	}
}
