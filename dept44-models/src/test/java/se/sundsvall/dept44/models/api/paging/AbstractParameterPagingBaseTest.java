package se.sundsvall.dept44.models.api.paging;

import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

class AbstractParameterPagingBaseTest {

	@Test
	void testBean() {
		assertThat(TestParameter.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals()));
	}

	@Test
	void hasCustomDefaultLimit() {
		assertThat(new TestParameter().limit).isEqualTo(200);
	}

	private static class TestParameter extends AbstractParameterPagingBase {
		public TestParameter() {
			super(200);
		}
	}
}
