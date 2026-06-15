package se.sundsvall.dept44.configuration.feign;

import feign.Feign;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import se.sundsvall.dept44.configuration.feign.interceptor.OAuth2RequestInterceptor;
import se.sundsvall.dept44.configuration.feign.retryer.ActionRetryer;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.dept44.support.Identifier.Type;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeignMultiCustomizerTest {

	@Mock
	private Feign.Builder builderMock;

	@Captor
	private ArgumentCaptor<OAuth2RequestInterceptor> oAuth2RequestInterceptorCaptor;

	@Test
	void testCreate() {
		final var customizer = FeignMultiCustomizer.create();
		customizer.composeCustomizersToOne().customize(builderMock);

		// One interceptor for x-request-id and one for X-Sent-By.
		assertThat(customizer).extracting("customizers").asInstanceOf(LIST).hasSize(2);
		verify(builderMock, times(2)).requestInterceptor(any(RequestInterceptor.class));
	}

	@Test
	void testCreatePropagatesIdentifier() {
		final var interceptorCaptor = ArgumentCaptor.forClass(RequestInterceptor.class);

		FeignMultiCustomizer.create().composeCustomizersToOne().customize(builderMock);
		verify(builderMock, times(2)).requestInterceptor(interceptorCaptor.capture());

		try {
			RequestId.init("test-request-id");
			Identifier.set(Identifier.create().withType(Type.AD_ACCOUNT).withValue("joe01doe"));

			final var template = new RequestTemplate();
			interceptorCaptor.getAllValues().forEach(interceptor -> interceptor.apply(template));

			org.assertj.core.api.Assertions.assertThat(template.headers().get(Identifier.HEADER_NAME))
				.containsExactly("joe01doe; type=adAccount");
		} finally {
			Identifier.remove();
			RequestId.reset();
		}
	}

	@Test
	void testCreateWithoutIdentifierOmitsHeader() {
		final var interceptorCaptor = ArgumentCaptor.forClass(RequestInterceptor.class);

		FeignMultiCustomizer.create().composeCustomizersToOne().customize(builderMock);
		verify(builderMock, times(2)).requestInterceptor(interceptorCaptor.capture());

		try {
			RequestId.init("test-request-id");
			// No Identifier set in the thread-local context.

			final var template = new RequestTemplate();
			interceptorCaptor.getAllValues().forEach(interceptor -> interceptor.apply(template));

			org.assertj.core.api.Assertions.assertThat(template.headers()).doesNotContainKey(Identifier.HEADER_NAME);
		} finally {
			RequestId.reset();
		}
	}

	@Test
	void testWithCustomizer() {
		final var feignBuilderCustomizerMock = Mockito.mock(FeignBuilderCustomizer.class);
		final var customizer = FeignMultiCustomizer.create()
			.withCustomizer(feignBuilderCustomizerMock)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(feignBuilderCustomizerMock).customize(same(builderMock));
	}

	@Test
	void testWithMultipleCustomizers() {
		final var feignBuilderCustomizerMock1 = Mockito.mock(FeignBuilderCustomizer.class);
		final var feignBuilderCustomizerMock2 = Mockito.mock(FeignBuilderCustomizer.class);
		final var customizer = FeignMultiCustomizer.create()
			.withCustomizer(feignBuilderCustomizerMock1)
			.withCustomizer(feignBuilderCustomizerMock2)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(feignBuilderCustomizerMock1).customize(same(builderMock));
		verify(feignBuilderCustomizerMock2).customize(same(builderMock));
	}

	@Test
	void testWithRetryableOauth2InterceptorForClientRegistrationWithDefaultScope() {
		final var clientRegistration = createClientRegistration();

		final var customizer = FeignMultiCustomizer.create()
			.withRetryableOAuth2InterceptorForClientRegistration(clientRegistration).composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock, atLeastOnce()).requestInterceptor(oAuth2RequestInterceptorCaptor.capture());
		verify(builderMock).retryer(any(ActionRetryer.class));

		assertThat(oAuth2RequestInterceptorCaptor.getValue())
			.extracting("clientRegistration").extracting("scopes")
			.asInstanceOf(collection(String.class)).hasSize(1)
			.first().matches(scope -> scope.matches("device_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$"));
	}

	@Test
	void testWithRetryableOauth2InterceptorForClientRegistrationWithEmptyExtraScopes() {
		final var clientRegistration = createClientRegistration();

		final var customizer = FeignMultiCustomizer.create()
			.withRetryableOAuth2InterceptorForClientRegistration(clientRegistration, Set.of()).composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock, atLeastOnce()).requestInterceptor(oAuth2RequestInterceptorCaptor.capture());
		verify(builderMock).retryer(any(ActionRetryer.class));

		// Spring Security 7 (Spring Boot 4.1) retains empty scopes as an empty set instead of nulling them out.
		// For the client_credentials flow this is functionally equivalent to no scopes (no scope parameter is sent).
		assertThat(oAuth2RequestInterceptorCaptor.getValue())
			.extracting("clientRegistration").extracting("scopes")
			.asInstanceOf(collection(String.class)).isEmpty();
	}

	@Test
	void testWithRetryableOauth2InterceptorForClientRegistrationWithDefaultAndExtraScope() {
		final var clientRegistration = createClientRegistration(Set.of("some_scope"));

		final var customizer = FeignMultiCustomizer.create()
			.withRetryableOAuth2InterceptorForClientRegistration(clientRegistration).composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock, atLeastOnce()).requestInterceptor(oAuth2RequestInterceptorCaptor.capture());
		verify(builderMock).retryer(any(ActionRetryer.class));

		assertThat(oAuth2RequestInterceptorCaptor.getValue())
			.extracting("clientRegistration").extracting("scopes")
			.asInstanceOf(collection(String.class)).hasSize(2);
	}

	@Test
	void testWithDecoder() {
		final var decoderMock = Mockito.mock(Decoder.class);
		final var customizer = FeignMultiCustomizer.create()
			.withDecoder(decoderMock)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock).decoder(same(decoderMock));
	}

	@Test
	void testWithEncoder() {
		final var encoderMock = Mockito.mock(Encoder.class);
		final var customizer = FeignMultiCustomizer.create()
			.withEncoder(encoderMock)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock).encoder(same(encoderMock));
	}

	@Test
	void testWithErrorDecoder() {
		final var errorDecoderMock = Mockito.mock(ErrorDecoder.class);
		final var customizer = FeignMultiCustomizer.create()
			.withErrorDecoder(errorDecoderMock)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock).errorDecoder(same(errorDecoderMock));
	}

	@Test
	void testWithRequestOptions() {
		final var requestOptionMock = Mockito.mock(Request.Options.class);
		final var customizer = FeignMultiCustomizer.create()
			.withRequestOptions(requestOptionMock)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock).options(same(requestOptionMock));
	}

	@Test
	void testWithRequestTimeoutInSeconds() {
		final var customizer = FeignMultiCustomizer.create()
			.withRequestTimeoutsInSeconds(1, 2)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		final var optionsCaptor = ArgumentCaptor.forClass(Request.Options.class);

		verify(builderMock).options(optionsCaptor.capture());

		final var options = optionsCaptor.getValue();

		assertThat(options.connectTimeoutMillis()).isEqualTo(1000);
		assertThat(options.readTimeoutMillis()).isEqualTo(2000);
		assertThat(options.isFollowRedirects()).isTrue();
	}

	@Test
	void testWithRequestInterceptor() {
		final var requestInterceptorMock = Mockito.mock(RequestInterceptor.class);
		final var customizer = FeignMultiCustomizer.create()
			.withRequestInterceptor(requestInterceptorMock)
			.composeCustomizersToOne();

		customizer.customize(builderMock);

		verify(builderMock).requestInterceptor(same(requestInterceptorMock));
	}

	private static ClientRegistration createClientRegistration() {
		return createClientRegistration(emptySet());
	}

	private static ClientRegistration createClientRegistration(final Set<String> scope) {
		return ClientRegistration
			.withRegistrationId("test")
			.clientId("id")
			.tokenUri("uri")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.scope(scope)
			.build();
	}
}
