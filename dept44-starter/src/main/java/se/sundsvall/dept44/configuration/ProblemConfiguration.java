package se.sundsvall.dept44.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import se.sundsvall.dept44.problem.ProblemExceptionHandler;

/**
 * Autoconfiguration for RFC 9457 Problem exception handling. Imports the global exception handler for validation and
 * problem exceptions.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ProblemExceptionHandler.class)
public class ProblemConfiguration {

}
