package se.sundsvall.dept44.scheduling.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.HealthContributors;

class Dept44CompositeHealthContributorTest {

	private Dept44CompositeHealthContributor healthContributor;

	@BeforeEach
	void setUp() {
		healthContributor = new Dept44CompositeHealthContributor();
	}

	@Test
	void testGetContributor() {
		// arrange
		final var methodName = "TestTask";
		final var indicator = healthContributor.getOrCreateIndicator(methodName);

		// act
		final var result = healthContributor.getContributor(methodName);

		// assert
		assertThat(result).isEqualTo(indicator);
	}

	@Test
	void testGetContributors() {
		// arrange
		final var methodName = "TestTask";
		final var indicator = healthContributor.getOrCreateIndicator(methodName);

		// act
		final var contributors = healthContributor.getContributors();

		// assert
		assertThat(contributors).containsEntry(methodName, indicator);
	}

	@Test
	void testGetOrCreateIndicator() {
		// arrange
		final var methodName = "TestTask";

		// act
		final var indicator = healthContributor.getOrCreateIndicator(methodName);

		// assert
		assertThat(indicator).isNotNull();
		assertThat(healthContributor.getContributors()).containsEntry(methodName, indicator);
	}

	@Test
	void testIterator() {
		// arrange
		final var methodName = "TestTask";
		final var indicator = healthContributor.getOrCreateIndicator(methodName);

		// act
		final Iterator<HealthContributors.Entry> iterator = healthContributor.iterator();

		// assert
		assertThat(iterator).isNotNull();
		assertThat(iterator.hasNext()).isTrue();
		final var namedContributor = iterator.next();
		assertThat(namedContributor.name()).isEqualTo(methodName);
		assertThat(namedContributor.contributor()).isEqualTo(indicator);
	}
}
