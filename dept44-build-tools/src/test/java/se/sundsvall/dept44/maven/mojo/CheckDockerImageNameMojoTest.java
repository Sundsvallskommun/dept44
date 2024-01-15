package se.sundsvall.dept44.maven.mojo;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckDockerImageNameMojoTest {

    @Mock
    private MavenProject mockMavenProject;
    @Mock
    private Properties mockProperties;

    private final CheckDockerImageNameMojo mojo = new CheckDockerImageNameMojo();

    @BeforeEach
    void setUp() {
        mojo.setProject(mockMavenProject);
    }

    @Test
    void executeWhenPomPackagingIsUsed() {
        when(mockMavenProject.getPackaging()).thenReturn("pom");

        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void executeWhenSkipAllChecksIsSet() {
        mojo.setSkipAllChecks(true);

        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void executeWhenSkipIsSet() {
        mojo.setSkip(true);

        assertThatNoException().isThrownBy(mojo::execute);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void executeWithBlankDockerImageName(final String value) {
        when(mockProperties.getProperty(eq("docker.image.name"), eq(""))).thenReturn(value);
        when(mockMavenProject.getProperties()).thenReturn(mockProperties);

        assertThatExceptionOfType(MojoFailureException.class)
            .isThrownBy(mojo::execute)
            .withMessageContaining("Build property \"docker.image.name\" is missing or empty");
    }

    @Test
    void executeWithInvalidDockerImageName() {
        when(mockProperties.getProperty(eq("docker.image.name"), eq(""))).thenReturn("invalid-docker-image-name");
        when(mockMavenProject.getProperties()).thenReturn(mockProperties);

        assertThatExceptionOfType(MojoFailureException.class)
            .isThrownBy(mojo::execute)
            .withMessageContaining("Build property \"docker.image.name\" must match regex");
    }

    @Test
    void executeWithValidDockerImageName() {
        when(mockProperties.getProperty(eq("docker.image.name"), eq(""))).thenReturn("ms-some-service");
        when(mockMavenProject.getProperties()).thenReturn(mockProperties);

        assertThatNoException().isThrownBy(mojo::execute);
    }
}
