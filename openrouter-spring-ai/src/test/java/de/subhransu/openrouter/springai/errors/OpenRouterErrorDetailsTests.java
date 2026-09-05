package de.subhransu.openrouter.springai.errors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterErrorDetailsTests {

	@Test
	void compatibilityConstructorDerivesCategoryFromNumericCode() {
		OpenRouterErrorDetails details = new OpenRouterErrorDetails("429", "slow down", null, null, null);

		assertThat(details.category()).isEqualTo(OpenRouterErrorCategory.RATE_LIMIT);
	}

}
