package se.sundsvall.dept44;

import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import javax.inject.Inject;

@Mojo(name = "format-apply", defaultPhase = LifecyclePhase.VALIDATE)
public class ApplyFormatMojo extends AbstractFormatMojo {

	@Inject
	public ApplyFormatMojo(final BuildPluginManager pluginManager) {
		super(pluginManager);
	}

	@Override
	protected String getGoal() {
		return "apply";
	}
}
