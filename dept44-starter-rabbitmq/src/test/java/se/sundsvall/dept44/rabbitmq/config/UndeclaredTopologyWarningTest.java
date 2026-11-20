package se.sundsvall.dept44.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class UndeclaredTopologyWarningTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withInitializer(context -> new RabbitMqDefaultPropertiesEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class, RabbitMqConfiguration.class));

	@Test
	void warnsAboutTopologyNothingDeclares(final CapturedOutput output) {
		contextRunner.withUserConfiguration(Topology.class, DeclaringListener.class).run(context -> {
			assertThat(context.getBean(UndeclaredTopologyWarning.class).findUndeclared()).containsExactlyInAnyOrder(
				"queue 'dept44.work'",
				"exchange 'dept44.events'",
				"binding of queue 'dept44.work' to exchange 'dept44.events' with routing key 'work'",
				"queue 'dept44.in-declarables'",
				"queue 'dept44.declared-by-listener'",
				"queue 'dept44.bound-by-listener'",
				"exchange 'dept44.listener-events'",
				"binding of queue 'dept44.bound-by-listener' to exchange 'dept44.listener-events' with routing key 'bound'");
			assertThat(output).contains("Nothing declares ")
				.contains("queue 'dept44.declared-by-listener'")
				.contains(" on the broker, since the service has no AmqpAdmin bean, as with spring.rabbitmq.dynamic=false. Declare them through rabbitmq-topology")
				// A RabbitAdmin would skip it too
				.doesNotContain("dept44.not-declared");
		});
	}

	@Test
	void leavesOutALazyBeanRatherThanCreateIt() {
		// One that cannot be created would otherwise fail the startup, where it fails only once used
		contextRunner.withUserConfiguration(LazyTopology.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(UndeclaredTopologyWarning.class).findUndeclared()).isEmpty();
		});
	}

	@Test
	void quietWithARabbitAdmin(final CapturedOutput output) {
		contextRunner.withUserConfiguration(Topology.class).withPropertyValues("spring.rabbitmq.dynamic=true").run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(AmqpAdmin.class);
			assertThat(output).doesNotContain("Nothing declares");
		});
	}

	@Test
	void quietWithoutTopology(final CapturedOutput output) {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(UndeclaredTopologyWarning.class);
			assertThat(output).doesNotContain("Nothing declares");
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Topology {

		@Bean
		Queue workQueue() {
			return QueueBuilder.durable("dept44.work").quorum().build();
		}

		@Bean
		DirectExchange eventsExchange() {
			return new DirectExchange("dept44.events");
		}

		@Bean
		Binding workBinding() {
			return BindingBuilder.bind(new Queue("dept44.work")).to(new DirectExchange("dept44.events")).with("work");
		}

		@Bean
		Declarables declarables() {
			return new Declarables(new Queue("dept44.in-declarables"));
		}

		@Bean
		Queue notDeclaredQueue() {
			final var queue = new Queue("dept44.not-declared");
			queue.setShouldDeclare(false);
			return queue;
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class LazyTopology {

		@Bean
		@Lazy
		Queue lazyQueue() {
			throw new IllegalStateException("cannot be created");
		}
	}

	static class DeclaringListener {

		@RabbitListener(queuesToDeclare = @org.springframework.amqp.rabbit.annotation.Queue("dept44.declared-by-listener"), autoStartup = "false")
		void listen(final String payload) {
			// Never runs in this test
		}

		@RabbitListener(bindings = @QueueBinding(value = @org.springframework.amqp.rabbit.annotation.Queue("dept44.bound-by-listener"),
			exchange = @org.springframework.amqp.rabbit.annotation.Exchange("dept44.listener-events"),
			key = "bound"), autoStartup = "false")
		void listenBound(final String payload) {
			// Never runs in this test
		}
	}
}
