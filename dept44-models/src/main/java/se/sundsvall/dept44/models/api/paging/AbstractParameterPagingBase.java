package se.sundsvall.dept44.models.api.paging;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;
import se.sundsvall.dept44.models.api.paging.validation.MaxPagingLimit;

import java.util.List;
import java.util.Optional;

import static java.lang.Integer.parseInt;
import static org.springframework.data.domain.Sort.DEFAULT_DIRECTION;

/**
 * Model class to extend when requesting paged result. Should be used as query parameters.
 * See {@link PagingMetaData} for response.
 * <p>
 * Use property 'dept44.models.api.paging.max.limit' to change default max allowed page size.
 */
@EqualsAndHashCode
@Getter
@Setter
public abstract class AbstractParameterPagingBase {

	private static final String DEFAULT_PAGE = "1";
	private static final String DEFAULT_LIMIT = "100";


	@Schema(description = "Page number", example = "1", minimum = "1")
	@Min(1)
	protected int page = parseInt(DEFAULT_PAGE);

	@Schema(description = "Result size per page", example = "15")
	@Min(1)
	@MaxPagingLimit
	protected int limit = parseInt(DEFAULT_LIMIT);

	@ArraySchema(schema = @Schema(description = "The properties to sort on", example = "propertyName"))
	protected List<String> sortBy;

	@Schema(description = "The sort order direction", example = "ASC", enumAsRef = true)
	protected Sort.Direction sortDirection = DEFAULT_DIRECTION;

	@JsonIgnore
	public Sort sort() {
		return Optional.ofNullable(this.sortBy)
			.map(sortByList -> Sort.by(Optional.ofNullable(this.sortDirection).orElse(DEFAULT_DIRECTION), sortByList.toArray(new String[0])))
			.orElseGet(Sort::unsorted);
	}
}
