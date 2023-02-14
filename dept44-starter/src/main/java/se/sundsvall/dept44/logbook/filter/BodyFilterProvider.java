package se.sundsvall.dept44.logbook.filter;

import static java.util.Objects.isNull;
import static org.apache.http.entity.ContentType.APPLICATION_XHTML_XML;
import static org.apache.http.entity.ContentType.APPLICATION_XML;
import static org.apache.http.entity.ContentType.TEXT_XML;
import static org.zalando.logbook.json.JsonBodyFilters.replaceJsonStringProperty;
import static org.zalando.logbook.json.JsonPathBodyFilters.jsonPath;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.http.entity.ContentType;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.zalando.logbook.BodyFilter;

public class BodyFilterProvider {
	
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
			if (isNull(contentTypeString) || isNull(body)) return body;
			
			try {
				ContentType contentType = ContentType.parse(contentTypeString);
				if (xmlContentTypes.contains(contentType.getMimeType())) {
					// Create document and xpath
					var builder = createDocumentBuilder(createDocumentBuilderFactory());
					Document document = builder.parse(new ByteArrayInputStream(body.getBytes(contentType.getCharset())));
					XPath path = XPathFactory.newInstance().newXPath();
					
					// Evaluate xpath matches and replace content
					NodeList matches = (NodeList)path.evaluate(xPath, document, XPathConstants.NODESET);
				    for (int i=0; i<matches.getLength(); i++) {
				    	matches.item(i).setTextContent(replacement);
				    }

				    // Return filtered body as string
				    StringWriter writer = new StringWriter();
				    transformer.transform(new DOMSource(document), new StreamResult(writer));
				    return writer.toString();
				}

				return body;
				
			} catch (Exception e) {
				return body;
			}
		};
	}
}
