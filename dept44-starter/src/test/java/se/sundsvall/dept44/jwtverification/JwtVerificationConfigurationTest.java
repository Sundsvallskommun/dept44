package se.sundsvall.dept44.jwtverification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.dept44.jwtverification.JwtVerificationConfiguration.FILTER_NAME;
import static se.sundsvall.dept44.jwtverification.JwtVerificationConfiguration.FILTER_ORDER;

class JwtVerificationConfigurationTest {

	private static final String PREFIX = "dept44.jwt-verification.";
	private static final String ENABLED = PREFIX + "enabled=true";
	private static final String HEADER_NAME = PREFIX + "verifiers.gateway.header-name=x-gateway-jwt";
	private static final String JWK_SET_URI = PREFIX + "verifiers.gateway.jwk-set-uris[0]=https://gateway.example/jwks";

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, JwtVerificationConfiguration.class));

	@Test
	void notRegisteredByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(FILTER_NAME);
			assertThat(context).doesNotHaveBean(JwtVerificationProperties.class);
		});
	}

	@Test
	void notRegisteredWhenDisabled() {
		contextRunner
			.withPropertyValues(PREFIX + "enabled=false")
			.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FILTER_NAME));
	}

	@Test
	void registeredWhenEnabled() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI)
			.run(context -> {
				assertThat(context).hasNotFailed();
				final var registration = context.getBean(FILTER_NAME, FilterRegistrationBean.class);
				assertThat(registration.getFilter()).isInstanceOf(JwtVerificationFilter.class);
				assertThat(registration.getOrder()).isEqualTo(FILTER_ORDER);

				// The decoders are the filter's own, so they can not make a service's resource-server decoder back off
				assertThat(context).doesNotHaveBean(JwtDecoder.class);
			});
	}

	@Test
	void verifiedHeadersAreMaskedInThePayloadLog() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI)
			.run(context -> {
				final var headerFilter = context.getBean(HeaderFilter.class);
				final var headers = HttpHeaders.empty()
					.update("X-Gateway-JWT", "eyJhbGciOiJSUzI1NiJ9.e30.c2ln")
					.update("x-request-id", "request-id");

				final var filtered = headerFilter.filter(headers);

				assertThat(filtered.get("x-gateway-jwt")).containsExactly(JwtVerificationConfiguration.MASK);
				assertThat(filtered.get("x-request-id")).containsExactly("request-id");
			});
	}

	@Test
	void defaults() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI)
			.run(context -> {
				final var properties = context.getBean(JwtVerificationProperties.class);
				assertThat(properties.excludedPaths()).isEmpty();

				final var verifier = properties.verifiers().get("gateway");
				assertThat(verifier.excludedPaths()).isEmpty();
				assertThat(verifier.issuer()).isNull();
				assertThat(verifier.audiences()).isEmpty();
				assertThat(verifier.algorithms()).containsExactly(SignatureAlgorithm.RS256);
				assertThat(verifier.connectTimeout()).hasSeconds(5);
				assertThat(verifier.readTimeout()).hasSeconds(10);
			});
	}

	@Test
	void severalVerifiersWithSeveralUrls() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI,
				PREFIX + "verifiers.gateway.jwk-set-uris[1]=https://external-gateway.example/jwks",
				PREFIX + "verifiers.other.header-name=x-other-jwt",
				PREFIX + "verifiers.other.jwk-set-uris[0]=https://other.example/keys",
				PREFIX + "verifiers.other.algorithms=ES256,RS256",
				PREFIX + "verifiers.other.issuer=https://login.microsoftonline.com/our-tenant/v2.0",
				PREFIX + "verifiers.other.audiences=our-app-id,api://our-app",
				PREFIX + "verifiers.other.excluded-paths[0]=/gateway-only/**")
			.run(context -> {
				assertThat(context).hasNotFailed();
				final var verifiers = context.getBean(JwtVerificationProperties.class).verifiers();
				assertThat(verifiers).containsOnlyKeys("gateway", "other");
				assertThat(verifiers.get("gateway").jwkSetUris()).hasSize(2);
				assertThat(verifiers.get("other").algorithms()).containsExactly(SignatureAlgorithm.ES256, SignatureAlgorithm.RS256);
				assertThat(verifiers.get("other").excludedPaths()).containsExactly("/gateway-only/**");
				assertThat(verifiers.get("other").issuer()).isEqualTo("https://login.microsoftonline.com/our-tenant/v2.0");
				assertThat(verifiers.get("other").audiences()).containsExactly("our-app-id", "api://our-app");
			});
	}

	@Test
	void enabledWithoutVerifiersStopsStartup() {
		contextRunner
			.withPropertyValues(ENABLED)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("verifiers");
			});
	}

	@Test
	void verifierWithoutHeaderNameStopsStartup() {
		contextRunner
			.withPropertyValues(ENABLED, JWK_SET_URI)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("headerName");
			});
	}

	@Test
	void verifierWithoutJwkSetUrisStopsStartup() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("jwkSetUris");
			});
	}

	@Test
	void twoVerifiersOnOneHeaderStopStartup() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI,
				PREFIX + "verifiers.other.header-name=X-Gateway-JWT",
				PREFIX + "verifiers.other.jwk-set-uris[0]=https://other.example/keys")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("both verify header");
			});
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"connect-timeout=0s", "read-timeout=0s", "read-timeout=10m", "connect-timeout=10m"
	})
	void timeoutOutOfBoundsStopsStartup(final String timeout) {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI, PREFIX + "verifiers.gateway." + timeout)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("Timeout");
			});
	}

	@Test
	void unknownAlgorithmStopsStartup() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI, PREFIX + "verifiers.gateway.algorithms=HS256")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void invalidExcludedPathStopsStartup() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI, PREFIX + "excluded-paths[0]=/{unclosed")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void invalidVerifierExcludedPathStopsStartup() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI, PREFIX + "verifiers.gateway.excluded-paths[0]=/{unclosed")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void actuatorAtTheRootStopsStartup() {
		contextRunner
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI, "management.endpoints.web.base-path=/")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("management.endpoints.web.base-path");
			});
	}

	@Test
	void notRegisteredInReactiveApplication() {
		new ReactiveWebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, JwtVerificationConfiguration.class))
			.withPropertyValues(ENABLED, HEADER_NAME, JWK_SET_URI)
			.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FILTER_NAME));
	}
}
