package se.sundsvall.dept44.configuration.resttemplate.interceptor;

import static java.util.Objects.isNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * Deprecated module. Use another web client (for example Feign) instead.
 * 
 * @deprecated
 */
@Deprecated(since = "2022-10-10", forRemoval = true)
public class OAuth2ClientCredentialsRestTemplateInterceptor implements ClientHttpRequestInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";

	private final OAuth2AuthorizedClientManager clientManager;
	private final Authentication principal;
	private final ClientRegistration clientRegistration;

	public OAuth2ClientCredentialsRestTemplateInterceptor(final OAuth2AuthorizedClientManager clientManager, final ClientRegistration clientRegistration) {
		requireNonNull(clientManager, "clientManager cannot be null");
		requireNonNull(clientRegistration, "clientRegistration cannot be null");

		this.clientManager = clientManager;
		this.clientRegistration = clientRegistration;

		principal = createPrincipal();
	}

	@Override
	public ClientHttpResponse intercept(final HttpRequest request, final byte[] body,
		final ClientHttpRequestExecution execution) throws IOException {
		var oAuth2AuthorizeRequest = OAuth2AuthorizeRequest
			.withClientRegistrationId(clientRegistration.getRegistrationId())
			.principal(principal)
			.build();
		var client = clientManager.authorize(oAuth2AuthorizeRequest);
		if (isNull(client)) {
			throw new IllegalStateException("client credentials flow on " + clientRegistration.getRegistrationId() + " failed, client is null");
		}

		request.getHeaders().add(AUTHORIZATION, BEARER_PREFIX + client.getAccessToken().getTokenValue());
		return execution.execute(request, body);
	}

	Authentication createPrincipal() {
		return new Authentication() {

			private static final long serialVersionUID = 6069811939549188960L;

			@Override
			public String getName() {
				return clientRegistration.getClientId();
			}

			@Override
			public Collection<? extends GrantedAuthority> getAuthorities() {
				return List.of();
			}

			@Override
			public Object getCredentials() {
				return null;
			}

			@Override
			public Object getDetails() {
				return null;
			}

			@Override
			public Object getPrincipal() {
				return null;
			}

			@Override
			public boolean isAuthenticated() {
				return false;
			}

			@Override
			public void setAuthenticated(final boolean isAuthenticated) throws IllegalArgumentException {
				// Do nothing
			}
		};
	}
}
