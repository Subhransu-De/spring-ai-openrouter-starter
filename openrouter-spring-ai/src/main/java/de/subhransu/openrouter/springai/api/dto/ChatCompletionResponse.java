package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ChatCompletionResponse(String id, String object, Long created, String model, String provider,
		List<Choice> choices, Usage usage) {
}
