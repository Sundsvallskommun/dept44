package se.sundsvall.dept44.models.api.paging.validation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import se.sundsvall.dept44.models.api.paging.AbstractParameterPagingAndSortingBase;
import se.sundsvall.dept44.models.api.paging.validation.ValidSortByProperty;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(classes = {
	MaxPagingLimitConstraintValidator.class, LocalValidatorFactoryBean.class
})
class ValidSortByPropertyConstraintValidatorTest {

	@Autowired
	private Validator validator;

	@Test
	void isValidWithFields() {
		final var parameters = new TestParameters();
		parameters.setSortBy(List.of("id", "myField"));
		assertThat(validator.validate(parameters)).isEmpty();
	}

	@Test
	void isValidWithFieldsWithNoEntity() {
		final var parameters = new TestParametersWithNoBackedEntity();
		parameters.setSortBy(List.of("includeField"));
		assertThat(validator.validate(parameters)).isEmpty();
	}

	@Test
	void isValidWithoutFields() {
		final var parameters = new TestParameters();
		assertThat(validator.validate(parameters)).isEmpty();
	}

	@Test
	void isValidWithoutFieldsWithNoEntity() {
		final var parameters = new TestParametersWithNoBackedEntity();
		assertThat(validator.validate(parameters)).isEmpty();
	}

	@Test
	void isNotValid() {
		final var parameters = new TestParameters();
		parameters.setSortBy(List.of("notASortableField"));
		assertThat(validator.validate(parameters))
			.first()
			.extracting(ConstraintViolation::getMessage)
			.isEqualTo(
				"One or more of the sortBy properties [notASortableField] are not valid. Valid properties to sort by are [myField, id, mySecondField].");
	}

	@Test
	void isNotValidWithNoEntity() {
		final var parameters = new TestParametersWithNoBackedEntity();
		parameters.setSortBy(List.of("notASortableField"));
		assertThat(validator.validate(parameters))
			.first()
			.extracting(ConstraintViolation::getMessage)
			.isEqualTo(
				"One or more of the sortBy properties [notASortableField] are not valid. Valid properties to sort by are [includeField].");
	}

	private static class TestEntity {

		@Column(name = " id")
		private Integer id;

		@Column(name = "my_field")
		private String myField;

		@Column(name = "my_second_field")
		private String mySecondField;

		@Column(name = "exclude_field")
		private String excludeField;

	}

	@ValidSortByProperty(value = TestEntity.class, exclude = "excludeField")
	public static class TestParameters extends AbstractParameterPagingAndSortingBase {
	}

	@ValidSortByProperty(include = "includeField")
	public static class TestParametersWithNoBackedEntity extends AbstractParameterPagingAndSortingBase {
	}

}
