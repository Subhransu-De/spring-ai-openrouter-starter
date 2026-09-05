package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ToolCall(String id, String type, FunctionCall function, Integer index) {

	public ToolCall(String id, String type, FunctionCall function) {
		this(id, type, function, null);
	}

}
