package se.sundsvall.petinventory.api.model;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

class PetImageTest {

	@Test
	void testBean() {
		assertThat(PetImage.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {

		final var id = 1L;
		final var fileName = "test.jpg";
		final var mimeType = "image/jpeg";

		final var bean = PetImage.create()
			.withId(id)
			.withFileName(fileName)
			.withMimeType(mimeType);

		assertThat(bean).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(bean.getId()).isEqualTo(id);
		assertThat(bean.getFileName()).isEqualTo(fileName);
		assertThat(bean.getMimeType()).isEqualTo(mimeType);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(new PetInventoryItem()).hasAllNullFieldsOrProperties();
		assertThat(PetInventoryItem.create()).hasAllNullFieldsOrProperties();
	}
}
