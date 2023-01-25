package se.sundsvall.dept44.maven.enforcer.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Dept44RequiredPropertiesRuleTest {

	@Mock
	private EnforcerRuleHelper enforcerRuleHelperMock;

	@Mock
	private MavenProject mavenProjectMock;

	@Mock
	private Properties propertiesMock;

	private static final String NEWLINE = System.lineSeparator();

	@ParameterizedTest
	@ValueSource(strings = { "src/test/resources/success", "src/test/resources/success_yml" })
	void ruleSuccess(String path) throws ExpressionEvaluationException, EnforcerRuleException {
		when(enforcerRuleHelperMock.evaluate(anyString())).thenReturn(mavenProjectMock).thenReturn(path);
		when(mavenProjectMock.getProperties()).thenReturn(propertiesMock);
		when(propertiesMock.getProperty("docker.image.name", "")).thenReturn("ms-testservice");

		final var dept44RequiredPropertiesRule = new Dept44RequiredPropertiesRule();

		dept44RequiredPropertiesRule.execute(enforcerRuleHelperMock);

		verify(enforcerRuleHelperMock, times(2)).evaluate(anyString());
	}

	@ParameterizedTest
	@MethodSource("toRuleBreakingArguments")
	void ruleBroken(String path, String imageName, String expectedMessage) throws ExpressionEvaluationException {
		when(enforcerRuleHelperMock.evaluate(anyString())).thenReturn(mavenProjectMock).thenReturn(path);
		when(mavenProjectMock.getProperties()).thenReturn(propertiesMock);
		when(propertiesMock.getProperty("docker.image.name", "")).thenReturn(imageName);
		final var dept44RequiredPropertiesRule = new Dept44RequiredPropertiesRule();

		final var enforcerRuleException = assertThrows(EnforcerRuleException.class, () -> dept44RequiredPropertiesRule.execute(enforcerRuleHelperMock));

		assertThat(enforcerRuleException).isNotNull().hasMessage(expectedMessage);
	}

	@Test
	void rulePassOnDept44() throws ExpressionEvaluationException, EnforcerRuleException {
		when(enforcerRuleHelperMock.evaluate(anyString())).thenReturn(mavenProjectMock).thenReturn("src/test/resources");
		when(mavenProjectMock.getPackaging()).thenReturn("pom");

		final var dept44RequiredPropertiesRule = new Dept44RequiredPropertiesRule();
		dept44RequiredPropertiesRule.execute(enforcerRuleHelperMock);

		verify(enforcerRuleHelperMock, times(2)).evaluate(anyString());
		verify(mavenProjectMock).getPackaging();
	}

	@Test
	void ruleSuccessWhenRequireDockerImageNameIsFalse() throws ExpressionEvaluationException, EnforcerRuleException {
		when(enforcerRuleHelperMock.evaluate(anyString())).thenReturn(mavenProjectMock).thenReturn("src/test/resources/success");
		when(mavenProjectMock.getProperties()).thenReturn(propertiesMock);
		when(propertiesMock.getProperty("docker.image.name", "")).thenReturn("");

		final var dept44RequiredPropertiesRule = new Dept44RequiredPropertiesRule();
		dept44RequiredPropertiesRule.setRequireDockerImageName(false);

		dept44RequiredPropertiesRule.execute(enforcerRuleHelperMock);

		verify(enforcerRuleHelperMock, times(2)).evaluate(anyString());
	}

	@Test
	void initialization() {
		final var dept44RequiredPropertiesRule = new Dept44RequiredPropertiesRule();

		// is always false
		assertThat(dept44RequiredPropertiesRule.isCacheable()).isFalse();
		// is always false
		assertThat(dept44RequiredPropertiesRule.isResultValid(dept44RequiredPropertiesRule)).isFalse();
		assertThat(isValidUUID(dept44RequiredPropertiesRule.getCacheId())).isTrue();
	}

	private static Stream<Arguments> toRuleBreakingArguments() {
		return Stream.of(
			Arguments.of("src/test/resources/missing_open_api", "ms-testservice", NEWLINE + " - Property \"openapi.name\" is missing or empty in application properties/YAML"
				+ NEWLINE + " - Property \"openapi.title\" is missing or empty in application*.properties/YAML"
				+ NEWLINE + " - Property \"openapi.version\" is missing or empty in application*.properties/YAML" + NEWLINE),
			Arguments.of("src/test/resources", "ms-testservice", "No application properties/YAML files exist"),
			Arguments.of("src/test/resources/success", "wrong-name", NEWLINE
				+ " - Build property \"docker.image.name\" must match regex \"^ms-[a-z0-9-]+$\", e.g. \"ms-service-123\"" + NEWLINE),
			Arguments.of("src/test/resources/success", "", NEWLINE + " - Required build property \"docker.image.name\" is missing or empty" + NEWLINE));
	}

	private boolean isValidUUID(String value) {
		try {
			UUID.fromString(String.valueOf(value));
		} catch (Exception e) {
			return false;
		}

		return true;
	}
}
