package se.sundsvall.dept44.async;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

	private static final String MUNICIPALITY_ID_KEY = "municipalityId";

	private final MdcTaskDecorator decorator = new MdcTaskDecorator();

	@AfterEach
	void cleanUp() {
		MDC.clear();
		Identifier.remove();
	}

	@Test
	void propagatesFullMdcAndIdentifierToWorkerThread() throws InterruptedException {
		// Arrange - populate the caller thread's context
		MDC.put(RequestId.MDC_REQUEST_ID_KEY, "req-1");
		MDC.put(MUNICIPALITY_ID_KEY, "2281");
		Identifier.set(Identifier.parse("joe001doe; type=adAccount"));

		final var observedRequestId = new AtomicReference<String>();
		final var observedMunicipalityId = new AtomicReference<String>();
		final var observedSentBy = new AtomicReference<String>();
		final var observedIdentifier = new AtomicReference<Identifier>();

		final var decorated = decorator.decorate(() -> {
			observedRequestId.set(MDC.get(RequestId.MDC_REQUEST_ID_KEY));
			observedMunicipalityId.set(MDC.get(MUNICIPALITY_ID_KEY));
			observedSentBy.set(MDC.get(Identifier.MDC_SENT_BY_KEY));
			observedIdentifier.set(Identifier.get());
		});

		// Act - run on a *different* thread
		runOnNewThread(decorated);

		// Assert - the worker saw the caller's full context
		assertThat(observedRequestId).hasValue("req-1");
		assertThat(observedMunicipalityId).hasValue("2281");
		assertThat(observedSentBy).hasValue("joe001doe");
		assertThat(observedIdentifier.get()).isNotNull();
		assertThat(observedIdentifier.get().getValue()).isEqualTo("joe001doe");
		assertThat(observedIdentifier.get().getTypeString()).isEqualTo("adAccount");
	}

	@Test
	void restoresPreExistingWorkerContextAfterExecution() throws InterruptedException {
		// Arrange - caller context that must NOT leak into the worker afterwards
		MDC.put(RequestId.MDC_REQUEST_ID_KEY, "caller-req");
		Identifier.set(Identifier.parse("caller; type=adAccount"));
		final var decorated = decorator.decorate(() -> {});

		final var remainingRequestId = new AtomicReference<String>("unset");
		final var remainingIdentifier = new AtomicReference<Identifier>();

		// Act - simulate a pooled worker thread that already carries its own context
		runOnNewThread(() -> {
			MDC.put(RequestId.MDC_REQUEST_ID_KEY, "worker-pre");
			Identifier.set(Identifier.parse("workerPre; type=adAccount"));

			decorated.run();

			remainingRequestId.set(MDC.get(RequestId.MDC_REQUEST_ID_KEY));
			remainingIdentifier.set(Identifier.get());
		});

		// Assert - the worker's own context is restored, not the caller's
		assertThat(remainingRequestId).hasValue("worker-pre");
		assertThat(remainingIdentifier.get()).isNotNull();
		assertThat(remainingIdentifier.get().getValue()).isEqualTo("workerPre");
	}

	@Test
	void clearsContextWhenWorkerThreadHadNone() throws InterruptedException {
		// Arrange
		MDC.put(RequestId.MDC_REQUEST_ID_KEY, "caller-req");
		Identifier.set(Identifier.parse("caller; type=adAccount"));
		final var decorated = decorator.decorate(() -> {});

		final var remainingRequestId = new AtomicReference<String>("unset");
		final var remainingIdentifier = new AtomicReference<Identifier>(Identifier.create());

		// Act - fresh worker thread with empty context
		runOnNewThread(() -> {
			decorated.run();
			remainingRequestId.set(MDC.get(RequestId.MDC_REQUEST_ID_KEY));
			remainingIdentifier.set(Identifier.get());
		});

		// Assert - no context leaked onto the worker thread
		assertThat(remainingRequestId.get()).isNull();
		assertThat(remainingIdentifier.get()).isNull();
	}

	private static void runOnNewThread(final Runnable runnable) throws InterruptedException {
		final var thread = new Thread(runnable);
		thread.start();
		thread.join();
	}
}
