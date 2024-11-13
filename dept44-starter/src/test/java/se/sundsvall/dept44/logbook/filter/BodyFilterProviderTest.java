package se.sundsvall.dept44.logbook.filter;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.APPLICATION_XHTML_XML;
import static org.apache.http.entity.ContentType.APPLICATION_XML;
import static org.apache.http.entity.ContentType.TEXT_XML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zalando.logbook.BodyFilter;
import se.sundsvall.dept44.test.annotation.resource.Load;
import se.sundsvall.dept44.test.extension.ResourceLoaderExtension;

@ExtendWith({
	MockitoExtension.class, ResourceLoaderExtension.class
})
class BodyFilterProviderTest {

	@Spy
	private ObjectMapper objectMapperSpy;

	@Spy
	private DocumentBuilderFactory documentBuilderFactorySpy;

	@Spy
	private TransformerFactory transformerFactorySpy;

	@Mock
	private DocumentBuilder documentBuilderMock;

	@Mock
	private Transformer transformerMock;

	@Test
	void testPasswordFilter() {
		assertThat(BodyFilterProvider.passwordFilter()).isNotNull();
	}

	@Test
	void testBuildJsonPathFilters() {
		assertThat(BodyFilterProvider.buildJsonPathFilters(objectMapperSpy, Map.of("key1", "value1", "key2", "value2")))
			.hasSize(2);
	}

	@Test
	void testJsonPathFilter(@Load("/json-path-filter.input.json") final String input,
		@Load("/json-path-filter.expected.json") final String expected) {
		final var filters = BodyFilterProvider.buildJsonPathFilters(objectMapperSpy,
			Map.of("$.pin", "[pin]", "$.social_accounts[*].password", "[password]", "$.missing", "???"));

		var result = input;
		for (final var filter : filters) {
			result = filter.filter("application/json", result);
		}

		assertThatJson(result).isEqualTo(expected);
	}

	@Test
	void testBuildXPathFilters() {
		assertThat(BodyFilterProvider.buildXPathFilters(Map.of("key1", "value1", "key2", "value2"))).hasSize(2);
	}

	@Test
	void testCreateDocumentBuilderFactory() throws Exception {
		try (MockedStatic<DocumentBuilderFactory> documentBuilderFactoryMock = Mockito.mockStatic(
			DocumentBuilderFactory.class)) {
			documentBuilderFactoryMock.when(DocumentBuilderFactory::newInstance).thenReturn(documentBuilderFactorySpy);

			assertThat(BodyFilterProvider.createDocumentBuilderFactory()).isSameAs(documentBuilderFactorySpy);
			verify(documentBuilderFactorySpy).setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		}
	}

	@Test
	void testCreateDocumentBuilderFactoryThrowsException() throws Exception {
		try (MockedStatic<DocumentBuilderFactory> documentBuilderFactoryMock = Mockito.mockStatic(
			DocumentBuilderFactory.class)) {
			final var cause = new ParserConfigurationException("test");
			documentBuilderFactoryMock.when(DocumentBuilderFactory::newInstance).thenReturn(documentBuilderFactorySpy);
			doThrow(cause).when(documentBuilderFactorySpy).setFeature(any(), anyBoolean());

			final InvalidConfigurationException exception = assertThrows(InvalidConfigurationException.class,
				BodyFilterProvider::createDocumentBuilderFactory);
			assertThat(exception).hasCause(cause);
			verify(documentBuilderFactorySpy).setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		}
	}

	@Test
	void testCreateDocumentBuilder() throws Exception {
		when(documentBuilderFactorySpy.newDocumentBuilder()).thenReturn(documentBuilderMock);

		assertThat(BodyFilterProvider.createDocumentBuilder(documentBuilderFactorySpy)).isSameAs(documentBuilderMock);
		verify(documentBuilderFactorySpy).newDocumentBuilder();
	}

	@Test
	void testCreateDocumentBuilderThrowsException() throws Exception {
		when(documentBuilderFactorySpy.newDocumentBuilder()).thenThrow(new ParserConfigurationException("test"));

		final var exception = assertThrows(InvalidConfigurationException.class, () -> BodyFilterProvider
			.createDocumentBuilder(documentBuilderFactorySpy));
		assertThat(exception.getCause()).isInstanceOf(ParserConfigurationException.class);
		assertThat(exception.getCause().getMessage()).isEqualTo("test");
		verify(documentBuilderFactorySpy).newDocumentBuilder();
	}

	@Test
	void testCreateTransformerFactory() {
		try (MockedStatic<TransformerFactory> transformerFactoryMock = Mockito.mockStatic(TransformerFactory.class)) {
			transformerFactoryMock.when(TransformerFactory::newInstance).thenReturn(transformerFactorySpy);

			assertThat(BodyFilterProvider.createTransformerFactory()).isSameAs(transformerFactorySpy);
			verify(transformerFactorySpy).setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			verify(transformerFactorySpy).setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
		}
	}

