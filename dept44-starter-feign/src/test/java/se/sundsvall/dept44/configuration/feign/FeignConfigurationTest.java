package se.sundsvall.dept44.configuration.feign;

import feign.QueryMapEncoder;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.okhttp.OkHttpClient;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.zalando.logbook.Logbook;
import se.sundsvall.dept44.security.Truststore;
import tools.jackson.databind.json.JsonMapper;

import static feign.Logger.Level.FULL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.http.MediaType.ALL;

@SpringBootTest(classes = {
	FeignConfiguration.class,
	JacksonAutoConfiguration.class,
	HttpMessageConvertersAutoConfiguration.class,
	FeignAutoConfiguration.class
})
class FeignConfigurationTest {

	@MockitoBean
	private Logbook logbookMock;

	@Autowired
	private FeignConfiguration configuration;

	@Autowired
	private ObjectProvider<FeignHttpMessageConverters> messageConverters;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void testMethodAnnotations() {
		final var methodsToTest = List.of("logLevel", "logbookLogger", "okHttpClient", "feignEncoder", "feignDecoder", "queryMapEncoder", "jacksonFeignConverter", "binaryContentConverter");
		for (final Method method : configuration.getClass().getDeclaredMethods()) {
			if (methodsToTest.contains(method.getName())) {
				verifyMethodAnnotations(method);
			}
		}
	}

	private void verifyMethodAnnotations(final Method method) {
		assertTrue(method.isAnnotationPresent(Bean.class));
		switch (method.getName()) {
			case "logLevel", "feignEncoder", "feignDecoder", "queryMapEncoder", "jacksonFeignConverter", "binaryContentConverter" -> {
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

	@Test
	void testFeignEncoder() {
		assertThat(configuration.feignEncoder(messageConverters)).isNotNull().isInstanceOf(Encoder.class);
	}

	@Test
	void testFeignDecoder() {
		assertThat(configuration.feignDecoder(messageConverters)).isNotNull().isInstanceOf(Decoder.class);
	}

	@Test
	void testQueryMapEncoder() {
		assertThat(configuration.queryMapEncoder()).isNotNull().isInstanceOf(QueryMapEncoder.class);
	}

	@Test
	void testJacksonFeignConverter() {
		final var customizer = configuration.jacksonFeignConverter(jsonMapper);
		assertThat(customizer).isNotNull();

		// Create a list with a default JacksonJsonHttpMessageConverter
		final var converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new JacksonJsonHttpMessageConverter());

		// Apply the customizer - adds the configured converter at position 0
		customizer.accept(converters);

		// Verify the converter was added at the front (position 0)
		assertThat(converters).hasSize(2);
		assertThat(converters.getFirst()).isInstanceOf(JacksonJsonHttpMessageConverter.class);
	}

	@Test
	void testBinaryContentConverter() {
		final var customizer = configuration.binaryContentConverter();
		assertThat(customizer).isNotNull();

		// Create an empty list
		final var converters = new ArrayList<HttpMessageConverter<?>>();

		// Apply the customizer
		customizer.accept(converters);

		// Verify the converter was added
		assertThat(converters).hasSize(1);
		assertThat(converters.getFirst()).isInstanceOf(ByteArrayHttpMessageConverter.class);

		// Verify it supports all media types
		final var byteArrayConverter = (ByteArrayHttpMessageConverter) converters.getFirst();
		assertThat(byteArrayConverter.getSupportedMediaTypes()).contains(ALL);
	}
}
