package se.sundsvall.dept44.util;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.length;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.math.NumberUtils.isDigits;
import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.dept44.test.annotation.resource.Load.ResourceType.STRING;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import se.sundsvall.dept44.test.annotation.resource.Load;
import se.sundsvall.dept44.test.extension.ResourceLoaderExtension;
import se.sundsvall.dept44.util.MunicipalityUtils.Municipality;

@ExtendWith(ResourceLoaderExtension.class)
class MunicipalityUtilsTest {

	private static final String TEST_JSON_FILE = "data/municipality.yml";
	private static final Integer EXPECTED_NUMBER_OF_MUNICIPALITY_RECORDS = 311;

	@Test
	void validFileContent(@Load(value = TEST_JSON_FILE, as = STRING) final String json) throws Exception {
		assertThat(new YAMLMapper().readValue(json, new TypeReference<List<Municipality>>() {}))
			.hasSize(EXPECTED_NUMBER_OF_MUNICIPALITY_RECORDS)
			.allMatch(municipality -> isNotBlank(municipality.id()))
			.allMatch(municipality -> isNotBlank(municipality.name()))
			.allMatch(municipality -> isDigits(municipality.id()))
			.allMatch(municipality -> hasNoSurroundingWhitespace(municipality.name()));
	}

	@Test
	void findById() {

		// Call
		final var municipality = MunicipalityUtils.findById("2281");

		// Verification
		assertThat(municipality).isNotNull();
		assertThat(municipality.id()).isEqualTo("2281");
		assertThat(municipality.name()).isEqualTo("Sundsvall");
	}

	@Test
	void findByIdNotFound() {

		// Call
		final var municipality = MunicipalityUtils.findById("666");

		// Verification
		assertThat(municipality).isNull();
	}

	@Test
	void findByName() {

		// Call
		final var municipality = MunicipalityUtils.findByName("Sundsvall");

		// Verification
		assertThat(municipality).isNotNull();
		assertThat(municipality.id()).isEqualTo("2281");
		assertThat(municipality.name()).isEqualTo("Sundsvall");
	}

	@Test
	void findByNameNotFound() {

		// Call
		final var municipality = MunicipalityUtils.findByName("Sörbäcken");

		// Verification
		assertThat(municipality).isNull();
	}

	@Test
	void findByNameCaseInsensitive() {

		// Call
		final var municipality = MunicipalityUtils.findByName("sUnDsVaLl");

		// Verification
		assertThat(municipality).isNotNull();
		assertThat(municipality.id()).isEqualTo("2281");
		assertThat(municipality.name()).isEqualTo("Sundsvall");
	}

	@Test
	void existsById() {
		assertThat(MunicipalityUtils.existsById("2281")).isTrue();
	}

	@Test
	void existsByIdNotFound() {
		assertThat(MunicipalityUtils.existsById("666")).isFalse();
	}

	@Test
	void existsByName() {
		assertThat(MunicipalityUtils.existsByName("Sundsvall")).isTrue();
	}

	@Test
	void existsByNameCaseInsensitive() {
		assertThat(MunicipalityUtils.existsByName("sUnDsVaLl")).isTrue();
	}

	@Test
	void existsByNameNotFound() {
		assertThat(MunicipalityUtils.existsByName("Sörbäcken")).isFalse();
	}

	@Test
	void findAll() {
		assertThat(MunicipalityUtils.findAll()).hasSize(EXPECTED_NUMBER_OF_MUNICIPALITY_RECORDS);
	}

	private boolean hasNoSurroundingWhitespace(final String string) {
		return trim(string).length() == length(string);
	}
}
