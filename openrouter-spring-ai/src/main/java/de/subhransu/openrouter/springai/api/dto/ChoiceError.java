package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An error embedded in an OpenRouter chat-completion choice, in either a blocking
 * response or a streamed chunk.
 *
 * @param code OpenRouter or upstream provider error code
 * @param message provider diagnostic message
 * @param metadata provider metadata, including {@code error_type} when supplied
 * @author Subhransu De
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ChoiceError(String code, String message, Map<String, Object> metadata) {

	public ChoiceError {
		metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
	}

}
