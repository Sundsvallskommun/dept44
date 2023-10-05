package se.sundsvall.dept44.models.api.paging.validation.impl;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import se.sundsvall.dept44.models.api.paging.AbstractParameterPagingBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(classes = {MaxPagingLimitConstraintValidator.class, LocalValidatorFactoryBean.class})
class MaxPagingLimitImplTest {

	@Autowired
	private Validator validator;

	@Test
	void isValidDefault() {
		assertThat(validator.validate(new TestModel())).isEmpty();
	}

	@Test
	void isValidLowLimit() {
		assertThat(validator.validate(new TestModel().withLimit(33))).isEmpty();
	}

	@Test
	void isNotValid() {
		assertThat(validator.validate(new TestModel().withLimit(1001)))
			.first()
			.extracting(ConstraintViolation::getMessage)
			.isEqualTo("Page limit cannot be greater than 1000");
	}

	@Nested
	@TestPropertySource(properties = "dept44.models.api.paging.max.limit=20")
	class MaxPagingLimitImplNonDefaultConfigTest {

		@Mock
		private ConstraintValidatorContext contextMock;

		@Mock
		private ConstraintValidatorContext.ConstraintViolationBuilder constraintViolationBuilderMock;

		@Autowired
		private MaxPagingLimitConstraintValidator validator;

		@Test
		void isValidCustomMaxLimit() {
			assertThat(validator.isValid(20, contextMock)).isTrue();
		}

		@Test
		void isNotValidCustomMaxLimit() {
			when(contextMock.buildConstraintViolationWithTemplate(any())).thenReturn(constraintViolationBuilderMock);

			assertThat(validator.isValid(21, contextMock)).isFalse();

			verify(contextMock).buildConstraintViolationWithTemplate("Page limit cannot be greater than 20");
			verify(constraintViolationBuilderMock).addConstraintViolation();
		}
	}

	static class TestModel extends AbstractParameterPagingBase {
		TestModel withLimit(int limit) {
			this.limit = limit;
			return this;
		}
	}
}