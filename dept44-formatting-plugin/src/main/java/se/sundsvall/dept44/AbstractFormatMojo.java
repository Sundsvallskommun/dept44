package se.sundsvall.dept44;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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

	@Parameter(property = "javaIncludes")
	private List<String> javaIncludes;

	@Parameter(property = "javaExcludes")
	private List<String> javaExcludes;

	@Parameter(property = "jsonIncludes")
	private List<String> jsonIncludes;

	@Parameter(property = "jsonExcludes")
	private List<String> jsonExcludes;

	@Parameter(property = "sqlIncludes")
	private List<String> sqlIncludes;

	@Parameter(property = "sqlExcludes")
	private List<String> sqlExcludes;
	@Parameter(property = "markdownIncludes")
	private List<String> markdownIncludes;

	@Parameter(property = "markdownExcludes")
	private List<String> markdownExcludes;

	@Parameter(property = "pomIncludes")
	private List<String> pomIncludes;

	@Parameter(property = "pomExcludes")
	private List<String> pomExcludes;

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

			setExcludes(configuration.getChild("java"), javaExcludes);
			setExcludes(configuration.getChild("json"), jsonExcludes);
			setExcludes(configuration.getChild("sql"), sqlExcludes);
			setExcludes(configuration.getChild("markdown"), markdownExcludes);
			setExcludes(configuration.getChild("pom"), pomExcludes);

			setIncludes(configuration.getChild("java"), javaIncludes);
			setIncludes(configuration.getChild("json"), jsonIncludes);
			setIncludes(configuration.getChild("sql"), sqlIncludes);
			setIncludes(configuration.getChild("markdown"), markdownIncludes);
			setIncludes(configuration.getChild("pom"), pomIncludes);

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

	private void setExcludes(final Xpp3Dom languageConfig, final List<String> excludes) {
		if (languageConfig != null && excludes != null) {
			Xpp3Dom excludesNode = languageConfig.getChild("excludes");
			if (excludesNode == null) {
				excludesNode = new Xpp3Dom("excludes");
				languageConfig.addChild(excludesNode);
			}
			for (final var exclude : excludes) {
				final var excludeNode = new Xpp3Dom("exclude");
				excludeNode.setValue(exclude);
				excludesNode.addChild(excludeNode);
			}
		}
	}

	private void setIncludes(final Xpp3Dom languageConfig, final List<String> includes) {
		if (languageConfig != null && includes != null) {
			Xpp3Dom includesNode = languageConfig.getChild("includes");
			if (includesNode == null) {
				includesNode = new Xpp3Dom("includes");
				languageConfig.addChild(includesNode);
			}
			for (final var include : includes) {
				final var excludeNode = new Xpp3Dom("include");
				excludeNode.setValue(include);
				includesNode.addChild(excludeNode);
			}
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
