package se.sundsvall.dept44.scheduling.health;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;
import se.sundsvall.dept44.scheduling.Dept44SchedulerAspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end check that a {@link Dept44Scheduled} method appears as a child component
 * under the composite scheduler health contributor when Spring Boot's
 * {@link HealthEndpoint} renders the contributor tree. This is the exact code path
 * the actuator HTTP endpoint takes to populate {@code /actuator/health}, so it
 * guards against regressions of the Spring Boot 4 {@code stream()} contract on
 * {@link Dept44CompositeHealthContributor} without spinning up a web server.
 */
@SpringBootTest(classes = Dept44SchedulerHealthEndpointTest.TestApp.class)
class Dept44SchedulerHealthEndpointTest {

	private static final String JOB_NAME = "TestScheduledJob";
	// Spring Boot's default HealthContributorNameGenerator strips the "healthcontributor" suffix
	// from bean names, so the @Component("dept44CompositeSchedulerHealthContributor") bean is
	// registered in the HealthContributorRegistry — and exposed under /actuator/health — as this:
	private static final String CONTRIBUTOR_NAME = "dept44CompositeScheduler";

	@Autowired
	private HealthEndpoint healthEndpoint;

	@Test
	void scheduledJobAppearsInHealthEndpoint() {
		await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			final var descriptor = healthEndpoint.healthForPath(CONTRIBUTOR_NAME);

			assertThat(descriptor)
				.as("HealthEndpoint should expose the composite scheduler contributor")
				.isInstanceOf(CompositeHealthDescriptor.class);
			assertThat(((CompositeHealthDescriptor) descriptor).getComponents())
				.as("Scheduled job '%s' should appear as a child component (regression: stream() returned Stream.empty())", JOB_NAME)
				.containsKey(JOB_NAME);
		});
	}

	@Configuration
	@EnableScheduling
	@EnableAspectJAutoProxy
	@ImportAutoConfiguration({
		HealthContributorRegistryAutoConfiguration.class,
		HealthEndpointAutoConfiguration.class
	})
	@Import({
		Dept44CompositeHealthContributor.class,
		Dept44SchedulerAspect.class
	})
	static class TestApp {

		@Component
		static class TestScheduledJob {
			@Dept44Scheduled(cron = "* * * * * *", name = JOB_NAME)
			public void run() {
				// no-op; the aspect registers the indicator on first invocation
			}
		}
	}
}
