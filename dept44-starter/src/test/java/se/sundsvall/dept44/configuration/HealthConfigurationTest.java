package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.health.contributor.Status.DOWN;
import static org.springframework.boot.health.contributor.Status.OUT_OF_SERVICE;
import static org.springframework.boot.health.contributor.Status.UNKNOWN;
import static org.springframework.boot.health.contributor.Status.UP;
import static se.sundsvall.dept44.configuration.HealthConfiguration.CIRCUIT_HALF_OPEN;
import static se.sundsvall.dept44.configuration.HealthConfiguration.CIRCUIT_OPEN;
import static se.sundsvall.dept44.configuration.HealthConfiguration.RESTRICTED;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = HealthConfiguration.class)
class HealthConfigurationTest {

	@Autowired(required = false)
	private StatusAggregator statusAggregator;

	/**
	 * Return two arguments for the tests: Set<Status> setOfStatusesToTest, Status expectedAggregatedStatus
	 */
	private static Stream<Arguments> aggregateArguments() {
		return Stream.of(
			// Single mapping
			Arguments.of(Set.of(UP), UP),
			Arguments.of(Set.of(DOWN), DOWN),
			Arguments.of(Set.of(OUT_OF_SERVICE), OUT_OF_SERVICE),
			Arguments.of(Set.of(UNKNOWN), UNKNOWN),
			Arguments.of(Set.of(RESTRICTED), RESTRICTED),
			Arguments.of(Set.of(CIRCUIT_HALF_OPEN), RESTRICTED),
			Arguments.of(Set.of(CIRCUIT_OPEN), RESTRICTED),

			// Aggregate order DOWN highest
			Arguments.of(Set.of(UNKNOWN, DOWN), DOWN),
			Arguments.of(Set.of(UNKNOWN, DOWN, OUT_OF_SERVICE), DOWN),
			Arguments.of(Set.of(UNKNOWN, DOWN, OUT_OF_SERVICE, RESTRICTED), DOWN),
			Arguments.of(Set.of(UNKNOWN, DOWN, OUT_OF_SERVICE, RESTRICTED, UP), DOWN),

			// Aggregate order OUT_OF_SERVICE highest
			Arguments.of(Set.of(UNKNOWN, UP, OUT_OF_SERVICE), OUT_OF_SERVICE),
			Arguments.of(Set.of(UNKNOWN, UP, OUT_OF_SERVICE), OUT_OF_SERVICE),
			Arguments.of(Set.of(UNKNOWN, UP, OUT_OF_SERVICE, RESTRICTED), OUT_OF_SERVICE),

			// Aggregate order RESTRICTED highest
			Arguments.of(Set.of(UNKNOWN, RESTRICTED), RESTRICTED),
			Arguments.of(Set.of(UNKNOWN, UP, RESTRICTED), RESTRICTED),

			// Aggregate order UP highest
			Arguments.of(Set.of(UNKNOWN, UP), UP));
	}

	@Test
	void statusAggregatorIsAutowired() {
		assertThat(statusAggregator).isNotNull();
	}

	@ParameterizedTest
	@MethodSource("aggregateArguments")
	void aggregate(final Set<Status> setOfStatusesToTest, final Status expectedAggregatedStatus) {
		assertThat(statusAggregator.getAggregateStatus(setOfStatusesToTest)).isEqualTo(expectedAggregatedStatus);
	}
}
