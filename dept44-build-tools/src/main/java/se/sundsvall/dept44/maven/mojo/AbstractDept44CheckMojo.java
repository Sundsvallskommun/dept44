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

    private MavenProject project;
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

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    public void setProject(final MavenProject project) {
        this.project = project;
    }

    protected boolean isSkipAllChecks() {
        return skipAllChecks;
    }

    @Parameter(property = "dept44.check.skipAll", defaultValue = "false")
    public void setSkipAllChecks(final boolean skipAllChecks) {
        this.skipAllChecks = skipAllChecks;
    }
}
