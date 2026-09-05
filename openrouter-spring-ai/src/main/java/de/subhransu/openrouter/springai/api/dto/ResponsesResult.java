package de.subhransu.openrouter.springai.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.subhransu.openrouter.springai.errors.TolerantJsonStringDeserializer;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ResponsesResult(String id, String object, @JsonProperty("created_at") Long createdAt, String model,
		String status, List<ResponsesOutputItem> output, Usage usage, StreamError error,
		@JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
		@JsonDeserialize(using = TolerantJsonStringDeserializer.class) @JsonProperty("error_type") String errorType) {

	public ResponsesResult(String id, String object, Long createdAt, String model, String status,
			List<ResponsesOutputItem> output, Usage usage, StreamError error, IncompleteDetails incompleteDetails) {
		this(id, object, createdAt, model, status, output, usage, error, incompleteDetails, null);
	}

	public ResponsesResult(String id, String object, Long createdAt, String model, String status,
			List<ResponsesOutputItem> output, Usage usage, StreamError error) {
		this(id, object, createdAt, model, status, output, usage, error, null, null);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(Include.NON_NULL)
	public record IncompleteDetails(String reason) {
	}
}
