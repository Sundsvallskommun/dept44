package se.sundsvall.dept44.logbook.filter.json;

import static com.jayway.jsonpath.JsonPath.compile;
import static org.apiguardian.api.API.Status.EXPERIMENTAL;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.BodyFilter;

/**
 * Workaround copy of the original {@link org.zalando.logbook.json.JsonPathBodyFilters} and
 * {@link org.zalando.logbook.json.JsonMediaType#JSON} that uses {@code JacksonJsonProvider} instead
 * of {@code JacksonJsonNodeJsonProvider} in order to avoid NPE:s due to a bug in JsonPath (see
 * https://github.com/zalando/logbook/issues/1369 and https://github.com/json-path/JsonPath/issues/678).
 *
 * TODO: should be removed when the above is fixed.
 */
@API(status = EXPERIMENTAL)
public final class JsonPathBodyFilters {

    private static final Logger log = LoggerFactory.getLogger(JsonPathBodyFilters.class);

    private JsonPathBodyFilters() { }

    public static final class JsonPathBodyFilterBuilder {

        private final JsonPath path;

        private JsonPathBodyFilterBuilder(final JsonPath path) {
            this.path = path;
        }

        public BodyFilter delete() {
            return filter(context -> context.delete(path));
        }

        public BodyFilter replace(final String replacement) {
            return replace(new TextNode(replacement));
        }

        public BodyFilter replace(final Boolean replacement) {
            return replace(BooleanNode.valueOf(replacement));
        }

        public BodyFilter replace(final Double replacement) {
            return replace(new DoubleNode(replacement));
        }

        public BodyFilter replace(final JsonNode replacement) {
            return filter(context -> context.set(path, replacement));
        }

        public BodyFilter replace(final UnaryOperator<String> replacementFunction) {
            return filter(context -> context.map(path, (node, config) -> node == null ? NullNode.getInstance() : new TextNode(replacementFunction.apply(node.toString()))));
        }

        public BodyFilter replace(final Pattern pattern, final String replacement) {
            return filter(context -> context.map(path, (node, config) -> {
                if (node == null) {
                    return NullNode.getInstance();
                }

                final var matcher = pattern.matcher(node.toString());

                if (matcher.find()) {
                    return new TextNode(matcher.replaceAll(replacement));
                } else {
                    return node;
                }
            }));
        }
    }

    private static JsonPathBodyFilter filter(final Operation operation) {
        return new JsonPathBodyFilter(operation);
    }

    private static class JsonPathBodyFilter implements BodyFilter {

        private static final ParseContext CONTEXT = JsonPath.using(
                Configuration.builder()
                        .jsonProvider(new JacksonJsonProvider())
                        .mappingProvider(new JacksonMappingProvider())
                        .options(Option.SUPPRESS_EXCEPTIONS)
                        .options(Option.ALWAYS_RETURN_LIST)
                        .build());

        private final Operation operation;

        public JsonPathBodyFilter(final Operation operation) {
            this.operation = operation;
        }

        @Override
        public String filter(
                @Nullable final String contentType, final String body) {

            if (body.isEmpty() || !JSON.test(contentType)) {
                return body;
            }

            try {
                final var original = CONTEXT.parse(body);

                return operation.filter(original).jsonString();
            } catch (Exception e) {
                log.trace("The body could not be filtered, the following exception {} has been thrown", e.getClass());
                return body;
            }
        }

        @Nullable
        @Override
        public BodyFilter tryMerge(final BodyFilter next) {
            if (next instanceof JsonPathBodyFilter) {
                final var filter = (JsonPathBodyFilter) next;

                return new JsonPathBodyFilter(Operation.composite(operation, filter.operation));
            }
            return BodyFilter.super.tryMerge(next);
        }
    }

    @FunctionalInterface
    private interface Operation {

        DocumentContext filter(final DocumentContext context);

        static Operation composite(final Operation... operations) {
            return composite(Arrays.asList(operations));
        }

        static Operation composite(final Collection<Operation> operations) {
            return new CompositeOperation(operations);
        }
    }

    private static final class CompositeOperation implements Operation {

        private final Collection<Operation> operations;

        public CompositeOperation(final Collection<Operation> operations) {
            this.operations = operations;
        }

        @Override
        public DocumentContext filter(final DocumentContext context) {
            var result = context;

            for (final var operation : operations) {
                result = operation.filter(result);
            }

            return result;
        }
    }

    public static JsonPathBodyFilterBuilder jsonPath(final String jsonPath) {
        return new JsonPathBodyFilterBuilder(compile(jsonPath));
    }

    static final Predicate<String> JSON = (contentType) -> {
        if (contentType == null) {
            return false;
        } else if (contentType.startsWith("application/")) {
            int index = contentType.indexOf(59, 12);
            if (index != -1) {
                return index > 16 ? contentType.regionMatches(index - 5, "+json", 0, 5) : contentType.regionMatches(index - 4, "json", 0, 4);
            } else {
                return contentType.length() == 16 ? contentType.endsWith("json") : contentType.endsWith("+json");
            }
        } else {
            return false;
        }
    };
}
