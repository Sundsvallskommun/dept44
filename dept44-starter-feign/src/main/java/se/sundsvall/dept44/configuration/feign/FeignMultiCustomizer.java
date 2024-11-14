package se.sundsvall.dept44.configuration.feign;

import static java.util.concurrent.TimeUnit.SECONDS;

import feign.Request;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import se.sundsvall.dept44.configuration.feign.interceptor.OAuth2RequestInterceptor;
import se.sundsvall.dept44.configuration.feign.retryer.ActionRetryer;
import se.sundsvall.dept44.requestid.RequestId;

/**
 * Chains multiple {@link FeignBuilderCustomizer} into one.
 */
public class FeignMultiCustomizer {

	private final List<FeignBuilderCustomizer> customizers;

	private FeignMultiCustomizer() {
		customizers = new ArrayList<>();
	}

	public static FeignMultiCustomizer create() {
		return new FeignMultiCustomizer()
			.withRequestInterceptor(builder -> builder.header(RequestId.HEADER_NAME, RequestId.get()));
	}

	public FeignMultiCustomizer withCustomizer(final FeignBuilderCustomizer feignBuilderCustomizer) {
		customizers.add(feignBuilderCustomizer);
		return this;
	}

	public FeignMultiCustomizer withDecoder(final Decoder decoder) {
		return withCustomizer(builder -> builder.decoder(decoder));
	}

	public FeignMultiCustomizer withEncoder(final Encoder encoder) {
		return withCustomizer(builder -> builder.encoder(encoder));
	}

	public FeignMultiCustomizer withErrorDecoder(final ErrorDecoder errorDecoder) {
		return withCustomizer(builder -> builder.errorDecoder(errorDecoder));
	}

	/**
	 * Method for creating a RetryableOAuth2InterceptorForClientRegistration with default scope-set, which includes the
	 * device-scope needed to ensure correct handling of multiple instances in WSO2. The device-scope will be merged with
	 * the scopes defined in the clientRegistration.
	 *
	 * @param  clientRegistration containing authorization information for the client
	 * @return                    FeignMultiCustomizer with a configured RetryableOAuth2InterceptorForClientRegistration
	 */
	public FeignMultiCustomizer withRetryableOAuth2InterceptorForClientRegistration(final ClientRegistration clientRegistration) {
		return withRetryableOAuth2InterceptorForClientRegistration(clientRegistration, Set.of("device_" + UUID.randomUUID()));
	}

	/**
	 * Method for creating a RetryableOAuth2InterceptorForClientRegistration with a set of extra scopes (may be empty).
	 * Any extra scopes will be merged with the scopes defined in the clientRegistration.
	 *
	 * @param  clientRegistration containing authorization information for the client
	 * @param  extraScopes        a set of extra scopes
	 * @return                    FeignMultiCustomizer with a configured RetryableOAuth2InterceptorForClientRegistration
	 */
	public FeignMultiCustomizer withRetryableOAuth2InterceptorForClientRegistration(final ClientRegistration clientRegistration, final Set<String> extraScopes) {
		return withCustomizer(builder -> {
			final var oAuth2RequestInterceptor = new OAuth2RequestInterceptor(clientRegistration, extraScopes);
			builder.requestInterceptor(oAuth2RequestInterceptor);
			builder.retryer(new ActionRetryer(oAuth2RequestInterceptor::removeToken, 1));
		});
	}

	public FeignMultiCustomizer withRequestOptions(final Request.Options options) {
		return withCustomizer(builder -> builder.options(options));
	}

	public FeignMultiCustomizer withRequestTimeoutsInSeconds(final int connectionTimeout, final int readTimeout) {
		return withRequestOptions(new Request.Options(connectionTimeout, SECONDS, readTimeout, SECONDS, true));
	}

	public FeignMultiCustomizer withRequestInterceptor(final RequestInterceptor requestInterceptor) {
		return withCustomizer(builder -> builder.requestInterceptor(requestInterceptor));
	}

	public FeignBuilderCustomizer composeCustomizersToOne() {
		return builder -> customizers.forEach(customizer -> customizer.customize(builder));
	}
}
