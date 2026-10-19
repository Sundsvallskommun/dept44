package se.sundsvall.dept44.rabbitmq.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RabbitMqAutoConfigurationImportFilterTest {

	private static final String[] AUTO_CONFIGURATIONS = {
		"org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
		"org.springframework.boot.amqp.autoconfigure.health.RabbitHealthContributorAutoConfiguration",
		"se.sundsvall.dept44.rabbitmq.config.RabbitMqConfiguration",
		"se.sundsvall.dept44.rabbitmq.config.RabbitMqDisabledConfiguration",
		"org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
		null
	};

	@Test
	void leavesOutRabbitMqUnlessEnabled() {
		final var filter = new RabbitMqAutoConfigurationImportFilter();
		filter.setEnvironment(new MockEnvironment());

		assertThat(filter.match(AUTO_CONFIGURATIONS, null)).containsExactly(false, false, false, true, true, true);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"false", "off", "no", "0", "", "ture"
	})
	void onlyTrueTurnsRabbitMqOn(final String value) {
		final var filter = new RabbitMqAutoConfigurationImportFilter();
		filter.setEnvironment(new MockEnvironment().withProperty("dept44.rabbitmq.enabled", value));

		assertThat(filter.match(AUTO_CONFIGURATIONS, null)).containsExactly(false, false, false, true, true, true);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"true", "TRUE"
	})
	void keepsRabbitMqAndLeavesOutTheWarningWhenEnabled(final String value) {
		final var filter = new RabbitMqAutoConfigurationImportFilter();
		filter.setEnvironment(new MockEnvironment().withProperty("dept44.rabbitmq.enabled", value));

		assertThat(filter.match(AUTO_CONFIGURATIONS, null)).containsExactly(true, true, true, false, true, true);
	}

	@Test
	void disabledApplicationHasNoRabbitMqBeans(final CapturedOutput output) {
		final var application = application();
		application.addPrimarySources(List.of(Listener.class));

		try (final var context = application.run()) {
			assertThat(context.getBeanNamesForType(ConnectionFactory.class)).isEmpty();
			assertThat(context.getBeanNamesForType(RabbitTemplate.class)).isEmpty();
			assertThat(context.getBeanNamesForType(Dept44RabbitPublisher.class)).isEmpty();
			// The defaults are still there for the service to read
			assertThat(context.getEnvironment().getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
			assertThat(output).contains("so the listeners in " + Listener.class.getName() + " receive nothing");
		}
	}

	@Test
	void enabledApplicationHasThePublisher() {
		try (final var context = application().run("--dept44.rabbitmq.enabled=true")) {
			assertThat(context.getBeanNamesForType(ConnectionFactory.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(Dept44RabbitPublisher.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(DisabledListenerWarning.class)).isEmpty();
		}
	}

	/**
	 * A running application, so that the filter and the defaults are picked up from spring.factories as in a service. No
	 * broker is needed: the connection is opened on first use.
	 */
	private static SpringApplication application() {
		final var application = new SpringApplication(RabbitApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		return application;
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({
		JacksonAutoConfiguration.class, RabbitAutoConfiguration.class, RabbitMqConfiguration.class, RabbitMqDisabledConfiguration.class
	})
	static class RabbitApplication {
	}

	static class Listener {

		@RabbitListener(queues = "work")
		void receive(final String message) {
			// Never runs in this test
		}
	}
}
