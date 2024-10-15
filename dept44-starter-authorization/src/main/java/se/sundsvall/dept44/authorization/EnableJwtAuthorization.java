package se.sundsvall.dept44.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import se.sundsvall.dept44.authorization.configuration.JwtAuthorizationProperties;
import se.sundsvall.dept44.authorization.configuration.PrePostMethodSecurityConfiguration;
import se.sundsvall.dept44.authorization.configuration.UnauthorizedExceptionHandlerConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({
	PrePostMethodSecurityConfiguration.class, UnauthorizedExceptionHandlerConfiguration.class, JwtAuthorizationProperties.class
})
public @interface EnableJwtAuthorization {
}
