package se.sundsvall.dept44.logbook.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.zalando.logbook.BodyFilter;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

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
	private static final String INVALID_JSON_REPLACEMENT = "<body omitted: not valid JSON, so json-path filters could not be applied>";
	private static final String INVALID_XML_REPLACEMENT = "<body omitted: not valid XML, so xpath filters could not be applied>";
	private static final List<String> XML_MIME_TYPES = List.of(APPLICATION_XHTML_XML.getMimeType(), APPLICATION_XML.getMimeType(), TEXT_XML.getMimeType());

	private BodyFilterProvider() {}

	public static BodyFilter passwordFilter() {
		return replaceJsonStringProperty(p -> p.toLowerCase().contains("password"), "*********");
	}

	/**
	 * Replaces a body larger than the policy allows with a short note, so that no later filter or the log formatter has
	 * to process it. Bodies can only get here oversized when their length was unknown when capturing started.
	 */
	public static BodyFilter oversizedBodyFilter(final BodyCapturePolicy policy) {
		return (contentType, body) -> {
			if (policy.exceedsLimit(body.length())) {
				return policy.omittedNote(contentType);
			}
			return body;
		};
	}

	public static List<BodyFilter> buildJsonPathFilters(final ObjectMapper objectMapper, final Map<String, String> jsonPathFilters) {

		final var jsonPathConfiguration = Configuration.builder()
			.jsonProvider(new JacksonJsonProvider(objectMapper))
			.mappingProvider(new JacksonMappingProvider(objectMapper))
			.options(Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST)
			.build();

		return jsonPathFilters.entrySet()
			.stream()
			.map(filter -> jsonPathFilter(jsonPathConfiguration, compileJsonPath(filter.getKey()), filter.getValue()))
			.toList();
	}

	/**
	 * Compiled once, when the filters are built, so that a path with a syntax error stops the application from starting.
	 * Compiled per body, it would fail on every JSON body instead, and those bodies would be logged as if they were not
	 * valid JSON.
	 */
	private static JsonPath compileJsonPath(final String path) {
		try {
			return JsonPath.compile(path);
		} catch (final InvalidPathException | IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid json-path '%s' in logbook.body-filters.json-path: %s".formatted(path, e.getMessage()), e);
		}
	}

	private static BodyFilter jsonPathFilter(final Configuration jsonPathConfiguration, final JsonPath path, final String replacement) {
		return merge(defaultValue(), (contentType, body) -> applyJsonPathFilter(jsonPathConfiguration, path, replacement, contentType, body));
	}

	private static String applyJsonPathFilter(final Configuration jsonPathConfiguration, final JsonPath path, final String replacement, final String contentType, final String body) {
		if (anyNull(contentType, body)) {
			return body;
		}

		if (body.trim().isEmpty()) {
			return "";
		}

		if (!isJson(contentType)) {
			return body;
		}

		try {
			return maskJsonPath(jsonPathConfiguration, path, replacement, body);
		} catch (final RuntimeException e) {
			// Never fall back to the unfiltered body: it holds exactly the fields these filters exist to mask
			LOGGER.debug("Could not apply json-path filter to a body that is not valid JSON ({})", e.getMessage());
			return INVALID_JSON_REPLACEMENT;
		}
	}

	private static String maskJsonPath(final Configuration jsonPathConfiguration, final JsonPath path, final String replacement, final String body) {
		final var documentContext = JsonPath.using(jsonPathConfiguration).parse(body);
		final Object value = documentContext.read(path);
		if (value instanceof final Collection<?> valueAsCollection && !valueAsCollection.isEmpty()) {
			documentContext.set(path, replacement);
		}

		return documentContext.jsonString();
	}

	public static List<BodyFilter> buildXPathFilters(final Map<String, String> xPathFilters) {
		return xPathFilters.entrySet()
			.stream()
			.map(filter -> xPath(validateXPath(filter.getKey()), filter.getValue()))
			.toList();
	}

	/**
	 * Compiled once, when the filters are built, and evaluated against an empty document the way the filter evaluates it,
	 * so that an expression the filter could never use stops the application from starting. That covers a syntax error
	 * as well as an expression that does not select nodes, such as {@code count(//x)}. A compiled expression is not
	 * thread-safe, so each body is filtered with an expression of its own.
	 */
	private static String validateXPath(final String xPath) {
		if (xPath == null) {
			throw new IllegalArgumentException("Invalid xpath 'null' in logbook.body-filters.x-path");
		}
		try {
			XPathFactory.newInstance().newXPath().compile(xPath)
				.evaluate(createDocumentBuilder(createDocumentBuilderFactory()).newDocument(), XPathConstants.NODESET);
			return xPath;
		} catch (final XPathExpressionException e) {
			throw new IllegalArgumentException("Invalid xpath '%s' in logbook.body-filters.x-path: %s".formatted(xPath, e.getMessage()), e);
		}
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

	static BodyFilter xPath(final String xPath, final String replacement) {
		return (contentType, body) -> {
			if (anyNull(contentType, body) || body.isBlank() || !isXml(contentType)) {
				return body;
			}

			try {
				return maskXPath(xPath, replacement, contentType, body);
			} catch (final IOException | SAXException | XPathExpressionException | TransformerException | RuntimeException e) {
				// Never fall back to the unfiltered body: it holds exactly the content these filters exist to mask
				LOGGER.debug("Could not apply xpath filter to a body that is not valid XML ({})", e.getMessage());
				return INVALID_XML_REPLACEMENT;
			}
		};
	}

	private static String maskXPath(final String xPath, final String replacement, final String contentType, final String body)
		throws IOException, SAXException, XPathExpressionException, TransformerException {
		final var charset = evaluateCharset(ContentType.parse(contentType));
		final Document document = createDocumentBuilder(createDocumentBuilderFactory()).parse(new ByteArrayInputStream(body.getBytes(charset)));

		final NodeList matches = (NodeList) XPathFactory.newInstance().newXPath().evaluate(xPath, document, XPathConstants.NODESET);
		for (int i = 0; i < matches.getLength(); i++) {
			matches.item(i).setTextContent(replacement);
		}

		// A transformer is not thread-safe, so every body gets its own
		final var transformer = createTransformer(createTransformerFactory());
		transformer.setOutputProperty(OutputKeys.ENCODING, charset.name());
		transformer.setOutputProperty(OutputKeys.STANDALONE, standalone(document));

		final var writer = new StringWriter();
		transformer.transform(new DOMSource(document), new StreamResult(writer));
		return writer.toString();
	}

	private static String standalone(final Document document) {
		if (document.getXmlStandalone()) {
			return "yes";
		}
		return "no";
	}

	private static boolean isJson(final String contentType) {
		final var mimeType = mimeType(contentType);
		return mimeType.equals(APPLICATION_JSON.getMimeType()) || mimeType.endsWith("+json");
	}

	private static boolean isXml(final String contentType) {
		final var mimeType = mimeType(contentType);
		return XML_MIME_TYPES.contains(mimeType) || mimeType.endsWith("+xml");
	}

	private static String mimeType(final String contentType) {
		try {
			final var parsed = ContentType.parse(contentType);
			if (parsed == null) {
				return "";
			}
			return parsed.getMimeType().toLowerCase(Locale.ROOT);
		} catch (final RuntimeException _) {
			// Such as a charset Java does not know: the body is still of the type before the parameters
			return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		}
	}

	private static Charset evaluateCharset(final ContentType contentType) {
		// If the incoming contentType hasn't defined any charset, then UTF-8 is returned; otherwise incoming charset is
		// returned
		return isNull(contentType.getCharset()) ? StandardCharsets.UTF_8 : contentType.getCharset();
	}
}
