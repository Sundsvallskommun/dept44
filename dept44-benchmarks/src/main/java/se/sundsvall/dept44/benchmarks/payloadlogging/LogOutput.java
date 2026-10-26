package se.sundsvall.dept44.benchmarks.payloadlogging;

/**
 * Where the payload log goes, set up by the {@code logback-spring.xml} of dept44-starter-logback-logserver as in a
 * service. The appenders are synchronous, so their work is done on the thread that handles the request.
 */
public enum LogOutput {

	/** Discarded at the logger, so that only Logbook's own work is measured. */
	DISCARD(false, false),

	/** The console appender, without PII masking. */
	CONSOLE(false, false),

	/** The console appender, with {@code dept44.logback.pii-masking.enabled}. */
	CONSOLE_MASKED(false, true),

	/** The console appender and GELF to a log server on localhost, without PII masking. */
	CONSOLE_GELF(true, false),

	/**
	 * The console appender and GELF to a log server on localhost, with PII masking. Each line is masked three times: in
	 * the console pattern and in both GELF layouts.
	 */
	CONSOLE_GELF_MASKED(true, true);

	private final boolean gelf;
	private final boolean masked;

	LogOutput(final boolean gelf, final boolean masked) {
		this.gelf = gelf;
		this.masked = masked;
	}

	public boolean gelf() {
		return gelf;
	}

	public boolean masked() {
		return masked;
	}
}
