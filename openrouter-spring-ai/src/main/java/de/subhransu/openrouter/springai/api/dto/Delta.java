package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record Delta(String role, String content, String reasoning, @JsonProperty("tool_calls") List<ToolCall> toolCalls,
		List<ContentPart> images) {

	public Delta(String role, String content, String reasoning, List<ToolCall> toolCalls) {
		this(role, content, reasoning, toolCalls, null);
	}
}
