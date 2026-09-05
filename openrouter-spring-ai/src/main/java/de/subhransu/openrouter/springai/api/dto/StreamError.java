package de.subhransu.openrouter.springai.api.dto;

import tools.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record StreamError(String code, String message, JsonNode metadata,
		@JsonProperty("error_type") String errorType) {

	@JsonCreator
	public StreamError(@JsonProperty("code") String code, @JsonProperty("message") JsonNode message,
			@JsonProperty("metadata") JsonNode metadata, @JsonProperty("error_type") JsonNode errorType) {
		this(code, text(message), metadata, text(errorType));
	}

	public StreamError(String code, String message) {
		this(code, message, null, null);
	}

	private static String text(JsonNode value) {
		if (value == null || value.isNull()) {
			return null;
		}
		return value.isString() ? value.stringValue() : value.toString();
	}
}
