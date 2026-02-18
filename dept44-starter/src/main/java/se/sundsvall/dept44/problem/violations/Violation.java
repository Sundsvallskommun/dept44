package se.sundsvall.dept44.problem.violations;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * Represents a constraint violation with a field and message.
 */
public record Violation(
	@JsonProperty("field") String field,
	@JsonProperty("message") String message)
	implements
	Serializable {
}
