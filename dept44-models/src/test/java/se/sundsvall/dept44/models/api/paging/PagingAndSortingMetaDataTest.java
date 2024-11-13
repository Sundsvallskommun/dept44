package se.sundsvall.dept44.models.api.paging;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

class PagingAndSortingMetaDataTest {

	@Test
	void testBean() {
		assertThat(PagingAndSortingMetaData.class, allOf(
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

		final var meta = PagingAndSortingMetaData.create()
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
	void testPopulationWithPage() {

		var pageMock = Mockito.mock(Page.class);

		when(pageMock.getNumber()).thenReturn(10);
		when(pageMock.getSize()).thenReturn(20);
		when(pageMock.getNumberOfElements()).thenReturn(30);
		when(pageMock.getTotalElements()).thenReturn(40L);
		when(pageMock.getTotalPages()).thenReturn(50);
		when(pageMock.getSort()).thenReturn(Sort.by(Sort.Direction.ASC, "prop"));

		final var meta = PagingAndSortingMetaData.create().withPageData(pageMock);

		assertThat(meta.getPage()).isEqualTo(11);
		assertThat(meta.getLimit()).isEqualTo(20);
		assertThat(meta.getCount()).isEqualTo(30);
		assertThat(meta.getTotalRecords()).isEqualTo(40L);
		assertThat(meta.getTotalPages()).isEqualTo(50);
		assertThat(meta.getSortBy()).containsExactly("prop");
		assertThat(meta.getSortDirection()).isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void testPopulationWithPageNoSorting() {

		var pageMock = Mockito.mock(Page.class);

		when(pageMock.getNumber()).thenReturn(10);
		when(pageMock.getSize()).thenReturn(20);
		when(pageMock.getNumberOfElements()).thenReturn(30);
		when(pageMock.getTotalElements()).thenReturn(40L);
		when(pageMock.getTotalPages()).thenReturn(50);
		when(pageMock.getSort()).thenReturn(Sort.unsorted());

		final var meta = PagingAndSortingMetaData.create().withPageData(pageMock);

		assertThat(meta.getPage()).isEqualTo(11);
		assertThat(meta.getLimit()).isEqualTo(20);
		assertThat(meta.getCount()).isEqualTo(30);
		assertThat(meta.getTotalRecords()).isEqualTo(40L);
		assertThat(meta.getTotalPages()).isEqualTo(50);
		assertThat(meta.getSortBy()).isNull();
		assertThat(meta.getSortDirection()).isNull();
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(PagingAndSortingMetaData.create())
			.hasFieldOrPropertyWithValue("count", 0)
			.hasFieldOrPropertyWithValue("limit", 0)
			.hasFieldOrPropertyWithValue("page", 0)
			.hasFieldOrPropertyWithValue("totalRecords", 0L)
			.hasFieldOrPropertyWithValue("totalPages", 0);

		assertThat(new PagingAndSortingMetaData())
			.hasFieldOrPropertyWithValue("count", 0)
			.hasFieldOrPropertyWithValue("limit", 0)
			.hasFieldOrPropertyWithValue("page", 0)
			.hasFieldOrPropertyWithValue("totalRecords", 0L)
			.hasFieldOrPropertyWithValue("totalPages", 0)
			.hasFieldOrPropertyWithValue("sortBy", null)
			.hasFieldOrPropertyWithValue("sortDirection", null);
	}

}
