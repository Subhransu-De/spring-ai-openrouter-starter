package de.subhransu.openrouter.springai.chat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.subhransu.openrouter.springai.api.dto.ChatCompletionResponse;
import de.subhransu.openrouter.springai.api.dto.ChatMessage;
import de.subhransu.openrouter.springai.api.dto.Choice;
import de.subhransu.openrouter.springai.api.dto.Usage;
import de.subhransu.openrouter.springai.chat.OpenRouterUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

class OpenRouterChatResponseMapperTests {

	@Test
	void mapsResponseContentMetadataAndUsage() {
		ChatCompletionResponse response = new ChatCompletionResponse("gen-1", "chat.completion", 123L,
				"openai/gpt-5.4-mini", "openai",
				List.of(new Choice(0, new ChatMessage("assistant", "hello", null, null, null), null, "stop", "stop")),
				new Usage(10, 5, 15, 2, 1, 0.001, null, null, null));

		ChatResponse mapped = new OpenRouterChatResponseMapper().map(response);

		assertThat(mapped.getResult().getOutput().getText()).isEqualTo("hello");
		assertThat(mapped.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
		assertThat(mapped.getMetadata().getId()).isEqualTo("gen-1");
		assertThat(mapped.getMetadata().getModel()).isEqualTo("openai/gpt-5.4-mini");
		assertThat(mapped.getMetadata().getUsage()).isInstanceOf(OpenRouterUsage.class);
		assertThat(((OpenRouterUsage) mapped.getMetadata().getUsage()).getCachedTokens()).isEqualTo(2);
	}

	@Test
	void emptyChoicesListProducesNoGenerations() {
		ChatCompletionResponse response = new ChatCompletionResponse("gen-empty", "chat.completion", 1L, "m", "p",
				List.of(), null);

		ChatResponse mapped = new OpenRouterChatResponseMapper().map(response);

		assertThat(mapped.getResults()).isEmpty();
	}

	@Test
	void nullChoicesListProducesNoGenerations() {
		ChatCompletionResponse response = new ChatCompletionResponse("gen-null", "chat.completion", 1L, "m", "p", null,
				null);

		ChatResponse mapped = new OpenRouterChatResponseMapper().map(response);

		assertThat(mapped.getResults()).isEmpty();
	}

	@Test
	void readsCachedAndReasoningTokensFromNestedUsageDetails() {
		Usage usage = new Usage(10, 5, 15, null, null, 0.001, new Usage.PromptTokensDetails(3),
				new Usage.CompletionTokensDetails(4), null);
		ChatCompletionResponse response = new ChatCompletionResponse("gen-1", "chat.completion", 123L,
				"openai/gpt-5.4-mini", "openai",
				List.of(new Choice(0, new ChatMessage("assistant", "hello", null, null, null), null, "stop", "stop")),
				usage);

		ChatResponse mapped = new OpenRouterChatResponseMapper().map(response);

		OpenRouterUsage mappedUsage = (OpenRouterUsage) mapped.getMetadata().getUsage();
		assertThat(mappedUsage.getCachedTokens()).isEqualTo(3);
		assertThat(mappedUsage.getReasoningTokens()).isEqualTo(4);
	}

}
