package se.sundsvall.dept44.models.api.paging;

import static org.springframework.data.domain.Sort.DEFAULT_DIRECTION;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;

/**
 * Model class to extend when requesting paged result with sorting. Should be used as query parameters.
 * See {@link PagingAndSortingMetaData} for response.
 * <p>
 * Use property 'dept44.models.api.paging.max.limit' to change default max allowed page size.
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public abstract class AbstractParameterPagingAndSortingBase extends AbstractParameterPagingBase {

	protected AbstractParameterPagingAndSortingBase(int defaultLimit) {
		super(defaultLimit);
	}

	protected AbstractParameterPagingAndSortingBase() {
		super(100);
	}

	@ArraySchema(schema = @Schema(description = "The properties to sort on", examples = "propertyName"))
	protected List<String> sortBy;

	@Schema(description = "The sort order direction", examples = "ASC", enumAsRef = true)
	protected Sort.Direction sortDirection = DEFAULT_DIRECTION;

	@JsonIgnore
	public Sort sort() {
		return Optional.ofNullable(this.sortBy)
			.map(sortByList -> Sort.by(Optional.ofNullable(this.sortDirection).orElse(DEFAULT_DIRECTION), sortByList.toArray(new String[0])))
			.orElseGet(Sort::unsorted);
	}
}
