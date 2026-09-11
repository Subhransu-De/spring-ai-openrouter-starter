package de.subhransu.openrouter.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import de.subhransu.openrouter.springai.api.OpenRouterApi;
import de.subhransu.openrouter.springai.chat.OpenRouterUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenRouterEmbeddingModelTests {

	private static final String BASE_URL = "https://openrouter.test/api/v1";

	private static final String MODEL = "openai/text-embedding-3-small";

	private static final String SUCCESS_BODY = """
			{
			  "object": "list",
			  "data": [
			    {"object": "embedding", "index": 0, "embedding": [0.25, -0.5]},
			    {"object": "embedding", "index": 1, "embedding": [0.75, 1.0]}
			  ],
			  "model": "openai/text-embedding-3-small",
			  "usage": {"prompt_tokens": 4, "total_tokens": 4}
			}
			""";

	private Fixture fixture(OpenRouterEmbeddingOptions defaultOptions) {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		OpenRouterApi api = OpenRouterApi.builder()
			.apiKey("test-key")
			.baseUrl(BASE_URL)
			.restClientBuilder(restClientBuilder)
			.build();
		OpenRouterEmbeddingModel model = OpenRouterEmbeddingModel.builder()
			.openRouterApi(api)
			.defaultOptions(defaultOptions)
			.build();
		return new Fixture(model, server);
	}

	@Test
	void mapsEmbeddingsResponseIntoSpringAiTypes() {
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).build());
		fixture.server()
			.expect(once(), requestTo(BASE_URL + "/embeddings"))
			.andExpect(jsonPath("$.model").value(MODEL))
			.andExpect(jsonPath("$.input[0]").value("hello"))
			.andExpect(jsonPath("$.input[1]").value("world"))
			.andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

		EmbeddingResponse response = fixture.model()
			.call(new EmbeddingRequest(List.of("hello", "world"), OpenRouterEmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getOutput()).containsExactly(0.25f, -0.5f);
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(response.getMetadata().getModel()).isEqualTo(MODEL);
		assertThat(response.getMetadata().getUsage()).isInstanceOf(OpenRouterUsage.class);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(4);
		fixture.server().verify();
	}

	@Test
	void runtimeOptionsOverrideDefaults() {
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).dimensions(2).build());
		fixture.server()
			.expect(once(), requestTo(BASE_URL + "/embeddings"))
			.andExpect(jsonPath("$.model").value("qwen/qwen3-embedding-8b"))
			.andExpect(jsonPath("$.dimensions").value(2))
			.andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

		fixture.model()
			.call(new EmbeddingRequest(List.of("hello", "world"),
					OpenRouterEmbeddingOptions.builder().model("qwen/qwen3-embedding-8b").build()));

		fixture.server().verify();
	}

	@Test
	void embedsSingleTextAndDocumentThroughConvenienceMethods() {
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).build());
		fixture.server().expect(once(), requestTo(BASE_URL + "/embeddings")).andRespond(withSuccess("""
				{"data":[{"index":0,"embedding":[0.25,-0.5]}]}
				""", MediaType.APPLICATION_JSON));

		float[] embedding = fixture.model().embed(new Document("document text"));

		assertThat(embedding).containsExactly(0.25f, -0.5f);
		fixture.server().verify();
	}

	@Test
	void rejectsNonFloatEncodingFormatBeforeCallingTheApi() {
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).encodingFormat("base64").build());
		OpenRouterEmbeddingModel model = fixture.model();

		assertThatThrownBy(() -> model.embed("hello")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("encoding_format");
	}

	@ParameterizedTest
	@ValueSource(strings = { "[{\"index\":0,\"embedding\":[1,2]},{\"index\":1,\"embedding\":[3,4]}]",
			"[{\"index\":1,\"embedding\":[3,4]},{\"index\":0,\"embedding\":[1,2]}]" })
	void convenienceMethodReturnsVectorsInInputOrder(String data) {
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).build());
		fixture.server()
			.expect(once(), requestTo(BASE_URL + "/embeddings"))
			.andRespond(withSuccess("{\"data\":" + data + "}", MediaType.APPLICATION_JSON));

		List<float[]> vectors = fixture.model().embed(List.of("first", "second"));

		assertThat(vectors).hasSize(2);
		assertThat(vectors.get(0)).containsExactly(1, 2);
		assertThat(vectors.get(1)).containsExactly(3, 4);
		fixture.server().verify();
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "[]", "[{\"index\":0,\"embedding\":[1]}]",
			"[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[2]},{\"index\":2,\"embedding\":[3]}]",
			"[null,{\"index\":1,\"embedding\":[2]}]",
			"[{\"index\":null,\"embedding\":[1]},{\"index\":1,\"embedding\":[2]}]",
			"[{\"embedding\":[1]},{\"index\":1,\"embedding\":[2]}]",
			"[{\"index\":-1,\"embedding\":[1]},{\"index\":1,\"embedding\":[2]}]",
			"[{\"index\":2,\"embedding\":[1]},{\"index\":1,\"embedding\":[2]}]",
			"[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[2]}]",
			"[{\"index\":0,\"embedding\":null},{\"index\":1,\"embedding\":[2]}]",
			"[{\"index\":0,\"embedding\":[]},{\"index\":1,\"embedding\":[2]}]",
			"[{\"index\":0,\"embedding\":[1,2]},{\"index\":1,\"embedding\":[2]}]",
			"[{\"index\":0,\"embedding\":[1e100]},{\"index\":1,\"embedding\":[2]}]" })
	void rejectsMalformedResults(String data) {
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).build());
		fixture.server()
			.expect(once(), requestTo(BASE_URL + "/embeddings"))
			.andRespond(withSuccess("{\"data\":" + data + "}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> fixture.model().embed(List.of("first", "second")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Embedding response");
		fixture.server().verify();
	}

	@ParameterizedTest
	@CsvSource({ "2, , true", "3, , false", "3, 2, true", "2, 3, false" })
	void validatesMergedDimensions(int defaultDimensions, Integer runtimeDimensions, boolean valid) {
		Fixture fixture = fixture(
				OpenRouterEmbeddingOptions.builder().model(MODEL).dimensions(defaultDimensions).build());
		fixture.server()
			.expect(once(), requestTo(BASE_URL + "/embeddings"))
			.andExpect(
					jsonPath("$.dimensions").value(runtimeDimensions == null ? defaultDimensions : runtimeDimensions))
			.andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));
		EmbeddingRequest request = new EmbeddingRequest(List.of("first", "second"),
				OpenRouterEmbeddingOptions.builder().dimensions(runtimeDimensions).build());

		if (valid) {
			assertThat(fixture.model().call(request).getResults()).hasSize(2);
		}
		else {
			assertThatThrownBy(() -> fixture.model().call(request)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("dimensions");
		}
		fixture.server().verify();
	}

	private record Fixture(OpenRouterEmbeddingModel model, MockRestServiceServer server) {
	}

}
