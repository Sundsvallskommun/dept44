package se.sundsvall.dept44.rabbitmq.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The starter's own settings. The connection, and everything else Spring Boot covers, is set under
 * {@code spring.rabbitmq}.
 * <p>
 * {@code dept44.rabbitmq.enabled} is read by {@link RabbitMqAutoConfigurationImportFilter}, before any properties are
 * bound.
 *
 * @param publishConfirmTimeout how long {@link se.sundsvall.dept44.rabbitmq.Dept44RabbitPublisher} waits for the
 *                              broker to confirm a message
 */
@ConfigurationProperties("dept44.rabbitmq")
public record RabbitMqProperties(@DefaultValue("5s") Duration publishConfirmTimeout) {
}
