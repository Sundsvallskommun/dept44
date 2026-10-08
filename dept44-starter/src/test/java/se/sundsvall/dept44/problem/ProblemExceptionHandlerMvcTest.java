package se.sundsvall.dept44.problem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the handler in Spring MVC, so that the exceptions Spring itself throws, and the way it picks a handler for them,
 * are the real ones.
 */
class ProblemExceptionHandlerMvcTest {

	private final MockMvc mockMvc = mockMvc();

	@Test
	void methodValidationOnAControllerWithoutValidatedListsTheViolations() throws Exception {
		mockMvc.perform(get("/items/{id}", "too-long"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Constraint Violation"))
			.andExpect(jsonPath("$.violations[0].field").value(startsWith("get.")))
			.andExpect(jsonPath("$.violations[0].message").value("size must be between 0 and 3"));
	}

	@Test
	void methodValidationOfAValidObjectListsTheFieldViolations() throws Exception {
		mockMvc.perform(get("/search").param("name", " ").param("id", "too-long"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Constraint Violation"))
			.andExpect(jsonPath("$.violations.length()").value(1))
			.andExpect(jsonPath("$.violations[0].field").value("name"));
	}

	@Test
	void wrappedTimeoutGivesGatewayTimeout() throws Exception {
		mockMvc.perform(get("/wrapped-timeout"))
			.andExpect(status().isGatewayTimeout())
			.andExpect(jsonPath("$.detail").value("Read timed out"));
	}

	@Test
	void wrappedProblemKeepsItsStatus() throws Exception {
		mockMvc.perform(get("/wrapped-problem"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("missing"));
	}

	@Test
	void otherExceptionsGiveInternalServerError() throws Exception {
		mockMvc.perform(get("/failure"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.detail").value("boom"));
	}

	private static MockMvc mockMvc() {
		final var validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		return MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new ProblemExceptionHandler())
			.setValidator(validator)
			.build();
	}

	public record SearchParameters(@NotBlank String name, String id) {
	}

	@RestController
	static class TestController {

		@GetMapping("/items/{id}")
		String get(@PathVariable("id") @Size(max = 3) final String id) {
			return id;
		}

		@GetMapping("/search")
		String search(@Valid @ModelAttribute final SearchParameters parameters, @RequestParam(name = "unused", required = false) @Size(max = 3) final String unused) {
			return parameters.name();
		}

		@GetMapping("/wrapped-timeout")
		String wrappedTimeout() {
			throw new IllegalStateException("Request failed", new SocketTimeoutException("Read timed out"));
		}

		@GetMapping("/wrapped-problem")
		String wrappedProblem() {
			throw new CompletionException(Problem.valueOf(NOT_FOUND, "missing"));
		}

		@GetMapping("/failure")
		String failure() {
			throw new IllegalStateException("boom");
		}
	}
}
