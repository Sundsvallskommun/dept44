package se.sundsvall.dept44.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasicAuthenticationTest {

    @Test
    void test_basicAuthentication() {
        final var userName = "userName";
        final var password = "password";
        final var basicAuthentication = new BasicAuthentication(userName, password);

        assertThat(basicAuthentication.username()).isEqualTo(userName);
        assertThat(basicAuthentication.password()).isEqualTo(password);
    }

    @ParameterizedTest
    @MethodSource("toBlankErrorArguments")
    void test_argumentIsBlank(String userName, String password, String expectedMessage) {

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
