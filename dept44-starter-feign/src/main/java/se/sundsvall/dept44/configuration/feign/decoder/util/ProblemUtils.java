package se.sundsvall.dept44.configuration.feign.decoder.util;

import java.util.List;
import java.util.Optional;
import org.zalando.problem.Problem;
import org.zalando.problem.violations.ConstraintViolationProblem;
import org.zalando.problem.violations.Violation;

import static java.util.stream.Collectors.joining;

public final class ProblemUtils {

	private ProblemUtils() {}

	public static Problem toProblem(ConstraintViolationProblem constraintViolationProblem) {
		return Optional.ofNullable(constraintViolationProblem)
			.map(cvProblem -> Problem.builder()
				.withStatus(cvProblem.getStatus())
				.withTitle(cvProblem.getTitle())
				.withInstance(cvProblem.getInstance())
				.withType(cvProblem.getType())
				.withDetail(toViolationsString(cvProblem.getViolations()))
				.build())
			.orElse(null);
	}

	private static String toViolationsString(List<Violation> violations) {
		return violations.stream()
			.map(violation -> "%s: %s".formatted(violation.getField(), violation.getMessage()))
			.collect(joining(", "));
	}
}
