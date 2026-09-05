package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public record ChatCompletionRequest(String model, List<String> models, List<ChatMessage> messages, Double temperature,
		@JsonProperty("top_p") Double topP, @JsonProperty("top_k") Integer topK,
		@JsonProperty("frequency_penalty") Double frequencyPenalty,
		@JsonProperty("presence_penalty") Double presencePenalty,
		@JsonProperty("repetition_penalty") Double repetitionPenalty, @JsonProperty("min_p") Double minP,
		@JsonProperty("top_a") Double topA, @JsonProperty("max_tokens") Integer maxTokens,
		@JsonProperty("max_completion_tokens") Integer maxCompletionTokens, List<String> stop, Integer seed,
		String user, Boolean stream, @JsonProperty("response_format") Object responseFormat, List<Tool> tools,
		@JsonProperty("tool_choice") Object toolChoice, @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
		ProviderPreferences provider, ReasoningOptions reasoning, @JsonProperty("service_tier") String serviceTier,
		Map<String, Object> metadata, String route, UsageConfig usage, List<String> modalities,
		@JsonProperty("image_config") Map<String, Object> imageConfig) {
}
