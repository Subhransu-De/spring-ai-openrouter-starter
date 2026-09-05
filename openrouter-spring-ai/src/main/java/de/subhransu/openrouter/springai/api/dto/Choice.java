package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record Choice(Integer index, ChatMessage message, Delta delta,
		@JsonProperty("finish_reason") String finishReason,
		@JsonProperty("native_finish_reason") Object nativeFinishReason, ChoiceError error) {

	public Choice(Integer index, ChatMessage message, Delta delta, String finishReason, Object nativeFinishReason) {
		this(index, message, delta, finishReason, nativeFinishReason, null);
	}

}
