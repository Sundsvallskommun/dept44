package se.sundsvall.petinventory.integration.petstore.configuration;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;

@Import(FeignConfiguration.class)
public class PetStoreConfiguration {

	public static final String CLIENT_ID = "petstore";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final PetStoreProperties petstoreProperties, final ClientRegistrationRepository clientRegistrationRepository) {
		return FeignMultiCustomizer.create()
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, List.of(NOT_FOUND.value())))
			.withRequestTimeoutsInSeconds(petstoreProperties.connectTimeout(), petstoreProperties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRegistrationRepository.findByRegistrationId(CLIENT_ID))
			.composeCustomizersToOne();
	}
}
