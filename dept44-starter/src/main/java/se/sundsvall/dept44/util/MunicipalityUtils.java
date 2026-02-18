package se.sundsvall.dept44.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.StringUtils.upperCase;
import static org.springframework.util.ResourceUtils.getURL;

public final class MunicipalityUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(MunicipalityUtils.class);
	private static final String MUNICIPALITY_DEFINITION_PATH = "classpath:data/municipality.yml";
	private static final Map<String, Municipality> MUNICIPALITY_BY_ID_MAP = new HashMap<>();
	private static final Map<String, Municipality> MUNICIPALITY_BY_NAME_MAP = new HashMap<>();

	static {
		try {
			new YAMLMapper().readValue(getURL(MUNICIPALITY_DEFINITION_PATH), new TypeReference<List<Municipality>>() {}).stream()
				.forEach(municipality -> {
					MUNICIPALITY_BY_ID_MAP.put(municipality.id(), municipality);
					MUNICIPALITY_BY_NAME_MAP.put(upperCase(municipality.name()), municipality);
				});
		} catch (final Exception e) {
			LOGGER.error("Error during class initialization", e);
		}
	}

	private MunicipalityUtils() {}

	/**
	 * Find municipality by ID.
	 *
	 * @param  id the municipality ID.
	 * @return    The municipality object if found, otherwise null.
	 */
	public static Municipality findById(final String id) {
		return MUNICIPALITY_BY_ID_MAP.get(id);
	}

	/**
	 * Find municipality by name.
	 *
	 * @param  name the municipality name (case insensitive).
	 * @return      The municipality object if found, otherwise null.
	 */
	public static Municipality findByName(final String name) {
		return MUNICIPALITY_BY_NAME_MAP.get(upperCase(name));
	}

	/**
	 * Returns true if a municipality exists by the provided key.
	 *
	 * @param  id the municipality ID.
	 * @return    true if the municipality exists, otherwise false.
	 */
	public static boolean existsById(final String id) {
		return MUNICIPALITY_BY_ID_MAP.containsKey(id);
	}

	/**
	 * Returns true if a municipality exists by the provided name (case insensitive).
	 *
	 * @param  name the municipality name.
	 * @return      true if the municipality exists, otherwise false.
	 */
	public static boolean existsByName(final String name) {
		return MUNICIPALITY_BY_NAME_MAP.containsKey(upperCase(name));
	}

	/**
	 * Returns the total list of municipality id/name mappings.
	 *
	 * @return all municipalities.
	 */
	public static List<Municipality> findAll() {
		return MUNICIPALITY_BY_ID_MAP.values().stream()
			.sorted(comparing(Municipality::id))
			.toList();
	}

	public static record Municipality(String id, String name) {}
}
