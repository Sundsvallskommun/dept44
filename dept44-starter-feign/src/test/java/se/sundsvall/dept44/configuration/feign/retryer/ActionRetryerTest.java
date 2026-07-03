package se.sundsvall.dept44.configuration.feign.retryer;

import feign.Request;
import feign.RetryableException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ActionRetryerTest {

	private final Action actionMock = Mockito.mock(Action.class);

	private static RetryableException retryableException(final Map<String, Collection<String>> headers) {
		final var request = Request.create(Request.HttpMethod.GET, "http://localhost", headers, Request.Body.empty(), null);
		return new RetryableException(200, "message", Request.HttpMethod.GET, (Long) null, request);
	}

	@Test
	void continueOrPropagate() {
		final var actionRetryer = new ActionRetryer(actionMock, 2);
		final var retryableException = retryableException(Map.of("Authorization", List.of("Bearer abc")));

		assertDoesNotThrow(() -> actionRetryer.continueOrPropagate(retryableException));
		assertDoesNotThrow(() -> actionRetryer.continueOrPropagate(retryableException));
		final var exception = assertThrows(RetryableException.class, () -> actionRetryer.continueOrPropagate(retryableException));

		assertThat(exception).isSameAs(retryableException);
		// The action must receive the exact Authorization header carried by the failed request.
		verify(actionMock, times(2)).execute("Bearer abc");
	}

	@Test
	void continueOrPropagatePassesNullWhenNoAuthorizationHeader() {
		final var actionRetryer = new ActionRetryer(actionMock, 1);
		final var retryableException = retryableException(Map.of());

		assertDoesNotThrow(() -> actionRetryer.continueOrPropagate(retryableException));

		verify(actionMock).execute(isNull());
	}

	@Test
	void testClone() {
		final var actionRetryer = new ActionRetryer(actionMock, 2);
		final var actionRetryerClone = actionRetryer.clone();

		assertThat(actionRetryerClone).isNotSameAs(actionRetryer);
		assertThat(actionRetryerClone).isInstanceOf(ActionRetryer.class);
	}
}
