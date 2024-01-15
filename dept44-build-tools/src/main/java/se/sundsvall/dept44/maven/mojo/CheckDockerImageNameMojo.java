package se.sundsvall.dept44.maven.mojo;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "check-docker-image-name", defaultPhase = LifecyclePhase.INITIALIZE)
public class CheckDockerImageNameMojo extends AbstractDept44CheckMojo {

    private static final String DOCKER_IMAGE_NAME_REGEX = "^ms-[a-z0-9-]+$";

    private boolean skip;

    @Override
    public void doExecute() {
        if (isSkipAllChecks() || skip || "pom".equalsIgnoreCase(getProject().getPackaging())) {
            getLog().info("Skipping validation of Docker image name");

            return;
        }

        getLog().info("Validating Docker image name");

        var dockerImageName = getProject().getProperties().getProperty("docker.image.name", "");
        if (dockerImageName == null || dockerImageName.isBlank()) {
            addError("Build property \"docker.image.name\" is missing or empty");
        } else if (!dockerImageName.matches(DOCKER_IMAGE_NAME_REGEX)) {
            addError("Build property \"docker.image.name\" must match regex \"" + DOCKER_IMAGE_NAME_REGEX + "\", e.g. \"ms-service-123\"");
        }
    }

    @Parameter(property = "dept44.check.docker-image-name.skip", defaultValue = "false")
    public void setSkip(final boolean skip) {
        this.skip = skip;
    }
}
