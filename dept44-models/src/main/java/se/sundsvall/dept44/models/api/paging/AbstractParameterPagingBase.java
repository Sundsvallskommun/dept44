package se.sundsvall.dept44.models.api.paging;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import se.sundsvall.dept44.models.api.paging.validation.MaxPagingLimit;

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

	protected AbstractParameterPagingBase(int defaultLimit) {
		this.limit = defaultLimit;
	}

	protected AbstractParameterPagingBase() {
		this.limit = 100;
	}

	@Schema(description = "Page number", example = "1", minimum = "1", defaultValue = "1")
	@Min(1)
	protected int page = 1;

	@Schema(description = "Result size per page. Maximum allowed value is dynamically configured",
		minimum = "1",
		example = "15")
	@Min(1)
	@MaxPagingLimit
	protected int limit;

}
