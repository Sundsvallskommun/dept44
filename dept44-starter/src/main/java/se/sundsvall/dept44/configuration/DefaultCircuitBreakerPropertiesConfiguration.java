package se.sundsvall.dept44.configuration;

import java.util.Objects;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties;
/**
 * The purpose with this class is to set circuitbreaker baseConfig to "default"
 * for all circuitbreaker-configs where baseconfig is null.
 *
 * This might be the case if the circuitbreaker belongs to a feign-client.
 * (spring-cloud-openfeign bundles resilience4j on it's own)
 */
@Configuration
public class DefaultCircuitBreakerPropertiesConfiguration implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
		if (bean instanceof final CircuitBreakerProperties circuitBreakerProperties) {
			circuitBreakerProperties.getInstances().values().stream()
				.filter(instanceProperties -> Objects.isNull(instanceProperties.getBaseConfig()))
				.forEach(instanceProperties -> instanceProperties.setBaseConfig("default"));
		}
		return bean;
	}
}
