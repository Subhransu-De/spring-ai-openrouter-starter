package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ResponsesOutputItem(String id, String type, String status, String role, List<ResponsesContent> content,
		@JsonProperty("call_id") String callId, String name, String arguments, String result) {

	public ResponsesOutputItem(String id, String type, String status, String role, List<ResponsesContent> content) {
		this(id, type, status, role, content, null, null, null, null);
	}
}
