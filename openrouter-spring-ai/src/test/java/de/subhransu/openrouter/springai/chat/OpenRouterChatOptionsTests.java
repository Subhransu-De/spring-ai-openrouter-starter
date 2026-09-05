package de.subhransu.openrouter.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenRouterChatOptionsTests {

	private static final String MODEL = "openai/gpt-5.4-mini";

	@Test
	void mergeKeepsDefaultsWhenRuntimeOptionsDoNotSetOpenRouterFields() {
		OpenRouterChatOptions defaults = OpenRouterChatOptions.builder()
			.model(MODEL)
			.models(List.of("anthropic/claude-3.5-sonnet"))
			.temperature(0.2)
			.route("fallback")
			.build();

		OpenRouterChatOptions runtime = OpenRouterChatOptions.builder().requestMode(null).temperature(0.8).build();

		OpenRouterChatOptions merged = defaults.merge(runtime);

		assertThat(merged.getModel()).isEqualTo(MODEL);
		assertThat(merged.getModels()).containsExactly("anthropic/claude-3.5-sonnet");
		assertThat(merged.getTemperature()).isEqualTo(0.8);
		assertThat(merged.getRoute()).isEqualTo("fallback");
		assertThat(merged.getRequestMode()).isEqualTo(defaults.getRequestMode());
	}

	@Test
	void collectionGettersDoNotExposeMutableInternalState() {
		OpenRouterChatOptions options = OpenRouterChatOptions.builder().stopSequences(List.of("END")).build();

		OpenRouterChatOptions copy = options.copy();

		assertThatThrownBy(() -> copy.getStopSequences().add("STOP")).isInstanceOf(UnsupportedOperationException.class);
		assertThat(options.getStopSequences()).containsExactly("END");
		assertThat(copy.getStopSequences()).containsExactly("END");
	}

}
