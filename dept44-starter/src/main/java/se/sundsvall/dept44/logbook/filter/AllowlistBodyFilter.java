package se.sundsvall.dept44.logbook.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.BodyFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.sundsvall.dept44.logbook.BodyCapturePolicy.isOmittedNote;
import static se.sundsvall.dept44.logbook.BodyCapturePolicy.jsonNote;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.isJson;
import static tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS;

/**
 * Logs only the values of the fields that {@code logbook.body-filters.allowlist.paths} names. Every other value of a
 * JSON body, whatever its type, is replaced by {@value #PLACEHOLDER}. The names of the fields are kept, so that a log
 * entry still shows the shape of what was sent. A body that is not JSON is logged without its body.
 * <p>
 * The note dept44 logs in place of a body larger than it captures is kept whatever the content type, so that the log
 * tells a body that was left out from one that was empty. It holds nothing of the body.
 * <p>
 * The names are kept also where they are data rather than names, such as the keys of a map keyed by personal number or
 * e-mail address. JSON does not tell such a map from an object, so this filter does not mask them.
 * <p>
 * An allowlist rather than a list of what to mask: a field added to an API is withheld until someone adds it to the
 * list, instead of logged until someone remembers to mask it.
 * <p>
 * The body is parsed once and walked once, whatever the number of paths. A body longer than the maximum size is not
 * parsed at all, since parsing it builds a tree several times its size on the thread that handles the request.
 */
public final class AllowlistBodyFilter implements BodyFilter {

	static final String PLACEHOLDER = "[masked]";

	private static final Logger LOGGER = LoggerFactory.getLogger(AllowlistBodyFilter.class);

	/**
	 * Reads decimals as {@link java.math.BigDecimal}, so that a kept number is logged as it was sent. Read as a double,
	 * {@code 100.50} would be logged as {@code 100.5} and {@code 1e400} as {@code "Infinity"}.
	 */
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().enable(USE_BIG_DECIMAL_FOR_FLOATS).build();

	private static final String INVALID_JSON_NOTE = jsonNote("not valid JSON, so the allowlist could not be applied");
	private static final String FAILED_NOTE = jsonNote("the allowlist could not be applied");

	private final AllowlistRules rules;
	private final long maxSize;
	private final String tooLargeNote;
	private final AtomicBoolean failureLogged = new AtomicBoolean();

	/**
	 * @param  paths                    the fields whose values are logged, in the syntax {@link AllowlistRules}
	 *                                  describes
	 * @param  maxSize                  the longest body, in characters, that is parsed. A longer body is replaced by a
	 *                                  note. A negative value disables the limit.
	 * @throws IllegalArgumentException if a path is not within the supported syntax
	 */
	public AllowlistBodyFilter(final Collection<String> paths, final long maxSize) {
		this(AllowlistRules.of(paths), maxSize);
	}

	AllowlistBodyFilter(final AllowlistRules rules, final long maxSize) {
		this.rules = rules;
		this.maxSize = maxSize;
		this.tooLargeNote = jsonNote("larger than " + maxSize + " characters, so the allowlist could not be applied");
	}

	@Override
	public String filter(final String contentType, final String body) {
		if (isBlank(body) || isOmittedNote(body)) {
			return body;
		}
		if (contentType == null || !isJson(contentType)) {
			return "";
		}
		if (maxSize >= 0 && body.length() > maxSize) {
			return tooLargeNote;
		}

		try {
			final var root = JSON_MAPPER.readTree(body);
			if (!root.isContainer()) {
				// A body that is a single value has no field that could keep it
				return JSON_MAPPER.writeValueAsString(PLACEHOLDER);
			}
			mask(root, new ArrayList<>());
			return root.toString();
		} catch (final JacksonException e) {
			// The message of a parse error quotes the body, so only the kind of error is logged
			LOGGER.debug("Could not apply the allowlist to a body that is not valid JSON ({})", e.getClass().getSimpleName());
			return INVALID_JSON_NOTE;
		} catch (final RuntimeException e) {
			// A fault of the filter's own, not of the body. The body is still withheld, so that a fault cannot log what the
			// allowlist would have masked
			logFailure(e);
			return FAILED_NOTE;
		}
	}

	/**
	 * A fault of the filter's own may hit every body, so it is warned about loudly, though only once, so that it does not
	 * add a stack trace to every request.
	 */
	private void logFailure(final RuntimeException e) {
		if (failureLogged.compareAndSet(false, true)) {
			LOGGER.warn("Could not apply the allowlist to a body", e);
			return;
		}
		LOGGER.debug("Could not apply the allowlist to a body ({})", e.getClass().getSimpleName());
	}

	/**
	 * @param node the node to mask in place
	 * @param path the fields from the root of the body down to this node, used as a stack. An array is not a field of
	 *             its own: its elements are at the path of the array.
	 */
	private void mask(final JsonNode node, final List<String> path) {
		if (node instanceof final ObjectNode object) {
			maskProperties(object, path);
		} else if (node instanceof final ArrayNode array) {
			maskElements(array, path);
		}
	}

	private void maskProperties(final ObjectNode object, final List<String> path) {
		// Collected and replaced afterwards, so that the object is not written to while it is read
		final var masked = new ArrayList<String>();
		object.properties().forEach(property -> {
			path.add(property.getKey());
			if (property.getValue().isContainer()) {
				mask(property.getValue(), path);
			} else if (!rules.keeps(path)) {
				masked.add(property.getKey());
			}
			path.removeLast();
		});
		masked.forEach(name -> object.put(name, PLACEHOLDER));
	}

	private void maskElements(final ArrayNode array, final List<String> path) {
		for (var i = 0; i < array.size(); i++) {
			final var element = array.get(i);
			if (element.isContainer()) {
				mask(element, path);
			} else if (!rules.keeps(path)) {
				array.set(i, PLACEHOLDER);
			}
		}
	}
}
