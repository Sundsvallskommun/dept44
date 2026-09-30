package se.sundsvall.dept44.test.annotation.wiremock;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.WireMockConfigurationCustomizer;

/**
 * Gives a WireMock server a {@link SwitchableFileSource}, for a server that the test classes of a shared application
 * context take turns to use.
 */
public class SwitchableFileSourceCustomizer implements WireMockConfigurationCustomizer {

	@Override
	public void customize(final WireMockConfiguration configuration, final ConfigureWireMock options) {
		configuration.fileSource(new SwitchableFileSource());
	}
}
