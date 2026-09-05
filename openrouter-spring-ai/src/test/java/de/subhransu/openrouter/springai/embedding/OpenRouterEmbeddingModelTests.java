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
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).dimensions(128).build());
		fixture.server()
			.expect(once(), requestTo(BASE_URL + "/embeddings"))
			.andExpect(jsonPath("$.model").value("qwen/qwen3-embedding-8b"))
			.andExpect(jsonPath("$.dimensions").value(128))
			.andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

		fixture.model()
			.call(new EmbeddingRequest(List.of("hello"),
					OpenRouterEmbeddingOptions.builder().model("qwen/qwen3-embedding-8b").build()));

		fixture.server().verify();
	}

	@Test
	void embedsSingleTextAndDocumentThroughConvenienceMethods() {
		Fixture fixture = fixture(OpenRouterEmbeddingOptions.builder().model(MODEL).build());
		fixture.server()
			.expect(once(), requestTo(BASE_URL + "/embeddings"))
			.andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

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

	private record Fixture(OpenRouterEmbeddingModel model, MockRestServiceServer server) {
	}

}
