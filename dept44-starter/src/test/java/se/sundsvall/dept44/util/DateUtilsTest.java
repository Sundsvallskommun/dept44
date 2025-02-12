package se.sundsvall.dept44.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.chrono.ThaiBuddhistDate;
import java.time.temporal.Temporal;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DateUtilsTest {

	private static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone("Europe/Stockholm");

	@BeforeAll
	static void beforeAll() {
		TimeZone.setDefault(DEFAULT_TIMEZONE);
	}

	@Test
	void toOffsetDateTimeWithLocalOffset() {
		// Zulu time zone into time with local offset +1h.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(OffsetDateTime.parse("2021-11-10T09:23:42.500Z"))).hasToString("2021-11-10T10:23:42.500+01:00");

		// Zulu time zone into time with local offset +2h, with DST.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(OffsetDateTime.parse("2021-06-10T09:23:42.500Z"))).hasToString("2021-06-10T11:23:42.500+02:00");

		// Time with offset +4h into time with local offset +1h.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(OffsetDateTime.parse("2021-11-10T12:23:42.500+04:00"))).hasToString("2021-11-10T09:23:42.500+01:00");

		// Time with offset -1h into time with local offset +1h.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(OffsetDateTime.parse("2021-11-10T12:23:42.500-01:00"))).hasToString("2021-11-10T14:23:42.500+01:00");

		// Time with no offset (e.q. localDateTime) into time with local offset +1h.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(LocalDateTime.parse("2021-11-10T12:23:42"))).hasToString("2021-11-10T12:23:42+01:00");

		// Time with no offset (e.q. localDateTime) into time with local offset +2h.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(LocalDateTime.parse("2021-06-10T12:23:42"))).hasToString("2021-06-10T12:23:42+02:00");

		// Null input renders null output.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(null)).isNull();
	}

	@ParameterizedTest
	@MethodSource("toOffsetDateTimeWithLocalOffsetThrowsExceptionWhenIllegalArgumentIsProvidedArguments")
	void toOffsetDateTimeWithLocalOffsetThrowsExceptionWhenIllegalArgumentIsProvided(Temporal nonSupportedTemporal, String errorMessage) {
		final var exception = assertThrows(IllegalArgumentException.class, () -> DateUtils.toOffsetDateTimeWithLocalOffset(nonSupportedTemporal));
		assertThat(exception.getMessage()).isEqualTo(errorMessage);
	}

	private static Stream<Arguments> toOffsetDateTimeWithLocalOffsetThrowsExceptionWhenIllegalArgumentIsProvidedArguments() {
		return Stream.of(
			Arguments.of(ZonedDateTime.now(), "Method has no support for type java.time.ZonedDateTime"),
			Arguments.of(ThaiBuddhistDate.now(), "Method has no support for type java.time.chrono.ThaiBuddhistDate"),
			Arguments.of(OffsetTime.now(), "Method has no support for type java.time.OffsetTime"),
			Arguments.of(LocalDate.now(), "Method has no support for type java.time.LocalDate"),
			Arguments.of(LocalTime.now(), "Method has no support for type java.time.LocalTime"));
	}
}
