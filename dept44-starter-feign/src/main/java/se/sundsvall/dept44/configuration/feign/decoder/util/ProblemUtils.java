package se.sundsvall.dept44.configuration.feign.decoder.util;

import java.util.List;
import java.util.Optional;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

import static java.util.stream.Collectors.joining;

public final class ProblemUtils {

	private ProblemUtils() {}

	public static Problem toProblem(final ConstraintViolationProblem constraintViolationProblem) {
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

	private static String toViolationsString(final List<Violation> violations) {
		return violations.stream()
			.map(violation -> "%s: %s".formatted(violation.field(), violation.message()))
			.collect(joining(", "));
	}
}
