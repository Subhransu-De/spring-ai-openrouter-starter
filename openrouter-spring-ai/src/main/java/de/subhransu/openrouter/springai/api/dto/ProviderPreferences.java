package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record ProviderPreferences(@JsonProperty("allow_fallbacks") Boolean allowFallbacks,
		@JsonProperty("require_parameters") Boolean requireParameters,
		@JsonProperty("data_collection") String dataCollection, List<String> order, List<String> ignore,
		List<String> quantizations, @JsonProperty("sort") String sort) {
}
