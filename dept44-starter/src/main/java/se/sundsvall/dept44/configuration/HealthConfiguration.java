package se.sundsvall.dept44.configuration;

import static org.springframework.boot.actuate.health.Status.DOWN;
import static org.springframework.boot.actuate.health.Status.OUT_OF_SERVICE;
import static org.springframework.boot.actuate.health.Status.UNKNOWN;
import static org.springframework.boot.actuate.health.Status.UP;

import java.util.Set;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class HealthConfiguration {

	// Statuses returned by resilience4j circuit breakers.
	static final Status CIRCUIT_OPEN = new Status("CIRCUIT_OPEN");
	static final Status CIRCUIT_HALF_OPEN = new Status("CIRCUIT_HALF_OPEN");
	static final Status RESTRICTED = new Status("RESTRICTED");

	@Bean
	@Primary
	StatusAggregator statusAggregator() {
		return statuses -> {

			if (matches(statuses, Set.of(Status.DOWN))) {
				return DOWN;
			}
			if (matches(statuses, Set.of(OUT_OF_SERVICE))) {
				return OUT_OF_SERVICE;
			}
			if (matches(statuses, Set.of(CIRCUIT_OPEN, CIRCUIT_HALF_OPEN, RESTRICTED))) {
				return RESTRICTED;
			}
			if (matches(statuses, Set.of(UP))) {
				return UP;
			}

			return UNKNOWN;
		};
	}

	/**
	 * Returns true if any of the sent in statuses matches any of the statuses in the match-set.
	 *
	 * @param  statuses the statuses to check.
	 * @param  matchSet the set to match the provided statuses against.
	 * @return          true if match, false otherwise.
	 */
	private boolean matches(Set<Status> statuses, Set<Status> matchSet) {
		return statuses.stream().anyMatch(status -> matchSet.stream().anyMatch(s -> s.equals(status)));
	}
}
