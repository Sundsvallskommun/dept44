package se.sundsvall.dept44.models.api.paging;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * Model class to use when returning paged result with sorting. Should be added in root of response under attribute
 * "_meta".
 * In compliance with
 * <a href="https://dev.dataportal.se/rest-api-profil/filtrering-paginering-och-sokparametrar#paginering">DIGG
 * Paginering</a>
 * See {@link AbstractParameterPagingAndSortingBase} for request.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "PagingAndSortingMetaData model")
public class PagingAndSortingMetaData extends PagingMetaData {

	@ArraySchema(schema = @Schema(description = "The properties to sort by", examples = "property", accessMode = READ_ONLY))
	private List<String> sortBy;

	@Schema(description = "The sort order direction", examples = "ASC", enumAsRef = true)
	private Sort.Direction sortDirection;

	public static PagingAndSortingMetaData create() {
		return new PagingAndSortingMetaData();
	}

	@Override
	public PagingAndSortingMetaData withPage(int page) {
		super.setPage(page);
		return this;
	}

	@Override
	public PagingAndSortingMetaData withLimit(int limit) {
		super.setLimit(limit);
		return this;
	}

	@Override
	public PagingAndSortingMetaData withCount(int count) {
		super.setCount(count);
		return this;
	}

	@Override
	public PagingAndSortingMetaData withTotalRecords(long totalRecords) {
		super.setTotalRecords(totalRecords);
		return this;
	}

	@Override
	public PagingAndSortingMetaData withTotalPages(int totalPages) {
		super.setTotalPages(totalPages);
		return this;
	}

	public PagingAndSortingMetaData withSortBy(List<String> sortBy) {
		this.sortBy = sortBy;
		return this;
	}

	public PagingAndSortingMetaData withSortDirection(Sort.Direction sortDirection) {
		this.sortDirection = sortDirection;
		return this;
	}

	public PagingAndSortingMetaData withPageData(Page<?> page) {
		setPage(page.getNumber() + 1);
		setLimit(page.getSize());
		setCount(page.getNumberOfElements());
		setTotalRecords(page.getTotalElements());
		setTotalPages(page.getTotalPages());
		setSortBy(page.getSort().get()
			.map(Sort.Order::getProperty)
			.collect(collectingAndThen(toList(), list -> list.isEmpty() ? null : list)));
		setSortDirection(page.getSort().stream().findFirst().map(Sort.Order::getDirection).orElse(null));

		return this;
	}
}
