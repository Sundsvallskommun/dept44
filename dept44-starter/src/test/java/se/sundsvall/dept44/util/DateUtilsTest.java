package se.sundsvall.dept44.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ThaiBuddhistDate;
import java.time.temporal.Temporal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateUtilsTest {

	@Test
	void toOffsetDateTimeWithLocalOffset() {

		// Create Offset
		final var zoneOffsetWinter = OffsetDateTime.parse("2021-11-10T09:23:42.500Z").atZoneSameInstant(ZoneId.systemDefault()).getOffset();
		final var zoneOffsetSummer = OffsetDateTime.parse("2021-06-10T09:23:42.500Z").atZoneSameInstant(ZoneId.systemDefault()).getOffset();

		final var winterOffsetDateTimeWithoutZuluTimeZone = OffsetDateTime.parse("2021-11-10T09:23:42.500Z");
		final var summerOffsetDateTimeWithoutZuluTimeZone = OffsetDateTime.parse("2021-06-10T09:23:42.500Z");
		final var winterOffsetDateTimeWithPlusFour = OffsetDateTime.parse("2021-11-10T12:23:42.500+04:00");
		final var winterOffsetDateTimeWithMinusOne = OffsetDateTime.parse("2021-11-10T12:23:42.500-01:00");
		final var winterLocalDateTimeNoOffset = LocalDateTime.parse("2021-11-10T12:23:42");
		final var summerLocalDateTimeNoOffset = LocalDateTime.parse("2021-06-10T12:23:42");

		// Zulu time zone into time with local offset +1h in Sweden.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(winterOffsetDateTimeWithoutZuluTimeZone))
			.hasToString(winterOffsetDateTimeWithoutZuluTimeZone.atZoneSameInstant(zoneOffsetWinter).toString());

		// Zulu time zone into time with local offset +2h, with DST in Sweden.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(summerOffsetDateTimeWithoutZuluTimeZone))
			.hasToString(summerOffsetDateTimeWithoutZuluTimeZone.atZoneSameInstant(zoneOffsetSummer).toString());

		// Time with offset +4h into time with local offset +1h in Sweden.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(winterOffsetDateTimeWithPlusFour))
			.hasToString(winterOffsetDateTimeWithPlusFour.atZoneSameInstant(zoneOffsetWinter).toString());

		// Time with offset -1h into time with local offset +1h in Sweden.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(winterOffsetDateTimeWithMinusOne))
			.hasToString(winterOffsetDateTimeWithMinusOne.atZoneSameInstant(zoneOffsetWinter).toString());

		// Time with no offset (e.q. localDateTime) into time with local offset +1h in Sweden.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(winterLocalDateTimeNoOffset))
			.hasToString(winterLocalDateTimeNoOffset.atOffset(zoneOffsetWinter).toString());

		// Time with no offset (e.q. localDateTime) into time with local offset +2h in Sweden.
		assertThat(DateUtils.toOffsetDateTimeWithLocalOffset(summerLocalDateTimeNoOffset))
			.hasToString(summerLocalDateTimeNoOffset.atOffset(zoneOffsetSummer).toString());

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
