package se.sundsvall.dept44;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.twdata.maven.mojoexecutor.MojoExecutor;

public abstract class AbstractFormatMojo extends AbstractMojo {

	private final BuildPluginManager pluginManager;

	private MavenProject project;

	private MavenSession mavenSession;

	private String spotlessMavenPluginVersion;

	@Inject
	AbstractFormatMojo(final BuildPluginManager pluginManager) {
		this.pluginManager = pluginManager;
	}

	@Parameter(property = "spotless-maven-plugin-version")
	public void setSpotlessMavenPluginVersion(final String spotlessMavenPluginVersion) {
		this.spotlessMavenPluginVersion = spotlessMavenPluginVersion;
	}

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	public void setProject(final MavenProject project) {
		this.project = project;
	}

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	public void setMavenSession(final MavenSession mavenSession) {
		this.mavenSession = mavenSession;
	}

	protected abstract String getGoal();

	public void execute() throws MojoExecutionException {
		try {
			final MojoExecutor.ExecutionEnvironment env = MojoExecutor.executionEnvironment(
				project,
				mavenSession,
				pluginManager);

			final var configuration = loadConfiguration();

			MojoExecutor.executeMojo(
				MojoExecutor.plugin(
					MojoExecutor.groupId("com.diffplug.spotless"),
					MojoExecutor.artifactId("spotless-maven-plugin"),
					MojoExecutor.version(spotlessMavenPluginVersion)),
				MojoExecutor.goal(getGoal()),
				configuration,
				env);

		} catch (final MojoExecutionException e) {
			// Customize the error message
			String message = e.getMessage();
			if (message != null && message.contains("Run 'mvn spotless:apply' to fix these violations.")) {
				message = message.replace("Run 'mvn spotless:apply' to fix these violations.", "Run 'mvn dept44-formatting:apply' to fix these violations.");
			}
			throw new MojoExecutionException(message, e.getCause());
		} catch (final Exception e) {
			throw new MojoExecutionException("Failed to execute Spotless plugin", e);
		}
	}

	public Xpp3Dom loadConfiguration() throws IOException, XmlPullParserException {
		try (final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.xml")) {
			if (inputStream == null) {
				throw new IOException("Configuration file 'config.xml' not found in resources");
			}

			final var config = Xpp3DomBuilder.build(inputStream, UTF_8.name());

			Optional.ofNullable(config.getChild("java"))
				.map(java -> java.getChild("eclipse"))
				.map(eclipse -> eclipse.getChild("file"))
				.ifPresent(file -> file.setValue(Objects.requireNonNull(getClass()
					.getClassLoader()
					.getResource("sundsvall_formatting.xml")).toString()));

			return config;
		}
	}
}
