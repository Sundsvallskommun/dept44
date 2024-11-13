package se.sundsvall.petinventory.api.model;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

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
		final var images = List.of(PetImage.create());
		final var name = "name";
		final var price = 1.0F;
		final var type = "type";

		final var bean = PetInventoryItem.create()
			.withId(id)
			.withImages(images)
			.withName(name)
			.withPrice(price)
			.withType(type);

		assertThat(bean).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(bean.getId()).isEqualTo(id);
		assertThat(bean.getImages()).isEqualTo(images);
		assertThat(bean.getName()).isEqualTo(name);
		assertThat(bean.getPrice()).isEqualTo(price);
		assertThat(bean.getType()).isEqualTo(type);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(new PetInventoryItem()).hasAllNullFieldsOrProperties();
		assertThat(PetInventoryItem.create()).hasAllNullFieldsOrProperties();
	}
}
