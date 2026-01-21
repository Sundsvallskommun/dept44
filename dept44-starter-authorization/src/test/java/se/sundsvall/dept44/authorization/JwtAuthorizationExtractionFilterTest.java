package se.sundsvall.dept44.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import se.sundsvall.dept44.ServiceApplication;
import se.sundsvall.dept44.authorization.configuration.JwtAuthorizationProperties;
import se.sundsvall.dept44.authorization.model.GenericGrantedAuthority;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;
import se.sundsvall.dept44.problem.Status;
import se.sundsvall.dept44.problem.ThrowableProblem;

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
	private ApplicationContext applicationContextMock;

	@Mock
	private PrintWriter printWriterMock;

	@Mock
	private ObjectMapper objectMapperMock;

	@Mock
	private JwtTokenUtil jwtTokenUtilMock;

	@Mock
	private WebAuthenticationDetailsSource webAuthenticationDetailsSourceMock;

	@Mock
	private FilterChain filterChainMock;

	@Mock
	private SecurityContext securityContextMock;

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
	void shouldReturnTrueWhenAuthorizationEnabledOnApplication() {
		when(applicationContextMock.getBeansWithAnnotation(ServiceApplication.class)).thenReturn(Map.of("application", new ServiceApplicationWithJwtAuthorization()));

		assertThat(filter.shouldNotFilter(requestMock)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(classes = {
		PlainServiceApplication.class, PlainBean.class
	})
	void shouldReturnFalseWhenAuthorizationNotEnabledOnApplication(final Class<?> beanClass) throws Exception {
		when(applicationContextMock.getBeansWithAnnotation(ServiceApplication.class)).thenReturn(Map.of("application", beanClass.getDeclaredConstructor().newInstance()));

		assertThat(filter.shouldNotFilter(requestMock)).isFalse();
	}

	@Test
	void doFilterInternalWhenNoJwtPresent() throws Exception {
		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);

		filter.doFilterInternal(requestMock, responseMock, filterChainMock);

		verify(propertiesMock).getHeaderName();
		verify(requestMock).getHeader(DEFAULT_JWT_HEADER_NAME);
		verify(filterChainMock).doFilter(requestMock, responseMock);
		verifyNoInteractions(jwtTokenUtilMock, objectMapperMock, webAuthenticationDetailsSourceMock);
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
		verifyNoInteractions(objectMapperMock, webAuthenticationDetailsSourceMock);
	}

	@Test
	void doFilterInternalWhenCompleteJwtPresent() throws Exception {
		final var jwt = "jwttoken";
		final var username = "username";

		when(propertiesMock.getHeaderName()).thenReturn(DEFAULT_JWT_HEADER_NAME);
		when(requestMock.getHeader(DEFAULT_JWT_HEADER_NAME)).thenReturn(jwt);
		when(jwtTokenUtilMock.getUsernameFromToken(jwt)).thenReturn(username);
		when(jwtTokenUtilMock.getRolesFromToken(jwt)).thenReturn(List.of(genericGrantedAuthorityMock));

		try (final MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
			securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContextMock);
			filter.doFilterInternal(requestMock, responseMock, filterChainMock);

			verify(propertiesMock).getHeaderName();
			verify(requestMock).getHeader(DEFAULT_JWT_HEADER_NAME);
			verify(jwtTokenUtilMock).getUsernameFromToken(jwt);
			verify(jwtTokenUtilMock).getRolesFromToken(jwt);
			verify(webAuthenticationDetailsSourceMock).buildDetails(requestMock);
			securityContextHolderMock.verify(SecurityContextHolder::getContext, times(2));
			verify(securityContextMock).setAuthentication(any(Authentication.class));
			verify(filterChainMock).doFilter(requestMock, responseMock);

			verifyNoInteractions(objectMapperMock);
		}
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
		when(objectMapperMock.writeValueAsString(any())).thenReturn(problemString);

		try (final MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
			filter.doFilterInternal(requestMock, responseMock, filterChainMock);

			final ArgumentCaptor<ThrowableProblem> throwableProblemCaptor = ArgumentCaptor.forClass(ThrowableProblem.class);

			verify(propertiesMock).getHeaderName();
			verify(requestMock).getHeader(DEFAULT_JWT_HEADER_NAME);
			verify(jwtTokenUtilMock).getUsernameFromToken(jwt);
			verify(objectMapperMock).writeValueAsString(throwableProblemCaptor.capture());
			verify(printWriterMock).write(problemString);

			assertThat(throwableProblemCaptor.getValue().getTitle()).isEqualTo(title);
			assertThat(throwableProblemCaptor.getValue().getDetail()).isEqualTo(e.getMessage());
			assertThat(throwableProblemCaptor.getValue().getStatus()).isEqualTo(Status.UNAUTHORIZED);

			verifyNoMoreInteractions(jwtTokenUtilMock, objectMapperMock, printWriterMock);
			verifyNoInteractions(webAuthenticationDetailsSourceMock, securityContextMock, filterChainMock);
			securityContextHolderMock.verifyNoInteractions();
		}
	}

	// Dummy classes to test annotation verification in shouldNotFilter method
	private static class PlainBean {
	}

	@ServiceApplication
	private static class PlainServiceApplication {
	}

	@ServiceApplication
	@EnableJwtAuthorization
	private static class ServiceApplicationWithJwtAuthorization {
	}
}
