package se.sundsvall.dept44.configuration;

import org.springframework.http.MediaType;

public final class Constants {

	private Constants() {}

	/*
	 * Default timeout values used by RestTemplate, WebClient, etc.
	 */
	public static final int DEFAULT_CONNECT_TIMEOUT_IN_SECONDS = 10;
	public static final int DEFAULT_READ_TIMEOUT_IN_SECONDS = 30;
	public static final int DEFAULT_WRITE_TIMEOUT_IN_SECONDS = 30;

	/*
	 * Additional media types that aren't defined in Spring:s MediaType
	 */
	public static final MediaType APPLICATION_YAML = new MediaType("application", "yaml");
	public static final MediaType APPLICATION_YML = new MediaType("application", "yml");

}
