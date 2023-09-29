package se.sundsvall.dept44.models.api.paging.validation.impl;


import jakarta.persistence.Column;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import se.sundsvall.dept44.models.api.paging.AbstractParameterPagingBase;
import se.sundsvall.dept44.models.api.paging.validation.ValidSortByProperty;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(classes = {MaxPagingLimitConstraintValidator.class, LocalValidatorFactoryBean.class})
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
	void isValidWithoutFields() {
		final var parameters = new TestParameters();
		assertThat(validator.validate(parameters)).isEmpty();
	}

	@Test
	void isNotValid() {
		final var parameters = new TestParameters();
		parameters.setSortBy(List.of("notASortableField"));
		assertThat(validator.validate(parameters))
			.first()
			.extracting(ConstraintViolation::getMessage)
			.isEqualTo("One or more of the sortBy properties [notASortableField] are not valid. Valid properties to sort by are [myField, id, mySecondField].");
	}

	private Set<ConstraintViolation<Object>> validate(Validator validator, AbstractParameterPagingBase dataClass) {
		return validator.validate(dataClass);
	}

	private static class TestEntity {

		@Column(name= " id" )
		private Integer id;

		@Column(name = "my_field")
		private String myField;

		@Column(name = "my_second_field")
		private String mySecondField;

		private String notASortableField;

	}

	@ValidSortByProperty(TestEntity.class)
	public static class TestParameters extends AbstractParameterPagingBase {
	}

}