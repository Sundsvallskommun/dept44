package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapEnvelope;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

class DefaultFaultInterceptorTest {

	private final DefaultFaultInterceptor interceptor = new DefaultFaultInterceptor();
	@Mock
	private MessageContext messageContextMock;
	@Mock
	private SoapMessage soapMessageMock;
	@Mock
	private SoapEnvelope soapEnvelopeMock;
	@Mock
	private SoapBody soapBodyMock;
	@Mock
	private SoapFault soapFaultMock;

	@BeforeEach
	void initMocks() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testInheritance() {
		assertThat(interceptor).isInstanceOf(ClientInterceptorAdapter.class);
	}

	@Test
	void testHandleResponseWhenFaultNotPresent() {
		// Setup mocks
		when(messageContextMock.getResponse()).thenReturn(soapMessageMock);
		when(soapMessageMock.getEnvelope()).thenReturn(soapEnvelopeMock);
		when(soapEnvelopeMock.getBody()).thenReturn(soapBodyMock);

		// Call and assert
		assertThat(interceptor.handleResponse(messageContextMock)).isTrue();

		// Verify mocks
		verify(messageContextMock).getResponse();
		verify(soapMessageMock).getEnvelope();
		verify(soapEnvelopeMock).getBody();
		verify(soapBodyMock).getFault();
	}

	@Test
	void testHandleResponseWhenFaultPresent() {
		// Create variables
		final var faultStringOrReason = "faultStringOrReason";

		// Setup mocks
		when(messageContextMock.getResponse()).thenReturn(soapMessageMock);
		when(soapMessageMock.getEnvelope()).thenReturn(soapEnvelopeMock);
		when(soapEnvelopeMock.getBody()).thenReturn(soapBodyMock);
		when(soapBodyMock.getFault()).thenReturn(soapFaultMock);
		when(soapFaultMock.getFaultStringOrReason()).thenReturn(faultStringOrReason);

		// Call and assert
		final ThrowableProblem problem = assertThrows(ThrowableProblem.class, () -> interceptor.handleResponse(messageContextMock));

		assertThat(problem.getTitle()).isEqualTo("Error while calling SOAP-service");
		assertThat(problem.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(problem.getDetail()).isEqualTo(faultStringOrReason);

		// Verify mocks
		verify(messageContextMock).getResponse();
		verify(soapMessageMock).getEnvelope();
		verify(soapEnvelopeMock).getBody();
		verify(soapBodyMock).getFault();
	}

	@Test
	void testHandleFaultWhenFaultNotPresent() {
		// Setup mocks
		when(messageContextMock.getResponse()).thenReturn(soapMessageMock);
		when(soapMessageMock.getEnvelope()).thenReturn(soapEnvelopeMock);
		when(soapEnvelopeMock.getBody()).thenReturn(soapBodyMock);

		// Call and assert
		assertThat(interceptor.handleFault(messageContextMock)).isTrue();

		// Verify mocks
		verify(messageContextMock).getResponse();
		verify(soapMessageMock).getEnvelope();
		verify(soapEnvelopeMock).getBody();
		verify(soapBodyMock).getFault();
	}

	@Test
	void testHandleFaultWhenFaultPresent() {
		// Create variables
		final var faultStringOrReason = "faultStringOrReason";

		// Setup mocks
		when(messageContextMock.getResponse()).thenReturn(soapMessageMock);
		when(soapMessageMock.getEnvelope()).thenReturn(soapEnvelopeMock);
		when(soapEnvelopeMock.getBody()).thenReturn(soapBodyMock);
		when(soapBodyMock.getFault()).thenReturn(soapFaultMock);
		when(soapFaultMock.getFaultStringOrReason()).thenReturn(faultStringOrReason);

		// Call and assert
		final ThrowableProblem problem = assertThrows(ThrowableProblem.class, () -> interceptor.handleFault(messageContextMock));

		assertThat(problem.getTitle()).isEqualTo("Error while calling SOAP-service");
		assertThat(problem.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(problem.getDetail()).isEqualTo(faultStringOrReason);

		// Verify mocks
		verify(messageContextMock).getResponse();
		verify(soapMessageMock).getEnvelope();
		verify(soapEnvelopeMock).getBody();
		verify(soapBodyMock).getFault();
	}
}
