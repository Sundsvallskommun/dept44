package se.sundsvall.dept44.logbook.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.zalando.logbook.BodyFilter;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.ObjectUtils.anyNull;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.hc.core5.http.ContentType.APPLICATION_XHTML_XML;
import static org.apache.hc.core5.http.ContentType.APPLICATION_XML;
import static org.apache.hc.core5.http.ContentType.TEXT_XML;
import static org.zalando.logbook.json.JsonBodyFilters.replaceJsonStringProperty;

public final class BodyFilterProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(BodyFilterProvider.class);
	private static final String INVALID_JSON_REPLACEMENT = "<body omitted: not valid JSON, so json-path filters could not be applied>";
	private static final String INVALID_XML_REPLACEMENT = "<body omitted: not valid XML, so xpath filters could not be applied>";
	private static final String FAILED_XML_REPLACEMENT = "<body omitted: xpath filters could not be applied>";
	private static final String INVALID_XPATH = "Invalid xpath '%s' in logbook.body-filters.x-path: %s";
	private static final DefaultHandler THROWING_ERROR_HANDLER = new DefaultHandler();
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

	/**
	 * Builds one filter for all configured json-paths, so that a body is parsed once however many paths there are.
	 */
	public static List<BodyFilter> buildJsonPathFilters(final ObjectMapper objectMapper, final Map<String, String> jsonPathFilters) {
		if (jsonPathFilters.isEmpty()) {
			return List.of();
		}

		final var jsonPathConfiguration = Configuration.builder()
			.jsonProvider(new JacksonJsonProvider(objectMapper))
			.mappingProvider(new JacksonMappingProvider(objectMapper))
			.options(Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST)
			.build();

		final var masks = jsonPathFilters.entrySet()
			.stream()
			.map(filter -> new JsonPathMask(compileJsonPath(filter.getKey()), filter.getValue()))
			.toList();

		return List.of((contentType, body) -> applyJsonPathFilters(jsonPathConfiguration, masks, contentType, body));
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

	private static String applyJsonPathFilters(final Configuration jsonPathConfiguration, final List<JsonPathMask> masks, final String contentType, final String body) {
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
			return maskJsonPaths(jsonPathConfiguration, masks, body);
		} catch (final RuntimeException e) {
			// Never fall back to the unfiltered body: it holds exactly the fields these filters exist to mask
			LOGGER.debug("Could not apply json-path filters to a body that is not valid JSON ({})", e.getMessage());
			return INVALID_JSON_REPLACEMENT;
		}
	}

	private static String maskJsonPaths(final Configuration jsonPathConfiguration, final List<JsonPathMask> masks, final String body) {
		final var documentContext = JsonPath.using(jsonPathConfiguration).parse(body);
		for (final var mask : masks) {
			final Object value = documentContext.read(mask.path());
			if (value instanceof final Collection<?> valueAsCollection && !valueAsCollection.isEmpty()) {
				documentContext.set(mask.path(), mask.replacement());
			}
		}

		return documentContext.jsonString();
	}

	/**
	 * Builds one filter for all configured xpaths, so that a body is parsed once however many expressions there are.
	 */
	public static List<BodyFilter> buildXPathFilters(final Map<String, String> xPathFilters) {
		if (xPathFilters.isEmpty()) {
			return List.of();
		}

		final var masks = xPathFilters.entrySet()
			.stream()
			.map(filter -> new XPathMask(validateXPath(filter.getKey()), filter.getValue()))
			.toList();

		return List.of(xPathFilter(masks));
	}

	/**
	 * Compiled once, when the filters are built, and evaluated against an empty document the way the filter evaluates it,
	 * so that an expression the filter could never use stops the application from starting. That covers a syntax error,
	 * an expression that does not select nodes, such as {@code count(//x)}, a variable, which can never be given a value,
	 * and a namespace prefix, which can never match since bodies are parsed without namespaces.
	 */
	private static String validateXPath(final String xPath) {
		if (xPath == null) {
			throw new IllegalArgumentException(INVALID_XPATH.formatted(null, "no expression"));
		}
		if (hasVariableReference(xPath)) {
			throw new IllegalArgumentException(INVALID_XPATH.formatted(xPath, "variables cannot be used, as no value can be given for them"));
		}

		final var namespaceContext = new PrefixRecordingNamespaceContext();
		final var compiler = XPathFactory.newInstance().newXPath();
		compiler.setNamespaceContext(namespaceContext);
		try {
			compiler.compile(xPath).evaluate(createDocumentBuilder(createDocumentBuilderFactory()).newDocument(), XPathConstants.NODESET);
			return xPath;
		} catch (final XPathExpressionException e) {
			if (namespaceContext.requestedPrefix() != null) {
				throw new IllegalArgumentException(INVALID_XPATH.formatted(xPath, ("namespace prefix '%s' cannot be used, as bodies are parsed without namespaces. "
					+ "Match on the local name instead, such as //*[local-name()='name']").formatted(namespaceContext.requestedPrefix())), e);
			}
			throw new IllegalArgumentException(INVALID_XPATH.formatted(xPath, e.getMessage()), e);
		}
	}

	/**
	 * In XPath 1.0 a {@code $} outside a string literal always starts a variable reference.
	 */
	private static boolean hasVariableReference(final String xPath) {
		char openQuote = 0;
		for (final char character : xPath.toCharArray()) {
			if (openQuote != 0) {
				if (character == openQuote) {
					openQuote = 0;
				}
			} else if (character == '\'' || character == '"') {
				openQuote = character;
			} else if (character == '$') {
				return true;
			}
		}
		return false;
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

	private static BodyFilter xPathFilter(final List<XPathMask> masks) {
		final var maskers = ThreadLocal.withInitial(() -> new XmlMasker(masks));
		final var failureLogged = new AtomicBoolean();

		return (contentType, body) -> {
			if (anyNull(contentType, body) || body.isBlank() || !isXml(contentType)) {
				return body;
			}

			// Never fall back to the unfiltered body: it holds exactly the content these filters exist to mask
			try {
				return maskers.get().mask(body, charset(contentType));
			} catch (final IOException | SAXException e) {
				LOGGER.debug("Could not apply xpath filters to a body that is not valid XML ({})", e.getMessage());
				return INVALID_XML_REPLACEMENT;
			} catch (final XPathExpressionException | TransformerException | RuntimeException e) {
				logXPathFailure(failureLogged, e);
				return FAILED_XML_REPLACEMENT;
			}
		};
	}

	/**
	 * A failure that is not the body's fault, such as in the XML platform, would hit every XML body, so it is worth a
	 * warning, though only once per filter.
	 */
	private static void logXPathFailure(final AtomicBoolean failureLogged, final Exception e) {
		if (failureLogged.compareAndSet(false, true)) {
			LOGGER.warn("Could not apply xpath filters, so XML bodies are logged without their content", e);
			return;
		}
		LOGGER.debug("Could not apply xpath filters ({})", e.getMessage());
	}

	/**
	 * The body is already decoded, so its charset only names the encoding in the XML declaration of the masked body. A
	 * charset Java does not know falls back to UTF-8 rather than costing the body.
	 */
	private static Charset charset(final String contentType) {
		try {
			final var parsed = ContentType.parse(contentType);
			if (parsed == null || parsed.getCharset() == null) {
				return UTF_8;
			}
			return parsed.getCharset();
		} catch (final RuntimeException _) {
			return UTF_8;
		}
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

	private record JsonPathMask(JsonPath path, String replacement) {}

	private record XPathMask(String expression, String replacement) {}

	private record CompiledXPathMask(XPathExpression expression, String replacement) {}

	/**
	 * What one thread needs to mask XML bodies. None of these JAXP objects is thread-safe, and looking up their factories
	 * for every body is slow, so each thread keeps its own.
	 */
	private static final class XmlMasker {

		private final DocumentBuilder documentBuilder = createDocumentBuilder(createDocumentBuilderFactory());
		private final Transformer transformer = createTransformer(createTransformerFactory());
		private final List<CompiledXPathMask> masks = new ArrayList<>();

		private XmlMasker(final List<XPathMask> masks) {
			final XPath compiler = XPathFactory.newInstance().newXPath();
			for (final var mask : masks) {
				this.masks.add(new CompiledXPathMask(compile(compiler, mask.expression()), mask.replacement()));
			}
		}

		private static XPathExpression compile(final XPath compiler, final String expression) {
			try {
				return compiler.compile(expression);
			} catch (final XPathExpressionException e) {
				throw new InvalidConfigurationException(e);
			}
		}

		private String mask(final String body, final Charset charset) throws IOException, SAXException, XPathExpressionException, TransformerException {
			documentBuilder.reset();
			documentBuilder.setErrorHandler(THROWING_ERROR_HANDLER);
			// Parsed from the decoded body, so that an encoding named in its XML declaration cannot disagree with it
			final Document document = documentBuilder.parse(new InputSource(new StringReader(body)));

			for (final var mask : masks) {
				final NodeList matches = (NodeList) mask.expression().evaluate(document, XPathConstants.NODESET);
				for (int i = 0; i < matches.getLength(); i++) {
					matches.item(i).setTextContent(mask.replacement());
				}
			}

			transformer.reset();
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
	}

	/**
	 * Resolves no prefix, and remembers the prefix an expression asked for.
	 */
	static final class PrefixRecordingNamespaceContext implements NamespaceContext {

		private String requestedPrefix;

		String requestedPrefix() {
			return requestedPrefix;
		}

		@Override
		public String getNamespaceURI(final String prefix) {
			requestedPrefix = prefix;
			return null;
		}

		@Override
		public String getPrefix(final String namespaceURI) {
			return null;
		}

		@Override
		public Iterator<String> getPrefixes(final String namespaceURI) {
			return Collections.emptyIterator();
		}
	}
}
