package se.sundsvall.dept44.configuration.feign;

import feign.Client;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.okhttp.OkHttpClient;
import feign.optionals.OptionalDecoder;
import java.util.List;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.PageableSpringEncoder;
import org.springframework.cloud.openfeign.support.PageableSpringQueryMapEncoder;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import se.sundsvall.dept44.configuration.feign.decoder.BinaryAwareDecoder;
import se.sundsvall.dept44.security.Truststore;
import tools.jackson.databind.json.JsonMapper;

public class FeignConfiguration {

	@Bean
	Logger.Level logLevel() {
		return Logger.Level.FULL;
	}

	/**
	 * Customizer for Feign's HttpMessageConverters to use Spring Boot's configured JsonMapper. Workaround for Spring Boot
	 * #48310 breaking the fix in spring-cloud-openfeign #1277. Adds the properly configured converter at position 0 to take
	 * priority.
	 */
	@Bean
	HttpMessageConverterCustomizer jacksonFeignConverter(final JsonMapper jsonMapper) {
		return converters -> converters.addFirst(new JacksonJsonHttpMessageConverter(jsonMapper));
	}

	/**
	 * Customizer to add a ByteArrayHttpMessageConverter that supports all media types. This enables Feign clients to
	 * properly decode binary responses (images, files, etc.) with any Content-Type. The default
	 * ByteArrayHttpMessageConverter only supports
	 * application/octet-stream.
	 */
	@Bean
	HttpMessageConverterCustomizer binaryContentConverter() {
		return converters -> {
			final var byteArrayConverter = new ByteArrayHttpMessageConverter();
			byteArrayConverter.setSupportedMediaTypes(List.of(MediaType.ALL));
			converters.addFirst(byteArrayConverter);
		};
	}

	/**
	 * Query map encoder for @QueryMap and @SpringQueryMap parameters. Converts objects like Pageable to query parameters
	 * instead of request body.
	 */
	@Bean
	QueryMapEncoder queryMapEncoder() {
		return new PageableSpringQueryMapEncoder();
	}

	/**
	 * Feign encoder using Spring's HttpMessageConverters. Handles: - Pageable parameters as query strings (via
	 * PageableSpringEncoder) - Multipart form-data - JSON with Jackson 3.x and configured DateTimeFeature settings
	 */
	@Bean
	Encoder feignEncoder(final ObjectProvider<FeignHttpMessageConverters> messageConverters) {
		return new PageableSpringEncoder(new SpringEncoder(messageConverters));
	}

	/**
	 * Feign decoder using Spring's HttpMessageConverters. Handles: - JSON with Jackson 3.x and configured DateTimeFeature
	 * settings - byte[], InputStream, InputStreamResource via BinaryAwareDecoder (Spring's ResourceHttpMessageConverter
	 * can't read into
	 * InputStreamResource) - Page deserialization via Spring Data's autoconfigured PageModule - ResponseEntity wrapping via
	 * ResponseEntityDecoder - Optional wrapping via OptionalDecoder
	 */
	@Bean
	Decoder feignDecoder(final ObjectProvider<FeignHttpMessageConverters> messageConverters) {
		return new OptionalDecoder(
			new ResponseEntityDecoder(
				new BinaryAwareDecoder(
					new SpringDecoder(messageConverters))));
	}

	@Bean
	@ConditionalOnBean(Truststore.class)
	Client okHttpClient(final Truststore trustStore) {

		final var trustManagerFactory = trustStore.getTrustManagerFactory();
		final var trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];

		return new OkHttpClient(new okhttp3.OkHttpClient.Builder()
			.sslSocketFactory(trustStore.getSSLContext().getSocketFactory(), trustManager)
			.build());
	}
}
