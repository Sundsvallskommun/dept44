package se.sundsvall.dept44.configuration;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @deprecated Since 4.3.0. Use {@link BodyFilterProperties} instead. Subject for removal in 5.0.0
 */
@ConfigurationProperties(prefix = "logbook.exclusionfilters")
@ConditionalOnMissingBean(ExclusionFilterProperties.class)
@Deprecated(since = "4.3.0", forRemoval = true)
public class ExclusionFilterProperties {
	private Map<String, String> jsonPath;
	private Map<String, String> xPath;

	public Map<String, String> getJsonPath() {
		return jsonPath;
	}

	public void setJsonPath(Map<String, String> jsonPath) {
		this.jsonPath = jsonPath;
	}

	public Map<String, String> getXPath() {
		return xPath;
	}

	public void setXPath(Map<String, String> xPath) {
		this.xPath = xPath;
	}
}
