package se.sundsvall.dept44.configuration;


import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logbook.body-filters")
@ConditionalOnMissingBean(BodyFilterProperties.class)
public class BodyFilterProperties {

	private List<Map<String, String>> jsonPath;
	private List<Map<String, String>> xPath;

	public List<Map<String, String>> getJsonPath() {
		return jsonPath;
	}

	public void setJsonPath(final List<Map<String, String>> jsonPath) {
		this.jsonPath = jsonPath;
	}

	public List<Map<String, String>> getxPath() {
		return xPath;
	}

	public void setxPath(final List<Map<String, String>> xPath) {
		this.xPath = xPath;
	}

}
