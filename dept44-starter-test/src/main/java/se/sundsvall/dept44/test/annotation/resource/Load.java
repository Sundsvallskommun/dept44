package se.sundsvall.dept44.test.annotation.resource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the path of a classpath resource.
 * <p>
 * The resource is read as UTF-8 text with its lines joined by {@code \n}: line endings are normalized to {@code \n}
 * and a final line break is dropped. That keeps a value such as a token or an id usable as it is, but a fixture whose
 * exact line endings matter (such as a CRLF file format) must be read directly instead.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Load {

	enum ResourceType {
		JSON,
		XML,
		STRING
	}

	String value();

	ResourceType as() default ResourceType.STRING;
}
