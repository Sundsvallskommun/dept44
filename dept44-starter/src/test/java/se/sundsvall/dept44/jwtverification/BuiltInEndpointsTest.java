package se.sundsvall.dept44.jwtverification;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInEndpointsTest {

	@Mock
	private ObjectProvider<HandlerMappingIntrospector> introspectorProviderMock;

	@Mock
	private HandlerMappingIntrospector introspectorMock;

	@Mock
	private HandlerMapping firstMappingMock;

	@Mock
	private HandlerMapping secondMappingMock;

	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/1/things");

	@Test
	void indexPageControllerExists() {
		assertThatNoException().isThrownBy(() -> Class.forName(BuiltInEndpoints.INDEX_PAGE_CONTROLLER));
	}

	@Test
	void withoutSpringMvcEveryMatchCounts() {
		assertThat(builtInEndpoints().serve(request)).isTrue();
	}

	@Test
	void serviceControllerIsNotBuiltIn() throws Exception {
		givenMappings();
		when(secondMappingMock.getHandler(any())).thenReturn(new HandlerExecutionChain(handlerMethod(new ServiceController())));

		assertThat(builtInEndpoints().serve(request)).isFalse();
	}

	@Test
	void springMvcResourceHandlerIsBuiltIn() throws Exception {
		givenMappings();
		when(firstMappingMock.getHandler(any())).thenReturn(new HandlerExecutionChain(new ResourceHttpRequestHandler()));

		assertThat(builtInEndpoints().serve(request)).isTrue();
	}

	@Test
	void firstMappingWithAHandlerDecides() throws Exception {
		givenMappings();
		when(firstMappingMock.getHandler(any())).thenReturn(new HandlerExecutionChain(handlerMethod(new ServiceController())));

		assertThat(builtInEndpoints().serve(request)).isFalse();
	}

	@Test
	void noHandlerMeansNotFound() throws Exception {
		givenMappings();

		assertThat(builtInEndpoints().serve(request)).isTrue();
	}

	@Test
	void lookupErrorMeansNoHandlerRuns() throws Exception {
		givenMappings();
		when(firstMappingMock.getHandler(any())).thenThrow(new HttpRequestMethodNotSupportedException("POST"));

		assertThat(builtInEndpoints().serve(request)).isTrue();
	}

	@Test
	void requestAttributesAreLeftAsTheyWere() throws Exception {
		givenMappings();
		request.setAttribute("kept", "value");
		when(firstMappingMock.getHandler(any())).thenAnswer(invocation -> {
			final var shielded = invocation.getArgument(0, HttpServletRequest.class);
			assertThat(shielded.getAttribute("kept")).isEqualTo("value");
			assertThat(Collections.list(shielded.getAttributeNames())).contains("kept");
			shielded.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, "matched");
			shielded.setAttribute("kept", null);
			shielded.removeAttribute("other");
			return null;
		});

		builtInEndpoints().serve(request);

		assertThat(request.getAttribute("kept")).isEqualTo("value");
		assertThat(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).isNull();
	}

	private void givenMappings() {
		when(introspectorProviderMock.getIfAvailable()).thenReturn(introspectorMock);
		when(introspectorMock.getHandlerMappings()).thenReturn(List.of(firstMappingMock, secondMappingMock));
	}

	private BuiltInEndpoints builtInEndpoints() {
		return new BuiltInEndpoints(introspectorProviderMock);
	}

	private static HandlerMethod handlerMethod(final Object controller) throws NoSuchMethodException {
		final Method method = controller.getClass().getDeclaredMethod("things");
		return new HandlerMethod(controller, method);
	}

	static class ServiceController {

		String things() {
			return "things";
		}
	}
}
