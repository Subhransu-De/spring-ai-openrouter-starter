package de.subhransu.openrouter.springai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public record EmbeddingsResponse(String object, List<EmbeddingData> data, String model, Usage usage) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingData(String object, Integer index, float[] embedding) {

		// The generated record methods compare the float[] by identity; compare and
		// render its content instead.
		@Override
		public boolean equals(Object other) {
			return other instanceof EmbeddingData that && Objects.equals(this.object, that.object)
					&& Objects.equals(this.index, that.index) && Arrays.equals(this.embedding, that.embedding);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.object, this.index, Arrays.hashCode(this.embedding));
		}

		@Override
		public String toString() {
			return "EmbeddingData[object=" + this.object + ", index=" + this.index + ", embedding="
					+ Arrays.toString(this.embedding) + "]";
		}
	}
}
