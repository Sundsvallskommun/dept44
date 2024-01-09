package se.sundsvall.dept44.maven.mojo;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "check-docker-image-name", defaultPhase = LifecyclePhase.INITIALIZE)
public class CheckDockerImageNameMojo extends AbstractDept44CheckMojo {

    private static final String DOCKER_IMAGE_NAME_REGEX = "^ms-[a-z0-9-]+$";

    @Parameter(property = "dept44.check.docker-image-name.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "dept44.check.docker-image-name.required", defaultValue = "false")
    private boolean requireDockerImageName;

    @Override
    public void doExecute() {
        if (isSkipAllChecks() || skip) {
            getLog().info("Skipping validation of Docker image name");

            return;
        }

        getLog().info("Validating Docker image name");

        // Ensure that "docker.image.name" is set
        var dockerImageName = getProject().getProperties().getProperty("docker.image.name", "");
        if (requireDockerImageName && dockerImageName.isBlank()) {
            addError("Required build property \"docker.image.name\" is missing or empty");
        }

        // Ensure that "docker.image.name" has proper format
        if (!"".equals(dockerImageName) && !dockerImageName.matches(DOCKER_IMAGE_NAME_REGEX)) {
            addError("Build property \"docker.image.name\" must match regex \"" + DOCKER_IMAGE_NAME_REGEX + "\", e.g. \"ms-service-123\"");
        }
    }
}
