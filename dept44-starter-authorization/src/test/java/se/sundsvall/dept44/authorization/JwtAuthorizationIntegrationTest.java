package se.sundsvall.dept44.authorization;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Runs the filter in a real server, after dept44's Spring Security filter chain, the way a service runs it.
 */
@SpringBootTest(classes = JwtAuthorizationIntegrationTest.TestApplication.class, webEnvironment = RANDOM_PORT, properties = {
	"jwt.authorization.secret=" + JwtAuthorizationIntegrationTest.SECRET,
	"openapi.name=test", "openapi.title=test", "openapi.version=1.0"
})
@AutoConfigureWebTestClient
class JwtAuthorizationIntegrationTest {

	static final String SECRET = "df6b9fb15cfdbb7527be5a8a6e39f39e572c8ddb943fbc79a943438e9d3d85ebfc2ccf9e0eccd9346026c0b6876e0e01556fe56f135582c05fbdbb505d46755a";

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void aValidTokenAuthenticatesTheRequest() {
		webTestClient.get().uri("/whoami")
			.header("x-authorization-info", token())
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).isEqualTo("userName");
	}

	@Test
	void aValidTokenGrantsItsRoleAccesses() {
		webTestClient.get().uri("/read")
			.header("x-authorization-info", token())
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	void withoutATokenTheRequestIsRejected() {
		webTestClient.get().uri("/whoami")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	private static String token() {
		return Jwts.builder()
			.subject("userName")
			.claim("roles", Map.of("READ", List.of("CATEGORY_1")))
			.signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
			.compact();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableJwtAuthorization
	static class TestApplication {

		@Bean
		WhoAmIController whoAmIController() {
			return new WhoAmIController();
		}
	}

	@RestController
	static class WhoAmIController {

		@GetMapping("/whoami")
		@PreAuthorize("isAuthenticated() and !isAnonymous()")
		public String whoAmI(final Authentication authentication) {
			return authentication.getName();
		}

		@GetMapping("/read")
		@PreAuthorize("hasAuthority('READ')")
		public String read() {
			return "ok";
		}
	}
}