	@Test
	void testCreateTransformerFactoryThrowsException() {
		try (MockedStatic<TransformerFactory> transformerFactoryMock = Mockito.mockStatic(TransformerFactory.class)) {
			final var cause = new IllegalArgumentException("test");
			transformerFactoryMock.when(TransformerFactory::newInstance).thenReturn(transformerFactorySpy);
			doThrow(cause).when(transformerFactorySpy).setAttribute(any(), any());

			final InvalidConfigurationException exception = assertThrows(InvalidConfigurationException.class,
				BodyFilterProvider::createTransformerFactory);
			assertThat(exception).hasCause(cause);
			verify(transformerFactorySpy).setAttribute(any(), any());
		}
	}

	@Test
	void testCreateTransformer() throws Exception {
		when(transformerFactorySpy.newTransformer()).thenReturn(transformerMock);

		assertThat(BodyFilterProvider.createTransformer(transformerFactorySpy)).isSameAs(transformerMock);
		verify(transformerFactorySpy).newTransformer();
	}

	@Test
	void testCreateTransformerThrowsException() throws Exception {
		when(transformerFactorySpy.newTransformer()).thenThrow(new TransformerConfigurationException("test"));

		final var exception = assertThrows(InvalidConfigurationException.class, () -> BodyFilterProvider
			.createTransformer(transformerFactorySpy));
		assertThat(exception.getCause()).isInstanceOf(TransformerConfigurationException.class);
		assertThat(exception.getCause().getMessage()).isEqualTo("test");
		verify(transformerFactorySpy).newTransformer();
	}

	@ParameterizedTest
	@MethodSource("argumentProvider")
	void testBodyFilter(String contentType, String body, String expectedResult) {
		final var xPath = "//replace[string-length(text()) > 0]";
		final var transformer = BodyFilterProvider.createTransformer(BodyFilterProvider.createTransformerFactory());

		final BodyFilter filter = BodyFilterProvider.xPath(xPath, "replacement", transformer);
		assertThat(filter.filter(contentType, body)).isEqualTo(expectedResult);
	}

	private static Stream<Arguments> argumentProvider() {
		final var INVALID_TYPE = ";";

		return Stream.of(
			Arguments.of(null, "{\"node\": \"data\"}", "{\"node\": \"data\"}"),
			Arguments.of(null, "<node>some_long_data_string</node>", "<node>some_long_data_string</node>"),
			Arguments.of(INVALID_TYPE, "<node>some_long_data_string</node>", "<node>some_long_data_string</node>"),
			Arguments.of(APPLICATION_JSON.toString(), null, null),
			Arguments.of(APPLICATION_JSON.toString(), "{\"node\": \"data\"}", "{\"node\": \"data\"}"),
			Arguments.of(APPLICATION_JSON.toString(), "{\"parent\": [{\"node\": \"some_long_data_string\"}]}",
				"{\"parent\": [{\"node\": \"some_long_data_string\"}]}"),
			Arguments.of(APPLICATION_XHTML_XML.toString(), null, null),
			Arguments.of(APPLICATION_XHTML_XML.toString(),
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><replace>data</replace><keep>data</keep><replace>data</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>",
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><replace>replacement</replace><keep>data</keep><replace>replacement</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>"),
			Arguments.of(APPLICATION_XHTML_XML.toString(),
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>data</replace></parent><keep>data</keep><replace>data</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>",
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>replacement</replace></parent><keep>data</keep><replace>replacement</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>"),
			Arguments.of(APPLICATION_XML.toString(), null, null),
			Arguments.of(APPLICATION_XML.toString(),
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"yes\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><replace>data</replace><keep>data</keep><replace>data</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>",
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"yes\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><replace>replacement</replace><keep>data</keep><replace>replacement</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>"),
			Arguments.of(APPLICATION_XHTML_XML.withCharset("").toString(),
				"<?xml version=\"1.0\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>data</replace></parent><keep>data</keep><replace>data</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>",
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>replacement</replace></parent><keep>data</keep><replace>replacement</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>"),
			Arguments.of(APPLICATION_XML.toString(),
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>data</replace></parent><keep>data</keep><replace>data</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>",
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>replacement</replace></parent><keep>data</keep><replace>replacement</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>"),
			Arguments.of(TEXT_XML.toString(), null, null),
			Arguments.of(TEXT_XML.toString(),
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><replace>data</replace><keep>data</keep><replace>data</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>",
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><replace>replacement</replace><keep>data</keep><replace>replacement</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>"),
			Arguments.of(TEXT_XML.toString(),
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>data</replace></parent><keep>data</keep><replace>data</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>",
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><parent><keep>data</keep><replace>replacement</replace></parent><keep>data</keep><replace>replacement</replace></SOAP-ENV:Body></SOAP-ENV:Envelope>"));
	}
}
