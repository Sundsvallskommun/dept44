package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import static org.zalando.problem.Status.INTERNAL_SERVER_ERROR;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;
import org.zalando.problem.Problem;

public class DefaultFaultInterceptor extends ClientInterceptorAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultFaultInterceptor.class);

	@Override
	public boolean handleFault(MessageContext messageContext) {
		getFault(messageContext).ifPresent(this::handleSoapFault);

		return true;
	}

	@Override
	public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
		return handleFault(messageContext);
	}

	private void handleSoapFault(SoapFault soapFault) {
		String faultStringOrReason = soapFault.getFaultStringOrReason();
		LOG.error("Got a soap fault: {}", faultStringOrReason);
		throw Problem.builder()
			.withTitle("Error while calling SOAP-service")
			.withStatus(INTERNAL_SERVER_ERROR)
			.withDetail(faultStringOrReason)
			.build();
	}

	Optional<SoapFault> getFault(MessageContext messageContext) {
		SoapMessage soapMessage = (SoapMessage) messageContext.getResponse();
		SoapFault soapFault = soapMessage.getEnvelope().getBody().getFault();
		return Optional.ofNullable(soapFault);
	}
}
