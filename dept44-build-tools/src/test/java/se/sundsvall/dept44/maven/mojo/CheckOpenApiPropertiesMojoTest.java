package se.sundsvall.dept44.maven.mojo;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

import java.io.File;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckOpenApiPropertiesMojoTest {

    @Mock
    private MavenProject mockMavenProject;

    private final CheckOpenApiPropertiesMojo mojo = new CheckOpenApiPropertiesMojo();

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
    @ValueSource(strings = {"openapi-not-enabled", "using-properties-file", "using-yaml-file"})
    void execute(final String testdir) {
        when(mockMavenProject.getBasedir())
            .thenReturn(new File("src/test/resources/openapi-properties/" + testdir));

        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void executeWithMissingOpenApiProperties() {
        when(mockMavenProject.getBasedir())
            .thenReturn(new File("src/test/resources/openapi-properties/missing-properties"));

        assertThatExceptionOfType(MojoFailureException.class)
            .isThrownBy(mojo::execute)
            .withMessageContainingAll(
                "Property \"openapi.name\" is missing or empty in application properties/YAML",
                "Property \"openapi.title\" is missing or empty in application*.properties/YAML",
                "Property \"openapi.version\" is missing or empty in application*.properties/YAML");
    }
}
