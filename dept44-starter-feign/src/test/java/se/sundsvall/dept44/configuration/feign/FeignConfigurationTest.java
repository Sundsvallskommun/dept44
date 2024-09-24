package se.sundsvall.dept44.configuration.feign;

import static feign.Logger.Level.FULL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.zalando.logbook.Logbook;

import feign.okhttp.OkHttpClient;
import se.sundsvall.dept44.security.Truststore;

@SpringBootTest(classes = { FeignConfiguration.class })
class FeignConfigurationTest {

	@MockBean
	private Logbook logbookMock;

	@Autowired
	private FeignConfiguration configuration;

	@Test
	void testMethodAnnotations() {
		final var methodsToTest = List.of("logLevel", "logbookLogger", "okHttpClient");
		for (final Method method : configuration.getClass().getDeclaredMethods()) {
			if (methodsToTest.contains(method.getName())) {
				verifyMethodAnnotations(method);
			}
		}
	}

	private void verifyMethodAnnotations(final Method method) {
		assertTrue(method.isAnnotationPresent(Bean.class));
		switch (method.getName()) {
			case "logLevel" -> {
				assertFalse(method.isAnnotationPresent(ConditionalOnBean.class));
			}
			case "logbookLogger" -> {
				assertTrue(method.isAnnotationPresent(ConditionalOnBean.class));
				assertThat(method.getAnnotation(ConditionalOnBean.class).value()).containsExactly(Logbook.class);
			}
			case "okHttpClient" -> {
				assertTrue(method.isAnnotationPresent(ConditionalOnBean.class));
				assertThat(method.getAnnotation(ConditionalOnBean.class).value()).containsExactly(Truststore.class);
			}
			default -> fail();
		}
	}

	@Test
	void testLogLevel() {
		assertThat(configuration.logLevel()).isEqualTo(FULL);
	}

	@Test
	void testOkHttpClient() {
		assertThat(configuration.okHttpClient(new Truststore("/dummy"))).isNotNull().isInstanceOf(OkHttpClient.class);
	}
}
