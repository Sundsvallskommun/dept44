package se.sundsvall.dept44.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import se.sundsvall.dept44.logbook.filter.AllowlistBodyFilter;

@ConfigurationProperties(prefix = "logbook.body-filters")
@ConditionalOnMissingBean(BodyFilterProperties.class)
public class BodyFilterProperties {

	private List<Map<String, String>> jsonPath;
	private List<Map<String, String>> xPath;
	private Allowlist allowlist = new Allowlist();

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

	public Allowlist getAllowlist() {
		return allowlist;
	}

	public void setAllowlist(final Allowlist allowlist) {
		this.allowlist = allowlist;
	}

	/**
	 * Which fields of a JSON body have their values logged. See {@link AllowlistBodyFilter}.
	 */
	public static class Allowlist {

		/**
		 * Whether only the values of the fields named in {@link #paths} are logged. When false, bodies are logged in full.
		 */
		private boolean enabled = true;

		/**
		 * The longest body, in bytes of UTF-8, that is parsed to apply the allowlist. A longer one is replaced by a note. A
		 * negative value disables the limit.
		 */
		private DataSize maxSize = DataSize.ofKilobytes(256);

		/**
		 * The fields whose values are logged, such as {@code $..id} or {@code $.content[*].title}.
		 */
		private List<String> paths = new ArrayList<>();

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(final boolean enabled) {
			this.enabled = enabled;
		}

		public DataSize getMaxSize() {
			return maxSize;
		}

		public void setMaxSize(final DataSize maxSize) {
			this.maxSize = maxSize;
		}

		public List<String> getPaths() {
			return paths;
		}

		public void setPaths(final List<String> paths) {
			this.paths = paths;
		}
	}
}
