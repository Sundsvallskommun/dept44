package se.sundsvall.dept44.problem;

import java.net.URI;

/**
 * Default implementation of Problem used by the builder. This is an internal class - use {@link Problem#builder()} to
 * create instances.
 */
class DefaultProblem {

	private DefaultProblem() {
		// Utility class for builder
	}

	/**
	 * Builder implementation for creating ThrowableProblem instances.
	 */
	static class Builder implements Problem.Builder {

		private URI type = Problem.DEFAULT_TYPE;
		private String title;
		private StatusType status;
		private String detail;
		private URI instance;
		private ThrowableProblem cause;

		@Override
		public Problem.Builder withType(final URI type) {
			this.type = type;
			return this;
		}

		@Override
		public Problem.Builder withTitle(final String title) {
			this.title = title;
			return this;
		}

		@Override
		public Problem.Builder withStatus(final StatusType status) {
			this.status = status;
			return this;
		}

		@Override
		public Problem.Builder withDetail(final String detail) {
			this.detail = detail;
			return this;
		}

		@Override
		public Problem.Builder withInstance(final URI instance) {
			this.instance = instance;
			return this;
		}

		@Override
		public Problem.Builder withCause(final ThrowableProblem cause) {
			this.cause = cause;
			return this;
		}

		@Override
		public ThrowableProblem build() {
			return new ThrowableProblem(type, title, status, detail, instance, cause);
		}
	}
}
