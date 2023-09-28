package se.sundsvall.dept44.api.models.paging;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import java.util.List;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

class PagingMetaDataTest {

	@Test
	void testBean() {
		assertThat(PagingMetaData.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testCreatePattern() {

		final var count = 101;
		final var limit = 202;
		final var page = 303;
		final var sortBy = List.of("someProperty");
		final var sortDirection = Sort.Direction.DESC;
		final var totalPages = 404;
		final var totalRecords = 505;


		final var meta = PagingMetaData.create()
			.withCount(count)
			.withLimit(limit)
			.withPage(page)
			.withSortBy(sortBy)
			.withSortDirection(sortDirection)
			.withTotalPages(totalPages)
			.withTotalRecords(totalRecords);

		assertThat(meta.getCount()).isEqualTo(count);
		assertThat(meta.getLimit()).isEqualTo(limit);
		assertThat(meta.getPage()).isEqualTo(page);
		assertThat(meta.getSortBy()).isEqualTo(sortBy);
		assertThat(meta.getSortDirection()).isEqualTo(sortDirection);
		assertThat(meta.getTotalPages()).isEqualTo(totalPages);
		assertThat(meta.getTotalRecords()).isEqualTo(totalRecords);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(PagingMetaData.create())
			.hasFieldOrPropertyWithValue("count", 0)
			.hasFieldOrPropertyWithValue("limit", 0)
			.hasFieldOrPropertyWithValue("page", 0)
			.hasFieldOrPropertyWithValue("totalRecords", 0L)
			.hasFieldOrPropertyWithValue("totalPages", 0);

		assertThat(new PagingMetaData())
			.hasFieldOrPropertyWithValue("count", 0)
			.hasFieldOrPropertyWithValue("limit", 0)
			.hasFieldOrPropertyWithValue("page", 0)
			.hasFieldOrPropertyWithValue("totalRecords", 0L)
			.hasFieldOrPropertyWithValue("totalPages", 0);
	}

}