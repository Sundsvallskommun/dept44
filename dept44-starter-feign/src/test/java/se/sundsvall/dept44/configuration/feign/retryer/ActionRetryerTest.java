package se.sundsvall.dept44.configuration.feign.retryer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionRetryerTest {

	private final Action actionMock = Mockito.mock(Action.class);

	@Test
	void continueOrPropagate() {
		final var actionRetryer = new ActionRetryer(actionMock, 2);
		final var retryableException = new RetryableException(200, "message", Request.HttpMethod.GET, (Long) null,
			Mockito.mock(Request.class));

		assertDoesNotThrow(() -> actionRetryer.continueOrPropagate(retryableException));
		assertDoesNotThrow(() -> actionRetryer.continueOrPropagate(retryableException));
		final var exception = assertThrows(RetryableException.class, () -> actionRetryer.continueOrPropagate(
			retryableException));

		assertThat(exception).isSameAs(retryableException);
		verify(actionMock, times(2)).execute();
	}

	@Test
	void testClone() {
		final var actionRetryer = new ActionRetryer(actionMock, 2);
		final var actionRetryerClone = actionRetryer.clone();

		assertThat(actionRetryerClone).isNotSameAs(actionRetryer);
		assertThat(actionRetryerClone).isInstanceOf(ActionRetryer.class);
	}
}
