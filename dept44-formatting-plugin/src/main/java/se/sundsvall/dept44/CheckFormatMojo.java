package se.sundsvall.dept44;

import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import javax.inject.Inject;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VALIDATE)
public class CheckFormatMojo extends AbstractFormatMojo {

	@Inject
	public CheckFormatMojo(final BuildPluginManager pluginManager) {
		super(pluginManager);
	}

	@Override
	protected String getGoal() {
		return "check";
	}
}
