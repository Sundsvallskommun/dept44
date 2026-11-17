package se.sundsvall.dept44.rabbitmq.config;

import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	/**
	 * A lazy bean whose class has a method signature with a class that is not there at runtime, which fails only once the
	 * bean is created. Looking through its methods fails as well, and must not fail the startup.
	 */
	@Test
	void skipsABeanWhoseMethodsCannotBeLookedThrough() throws ClassNotFoundException {
		final var unresolvable = withoutMissingType(UnresolvableListener.class);

		contextRunner.withBean(MethodListener.class)
			.withInitializer(context -> {
				final var definition = new RootBeanDefinition(unresolvable);
				definition.setLazyInit(true);
				((GenericApplicationContext) context).registerBeanDefinition("unresolvableListener", definition);
			})
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(DisabledListenerWarning.class).findListeners()).containsExactly(MethodListener.class.getName());
			});
	}

	@Test
	void skipsABeanWhoseTypeCannotBeLoaded() {
		final var beanFactory = mock(ConfigurableListableBeanFactory.class);
		when(beanFactory.getBeanDefinitionNames()).thenReturn(new String[] {
			"unloadable", "methodListener"
		});
		when(beanFactory.getType("unloadable", false)).thenThrow(new NoClassDefFoundError("se/sundsvall/Missing"));
		doReturn(MethodListener.class).when(beanFactory).getType("methodListener", false);

		assertThat(new DisabledListenerWarning(beanFactory).findListeners()).containsExactly(MethodListener.class.getName());
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

	static class MissingType {
	}

	static class UnresolvableListener {

		@RabbitListener(queues = "work")
		void receive(final String message) {
			// Never runs in this test
		}

		void uses(final MissingType missing) {
			// Never runs in this test
		}
	}

	/**
	 * Loads the type through a class loader of its own, in which {@link MissingType} is not there
	 */
	private static Class<?> withoutMissingType(final Class<?> type) throws ClassNotFoundException {
		final var parent = DisabledListenerWarningTest.class.getClassLoader();
		return new ClassLoader(parent) {
			@Override
			protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
				if (name.equals(MissingType.class.getName())) {
					throw new ClassNotFoundException(name);
				}
				if (!name.equals(type.getName())) {
					return super.loadClass(name, resolve);
				}
				synchronized (getClassLoadingLock(name)) {
					final var loaded = findLoadedClass(name);
					return loaded != null ? loaded : define(name);
				}
			}

			private Class<?> define(final String name) throws ClassNotFoundException {
				try (final var bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
					final var classFile = Objects.requireNonNull(bytes, name).readAllBytes();
					return defineClass(name, classFile, 0, classFile.length);
				} catch (final IOException e) {
					throw new ClassNotFoundException(name, e);
				}
			}
		}.loadClass(type.getName());
	}
}
