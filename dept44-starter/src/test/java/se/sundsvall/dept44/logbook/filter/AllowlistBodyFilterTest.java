package se.sundsvall.dept44.logbook.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AllowlistBodyFilterTest {

	private static final String JSON = "application/json";
	private static final int MAX_SIZE = 262144;

	private static AllowlistBodyFilter filter(final String... paths) {
		return new AllowlistBodyFilter(List.of(paths), MAX_SIZE);
	}

	@Test
	void keepsWhatIsListedAndMasksTheRest() {
		final var result = filter("$..id", "$..status").filter(JSON,
			"""
				{"id":"abc","status":"ONGOING","title":"Something broken","description":"Free text"}""");

		assertThat(result).isEqualTo(
			"""
				{"id":"abc","status":"ONGOING","title":"[masked]","description":"[masked]"}""");
	}

	@Test
	void keepsEveryPropertyNameAndMasksEveryValueThatIsNotListed() {
		final var result = filter("$..fileSize").filter(JSON,
			"""
				{"fileSize":274,"internal":false,"viewed":null,"legalId":199001011234,"fileName":"anna.pdf"}""");

		assertThat(result).isEqualTo(
			"""
				{"fileSize":274,"internal":"[masked]","viewed":"[masked]","legalId":"[masked]","fileName":"[masked]"}""");
	}

	@Test
	void masksNestedObjectsAndArraysWithoutLosingTheirShape() {
		final var result = filter("$..id", "$..type").filter(JSON,
			"""
				{"stakeholders":[{"id":"1","firstName":"Anna","contactChannels":[{"type":"Email","value":"a@b.c"}]}]}""");

		assertThat(result).isEqualTo(
			"""
				{"stakeholders":[{"id":"1","firstName":"[masked]","contactChannels":[{"type":"Email","value":"[masked]"}]}]}""");
	}

	@Test
	void judgesValuesInAnArrayByTheFieldTheArrayBelongsTo() {
		final var result = filter("$..relationIds").filter(JSON,
			"""
				{"recipients":["a@b.c","d@e.f"],"relationIds":["REL-1",2],"counts":[1,2]}""");

		assertThat(result).isEqualTo(
			"""
				{"recipients":["[masked]","[masked]"],"relationIds":["REL-1",2],"counts":["[masked]","[masked]"]}""");
	}

	@Test
	void keepsAFieldOnlyUnderTheParentItIsListedWith() {
		final var filter = filter("$..key", "$..metadata.value");

		assertThat(filter.filter(JSON, """
			{"metadata":[{"key":"Status","value":"STATUS-3"}]}"""))
			.isEqualTo("""
				{"metadata":[{"key":"Status","value":"STATUS-3"}]}""");

		assertThat(filter.filter(JSON, """
			{"operations":[{"op":"replace","path":"/title","value":"It is my birthday"}]}"""))
			.isEqualTo("""
				{"operations":[{"op":"[masked]","path":"[masked]","value":"[masked]"}]}""");
	}

	@Test
	void masksThePageRowsWithoutReplacingThePageItself() {
		final var result = filter("$..id", "$.totalElements").filter(JSON,
			"""
				{"content":[{"id":"1","content":"Bifogar en fil"}],"totalElements":1}""");

		assertThat(result).isEqualTo(
			"""
				{"content":[{"id":"1","content":"[masked]"}],"totalElements":1}""");
	}

	@Test
	void dropsABodyThatIsNotJson() {
		assertThat(filter().filter("text/plain", "Testing! :)")).isEmpty();
		assertThat(filter().filter("application/xml", "<a>b</a>")).isEmpty();
		assertThat(filter().filter("application/x-www-form-urlencoded", "name=Anna")).isEmpty();
		assertThat(filter().filter(null, "{\"a\":\"b\"}")).isEmpty();
	}

	@Test
	void masksAProblemResponseToo() {
		assertThat(filter("$..status").filter("application/problem+json",
			"""
				{"status":404,"title":"Not Found","detail":"No errand with id 1"}"""))
			.isEqualTo("""
				{"status":404,"title":"[masked]","detail":"[masked]"}""");
	}

	@Test
	void passesOnAnEmptyBody() {
		assertThat(filter().filter(JSON, null)).isNull();
		assertThat(filter().filter(JSON, "  ")).isEqualTo("  ");
	}

	@Test
	void withholdsABodyItCannotRead() {
		assertThat(filter().filter(JSON, "{not json at all"))
			.isEqualTo("{\"bodyOmitted\":\"not valid JSON, so the allowlist could not be applied\"}");
	}

	@Test
	void logsNothingOfABodyItCannotRead() {
		final var logger = (Logger) LoggerFactory.getLogger(AllowlistBodyFilter.class);
		final var appender = new ListAppender<ILoggingEvent>();
		appender.start();
		logger.addAppender(appender);
		logger.setLevel(Level.DEBUG);
		try {
			filter().filter(JSON, "{\"name\":Anna Andersson}");
		} finally {
			logger.detachAppender(appender);
			logger.setLevel(null);
		}

		assertThat(appender.list).singleElement()
			.satisfies(event -> assertThat(event.getFormattedMessage()).doesNotContain("Anna"));
	}

	@Test
	void withholdsABodyTooLargeToReadWithoutReadingIt() {
		final var attachment = "{\"base64EncodedString\":\"" + "A".repeat(300_000) + "\"}";

		final var result = filter().filter(JSON, attachment);

		assertThat(result)
			.isEqualTo("{\"bodyOmitted\":\"larger than 262144 characters, so the allowlist could not be applied\"}")
			.doesNotContain("AAAA");
	}

	/**
	 * A body that looks like JSON is embedded into the log entry as it is, so a replacement that is not JSON costs the
	 * entry its fields wherever the log server reads it as JSON.
	 */
	@Test
	void replacesABodyWithSomethingThatIsStillJson() {
		assertThat(filter().filter(JSON, "{not json at all")).satisfies(AllowlistBodyFilterTest::isJson);
		assertThat(new AllowlistBodyFilter(List.of(), 8).filter(JSON, "{\"a\":\"bbbbbbbbbbbbbbbb\"}")).satisfies(AllowlistBodyFilterTest::isJson);
	}

	@Test
	void readsABodyOfAnySizeWithANegativeMaxSize() {
		final var body = "{\"id\":\"1\",\"description\":\"" + "x".repeat(MAX_SIZE) + "\"}";

		assertThat(new AllowlistBodyFilter(List.of("$..id"), -1).filter(JSON, body))
			.isEqualTo("{\"id\":\"1\",\"description\":\"[masked]\"}");
	}

	@Test
	void keepsTheNoteForABodyLargerThanWasCaptured() {
		assertThat(filter().filter(JSON, "{\"bodyOmitted\":\"larger than 100 bytes\"}"))
			.isEqualTo("{\"bodyOmitted\":\"larger than 100 bytes\"}");
	}

	@Test
	void masksAFieldThatIsOnlyNamedLikeTheNote() {
		assertThat(filter().filter(JSON, "{\"bodyOmitted\":\"Anna Andersson\"}"))
			.isEqualTo("{\"bodyOmitted\":\"[masked]\"}");
		assertThat(filter().filter(JSON, "{\"bodyOmitted\":\"larger than 100 bytes\",\"name\":\"Anna\"}"))
			.isEqualTo("{\"bodyOmitted\":\"[masked]\",\"name\":\"[masked]\"}");
	}

	@Test
	void keepsNothingBelowAPathToAnObject() {
		assertThat(filter("$.address").filter(JSON, """
			{"address":{"street":"Storgatan 1","city":"Sundsvall"}}"""))
			.isEqualTo("""
				{"address":{"street":"[masked]","city":"[masked]"}}""");
	}

	@Test
	void keepsEveryValueBelowAFieldWithADescendantWildcard() {
		assertThat(filter("$.address..*").filter(JSON, """
			{"address":{"street":"Storgatan 1","city":{"name":"Sundsvall"}},"name":"Anna"}"""))
			.isEqualTo("""
				{"address":{"street":"Storgatan 1","city":{"name":"Sundsvall"}},"name":"[masked]"}""");
	}

	@Test
	void keepsTheValuesOfNestedArrays() {
		assertThat(filter("$.matrix[*][*]").filter(JSON, """
			{"matrix":[["a","b"]],"name":"Anna"}"""))
			.isEqualTo("""
				{"matrix":[["a","b"]],"name":"[masked]"}""");
	}

	@Test
	void keepsAFieldOnlyWhereAnAnchoredPathSaysSo() {
		// $[*].name is a metadata list; $.sender.name is the name of whoever sent an e-mail
		final var filter = filter("$[*].name", "$..id");

		assertThat(filter.filter(JSON, """
			[{"id":"1","name":"STATUS-1"},{"id":"2","name":"STATUS-2"}]"""))
			.isEqualTo("""
				[{"id":"1","name":"STATUS-1"},{"id":"2","name":"STATUS-2"}]""");

		assertThat(filter.filter(JSON, """
			{"sender":{"name":"Bender Bending Rodriguez","address":"bender@rodriguez.com"}}"""))
			.isEqualTo("""
				{"sender":{"name":"[masked]","address":"[masked]"}}""");
	}

	@Test
	void treatsArrayNestingAsTransparentSoTheElementMarkerIsOptional() {
		final var withMarker = filter("$.content[*].title").filter(JSON, """
			{"content":[{"title":"Kept","description":"Masked"}]}""");
		final var withoutMarker = filter("$.content.title").filter(JSON, """
			{"content":[{"title":"Kept","description":"Masked"}]}""");

		assertThat(withMarker).isEqualTo(withoutMarker).isEqualTo("""
			{"content":[{"title":"Kept","description":"[masked]"}]}""");
	}

	@Test
	void anchorsAPathAtTheRootAndKeepsADescendantAtAnyDepth() {
		assertThat(filter("$.title").filter(JSON, """
			{"title":"Kept","errand":{"title":"Masked"}}"""))
			.isEqualTo("""
				{"title":"Kept","errand":{"title":"[masked]"}}""");

		assertThat(filter("$..title").filter(JSON, """
			{"title":"Kept","errand":{"title":"Kept"}}"""))
			.isEqualTo("""
				{"title":"Kept","errand":{"title":"Kept"}}""");
	}

	@Test
	void keepsAnyOneFieldForAWildcardSegment() {
		assertThat(filter("$.*.id").filter(JSON, """
			{"a":{"id":"1","x":"m"},"b":{"id":"2"},"c":{"d":{"id":"3"}}}"""))
			.isEqualTo("""
				{"a":{"id":"1","x":"[masked]"},"b":{"id":"2"},"c":{"d":{"id":"[masked]"}}}""");
	}

	@Test
	void refusesAPathItCannotRead() {
		assertThatIllegalArgumentException().isThrownBy(() -> filter("$.a[?(@.b=='c')].d"))
			.withMessage("Invalid path '$.a[?(@.b=='c')].d' in logbook.body-filters.allowlist.paths: a predicate, a slice or an index cannot be used. "
				+ "Only '[*]' is supported, and it may be left out");
		assertThatIllegalArgumentException().isThrownBy(() -> filter("$.a[0].b"))
			.withMessageContaining("a predicate, a slice or an index cannot be used");
		assertThatIllegalArgumentException().isThrownBy(() -> filter("$.content[*]title"))
			.withMessageContaining("'[*]' must be followed by '.' or end the path");
		assertThatIllegalArgumentException().isThrownBy(() -> filter("$.a.length()"))
			.withMessageContaining("a function cannot be used");
		assertThatIllegalArgumentException().isThrownBy(() -> filter("$.."))
			.withMessageContaining("it names an empty field");
		assertThatIllegalArgumentException().isThrownBy(() -> filter("$a.b"))
			.withMessageContaining("it must continue with '.' or '..' after the root");
		assertThatIllegalArgumentException().isThrownBy(() -> filter("title"))
			.withMessageContaining("it is not a path. Write '$..title'");
		assertThatIllegalArgumentException().isThrownBy(() -> filter(" "))
			.withMessageContaining("a path cannot be blank");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"\"Anna Andersson, Storgatan 1\"", "199001011234", "true", "null"
	})
	void masksABodyThatIsASingleValue(final String body) {
		// No field names it, so nothing keeps it. Quoted, so that what replaces it is still a JSON document
		assertThat(filter("$..title").filter(JSON, body))
			.isEqualTo("\"[masked]\"")
			.satisfies(AllowlistBodyFilterTest::isJson);
	}

	@Test
	void withholdsABodyNestedTooDeeplyToRead() {
		final var deep = "{\"a\":".repeat(6000) + "\"x\"" + "}".repeat(6000);

		assertThat(filter().filter(JSON, deep))
			.isEqualTo("{\"bodyOmitted\":\"not valid JSON, so the allowlist could not be applied\"}");
	}

	private static void isJson(final String body) {
		assertThatNoException().isThrownBy(() -> JsonMapper.builder().build().readTree(body));
	}
}
