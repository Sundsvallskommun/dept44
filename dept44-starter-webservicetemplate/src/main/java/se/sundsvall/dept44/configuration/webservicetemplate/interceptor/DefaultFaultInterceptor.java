package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

public class DefaultFaultInterceptor extends ClientInterceptorAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultFaultInterceptor.class);

	@Override
	public boolean handleFault(final MessageContext messageContext) {
		getFault(messageContext).ifPresent(this::handleSoapFault);

		return true;
	}

	@Override
	public boolean handleResponse(final MessageContext messageContext) throws WebServiceClientException {
		return handleFault(messageContext);
	}

	private void handleSoapFault(final SoapFault soapFault) {
		final String faultStringOrReason = soapFault.getFaultStringOrReason();
		LOG.error("Got a soap fault: {}", faultStringOrReason);
		throw Problem.builder()
			.withTitle("Error while calling SOAP-service")
			.withStatus(INTERNAL_SERVER_ERROR)
			.withDetail(faultStringOrReason)
			.build();
	}

	Optional<SoapFault> getFault(final MessageContext messageContext) {
		final SoapMessage soapMessage = (SoapMessage) messageContext.getResponse();
		final SoapFault soapFault = soapMessage.getEnvelope().getBody().getFault();
		return Optional.ofNullable(soapFault);
	}
}
