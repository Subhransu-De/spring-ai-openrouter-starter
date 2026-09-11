package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public record ResponsesRequest(String model, List<String> models, Object input, String instructions,
		@JsonProperty("max_output_tokens") Integer maxOutputTokens, Boolean stream, Double temperature,
		@JsonProperty("top_p") Double topP, @JsonProperty("top_k") Integer topK,
		@JsonProperty("frequency_penalty") Double frequencyPenalty,
		@JsonProperty("presence_penalty") Double presencePenalty, Map<String, Object> metadata,
		ProviderPreferences provider, ReasoningOptions reasoning, String route,
		@JsonProperty("service_tier") String serviceTier, String user,
		@JsonProperty("parallel_tool_calls") Boolean parallelToolCalls, @JsonProperty("tool_choice") Object toolChoice,
		List<ResponsesTool> tools, List<String> modalities,
		@JsonProperty("image_config") Map<String, Object> imageConfig, Map<String, Object> text) {
	public ResponsesRequest(String model, List<String> models, Object input, String instructions,
			Integer maxOutputTokens, Boolean stream, Double temperature, Double topP, Integer topK,
			Double frequencyPenalty, Double presencePenalty, Map<String, Object> metadata, ProviderPreferences provider,
			ReasoningOptions reasoning, String route, String serviceTier, String user, Boolean parallelToolCalls,
			Object toolChoice, List<ResponsesTool> tools, List<String> modalities, Map<String, Object> imageConfig) {
		this(model, models, input, instructions, maxOutputTokens, stream, temperature, topP, topK, frequencyPenalty,
				presencePenalty, metadata, provider, reasoning, route, serviceTier, user, parallelToolCalls, toolChoice,
				tools, modalities, imageConfig, null);
	}
}
