package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ChatMessage(String role, Object content, String name, @JsonProperty("tool_call_id") String toolCallId,
		@JsonProperty("tool_calls") List<ToolCall> toolCalls, List<ContentPart> images) {

	public ChatMessage(String role, Object content, String name, String toolCallId, List<ToolCall> toolCalls) {
		this(role, content, name, toolCallId, toolCalls, null);
	}
}
