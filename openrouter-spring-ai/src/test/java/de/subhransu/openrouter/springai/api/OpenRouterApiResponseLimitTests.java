package de.subhransu.openrouter.springai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import de.subhransu.openrouter.springai.api.dto.ChatCompletionRequest;
import de.subhransu.openrouter.springai.api.dto.ChatMessage;
import de.subhransu.openrouter.springai.api.dto.EmbeddingsRequest;
import de.subhransu.openrouter.springai.api.dto.ImagesRequest;
import de.subhransu.openrouter.springai.api.dto.ResponsesRequest;
import de.subhransu.openrouter.springai.errors.OpenRouterLimitExceededException;
import de.subhransu.openrouter.springai.errors.OpenRouterNonTransientApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenRouterApiResponseLimitTests {

	private static final String BASE_URL = "https://openrouter.test/api/v1";

	private static final int LIMIT = 64;

	private Fixture fixture(int successLimit, int errorLimit) {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		OpenRouterApi api = OpenRouterApi.builder()
			.apiKey("test-key")
			.baseUrl(BASE_URL)
			.restClientBuilder(restClientBuilder)
			.maxResponseBodyBytes(successLimit)
			.maxErrorBodyBytes(errorLimit)
			.build();
		return new Fixture(api, server);
	}

	static Stream<Arguments> acceptedSuccessBoundaries() {
		return Stream.of(Arguments.of("just below", LIMIT - 1), Arguments.of("exact", LIMIT));
	}

	@ParameterizedTest(name = "{0} success body is accepted")
	@MethodSource("acceptedSuccessBoundaries")
	void acceptsSuccessBodiesThroughTheExactLimit(String boundary, int size) {
		Fixture fixture = fixture(LIMIT, LIMIT);
		String body = paddedJson("{}", size);
		fixture.server()
			.expect(once(), requestTo(BASE_URL + Endpoint.CHAT.path))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		assertThatCode(() -> Endpoint.CHAT.invoke(fixture.api())).doesNotThrowAnyException();
		fixture.server().verify();
	}

	@ParameterizedTest
	@EnumSource(Endpoint.class)
	void rejectsOversizedSuccessBodiesAcrossEveryBlockingEndpoint(Endpoint endpoint) {
		Fixture fixture = fixture(LIMIT, LIMIT);
		fixture.server()
			.expect(once(), requestTo(BASE_URL + endpoint.path))
			.andRespond(withSuccess(" ".repeat(LIMIT + 1), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> endpoint.invoke(fixture.api())).isInstanceOf(OpenRouterLimitExceededException.class)
			.satisfies(thrown -> {
				OpenRouterLimitExceededException exception = (OpenRouterLimitExceededException) thrown;
				assertThat(exception.getLimit())
					.isEqualTo(OpenRouterLimitExceededException.Limit.BLOCKING_RESPONSE_BODY_BYTES);
				assertThat(exception.getConfiguredLimit()).isEqualTo(LIMIT);
				assertThat(exception.getObservedValue()).isEqualTo(LIMIT + 1L);
				assertThat(exception.getEndpoint()).isEqualTo(endpoint.path);
				assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.OK);
			});
		fixture.server().verify();
	}

	static Stream<Arguments> acceptedErrorBoundaries() {
		return Stream.of(Arguments.of("just below", LIMIT - 1), Arguments.of("exact", LIMIT));
	}

	@ParameterizedTest(name = "{0} error body remains a normal HTTP failure")
	@MethodSource("acceptedErrorBoundaries")
	void acceptsErrorBodiesThroughTheExactLimit(String boundary, int size) {
		Fixture fixture = fixture(LIMIT, LIMIT);
		String body = paddedJson("{\"error\":{\"code\":\"bad\",\"message\":\"denied\"}}", size);
		fixture.server()
			.expect(once(), requestTo(BASE_URL + Endpoint.CHAT.path))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body));

		assertThatThrownBy(() -> Endpoint.CHAT.invoke(fixture.api()))
			.isInstanceOf(OpenRouterNonTransientApiException.class);
		fixture.server().verify();
	}

	@ParameterizedTest
	@EnumSource(Endpoint.class)
	void oversizedErrorsKeepOnlyABoundedExcerptAndStructuredDetails(Endpoint endpoint) {
		Fixture fixture = fixture(LIMIT, LIMIT);
		String body = "{\"error\":{\"code\":\"bad\",\"message\":\"denied\"},\"padding\":\"" + "x".repeat(100) + "\"}";
		fixture.server()
			.expect(once(), requestTo(BASE_URL + endpoint.path))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body));

		assertThatThrownBy(() -> endpoint.invoke(fixture.api())).isInstanceOf(OpenRouterLimitExceededException.class)
			.satisfies(thrown -> {
				OpenRouterLimitExceededException exception = (OpenRouterLimitExceededException) thrown;
				assertThat(exception.getLimit())
					.isEqualTo(OpenRouterLimitExceededException.Limit.BLOCKING_ERROR_BODY_BYTES);
				assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
				assertThat(exception.getEndpoint()).isEqualTo(endpoint.path);
				assertThat(exception.getResponseBody().getBytes(StandardCharsets.UTF_8)).hasSize(LIMIT);
				assertThat(exception.getErrorDetails().code()).isEqualTo("bad");
				assertThat(exception.getErrorDetails().message()).isEqualTo("denied");
			});
		fixture.server().verify();
	}

	private static String paddedJson(String json, int size) {
		if (json.length() > size) {
			throw new IllegalArgumentException("JSON fixture exceeds requested size");
		}
		return json + " ".repeat(size - json.length());
	}

	private static ChatCompletionRequest chatRequest() {
		return new ChatCompletionRequest("openai/gpt-5.4-mini", null,
				List.of(new ChatMessage("user", "hello", null, null, null)), null, null, null, null, null, null, null,
				null, null, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null,
				null, null);
	}

	private static ResponsesRequest responsesRequest() {
		return new ResponsesRequest("openai/gpt-5.4", null, "hello", null, 128, false, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null);
	}

	private enum Endpoint {

		CHAT("/chat/completions", api -> api.chatCompletion(chatRequest())),

		RESPONSES("/responses", api -> api.responses(responsesRequest())),

		EMBEDDINGS("/embeddings",
				api -> api.embeddings(new EmbeddingsRequest("openai/text-embedding-3-small", List.of("hello"), null,
						null, null, null))),

		IMAGES("/images", api -> api.images(new ImagesRequest("openai/gpt-image-1", "hello", null, null, null, null,
				null, null, null, null, null, null, null, null)));

		private final String path;

		private final Consumer<OpenRouterApi> invocation;

		Endpoint(String path, Consumer<OpenRouterApi> invocation) {
			this.path = path;
			this.invocation = invocation;
		}

		void invoke(OpenRouterApi api) {
			this.invocation.accept(api);
		}

	}

	private record Fixture(OpenRouterApi api, MockRestServiceServer server) {
	}

}
