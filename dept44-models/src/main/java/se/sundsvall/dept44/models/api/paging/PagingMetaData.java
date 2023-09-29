package se.sundsvall.dept44.models.api.paging;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.springframework.data.domain.Sort.Direction;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Model class to use when returning paged result. Should be added in root of response under attribute "_meta".
 * In compliance with <a href="https://dev.dataportal.se/rest-api-profil/filtrering-paginering-och-sokparametrar#paginering">DIGG Paginering</a>
 * See {@link AbstractParameterPagingBase} for request.
 */
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PagingMetaData model")
public class PagingMetaData {

	@Schema(description = "Current page", example = "5", accessMode = READ_ONLY)
	private int page;

	@Schema(description = "Displayed objects per page", example = "20", accessMode = READ_ONLY)
	private int limit;

	@Schema(description = "Displayed objects on current page", example = "13", accessMode = READ_ONLY)
	private int count;

	@Schema(description = "Total amount of hits based on provided search parameters", example = "98", accessMode = READ_ONLY)
	private long totalRecords;

	@Schema(description = "Total amount of pages based on provided search parameters", example = "23", accessMode = READ_ONLY)
	private int totalPages;

	@ArraySchema(schema = @Schema(description = "The properties to sort by", example = "property", accessMode = READ_ONLY))
	private List<String> sortBy;

	@Schema(description = "The sort order direction", example = "ASC", enumAsRef = true)
	private Direction sortDirection;

	public static PagingMetaData create() {
		return new PagingMetaData();
	}
}