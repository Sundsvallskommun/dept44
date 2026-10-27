package se.sundsvall.dept44.benchmarks.payloadlogging;

import java.util.UUID;

/**
 * JSON bodies for the benchmarks, generated the same way on every run so that runs can be compared.
 * <p>
 * Values are strings, numbers and booleans in a fixed mix, since the allowlist masks every type. Field names are
 * {@code field0}, {@code field1} and so on, which the paths of {@link AllowlistPaths} name.
 */
public final class Payloads {

	/** The number of fields in a row of {@link #page(int)} and {@link #nested(int)}. */
	static final int FIELDS_PER_ROW = 20;

	/** How deep the objects of {@link #nested(int)} are nested. */
	static final int DEPTH = 10;

	private static final String FIELD = "field";

	private Payloads() {}

	/**
	 * A page of rows, like a page of errands: {@code {"content":[{...},...],"totalElements":n}}.
	 *
	 * @param  size the length to reach, in characters. The body ends with the row that reaches it.
	 * @return      the body
	 */
	public static String page(final int size) {
		final var json = new StringBuilder(size + 1024).append("{\"content\":[");
		var rows = 0;
		while (json.length() < size) {
			if (rows > 0) {
				json.append(',');
			}
			appendRow(json, rows++, FIELDS_PER_ROW);
		}
		return json.append("],\"totalElements\":").append(rows).append('}').toString();
	}

	/**
	 * One object with as many fields as it takes to reach the size, each with a name of its own.
	 *
	 * @param  size the length to reach, in characters
	 * @return      the body
	 */
	public static String flat(final int size) {
		final var json = new StringBuilder(size + 64).append('{');
		var field = 0;
		while (json.length() < size) {
			if (field > 0) {
				json.append(',');
			}
			appendField(json, FIELD + field, 0, field);
			field++;
		}
		return json.append('}').toString();
	}

	/**
	 * An array of objects nested {@value #DEPTH} levels deep, each level with a few fields and a {@code child}.
	 *
	 * @param  size the length to reach, in characters
	 * @return      the body
	 */
	public static String nested(final int size) {
		final var json = new StringBuilder(size + 4096).append('[');
		var chain = 0;
		while (json.length() < size) {
			if (chain > 0) {
				json.append(',');
			}
			appendLevel(json, chain++, 0);
		}
		return json.append(']').toString();
	}

	/**
	 * A page of errands shaped like those of api-service-support-management, with made-up stakeholders and texts.
	 *
	 * @param  count the number of errands on the page
	 * @return       the body
	 */
	public static String errandPage(final int count) {
		final var json = new StringBuilder(count * 2048).append("{\"content\":[");
		for (var i = 0; i < count; i++) {
			if (i > 0) {
				json.append(',');
			}
			appendErrand(json, i);
		}
		return json.append("],\"totalElements\":").append(count).append(",\"totalPages\":1,\"number\":0,\"size\":").append(count).append('}').toString();
	}

	private static void appendRow(final StringBuilder json, final int row, final int fields) {
		json.append('{');
		for (var field = 0; field < fields; field++) {
			if (field > 0) {
				json.append(',');
			}
			appendField(json, FIELD + field, row, field);
		}
		json.append('}');
	}

	private static void appendLevel(final StringBuilder json, final int chain, final int level) {
		json.append('{');
		for (var field = 0; field < 4; field++) {
			appendField(json, FIELD + field, chain, level * 4 + field);
			json.append(',');
		}
		if (level + 1 < DEPTH) {
			json.append("\"child\":");
			appendLevel(json, chain, level + 1);
		} else {
			json.append("\"child\":null");
		}
		json.append('}');
	}

	private static void appendField(final StringBuilder json, final String name, final int row, final int field) {
		json.append('"').append(name).append("\":");
		if (field % 5 == 4) {
			json.append(row * 31 + field);
		} else if (field % 7 == 6) {
			json.append(row % 2 == 0);
		} else {
			json.append("\"value ").append(row).append('-').append(field).append(" of some length\"");
		}
	}

	private static void appendErrand(final StringBuilder json, final int i) {
		json.append('{')
			.append("\"id\":\"").append(uuid(i, 1)).append("\",")
			.append("\"errandNumber\":\"KC-2026").append(String.format("%04d", i)).append("\",")
			.append("\"title\":\"Fråga om ärende ").append(i).append("\",")
			.append("\"priority\":\"MEDIUM\",")
			.append("\"stakeholders\":[")
			.append("{\"externalId\":\"").append(uuid(i, 2)).append("\",\"externalIdType\":\"PRIVATE\",\"role\":\"PRIMARY\",")
			.append("\"firstName\":\"Förnamn").append(i).append("\",\"lastName\":\"Efternamn").append(i).append("\",")
			.append("\"address\":\"Storgatan ").append(i % 90 + 1).append("\",\"careOf\":null,\"zipCode\":\"85230\",\"city\":\"Sundsvall\",")
			.append("\"country\":\"Sverige\",\"organizationName\":null,")
			.append("\"contactChannels\":[{\"type\":\"Email\",\"value\":\"person").append(i).append("@example.com\"},")
			.append("{\"type\":\"Phone\",\"value\":\"070-123 45 ").append(String.format("%02d", i % 100)).append("\"}],")
			.append("\"parameters\":[{\"key\":\"relation\",\"displayName\":\"Relation\",\"values\":[\"Sökande\"]}]}],")
			.append("\"externalTags\":[{\"key\":\"caseId\",\"value\":\"").append(uuid(i, 3)).append("\"}],")
			.append("\"parameters\":[{\"key\":\"reason\",\"displayName\":\"Anledning\",\"group\":\"Kontakt\",\"values\":[\"Felanmälan\",\"Uppföljning\"]}],")
			.append("\"classification\":{\"category\":\"SUPPORT_CASE\",\"type\":\"OTHER_ISSUES\"},")
			.append("\"status\":\"ONGOING\",\"resolution\":null,")
			.append("\"description\":\"Personen hörde av sig om ett ärende som gäller ").append(i)
			.append(". Hen vill bli kontaktad på telefon under eftermiddagen och har bifogat ett underlag som beskriver vad som hänt.\",")
			.append("\"channel\":\"PHONE\",\"reporterUserId\":\"user").append(i % 7).append("\",\"assignedUserId\":\"user").append(i % 5).append("\",")
			.append("\"assignedGroupId\":\"group").append(i % 3).append("\",\"escalationEmail\":null,")
			.append("\"contactReason\":\"Felanmälan\",\"contactReasonDescription\":\"Gatubelysning ur funktion\",")
			.append("\"businessRelated\":false,")
			.append("\"labels\":[{\"id\":\"").append(uuid(i, 4)).append("\",\"classification\":\"CATEGORY\",\"displayName\":\"Kategori\",")
			.append("\"resourcePath\":\"CATEGORY/SUPPORT\",\"resourceName\":\"SUPPORT\"}],")
			.append("\"activePhaseId\":null,")
			.append("\"created\":\"2026-10-01T09:00:00+02:00\",\"modified\":\"2026-10-02T10:15:00+02:00\",\"touched\":\"2026-10-02T10:15:00+02:00\",")
			.append("\"version\":").append(i % 4)
			.append('}');
	}

	private static String uuid(final int i, final int kind) {
		return new UUID(kind, i).toString();
	}
}
