package se.sundsvall.dept44.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

class Dept44AutoConfigurationImportFilterTest {

	private static final String USER_DETAILS_SERVICE_AUTO_CONFIGURATION = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration";

	@Test
	void leavesOutTheUserDetailsServiceAutoConfigurationOnly() {
		final var matches = new Dept44AutoConfigurationImportFilter().match(new String[] {
			"org.example.SomeAutoConfiguration", USER_DETAILS_SERVICE_AUTO_CONFIGURATION, null
		}, null);

		assertThat(matches).containsExactly(true, false, true);
	}

	@Test
	void isRegisteredForSpringBoot() {
		assertThat(SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class, getClass().getClassLoader()))
			.hasAtLeastOneElementOfType(Dept44AutoConfigurationImportFilter.class);
	}

	@Test
	void excludedClassExists() throws ClassNotFoundException {
		// Fails when Spring Boot moves or renames it, which would make the filter silently do nothing
		assertThat(Class.forName(USER_DETAILS_SERVICE_AUTO_CONFIGURATION)).isNotNull();
	}
}
