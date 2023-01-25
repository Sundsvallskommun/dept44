package se.sundsvall.dept44.test.annotation.resource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the path of a classpath resource.
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
