package se.sundsvall.dept44;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

@ExtendWith(MockitoExtension.class)
class ApplyFormatMojoTest {

	@Mock
	private BuildPluginManager pluginManager;
	@Mock
	private MavenProject mavenProjectMock;
	@Mock
	private RepositorySystemSession repositorySession;
	@Mock
	private MavenSession mavenSession;
	@InjectMocks
	private ApplyFormatMojo applyFormatMojo;

	@Test
	void testGetGoal() {
		// Act
		final var localApplyMojo = new ApplyFormatMojo(pluginManager);
		// Assert
		assertThat(localApplyMojo).isNotNull().isInstanceOf(ApplyFormatMojo.class);
		assertThat(localApplyMojo.getGoal()).isEqualTo("apply");
	}

	@Test
	void testExecute() throws Exception {
		// Arrange
		final var mavenDependencyPluginDescriptor = new PluginDescriptor();
		final var copyDependenciesMojoDescriptor = new MojoDescriptor();
		copyDependenciesMojoDescriptor.setGoal("apply");
		copyDependenciesMojoDescriptor.setConfiguration(new XmlPlexusConfiguration("configuration"));
		copyDependenciesMojoDescriptor.setPluginDescriptor(mavenDependencyPluginDescriptor);

		mavenDependencyPluginDescriptor.addMojo(copyDependenciesMojoDescriptor);

		applyFormatMojo.setMavenSession(mavenSession);
		applyFormatMojo.setProject(mavenProjectMock);

		when(pluginManager.loadPlugin(
			eq(plugin(
				groupId("com.diffplug.spotless"),
				artifactId("spotless-maven-plugin"),
				version("2.0"))),
			any(),
			any())).thenReturn(mavenDependencyPluginDescriptor);

		// Act
		applyFormatMojo.execute();

		// Assert
		assertThat(applyFormatMojo).isNotNull().isInstanceOf(ApplyFormatMojo.class);
		verify(pluginManager).executeMojo(same(mavenSession), any());
	}
}
