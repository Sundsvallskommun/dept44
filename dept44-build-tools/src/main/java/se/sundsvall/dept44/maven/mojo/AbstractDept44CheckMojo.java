package se.sundsvall.dept44.maven.mojo;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

abstract class AbstractDept44CheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "dept44.check.skipAll", defaultValue = "false")
    private boolean skipAllChecks;

    private final List<String> errors = new ArrayList<>();

    abstract void doExecute() throws MojoFailureException;

    @Override
    public void execute() throws MojoFailureException {
        doExecute();

        if (!errors.isEmpty()) {
            var errorsString = errors.stream().collect(joining(lineSeparator() + " - ", " - ", lineSeparator()));

            throw new MojoFailureException(lineSeparator() + errorsString);
        }
    }

    protected void addError(final String error) {
        errors.add(error);
    }

    protected MavenProject getProject() {
        return project;
    }

    protected boolean isSkipAllChecks() {
        return skipAllChecks;
    }
}
