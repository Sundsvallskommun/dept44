package se.sundsvall.dept44.test.annotation.wiremock;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchableFileSourceCustomizerTest {

	@Test
	void customizeGivesServerSwitchableFileSource() {
		final var configuration = WireMockConfiguration.options();

		new SwitchableFileSourceCustomizer().customize(configuration, null);

		assertThat(configuration.filesRoot()).isInstanceOf(SwitchableFileSource.class);
	}
}
