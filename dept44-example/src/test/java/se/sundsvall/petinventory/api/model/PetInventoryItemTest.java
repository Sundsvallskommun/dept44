package se.sundsvall.petinventory.api.model;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PetInventoryItemTest {

	@Test
	void testBean() {
		assertThat(PetInventoryItem.class, allOf(
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
		final var price = 1.0F;
		final var type = "type";

		final var petInventoryItem = PetInventoryItem.create()
			.withId(id)
			.withName(name)
			.withPrice(price)
			.withType(type);

		assertThat(petInventoryItem).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(petInventoryItem.getId()).isEqualTo(id);
		assertThat(petInventoryItem.getName()).isEqualTo(name);
		assertThat(petInventoryItem.getPrice()).isEqualTo(price);
		assertThat(petInventoryItem.getType()).isEqualTo(type);
	}

	@ParameterizedTest
	@MethodSource("testNoDirtOnCreatedBeanArguments")
	void testNoDirtOnCreatedBean(final PetInventoryItem petInventoryItem) {
		assertThat(petInventoryItem).hasAllNullFieldsOrProperties();
	}

	private static Stream<Arguments> testNoDirtOnCreatedBeanArguments() {
		return Stream.of(
			Arguments.of(new PetInventoryItem()),
			Arguments.of(PetInventoryItem.create()));
	}
}
