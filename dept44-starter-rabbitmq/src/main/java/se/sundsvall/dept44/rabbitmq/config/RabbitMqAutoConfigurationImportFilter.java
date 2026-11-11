package se.sundsvall.dept44.rabbitmq.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import static java.util.Objects.requireNonNull;

/**
 * Leaves out every RabbitMQ auto-configuration, Spring Boot's and this starter's, unless
 * {@code dept44.rabbitmq.enabled} is true, and {@link RabbitMqDisabledConfiguration} when it is.
 * <p>
 * Off means no beans and no connection to the broker. Spring Boot's {@code @EnableRabbit} is left out with the rest, so
 * a {@code @RabbitListener} is not started either, and a service needs no conditions of its own around its listeners.
 * <p>
 * Only {@code true}, in any case, turns RabbitMQ on, as in Spring Boot's own property conditions. Read here and nowhere
 * else, so that a value such as {@code off} cannot turn RabbitMQ off without the warning about its listeners.
 */
public class RabbitMqAutoConfigurationImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {

	static final String ENABLED_PROPERTY = "dept44.rabbitmq.enabled";
	static final String SPRING_BOOT_RABBIT_PACKAGE = "org.springframework.boot.amqp.autoconfigure.";
	static final String ENABLED_CONFIGURATION = "se.sundsvall.dept44.rabbitmq.config.RabbitMqConfiguration";
	static final String DISABLED_CONFIGURATION = "se.sundsvall.dept44.rabbitmq.config.RabbitMqDisabledConfiguration";

	// Set by Spring before it calls match
	private @Nullable Environment environment;

	@Override
	public void setEnvironment(final Environment environment) {
		this.environment = environment;
	}

	@Override
	public boolean[] match(final @Nullable String[] autoConfigurationClasses, final AutoConfigurationMetadata autoConfigurationMetadata) {
		final var enabled = Boolean.parseBoolean(requireNonNull(environment, "environment").getProperty(ENABLED_PROPERTY));
		final var matches = new boolean[autoConfigurationClasses.length];
		for (var index = 0; index < autoConfigurationClasses.length; index++) {
			final var autoConfigurationClass = autoConfigurationClasses[index];
			// An entry already left out by another filter is null
			matches[index] = autoConfigurationClass == null || (enabled ? !DISABLED_CONFIGURATION.equals(autoConfigurationClass) : !isRabbitMq(autoConfigurationClass));
		}
		return matches;
	}

	private static boolean isRabbitMq(final String autoConfigurationClass) {
		return autoConfigurationClass.startsWith(SPRING_BOOT_RABBIT_PACKAGE) || ENABLED_CONFIGURATION.equals(autoConfigurationClass);
	}
}
