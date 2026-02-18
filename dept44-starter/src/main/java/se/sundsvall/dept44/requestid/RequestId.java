package se.sundsvall.dept44.requestid;

import java.util.UUID;
import org.slf4j.MDC;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ZERO;

public final class RequestId {

	public static final String MDC_REQUEST_ID_KEY = "x-request-id";
	public static final String HEADER_NAME = "x-request-id";
	private static final ThreadLocal<Integer> THREAD_LOCAL_COUNTER = new ThreadLocal<>();

	private RequestId() {}

	public static boolean init() {
		return init(null);
	}

	public static boolean init(final String id) {
		var created = false;
		var counter = THREAD_LOCAL_COUNTER.get();
		if (isNull(counter)) {
			counter = INTEGER_ZERO;
		}

		if (INTEGER_ZERO.equals(counter)) {
			var localId = id;
			if (nonNull(localId)) {
				localId = localId.trim();
				if (localId.isEmpty()) {
					localId = null;
				}
			}
			if (isNull(localId)) {
				localId = UUID.randomUUID().toString();
			}
			MDC.put(MDC_REQUEST_ID_KEY, localId);
			created = true;
		}

		counter++;
		THREAD_LOCAL_COUNTER.set(counter);

		return created;
	}

	public static boolean reset() {
		var cleared = false;
		var counter = THREAD_LOCAL_COUNTER.get();
		if (nonNull(counter)) {
			counter--;
			THREAD_LOCAL_COUNTER.set(counter);
			if (INTEGER_ZERO.equals(counter)) {
				MDC.remove(MDC_REQUEST_ID_KEY);
				THREAD_LOCAL_COUNTER.remove();
				cleared = true;
			}
		}
		return cleared;
	}

	public static String get() {
		return MDC.get(MDC_REQUEST_ID_KEY);
	}
}
