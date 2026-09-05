package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ImagesRequest(String model, String prompt, Integer n, String size, String resolution,
		@JsonProperty("aspect_ratio") String aspectRatio, String quality,
		@JsonProperty("output_format") String outputFormat, String background,
		@JsonProperty("output_compression") Integer outputCompression, Integer seed, Boolean stream,
		@JsonProperty("input_references") List<ContentPart> inputReferences, Map<String, Object> provider) {
}
