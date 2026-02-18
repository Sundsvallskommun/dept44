package se.sundsvall.dept44.logbook.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.zalando.logbook.BodyFilter;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.ObjectUtils.anyNull;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.hc.core5.http.ContentType.APPLICATION_XHTML_XML;
import static org.apache.hc.core5.http.ContentType.APPLICATION_XML;
import static org.apache.hc.core5.http.ContentType.TEXT_XML;
import static org.zalando.logbook.BodyFilter.merge;
import static org.zalando.logbook.core.BodyFilters.defaultValue;
import static org.zalando.logbook.json.JsonBodyFilters.replaceJsonStringProperty;

public final class BodyFilterProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(BodyFilterProvider.class);

	private BodyFilterProvider() {}

	public static BodyFilter passwordFilter() {
		return replaceJsonStringProperty(p -> p.toLowerCase().contains("password"), "*********");
	}

	public static List<BodyFilter> buildJsonPathFilters(final ObjectMapper objectMapper, final Map<String, String> jsonPathFilters) {

		final var jsonPathConfiguration = Configuration.builder()
			.jsonProvider(new JacksonJsonProvider(objectMapper))
			.mappingProvider(new JacksonMappingProvider(objectMapper))
			.options(Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST)
			.build();

		return jsonPathFilters.entrySet()
			.stream()
			.map(filter -> merge(defaultValue(), (contentType, body) -> {

				if (anyNull(contentType, body)) {
					return body;
				}

				if (body.trim().isEmpty()) {
					return "";
				}

				final var parsedContentType = ContentType.parse(contentType);

				if (parsedContentType != null && parsedContentType.getMimeType().equals(APPLICATION_JSON.getMimeType())) {
					final var documentContext = JsonPath.using(jsonPathConfiguration).parse(body);
					final var value = documentContext.read(filter.getKey());
					if (value instanceof final Collection<?> valueAsCollection && !valueAsCollection.isEmpty()) {
						documentContext.set(filter.getKey(), filter.getValue());
					}

					return documentContext.jsonString();
				}

				return body;
			}))
			.toList();
	}

	public static List<BodyFilter> buildXPathFilters(final Map<String, String> xPathFilters) {
		final TransformerFactory transformerFactory = createTransformerFactory();

		return xPathFilters.entrySet()
			.stream()
			.map(filter -> xPath(filter.getKey(), filter.getValue(), createTransformer(transformerFactory)))
			.toList();
	}

	static DocumentBuilder createDocumentBuilder(final DocumentBuilderFactory factory) {
		try {
			return factory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new InvalidConfigurationException(e);
		}
	}

	static Transformer createTransformer(final TransformerFactory factory) {
		try {
			return factory.newTransformer();
		} catch (final TransformerConfigurationException e) {
			throw new InvalidConfigurationException(e);
		}
	}

	static DocumentBuilderFactory createDocumentBuilderFactory() {
		try {
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			return factory;
		} catch (final ParserConfigurationException e) {
			throw new InvalidConfigurationException(e);
		}

	}

	static TransformerFactory createTransformerFactory() {
		try {
			final TransformerFactory factory = TransformerFactory.newInstance();
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			return factory;
		} catch (final IllegalArgumentException e) {
			throw new InvalidConfigurationException(e);
		}
	}

	static BodyFilter xPath(final String xPath, final String replacement, final Transformer transformer) {
		final List<String> xmlContentTypes = List.of(APPLICATION_XHTML_XML.getMimeType(), APPLICATION_XML.getMimeType(), TEXT_XML.getMimeType());

		return (contentTypeString, body) -> {
			if (anyNull(contentTypeString, body)) {
				return body;
			}

			try {
				final ContentType contentType = ContentType.parse(contentTypeString);
				if (contentType != null && xmlContentTypes.contains(contentType.getMimeType())) {
					// Evaluate what charSet to use
					final var charSet = evaluateCharset(contentType);

					// Create a document and xpath
					final var builder = createDocumentBuilder(createDocumentBuilderFactory());
					final Document document = builder.parse(new ByteArrayInputStream(body.getBytes(charSet)));

					// Evaluate xpath matches and replace content
					final XPath path = XPathFactory.newInstance().newXPath();
					final NodeList matches = (NodeList) path.evaluate(xPath, document, XPathConstants.NODESET);
					for (int i = 0; i < matches.getLength(); i++) {
						matches.item(i).setTextContent(replacement);
					}

					// Set up a transformer to use incoming encoding and to set a standalone attribute
					transformer.setOutputProperty(OutputKeys.ENCODING, charSet.name());
					transformer.setOutputProperty(OutputKeys.STANDALONE, document.getXmlStandalone() ? "yes" : "no");

					// Return filtered body as string
					final StringWriter writer = new StringWriter();
					transformer.transform(new DOMSource(document), new StreamResult(writer));
					return writer.toString();
				}

				return body;

			} catch (final Exception e) {
				LOGGER.warn("An exception occurred while filtering content from incoming xml request body ({}).", e.getMessage());
				return body;
			}
		};
	}

	private static Charset evaluateCharset(final ContentType contentType) {
		// If the incoming contentType hasn't defined any charset, then UTF-8 is returned; otherwise incoming charset is
		// returned
		return isNull(contentType.getCharset()) ? StandardCharsets.UTF_8 : contentType.getCharset();
	}
}
