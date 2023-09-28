package se.sundsvall.dept44.api.models.paging;


import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

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
	void isUnsorted() {
		assertThat(new TestParameter().sort()).isEqualTo(Sort.unsorted());
	}

	@Test
	void isSortedASC() {
		var test = new TestParameter();
		test.setSortBy(List.of("field1", "field2"));

		var result = test.sort();

		assertThat(result.get()).extracting("property", "direction").containsExactly(
			tuple("field1", ASC),
			tuple("field2", ASC));
	}

	@Test
	void isSortedDESC() {
		var test = new TestParameter();
		test.setSortBy(List.of("field1", "field2"));
		test.setSortDirection(DESC);

		var result = test.sort();

		assertThat(result.get()).extracting("property", "direction").containsExactly(
			tuple("field1", DESC),
			tuple("field2", DESC));

	}

	private static class TestParameter extends AbstractParameterPagingBase{
	}
}