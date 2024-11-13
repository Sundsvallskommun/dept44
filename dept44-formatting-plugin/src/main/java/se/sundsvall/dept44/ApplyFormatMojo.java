package se.sundsvall.dept44;

import javax.inject.Inject;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "apply", defaultPhase = LifecyclePhase.VALIDATE)
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
