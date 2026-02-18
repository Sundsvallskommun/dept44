package se.sundsvall.dept44.configuration;

import io.github.resilience4j.common.circuitbreaker.configuration.CommonCircuitBreakerConfigurationProperties.InstanceProperties;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCircuitBreakerPropertiesConfigurationTest {

	@Mock
	private CircuitBreakerProperties circuitBreakerPropertiesMock;

	@Mock
	private InstanceProperties instancePropertiesMock;

	@InjectMocks
	private DefaultCircuitBreakerPropertiesConfiguration defaultCircuitBreakerPropertiesConfiguration;

	@Test
	void testPostProcessAfterInitialization() {

		// Setup.
		when(circuitBreakerPropertiesMock.getInstances()).thenReturn(Map.of("instance-1", instancePropertiesMock));

		// Call
		defaultCircuitBreakerPropertiesConfiguration.postProcessAfterInitialization(circuitBreakerPropertiesMock, "test");

		// Verification.
		verify(circuitBreakerPropertiesMock).getInstances();
		verify(instancePropertiesMock).getBaseConfig();
		verify(instancePropertiesMock).setBaseConfig("default");
	}
}
