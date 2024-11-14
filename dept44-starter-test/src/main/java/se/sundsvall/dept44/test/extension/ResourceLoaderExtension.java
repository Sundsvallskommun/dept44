package se.sundsvall.dept44.test.extension;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.util.AnnotationUtils;
import se.sundsvall.dept44.test.annotation.resource.Load;

/**
 * Extension to facilitate resource loading in tests - used in conjunction with the
 * {@link Load} annotation on test method parameters. Examples:<br />
 * <br />
 *
 * <p>
 * {@code @Load("myfile.txt") String s}
 * </p>
 * <p>
 * {@code @Load(value = "myfile.txt" as = Load.ResourceType.STRING) String s}
 * </p>
 * <p>
 * {@code @Load(value = "somefile.json", as = Load.ResourceType.JSON) MyClass mc)}
 * </p>
 * <p>
 * {@code @Load(value = "anotherfile.xml", as = Load.ResourceType.XML) AnotherClass ac)}
 * </p>
 */
public class ResourceLoaderExtension implements ParameterResolver {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
	private static final ObjectMapper XML_MAPPER = new XmlMapper().registerModule(new JavaTimeModule());

	@Override
	public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
		throws ParameterResolutionException {
		var parameter = parameterContext.getParameter();

		return AnnotationUtils.isAnnotated(parameter, Load.class);
	}

	@Override
	public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
		throws ParameterResolutionException {
		var parameter = parameterContext.getParameter();
		var parameterClass = parameter.getType();

		return findAnnotation(parameter, Load.class)
			.map(annotation -> {
				var path = annotation.value();

				return switch (annotation.as()) {
					case JSON -> fromJson(fromClasspath(path), parameterClass);
					case XML -> fromXml(fromClasspath(path), parameterClass);
					case STRING -> Optional.of(parameterClass)
						.filter(String.class::isAssignableFrom)
						.map(ignored -> fromClasspath(path))
						.orElseThrow(() -> new IllegalStateException("Unable to load resource into parameter of type " + parameterClass.getName()));
				};
			})
			.orElseThrow(() -> new IllegalStateException("No @Load annotation on parameter " + parameter.getName()));
	}

	private <T> T fromXml(final String value, final Class<T> clazz) {
		try {
			return XML_MAPPER.readValue(value, clazz);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to deserialize parameter from XML", e);
		}
	}

	private <T> T fromJson(final String value, final Class<T> clazz) {
		try {
			return OBJECT_MAPPER.readValue(value, clazz);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to deserialize parameter from JSON", e);
		}
	}

	private String fromClasspath(final String path) {
		try (var is = getClasspathResourceAsStream(path.startsWith("/") ? path.substring(1) : path)) {
			return convertStreamToString(is);
		} catch (Exception e) {
			throw new IllegalArgumentException("Cannot load classpath resource: '" + path + "'", e);
		}
	}

	private InputStream getClasspathResourceAsStream(final String resourceName) {
		var classLoader = Thread.currentThread().getContextClassLoader();

		return Optional.ofNullable(classLoader.getResourceAsStream(resourceName))
			.orElseThrow(() -> new IllegalArgumentException("Resource not found with name: " + resourceName));
	}

	private String convertStreamToString(final InputStream is) {
		return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
			.lines()
			.collect(Collectors.joining("\n"));
	}
}
