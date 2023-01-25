package se.sundsvall.dept44.configuration.feign.retryer;

import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ActionRetryerTest {


    private Action actionMock = Mockito.mock(Action.class);

    @Test
    void continueOrPropagate() {
        var actionRetryer = new ActionRetryer(actionMock, 2);
        var retryableException = new RetryableException(200, "message", Request.HttpMethod.GET, null, Mockito.mock(Request.class));

        assertDoesNotThrow(() -> actionRetryer.continueOrPropagate(retryableException));
        assertDoesNotThrow(() -> actionRetryer.continueOrPropagate(retryableException));
        var exception = assertThrows(RetryableException.class, () -> actionRetryer.continueOrPropagate(retryableException));

        assertThat(exception).isSameAs(retryableException);
        verify(actionMock, times(2)).execute();
    }

    @Test
    void testClone() {
        var actionRetryer = new ActionRetryer(actionMock, 2);
        var actionRetryerClone = actionRetryer.clone();

        assertThat(actionRetryerClone).isNotSameAs(actionRetryer);
        assertThat(actionRetryerClone).isInstanceOf(ActionRetryer.class);
    }
}