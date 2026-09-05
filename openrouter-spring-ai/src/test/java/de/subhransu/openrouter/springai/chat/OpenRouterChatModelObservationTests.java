package de.subhransu.openrouter.springai.chat;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.subhransu.openrouter.springai.api.OpenRouterApi;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionChunk;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionResponse;
import de.subhransu.openrouter.springai.api.dto.ChatMessage;
import de.subhransu.openrouter.springai.api.dto.Choice;
import de.subhransu.openrouter.springai.api.dto.Delta;
import de.subhransu.openrouter.springai.api.errors.OpenRouterApiException;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

/**
 * Micrometer observability contract: every {@code call()} and {@code stream()} is
 * recorded as a {@code gen_ai.client.operation} observation against the configured
 * {@link io.micrometer.observation.ObservationRegistry}, carrying the OpenRouter provider
 * tag, and stream errors are recorded on the observation instead of being dropped. This
 * is what makes ChatClient metrics and tracing work for this provider.
 */
class OpenRouterChatModelObservationTests {

	private static final String MODEL = "openai/gpt-5.4-mini";

	private static final String OBSERVATION_NAME = "gen_ai.client.operation";

	private static final String PROVIDER_KEY = "gen_ai.system";

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private OpenRouterChatModel model(OpenRouterApi api) {
		return OpenRouterChatModel.builder().openRouterApi(api).observationRegistry(this.observationRegistry).build();
	}

	private Prompt prompt() {
		return new Prompt(List.of(new UserMessage("hi")), OpenRouterChatOptions.builder().model(MODEL).build());
	}

	@Test
	void callRecordsAChatModelObservationWithProviderAndModel() {
		OpenRouterApi api = mock(OpenRouterApi.class);
		when(api.chatCompletion(any())).thenReturn(new ChatCompletionResponse("gen-1", "chat.completion", 123L, MODEL,
				"openai",
				List.of(new Choice(0, new ChatMessage("assistant", "Hello.", null, null, null), null, "stop", "stop")),
				null));

		model(api).call(prompt());

		assertThat(this.observationRegistry).hasObservationWithNameEqualTo(OBSERVATION_NAME)
			.that()
			.hasLowCardinalityKeyValue(PROVIDER_KEY, "openrouter")
			.hasLowCardinalityKeyValue("gen_ai.request.model", MODEL)
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void streamRecordsAnObservationStoppedAfterTheFluxCompletes() {
		OpenRouterApi api = mock(OpenRouterApi.class);
		when(api.chatCompletionStream(any()))
			.thenReturn(Flux.just(new ChatCompletionChunk("gen-1", "chat.completion.chunk", 123L, MODEL, "openai",
					List.of(new Choice(0, null, new Delta("assistant", "Hello.", null, null), "stop", "stop")), null,
					null)));

		model(api).stream(prompt()).collectList().block(Duration.ofSeconds(5));

		assertThat(this.observationRegistry).hasObservationWithNameEqualTo(OBSERVATION_NAME)
			.that()
			.hasLowCardinalityKeyValue(PROVIDER_KEY, "openrouter")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void streamErrorIsRecordedOnTheObservation() {
		OpenRouterApi api = mock(OpenRouterApi.class);
		when(api.chatCompletionStream(any())).thenReturn(Flux
			.error(new OpenRouterApiException("stream failed", HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":true}")));

		OpenRouterChatModel model = model(api);
		assertThatThrownBy(() -> model.stream(prompt()).collectList().block(Duration.ofSeconds(5)))
			.isInstanceOf(OpenRouterApiException.class);

		assertThat(this.observationRegistry).hasObservationWithNameEqualTo(OBSERVATION_NAME)
			.that()
			.hasBeenStarted()
			.hasBeenStopped()
			.thenError()
			.isInstanceOf(OpenRouterApiException.class);
	}

}
