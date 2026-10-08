package se.sundsvall.dept44.configuration;

import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

/**
 * Leaves out the auto-configurations dept44 does not use, whatever a service sets in
 * {@code spring.autoconfigure.exclude}.
 * Kept in that property, the exclusion would be lost by a service that sets the property itself, since its list
 * replaces dept44's instead of adding to it.
 */
public class Dept44AutoConfigurationImportFilter implements AutoConfigurationImportFilter {

	/**
	 * Spring Security's in-memory user with a generated password, which no dept44 service authenticates with.
	 */
	static final Set<String> EXCLUDED = Set.of("org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration");

	@Override
	public boolean[] match(final String[] autoConfigurationClasses, final AutoConfigurationMetadata autoConfigurationMetadata) {
		final var matches = new boolean[autoConfigurationClasses.length];
		for (var index = 0; index < autoConfigurationClasses.length; index++) {
			// An entry already left out by another filter is null
			matches[index] = autoConfigurationClasses[index] == null || !EXCLUDED.contains(autoConfigurationClasses[index]);
		}
		return matches;
	}
}
