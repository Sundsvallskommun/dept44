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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PetImageEntityTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> now().plusDays(new Random().nextInt()), OffsetDateTime.class);
	}

	@Test
	void testBean() {
		assertThat(PetImageEntity.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {

		final var id = 1L;
		final var content = new byte[6];
		final var created = now();
		final var fileName = "test.jpg";
		final var mimeType = "image/jpeg";
		final var modified = now().plusHours(5);
		final var petNameEntity = PetNameEntity.create();

		final var bean = PetImageEntity.create()
			.withId(id)
			.withContent(content)
			.withCreated(created)
			.withFileName(fileName)
			.withMimeType(mimeType)
			.withModified(modified)
			.withPetName(petNameEntity);

		assertThat(bean).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(bean.getId()).isEqualTo(id);
		assertThat(bean.getContent()).isEqualTo(content);
		assertThat(bean.getCreated()).isEqualTo(created);
		assertThat(bean.getFileName()).isEqualTo(fileName);
		assertThat(bean.getMimeType()).isEqualTo(mimeType);
		assertThat(bean.getModified()).isEqualTo(modified);
		assertThat(bean.getPetName()).isEqualTo(petNameEntity);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(new PetImageEntity()).hasAllNullFieldsOrProperties();
		assertThat(PetImageEntity.create()).hasAllNullFieldsOrProperties();
	}
}
