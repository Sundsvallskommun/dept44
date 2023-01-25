package se.sundsvall.dept44.configuration.resttemplate.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

class OAuth2ClientCredentialsRestTemplateInterceptorTest {

	@Mock
	private OAuth2AuthorizedClientManager clientManagerMock;

	@Mock
	private ClientRegistration clientRegistrationMock;

	@Mock
	private HttpRequest requestMock;

	@Mock
	private HttpHeaders headersMock;

	@Mock
	private ClientHttpRequestExecution executionMock;

	@Mock
	private OAuth2AuthorizedClient authorizedClientMock;

	@Mock
	private OAuth2AccessToken accessTokenMock;

	@BeforeEach
	public void initMocks() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testConstructor() {
		assertThat(createInterceptor(clientManagerMock, clientRegistrationMock))
			.isNotNull()
			.hasNoNullFieldsOrProperties()
			.hasFieldOrPropertyWithValue("clientManager", clientManagerMock)
			.hasFieldOrPropertyWithValue("clientRegistration", clientRegistrationMock);
	}

	@Test
	void testConstructorWithNull() {
		assertThat(assertThrows(IllegalArgumentException.class, () -> createInterceptor(null, clientRegistrationMock)))
			.hasMessage("clientManager cannot be null");

		assertThat(assertThrows(IllegalArgumentException.class, () -> createInterceptor(clientManagerMock, null)))
			.hasMessage("clientRegistration cannot be null");
	}

	@Test
	void testPrincipal() {
		var clientId = "clientId";
		when(clientRegistrationMock.getClientId()).thenReturn(clientId);

		var principal = createInterceptor(clientManagerMock, clientRegistrationMock).createPrincipal();

		assertThat(principal.getAuthorities()).isEmpty();
		assertThat(principal.getCredentials()).isNull();
		assertThat(principal.getDetails()).isNull();
		assertThat(principal.getName()).isEqualTo(clientId);
		assertThat(principal.isAuthenticated()).isFalse();
		assertThat(principal.getPrincipal()).isNull();

		verify(clientRegistrationMock).getClientId();
	}

	@Test
	void testIntercept() throws Exception {
		var body = "body".getBytes();
		var tokenValue = "tokenValue";

		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientManagerMock.authorize(any())).thenReturn(authorizedClientMock);
		when(authorizedClientMock.getAccessToken()).thenReturn(accessTokenMock);
		when(accessTokenMock.getTokenValue()).thenReturn(tokenValue);
		when(requestMock.getHeaders()).thenReturn(headersMock);

		createInterceptor(clientManagerMock, clientRegistrationMock).intercept(requestMock, body, executionMock);

		verify(requestMock).getHeaders();
		verify(headersMock).add(HttpHeaders.AUTHORIZATION, "Bearer tokenValue");
		verify(executionMock).execute(requestMock, body);
	}

	@Test
	void testInterceptWhenClientIsNotAuthorized() throws Exception {
		var body = "body".getBytes();

		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");

		final var interceptor = createInterceptor(clientManagerMock, clientRegistrationMock);
		assertThat(assertThrows(IllegalStateException.class, () -> interceptor.intercept(requestMock, body, executionMock)))
			.hasMessage("client credentials flow on registrationId failed, client is null");

		verifyNoInteractions(requestMock, executionMock);
	}

	private OAuth2ClientCredentialsRestTemplateInterceptor createInterceptor(OAuth2AuthorizedClientManager clientManager, ClientRegistration clientRegistration) {
		return new OAuth2ClientCredentialsRestTemplateInterceptor(clientManager, clientRegistration);
	}
}
