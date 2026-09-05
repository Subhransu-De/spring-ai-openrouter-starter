package de.subhransu.openrouter.springai.api.errors;

import de.subhransu.openrouter.springai.errors.OpenRouterErrorCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterApiExceptionTests {

	@Test
	void compatibilityConstructorAcceptsNullStatus() {
		OpenRouterApiException exception = new OpenRouterApiException("failure", null, "{}");

		assertThat(exception.getStatusCode()).isNull();
		assertThat(exception.getCategory()).isEqualTo(OpenRouterErrorCategory.UNKNOWN);
	}

}
