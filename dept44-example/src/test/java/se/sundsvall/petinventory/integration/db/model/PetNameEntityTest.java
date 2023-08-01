package se.sundsvall.petinventory.integration.db.model;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static com.google.code.beanmatchers.BeanMatchers.registerValueGenerator;
import static java.time.OffsetDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.OffsetDateTime;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PetNameEntityTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> now().plusDays(new Random().nextInt()), OffsetDateTime.class);
	}

	@Test
	void testBean() {
		assertThat(PetNameEntity.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {

		final var id = 1L;
		final var name = "name";
		final var created = now();
		final var modified = now().plusHours(5);

		final var petNameEntity = PetNameEntity.create()
			.withId(id)
			.withName(name)
			.withCreated(created)
			.withModified(modified);

		assertThat(petNameEntity).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(petNameEntity.getId()).isEqualTo(id);
		assertThat(petNameEntity.getName()).isEqualTo(name);
		assertThat(petNameEntity.getCreated()).isEqualTo(created);
		assertThat(petNameEntity.getModified()).isEqualTo(modified);
	}

	@ParameterizedTest
	@MethodSource("testNoDirtOnCreatedBeanArguments")
	void testNoDirtOnCreatedBean(final PetNameEntity petNameEntity) {
		assertThat(petNameEntity).hasAllNullFieldsOrProperties();
	}

	private static Stream<Arguments> testNoDirtOnCreatedBeanArguments() {
		return Stream.of(
			Arguments.of(new PetNameEntity()),
			Arguments.of(PetNameEntity.create()));
	}
}
