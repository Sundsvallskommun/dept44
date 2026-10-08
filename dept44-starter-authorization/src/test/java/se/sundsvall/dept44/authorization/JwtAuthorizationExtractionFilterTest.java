package se.sundsvall.dept44.authorization;

import io.jsonwebtoken.CompressionException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import se.sundsvall.dept44.authorization.configuration.JwtAuthorizationProperties;
import se.sundsvall.dept44.authorization.model.GenericGrantedAuthority;
import se.sundsvall.dept44.authorization.model.UsernameAuthenticationToken;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;
import se.sundsvall.dept44.problem.ThrowableProblem;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthorizationExtractionFilterTest {

	private static final String DEFAULT_JWT_HEADER_NAME = "x-authorization-info";

	@Mock
	private JwtAuthorizationProperties propertiesMock;

	@Mock
	private HttpServletRequest requestMock;

	@Mock
	private HttpServletResponse responseMock;

	@Mock
	private PrintWriter printWriterMock;

	@Mock
	private JsonMapper jsonMapperMock;

	@Mock
	private JwtTokenUtil jwtTokenUtilMock;

	@Mock
	private WebAuthenticationDetailsSource webAuthenticationDetailsSourceMock;

	@Mock
	private FilterChain filterChainMock;

	@Mock
	private GenericGrantedAuthority genericGrantedAuthorityMock;

	@InjectMocks
	private JwtAuthorizationExtractionFilter filter;

	private static Stream<Arguments> exceptionProvider() {
		return Stream.of(
			Arguments.of(new IllegalArgumentException("Exception 1"), "Credentials could not be read"),
			Arguments.of(new MalformedJwtException("Exception 2"), "Credentials could not be read"),
			Arguments.of(new UnsupportedJwtException("Exception 3"), "Credentials could not be read"),
			Arguments.of(new SignatureException("Exception 4"), "Invalid signature detected for credentials"),
			Arguments.of(new WeakKeyException("Exception 5"), "The verification key's size is not secure enough for the selected algorithm"),
			Arguments.of(new ExpiredJwtException(null, null, "Exception 6"), "Credentials has expired"),
			Arguments.of(new CompressionException("Exception 7"), "Exception occurred when reading credentials"));
	}

	@Test
	void doFilterInternalWhenNoJwtPresent() throws Exception {
		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);

		filter.doFilterInternal(requestMock, responseMock, filterChainMock);

		verify(propertiesMock).getHeaderName();
		verify(requestMock).getHeader(DEFAULT_JWT_HEADER_NAME);
		verify(filterChainMock).doFilter(requestMock, responseMock);
		verifyNoInteractions(jwtTokenUtilMock, jsonMapperMock, webAuthenticationDetailsSourceMock);
	}

	@Test
	void doFilterInternalWhenJwtPresentWithNoUsername() throws Exception {
		final var jwt = "jwttoken";

		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);
		when(requestMock.getHeader(DEFAULT_JWT_HEADER_NAME)).thenReturn(jwt);

		filter.doFilterInternal(requestMock, responseMock, filterChainMock);

		verify(propertiesMock).getHeaderName();
		verify(requestMock).getHeader(DEFAULT_JWT_HEADER_NAME);
		verify(jwtTokenUtilMock).getUsernameFromToken(jwt);
		verify(jwtTokenUtilMock).getRolesFromToken(jwt);
		verify(filterChainMock).doFilter(requestMock, responseMock);
		verifyNoMoreInteractions(jwtTokenUtilMock);
		verifyNoInteractions(jsonMapperMock, webAuthenticationDetailsSourceMock);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void doFilterInternalWhenCompleteJwtPresentReplacesAnonymousAuthentication() throws Exception {
		final var jwt = "jwttoken";
		final var username = "username";
		final var authentication = new AtomicReference<Authentication>();
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);
		when(requestMock.getHeader(DEFAULT_JWT_HEADER_NAME)).thenReturn(jwt);
		when(jwtTokenUtilMock.getUsernameFromToken(jwt)).thenReturn(username);
		when(jwtTokenUtilMock.getRolesFromToken(jwt)).thenReturn(List.of(genericGrantedAuthorityMock));
		doAnswer(_ -> {
			authentication.set(SecurityContextHolder.getContext().getAuthentication());
			return null;
		}).when(filterChainMock).doFilter(requestMock, responseMock);

		filter.doFilterInternal(requestMock, responseMock, filterChainMock);

		verify(webAuthenticationDetailsSourceMock).buildDetails(requestMock);
		verify(filterChainMock).doFilter(requestMock, responseMock);
		assertThat(authentication.get()).isInstanceOf(UsernameAuthenticationToken.class);
		assertThat(authentication.get().getName()).isEqualTo(username);
		assertThat(authentication.get().isAuthenticated()).isTrue();
		verifyNoInteractions(jsonMapperMock);
	}

	@Test
	void doFilterInternalKeepsAnExistingAuthentication() throws Exception {
		final var jwt = "jwttoken";
		final var existing = UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of());
		SecurityContextHolder.getContext().setAuthentication(existing);

		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);
		when(requestMock.getHeader(DEFAULT_JWT_HEADER_NAME)).thenReturn(jwt);
		when(jwtTokenUtilMock.getUsernameFromToken(jwt)).thenReturn("username");

		filter.doFilterInternal(requestMock, responseMock, filterChainMock);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
		verify(filterChainMock).doFilter(requestMock, responseMock);
		verifyNoInteractions(webAuthenticationDetailsSourceMock);
	}

	@Test
	void doFilterInternalLetsExceptionsFromTheRestOfTheChainThrough() throws Exception {
		final var jwt = "jwttoken";
		final var failure = new IllegalStateException("downstream");

		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);
		when(requestMock.getHeader(DEFAULT_JWT_HEADER_NAME)).thenReturn(jwt);
		when(jwtTokenUtilMock.getUsernameFromToken(jwt)).thenReturn("username");
		doThrow(failure).when(filterChainMock).doFilter(requestMock, responseMock);

		assertThatThrownBy(() -> filter.doFilterInternal(requestMock, responseMock, filterChainMock)).isSameAs(failure);

		verifyNoInteractions(jsonMapperMock, responseMock);
	}

	@ParameterizedTest
	@MethodSource("exceptionProvider")
	void doFilterInternalThrowsException(final Exception e, final String title) throws Exception {
		final var jwt = "jwttoken";
		final var problemString = "problemString";

		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);
		when(requestMock.getHeader(DEFAULT_JWT_HEADER_NAME)).thenReturn(jwt);
		when(jwtTokenUtilMock.getUsernameFromToken(jwt)).thenThrow(e);
		when(responseMock.getWriter()).thenReturn(printWriterMock);
		when(jsonMapperMock.writeValueAsString(any())).thenReturn(problemString);

		try (final MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
			filter.doFilterInternal(requestMock, responseMock, filterChainMock);

			final ArgumentCaptor<ThrowableProblem> throwableProblemCaptor = ArgumentCaptor.forClass(ThrowableProblem.class);

			verify(propertiesMock).getHeaderName();
			verify(requestMock).getHeader(DEFAULT_JWT_HEADER_NAME);
			verify(jwtTokenUtilMock).getUsernameFromToken(jwt);
			verify(jsonMapperMock).writeValueAsString(throwableProblemCaptor.capture());
			verify(printWriterMock).write(problemString);

			assertThat(throwableProblemCaptor.getValue().getTitle()).isEqualTo(title);
			assertThat(throwableProblemCaptor.getValue().getDetail()).isEqualTo(e.getMessage());
			assertThat(throwableProblemCaptor.getValue().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

			verifyNoMoreInteractions(jwtTokenUtilMock, jsonMapperMock, printWriterMock);
			verifyNoInteractions(webAuthenticationDetailsSourceMock, filterChainMock);
			securityContextHolderMock.verifyNoInteractions();
		}
	}
}
