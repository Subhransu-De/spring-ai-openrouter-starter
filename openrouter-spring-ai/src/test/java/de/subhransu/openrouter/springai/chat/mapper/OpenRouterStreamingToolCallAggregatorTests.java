package de.subhransu.openrouter.springai.chat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.subhransu.openrouter.springai.api.dto.ChatCompletionChunk;
import de.subhransu.openrouter.springai.api.dto.Choice;
import de.subhransu.openrouter.springai.api.dto.ChoiceError;
import de.subhransu.openrouter.springai.api.dto.Delta;
import de.subhransu.openrouter.springai.api.dto.FunctionCall;
import de.subhransu.openrouter.springai.api.dto.StreamError;
import de.subhransu.openrouter.springai.api.dto.ToolCall;
import de.subhransu.openrouter.springai.api.dto.Usage;
import de.subhransu.openrouter.springai.chat.errors.OpenRouterTransientChoiceException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class OpenRouterStreamingToolCallAggregatorTests {

	private static final String MODEL = "openai/gpt-5.4-mini";

	private final OpenRouterStreamingToolCallAggregator aggregator = new OpenRouterStreamingToolCallAggregator();

	@Test
	void interleavedChoicesCompleteIndependentlyAndKeepIndexedOrdering() {
		List<ChatCompletionChunk> aggregated = this.aggregator
			.aggregate(Flux.just(chunk(textChoice(1, "one "), textChoice(0, "zero ")),
					chunk(toolFragment(0, 0, "call-0", "weather", "{\"city\":")),
					chunk(toolFragment(1, 1, "call-1b", "time", "{\"zone\":")),
					chunk(toolFragment(1, 0, "call-1a", "weather", "{\"city\":")),
					chunk(toolFragment(0, 0, null, null, "\"Berlin\"}")), chunk(finishChoice(0)),
					chunk(toolFragment(1, 1, null, null, "\"UTC\"}")),
					chunk(toolFragment(1, 0, null, null, "\"Paris\"}")), chunk(finishChoice(1))))
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(aggregated).hasSize(3);
		assertThat(aggregated.get(0).choices()).extracting(Choice::index).containsExactly(0, 1);
		assertThat(aggregated.get(0).choices()).extracting(choice -> choice.delta().content())
			.containsExactly("zero ", "one ");

		Choice choice0 = aggregated.get(1).choices().get(0);
		assertThat(choice0.index()).isZero();
		assertThat(choice0.delta().toolCalls().get(0).function().arguments()).isEqualTo("{\"city\":\"Berlin\"}");

		Choice choice1 = aggregated.get(2).choices().get(0);
		assertThat(choice1.index()).isEqualTo(1);
		assertThat(choice1.delta().toolCalls()).extracting(ToolCall::index).containsExactly(0, 1);
		assertThat(choice1.delta().toolCalls()).extracting(call -> call.function().arguments())
			.containsExactly("{\"city\":\"Paris\"}", "{\"zone\":\"UTC\"}");

		OpenRouterStreamingResponseMapper mapper = new OpenRouterStreamingResponseMapper();
		assertThat(aggregated.stream()
			.map(mapper::map)
			.map(ChatResponse::getResults)
			.map(generations -> generations.stream()
				.map(generation -> generation.getMetadata().<Integer>get("openrouter.choice_index"))
				.toList()))
			.containsExactly(List.of(0, 1), List.of(0), List.of(1));
	}

	@Test
	void inStreamErrorClearsEveryBufferedChoice() {
		ChatCompletionChunk error = new ChatCompletionChunk("gen-1", "chat.completion.chunk", 123L, MODEL, "openai",
				List.of(), null, new StreamError("upstream_error", "failed"));

		List<ChatCompletionChunk> aggregated = this.aggregator
			.aggregate(Flux.just(chunk(toolFragment(0, 0, "stale-0", "weather", "stale-0")),
					chunk(toolFragment(1, 0, "stale-1", "weather", "stale-1")), error,
					chunk(toolFragment(0, 0, "fresh", "weather", "fresh")), chunk(finishChoice(0))))
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(aggregated).hasSize(2);
		assertThat(aggregated.get(0)).isSameAs(error);
		assertThat(aggregated.get(1).choices().get(0).delta().toolCalls().get(0).function().arguments())
			.isEqualTo("fresh");
	}

	@Test
	void reactiveErrorDiscardsIncompleteChoiceBuffers() {
		RuntimeException failure = new RuntimeException("stream failed");

		StepVerifier
			.create(this.aggregator.aggregate(Flux
				.concat(Flux.just(chunk(toolFragment(0, 0, "stale", "weather", "stale"))), Flux.error(failure))))
			.expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void trailingUsageWaitsForIncompleteChoicesToFlushOnCompletion() {
		ChatCompletionChunk usage = new ChatCompletionChunk("gen-1", "chat.completion.chunk", 123L, MODEL, "openai",
				List.of(), new Usage(10, 5, 15, null, null, null, null, null, null), null);

		List<ChatCompletionChunk> aggregated = this.aggregator
			.aggregate(Flux.just(chunk(toolFragment(0, 0, "call-0", "weather", "{}")), usage))
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(aggregated).hasSize(1);
		assertThat(aggregated.get(0).choices()).hasSize(1);
		assertThat(aggregated.get(0).choices().get(0).delta().toolCalls()).hasSize(1);
		assertThat(aggregated.get(0).usage().totalTokens()).isEqualTo(15);
	}

	@Test
	void cancellationReleasesStateBeforeAnotherSubscription() {
		AtomicInteger subscriptions = new AtomicInteger();
		AtomicBoolean firstSubscriptionCancelled = new AtomicBoolean();
		Flux<ChatCompletionChunk> source = Flux.defer(() -> {
			if (subscriptions.incrementAndGet() == 1) {
				return Flux.just(chunk(toolFragment(0, 0, "stale", "weather", "stale")))
					.concatWith(Flux.never())
					.doOnCancel(() -> firstSubscriptionCancelled.set(true));
			}
			return Flux.just(chunk(toolFragment(0, 0, "fresh", "weather", "fresh")), chunk(finishChoice(0)));
		});
		Flux<ChatCompletionChunk> aggregated = this.aggregator.aggregate(source);

		StepVerifier.create(aggregated).thenAwait(Duration.ofMillis(10)).thenCancel().verify(Duration.ofSeconds(5));
		assertThat(firstSubscriptionCancelled).isTrue();

		StepVerifier.create(aggregated).assertNext(chunk -> {
			String arguments = chunk.choices().get(0).delta().toolCalls().get(0).function().arguments();
			assertThat(arguments).isEqualTo("fresh");
		}).verifyComplete();
	}

	private ChatCompletionChunk chunk(Choice... choices) {
		return new ChatCompletionChunk("gen-1", "chat.completion.chunk", 123L, MODEL, "openai", List.of(choices), null,
				null);
	}

	private Choice textChoice(int choiceIndex, String text) {
		return new Choice(choiceIndex, null, new Delta("assistant", text, null, null), null, null);
	}

	private Choice toolFragment(int choiceIndex, int toolIndex, String id, String name, String arguments) {
		ToolCall toolCall = new ToolCall(id, id != null ? "function" : null, new FunctionCall(name, arguments),
				toolIndex);
		return new Choice(choiceIndex, null, new Delta(id != null ? "assistant" : null, null, null, List.of(toolCall)),
				null, null);
	}

	private Choice finishChoice(int choiceIndex) {
		return new Choice(choiceIndex, null, new Delta(null, null, null, null), "tool_calls", "tool_calls");
	}

	@Test
	void preservesChoiceErrorAndPartialOutputWhileToolCallChunksAreBuffered() {
		Choice toolCall = new Choice(0, null, new Delta("assistant", "partial ", null,
				List.of(new ToolCall("call-1", "function", new FunctionCall("lookup", "{"), 0))), null, null);
		ChoiceError error = new ChoiceError("503", "Provider failed during tool call",
				Map.of("error_type", "provider_unavailable"));
		// Deliberately omit finish_reason: the embedded error itself must close the
		// aggregation buffer.
		Choice failed = new Choice(0, null, new Delta(null, "output", null, null), null, null, error);
		ChatCompletionChunk first = chunk(toolCall);
		ChatCompletionChunk terminal = chunk(failed);
		OpenRouterStreamingResponseMapper mapper = new OpenRouterStreamingResponseMapper();

		StepVerifier
			.create(mapper.map(new OpenRouterStreamingToolCallAggregator().aggregate(Flux.just(first, terminal))))
			.expectErrorSatisfies(thrown -> {
				assertThat(thrown).isInstanceOf(OpenRouterTransientChoiceException.class);
				OpenRouterTransientChoiceException exception = (OpenRouterTransientChoiceException) thrown;
				assertThat(exception.getErrorDetails().partialOutput()).isEqualTo("partial output");
			})
			.verify();
	}

}
