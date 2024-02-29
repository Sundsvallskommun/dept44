package se.sundsvall.dept44.support;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BasicAuthenticationTest {

	@Test
	void basicAuthentication() {
		final var userName = "userName";
		final var password = "password";
		final var basicAuthentication = new BasicAuthentication(userName, password);

		assertThat(basicAuthentication.username()).isEqualTo(userName);
		assertThat(basicAuthentication.password()).isEqualTo(password);
	}

	@ParameterizedTest
	@MethodSource("toBlankErrorArguments")
	void argumentIsBlank(String userName, String password, String expectedMessage) {

		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> new BasicAuthentication(userName, password));

		assertThat(illegalArgumentException).isNotNull();
		assertThat(illegalArgumentException.getMessage()).isEqualTo(expectedMessage);
	}

	private static Stream<Arguments> toBlankErrorArguments() {
		return Stream.of(
			Arguments.of("userName", "", "Password must be set"),
			Arguments.of("", "password", "Username must be set"),
			Arguments.of("", "", "Username must be set"),
			Arguments.of("userName", null, "Password must be set"),
			Arguments.of(null, "password", "Username must be set"),
			Arguments.of(null, null, "Username must be set"));
	}

}
