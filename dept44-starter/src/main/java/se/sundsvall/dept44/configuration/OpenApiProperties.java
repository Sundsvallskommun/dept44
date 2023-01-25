package se.sundsvall.dept44.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "openapi")
public class OpenApiProperties {

	private List<Server> servers = new ArrayList<>();

	@NotBlank
	@Pattern(regexp = "[a-z0-9_-]+", flags = Pattern.Flag.CASE_INSENSITIVE)
	private String name;

	@NotBlank
	private String title;

	private String description;

	@NotBlank
	private String version;

	private License license = new License();

	private Contact contact = new Contact();

	private Map<String, Object> extensions = new HashMap<>();

	List<Server> getServers() {
		return servers;
	}

	void setServers(final List<Server> servers) {
		this.servers = servers;
	}

	String getName() {
		return name;
	}

	void setName(final String name) {
		this.name = name;
	}

	String getTitle() {
		return title;
	}

	void setTitle(final String title) {
		this.title = title;
	}

	String getDescription() {
		return description;
	}

	void setDescription(final String description) {
		this.description = description;
	}

	String getVersion() {
		return version;
	}

	void setVersion(final String version) {
		this.version = version;
	}

	License getLicense() {
		return license;
	}

	void setLicense(final License license) {
		this.license = license;
	}

	Contact getContact() {
		return contact;
	}

	void setContact(final Contact contact) {
		this.contact = contact;
	}

	Map<String, Object> getExtensions() {
		return extensions;
	}

	void setExtensions(final Map<String, Object> extensions) {
		this.extensions = extensions;
	}

	static class Server {

		private String url;
		private String description;

		String getUrl() {
			return url;
		}

		void setUrl(final String url) {
			this.url = url;
		}

		String getDescription() {
			return description;
		}

		void setDescription(final String description) {
			this.description = description;
		}
	}

	static class License {

		private String name = "MIT License";
		private String url = "https://opensource.org/licenses/MIT";

		String getName() {
			return name;
		}

		void setName(final String name) {
			this.name = name;
		}

		String getUrl() {
			return url;
		}

		void setUrl(final String url) {
			this.url = url;
		}
	}

	static class Contact {

		private String name;
		private String url;
		private String email;

		String getName() {
			return name;
		}

		void setName(final String name) {
			this.name = name;
		}

		String getUrl() {
			return url;
		}

		void setUrl(final String url) {
			this.url = url;
		}

		String getEmail() {
			return email;
		}

		void setEmail(final String email) {
			this.email = email;
		}
	}
}
