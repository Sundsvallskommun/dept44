package se.sundsvall.dept44.rabbitmq.config;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Loaded instead of {@link RabbitMqConfiguration} when {@code dept44.rabbitmq.enabled} is not true. Which of the two is
 * loaded is decided by {@link RabbitMqAutoConfigurationImportFilter}.
 */
@AutoConfiguration
class RabbitMqDisabledConfiguration {

	@Bean
	DisabledListenerWarning dept44DisabledListenerWarning(final ConfigurableListableBeanFactory beanFactory) {
		return new DisabledListenerWarning(beanFactory);
	}
}
