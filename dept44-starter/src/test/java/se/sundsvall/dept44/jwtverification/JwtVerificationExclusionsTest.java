package se.sundsvall.dept44.jwtverification;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static se.sundsvall.dept44.jwtverification.JwtVerificationExclusions.API_DOCS_PATH;
import static se.sundsvall.dept44.jwtverification.JwtVerificationExclusions.MANAGEMENT_BASE_PATH;
import static se.sundsvall.dept44.jwtverification.JwtVerificationExclusions.SERVLET_PATH;
import static se.sundsvall.dept44.jwtverification.JwtVerificationExclusions.SWAGGER_UI_PATH;

class JwtVerificationExclusionsTest {

	@Test
	void defaultsWhenNothingIsSet() {
		assertThat(JwtVerificationExclusions.builtIn(new MockEnvironment())).containsExactly(
			"/",
			"/actuator/**",
			"/v3/api-docs.yaml", "/v3/api-docs/**",
			"/swagger-ui.html", "/swagger-ui/**",
			"/webjars/**",
			"/favicon.ico");
	}

	@Test
	void doubleStarAlsoMatchesTheBasePath() {
		final var pattern = PathPatternParser.defaultInstance.parse("/actuator/**");

		assertThat(pattern.matches(PathContainer.parsePath("/actuator"))).isTrue();
	}

	@Test
	void pathsFollowTheProperties() {
		final var environment = new MockEnvironment()
			.withProperty(MANAGEMENT_BASE_PATH, "/manage/")
			.withProperty(API_DOCS_PATH, "docs")
			.withProperty(SWAGGER_UI_PATH, "/ui/index.html");

		assertThat(JwtVerificationExclusions.builtIn(environment)).containsExactly(
			"/",
			"/manage/**",
			"/docs.yaml", "/docs/**",
			"/ui/index.html", "/ui/swagger-ui/**", "/swagger-ui/**",
			"/webjars/**",
			"/favicon.ico");
	}

	@Test
	void servletPathPrefixesTheBuiltInExclusions() {
		final var environment = new MockEnvironment()
			.withProperty(SERVLET_PATH, "/api/")
			.withProperty(API_DOCS_PATH, "/api-docs");

		assertThat(JwtVerificationExclusions.builtIn(environment)).containsExactly(
			"/api", "/api/",
			"/api/actuator/**",
			"/api/api-docs.yaml", "/api/api-docs/**",
			"/api/swagger-ui.html", "/api/swagger-ui/**",
			"/api/webjars/**",
			"/api/favicon.ico");
	}

	@Test
	void servletPathPrefixesTheConfiguredExclusions() {
		final var environment = new MockEnvironment().withProperty(SERVLET_PATH, "/api");

		assertThat(JwtVerificationExclusions.configured(environment, List.of("/callback/**"))).containsExactly("/api/callback/**");
	}

	@Test
	void configuredExclusionGetsALeadingSlash() {
		assertThat(JwtVerificationExclusions.configured(new MockEnvironment(), List.of("callback/**"))).containsExactly("/callback/**");
	}

	@Test
	void rootServletPathAddsNoPrefix() {
		final var environment = new MockEnvironment().withProperty(SERVLET_PATH, "/");

		assertThat(JwtVerificationExclusions.configured(environment, List.of("/callback/**", "/callback/**"))).containsExactly("/callback/**");
	}

	@Test
	void actuatorAtTheRootIsRefused() {
		final var environment = new MockEnvironment().withProperty(MANAGEMENT_BASE_PATH, "/");

		assertThatIllegalStateException()
			.isThrownBy(() -> JwtVerificationExclusions.builtIn(environment))
			.withMessageContaining(MANAGEMENT_BASE_PATH);
	}

	@Test
	void blankActuatorPathIsTheRoot() {
		final var environment = new MockEnvironment().withProperty(MANAGEMENT_BASE_PATH, " ");

		assertThatIllegalStateException()
			.isThrownBy(() -> JwtVerificationExclusions.builtIn(environment));
	}
}
