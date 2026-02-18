package se.sundsvall.dept44.models.api.paging;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;

/**
 * Model class to use when returning paged result. Should be added in root of response under attribute "_meta".
 * In compliance with
 * <a href="https://dev.dataportal.se/rest-api-profil/filtrering-paginering-och-sokparametrar#paginering">DIGG
 * Paginering</a>
 * See {@link AbstractParameterPagingBase} for request.
 */
@Data
@Schema(description = "PagingMetaData model")
public class PagingMetaData {

	@Schema(description = "Current page", examples = "5", accessMode = READ_ONLY)
	private int page;

	@Schema(description = "Displayed objects per page", examples = "20", accessMode = READ_ONLY)
	private int limit;

	@Schema(description = "Displayed objects on current page", examples = "13", accessMode = READ_ONLY)
	private int count;

	@Schema(description = "Total amount of hits based on provided search parameters", examples = "98", accessMode = READ_ONLY)
	private long totalRecords;

	@Schema(description = "Total amount of pages based on provided search parameters", examples = "23", accessMode = READ_ONLY)
	private int totalPages;

	public static PagingMetaData create() {
		return new PagingMetaData();
	}

	public PagingMetaData withPage(int page) {
		this.page = page;
		return this;
	}

	public PagingMetaData withLimit(int limit) {
		this.limit = limit;
		return this;
	}

	public PagingMetaData withCount(int count) {
		this.count = count;
		return this;
	}

	public PagingMetaData withTotalRecords(long totalRecords) {
		this.totalRecords = totalRecords;
		return this;
	}

	public PagingMetaData withTotalPages(int totalPages) {
		this.totalPages = totalPages;
		return this;
	}
}
