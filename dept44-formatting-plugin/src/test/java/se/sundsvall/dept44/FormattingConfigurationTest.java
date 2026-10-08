package se.sundsvall.dept44;

import java.util.Arrays;
import org.apache.maven.plugin.BuildPluginManager;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FormattingConfigurationTest {

	@Test
	void javaIncludesTheIntegrationTests() throws Exception {
		final var configuration = new CheckFormatMojo(mock(BuildPluginManager.class)).loadConfiguration();

		assertThat(values(configuration.getChild("java").getChild("includes")))
			.containsExactly("src/main/java/**/*.java", "src/test/java/**/*.java", "src/integration-test/java/**/*.java");
	}

	@Test
	void markdownLeavesClaudeDirectoriesAlone() throws Exception {
		final var configuration = new CheckFormatMojo(mock(BuildPluginManager.class)).loadConfiguration();

		assertThat(values(configuration.getChild("markdown").getChild("excludes"))).contains("**/.claude/**");
	}

	private static String[] values(final Xpp3Dom node) {
		return Arrays.stream(node.getChildren()).map(Xpp3Dom::getValue).toArray(String[]::new);
	}
}
