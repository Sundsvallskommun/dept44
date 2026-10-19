package se.sundsvall.dept44.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DisabledListenerWarningTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RabbitMqDisabledConfiguration.class));

	@Test
	void warnsAboutListenersThatReceiveNothing(final CapturedOutput output) {
		contextRunner.withBean(MethodListener.class).withBean(ClassListener.class).withBean(NoListener.class).run(context -> {
			assertThat(context.getBean(DisabledListenerWarning.class).findListeners()).containsExactlyInAnyOrder(MethodListener.class.getName(), ClassListener.class.getName());
			assertThat(output).contains("RabbitMQ is off (dept44.rabbitmq.enabled is not true), so the listeners in ")
				.contains(MethodListener.class.getName())
				.contains(ClassListener.class.getName())
				.doesNotContain(NoListener.class.getName());
		});
	}

	@Test
	void quietWithoutListeners(final CapturedOutput output) {
		contextRunner.withBean(NoListener.class).run(context -> {
			assertThat(context).hasSingleBean(DisabledListenerWarning.class);
			assertThat(output).doesNotContain("RabbitMQ is off");
		});
	}

	static class MethodListener {

		@RabbitListener(queues = "work")
		void receive(final String message) {
			// Never runs in this test
		}
	}

	@RabbitListener(queues = "work")
	static class ClassListener {

		@RabbitHandler
		void receive(final String message) {
			// Never runs in this test
		}
	}

	static class NoListener {

		void receive(final String message) {
			// Never runs in this test
		}
	}
}
