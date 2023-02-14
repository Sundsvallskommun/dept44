package se.sundsvall.dept44.util;

import static java.util.Objects.isNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.Optional;

public final class DateUtils {

	private DateUtils() {}

	/**
	 * This method converts the provided OffsetDateTime or LocalDateTime object into a new OffsetDateTime with the local
	 * offset.
	 *
	 * Please note that it only has support for OffsetDateTime and LocalDateTime and no other Temporal-types.
	 *
	 * E.g. if local timezone is "Europe/Stockholm", this would be the result for the provided dates:
	 *
	 * <pre>
	 * Zulu time zone into time with local offset +1h.
	 * 2021-11-10T09:23:42.500Z -> 2021-11-10T10:23:42.500+01:00
	 *
	 * Zulu time zone into time with local offset +2h, with DST.
	 * 2021-06-10T09:23:42.500Z -> 2021-06-10T11:23:42.500+02:00
	 *
	 * Time with offset +4h into time with local offset +1h.
	 * 2021-11-10T12:23:42.500+04:00 -> 2021-11-10T09:23:42.500+01:00
	 *
	 * Time with no offset (e.g. LocalDateTime), with DST.
	 * 2021-06-10T09:23:42 -> 2021-06-10T11:23:42+02:00
	 *
	 * Time with no offset (e.g. LocalDateTime).
	 * 2021-11-10T09:23:42 -> 2021-11-10T11:23:42+01:00
	 * </pre>
	 *
	 * @param temporal the input date
	 * @return a new offsetDateTime with the local offset. If the temporal is null, null is returned.
	 * @throws IllegalArgumentException if a temporal type is not OffsetDateTime or LocalDateTime.
	 */
	public static OffsetDateTime toOffsetDateTimeWithLocalOffset(final Temporal temporal) {
		if (isNull(temporal)) {
			return null;
		}
		if (temporal instanceof final OffsetDateTime offsetDateTime) {
			return toOffsetDateTimeWithLocalOffset(offsetDateTime);
		}
		if (temporal instanceof final LocalDateTime localDateTime) {
			return toOffsetDateTimeWithLocalOffset(localDateTime);
		}
		throw new IllegalArgumentException("Method has no support for type " + temporal.getClass().getName());
	}

	/**
	 * Method for converting an OffsetDateTime to OffsetDateTime with local offset.
	 *
	 * @return offsetDateTime with local offset
	 */
	private static OffsetDateTime toOffsetDateTimeWithLocalOffset(final OffsetDateTime offsetDateTime) {
		return Optional.ofNullable(offsetDateTime)
			// Calculate local offset based on provided offsetDateTime, for this systems zoneId.
			.map(offsetDateTime1 -> offsetDateTime1.withOffsetSameInstant(ZoneId.systemDefault().getRules().getOffset(offsetDateTime1.toInstant())))
			.orElse(null);
	}

	/**
	 * Method for converting an LocalDateTime to OffsetDateTime with local offset.
	 *
	 * @return offsetDateTime with local offset
	 */
	private static OffsetDateTime toOffsetDateTimeWithLocalOffset(final LocalDateTime localDateTime) {
		return Optional.ofNullable(localDateTime)
			.map(localDateTime1 -> localDateTime1.atOffset(ZoneId.systemDefault().getRules().getOffset(localDateTime1)))
			.orElse(null);
	}
}
