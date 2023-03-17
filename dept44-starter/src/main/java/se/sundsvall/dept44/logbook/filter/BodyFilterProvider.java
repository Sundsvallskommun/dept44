package se.sundsvall.dept44.logbook.filter;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.ObjectUtils.anyNull;
import static org.apache.http.entity.ContentType.APPLICATION_XHTML_XML;
import static org.apache.http.entity.ContentType.APPLICATION_XML;
import static org.apache.http.entity.ContentType.TEXT_XML;
import static org.zalando.logbook.json.JsonBodyFilters.replaceJsonStringProperty;
import static se.sundsvall.dept44.logbook.filter.json.JsonPathBodyFilters.jsonPath;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.zalando.logbook.BodyFilter;

public class BodyFilterProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(BodyFilterProvider.class);

	private BodyFilterProvider() {}

	public static BodyFilter passwordFilter() {
		return replaceJsonStringProperty(p -> p.toLowerCase().contains("password"), "*********");
	}

	public static List<BodyFilter> buildJsonPathFilters(Map<String, String> jsonPathFilters) {
		return jsonPathFilters.entrySet()
			.stream()
			.map(filter -> jsonPath(filter.getKey()).replace(filter.getValue()))
			.toList();
	}

	public static List<BodyFilter> buildXPathFilters(Map<String, String> xPathFilters) {
		TransformerFactory transformerFactory = createTransformerFactory();

		return xPathFilters.entrySet()
			.stream()
			.map(filter -> xPath(filter.getKey(), filter.getValue(), createTransformer(transformerFactory)))
			.toList();
	}

	static DocumentBuilder createDocumentBuilder(DocumentBuilderFactory factory) {
		try {
			return factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new InvalidConfigurationException(e);
		}
	}

	static Transformer createTransformer(TransformerFactory factory) {
		try {
			return factory.newTransformer();
		} catch (TransformerConfigurationException e) {
			throw new InvalidConfigurationException(e);
		}
	}

	static DocumentBuilderFactory createDocumentBuilderFactory() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			return factory;
		} catch (ParserConfigurationException e) {
			throw new InvalidConfigurationException(e);
		}

	}

	static TransformerFactory createTransformerFactory() {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			return factory;
		} catch (IllegalArgumentException e) {
			throw new InvalidConfigurationException(e);
		}
	}

	static BodyFilter xPath(String xPath, String replacement, Transformer transformer) {
		final List<String> xmlContentTypes = List.of(APPLICATION_XHTML_XML.getMimeType(), APPLICATION_XML.getMimeType(), TEXT_XML.getMimeType());

		return (contentTypeString, body) -> {
			if (anyNull(contentTypeString, body)) {
				return body;
			}

			try {
				ContentType contentType = ContentType.parse(contentTypeString);
				if (xmlContentTypes.contains(contentType.getMimeType())) {
					// Evaluate what charSet to use
					final var charSet = evaluateCharset(contentType);

					// Create document and xpath
					var builder = createDocumentBuilder(createDocumentBuilderFactory());
					Document document = builder.parse(new ByteArrayInputStream(body.getBytes(charSet)));

					// Evaluate xpath matches and replace content
					XPath path = XPathFactory.newInstance().newXPath();
					NodeList matches = (NodeList) path.evaluate(xPath, document, XPathConstants.NODESET);
					for (int i = 0; i < matches.getLength(); i++) {
						matches.item(i).setTextContent(replacement);
					}

					// Setup transformer to use incoming encoding and to set standalone attribute
					transformer.setOutputProperty(OutputKeys.ENCODING, charSet.name());
					transformer.setOutputProperty(OutputKeys.STANDALONE, document.getXmlStandalone() ? "yes" : "no");

					// Return filtered body as string
					StringWriter writer = new StringWriter();
					transformer.transform(new DOMSource(document), new StreamResult(writer));
					return writer.toString();
				}

				return body;

			} catch (Exception e) {
				LOGGER.warn("An exception occured while filtering content from incoming xml request body ({}).", e.getMessage());
				return body;
			}
		};
	}

	private static Charset evaluateCharset(ContentType contentType) {
		// If incoming contentType hasn't defined any charset then UTF-8 is returned, otherwise incoming charset is returned
		return isNull(contentType.getCharset()) ? StandardCharsets.UTF_8 : contentType.getCharset();
	}
}
