package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ImagesStreamEvent(String type, @JsonProperty("partial_image_index") Integer partialImageIndex,
		@JsonProperty("b64_json") String b64Json, @JsonProperty("media_type") String mediaType, Long created,
		Usage usage, StreamError error) {

	public static final String PARTIAL_IMAGE = "image_generation.partial_image";

	public static final String COMPLETED = "image_generation.completed";

	public static final String ERROR_EVENT = "error";

}
