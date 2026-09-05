package de.subhransu.openrouter.springai.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenRouterExceptionMessageTests {

	@Test
	void diagnosticPolicyBoundsRedactsAndNeutralizesProviderText() {
		String diagnostic = "first line\r\nsecond line\u001B[31m Bearer secret-token " + "api_key=another-secret "
				+ "x".repeat(1200);

		String sanitized = OpenRouterExceptionMessage.sanitize(diagnostic);

		assertThat(sanitized).hasSize(OpenRouterExceptionMessage.MAX_DIAGNOSTIC_LENGTH + 3)
			.startsWith("first line second line [31m Bearer [REDACTED] api_key=[REDACTED]")
			.endsWith("...")
			.doesNotContain("\r", "\n", "\u001B", "secret-token", "another-secret");
	}

	@Test
	void hostMessageNeverIncludesTheProviderDiagnostic() {
		assertThat(OpenRouterExceptionMessage.build("OpenRouter request failed", "provider\r\ninjected"))
			.isEqualTo("OpenRouter request failed");
	}

	@Test
	void providerDiagnosticCannotBeUsedAsTheHostMessage() {
		String providerDiagnostic = "provider-controlled failure";

		assertThat(OpenRouterExceptionMessage.build(providerDiagnostic, providerDiagnostic))
			.isEqualTo("OpenRouter request failed");
	}

}
