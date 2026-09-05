package de.subhransu.openrouter.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionRequest;
import de.subhransu.openrouter.springai.api.dto.ResponsesRequest;
import de.subhransu.openrouter.springai.chat.mapper.OpenRouterChatRequestMapper;
import de.subhransu.openrouter.springai.chat.mapper.OpenRouterResponsesRequestMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * JSON-boundary serialization tests. Every public OpenRouter chat option is mapped to a
 * request DTO and serialized through the configured {@link ObjectMapper}, then asserted
 * against the OpenRouter wire field name and value shape. Object-equality mapper tests
 * cannot catch a wrong {@code @JsonProperty} name, an omitted field, or a NON_EMPTY
 * regression -- these can. Spring property binding and provider response mapping are out
 * of scope (separate issues).
 */
class OpenRouterChatRequestSerializationTests {

	private static final String FALLBACK_MODEL = "anthropic/claude-3.5-sonnet";

	private static final String PARALLEL_TOOL_CALLS = "parallel_tool_calls";

	private static final String MAX_OUTPUT_TOKENS = "max_output_tokens";

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final OpenRouterChatRequestMapper chatMapper = new OpenRouterChatRequestMapper(this.objectMapper);

	private final OpenRouterResponsesRequestMapper responsesMapper = new OpenRouterResponsesRequestMapper(
			this.objectMapper);

	private JsonNode serializeChat(OpenRouterChatOptions options, List<Message> messages, List<ToolDefinition> tools) {
		ChatCompletionRequest request = this.chatMapper.map(messages, options, false, tools);
		return this.objectMapper.valueToTree(request);
	}

	private JsonNode serializeChat(OpenRouterChatOptions options) {
		return serializeChat(options, List.of(new UserMessage("hello")), List.of());
	}

	private JsonNode serializeResponses(OpenRouterChatOptions options, List<Message> messages,
			List<ToolDefinition> tools) {
		ResponsesRequest request = this.responsesMapper.map(messages, options, false, tools);
		return this.objectMapper.valueToTree(request);
	}

	private OpenRouterChatOptions.Builder base() {
		return OpenRouterChatOptions.builder().model("openai/gpt-5.4-mini");
	}

	private ToolDefinition weatherTool() {
		return ToolDefinition.builder().name("get_weather").description("Look up the weather").inputSchema("""
				{
				  "type": "object",
				  "properties": {
				    "city": {
				      "type": "string"
				    }
				  }
				}
				""").build();
	}

	// ---------------------------------------------------------------------
	// Core sampling and identity options
	// ---------------------------------------------------------------------

	@Test
	void serializesModelAndModelsFallbackList() {
		JsonNode json = serializeChat(base().models(List.of(FALLBACK_MODEL, "openai/gpt-5.4")).build());

		assertThat(json.path("model").stringValue()).isEqualTo("openai/gpt-5.4-mini");
		assertThat(json.path("models").isArray()).isTrue();
		assertThat(json.path("models").get(0).stringValue()).isEqualTo(FALLBACK_MODEL);
		assertThat(json.path("models").get(1).stringValue()).isEqualTo("openai/gpt-5.4");
	}

	@Test
	void serializesSamplingScalarsUnderSnakeCaseNames() {
		JsonNode json = serializeChat(base().temperature(0.7)
			.topP(0.9)
			.topK(40)
			.frequencyPenalty(0.1)
			.presencePenalty(0.2)
			.repetitionPenalty(1.1)
			.minP(0.05)
			.topA(0.8)
			.seed(42)
			.maxTokens(256)
			.maxCompletionTokens(512)
			.stopSequences(List.of("STOP", "END"))
			.user("user-7")
			.build());

		assertThat(json.path("temperature").asDouble()).isEqualTo(0.7);
		assertThat(json.path("top_p").asDouble()).isEqualTo(0.9);
		assertThat(json.path("top_k").asInt()).isEqualTo(40);
		assertThat(json.path("frequency_penalty").asDouble()).isEqualTo(0.1);
		assertThat(json.path("presence_penalty").asDouble()).isEqualTo(0.2);
		assertThat(json.path("repetition_penalty").asDouble()).isEqualTo(1.1);
		assertThat(json.path("min_p").asDouble()).isEqualTo(0.05);
		assertThat(json.path("top_a").asDouble()).isEqualTo(0.8);
		assertThat(json.path("seed").asInt()).isEqualTo(42);
		assertThat(json.path("max_tokens").asInt()).isEqualTo(256);
		assertThat(json.path("max_completion_tokens").asInt()).isEqualTo(512);
		assertThat(json.path("stop").get(0).stringValue()).isEqualTo("STOP");
		assertThat(json.path("user").stringValue()).isEqualTo("user-7");
		// Camel-case forms must never leak.
		assertThat(json.has("topP")).isFalse();
		assertThat(json.has("maxTokens")).isFalse();
		assertThat(json.has("frequencyPenalty")).isFalse();
	}

	@Test
	void serializesStreamFlagAndUsageInclude() {
		ChatCompletionRequest streamed = this.chatMapper.map(List.of(new UserMessage("hi")),
				base().includeUsage(true).build(), true, List.of());
		JsonNode json = this.objectMapper.valueToTree(streamed);

		assertThat(json.path("stream").asBoolean()).isTrue();
		assertThat(json.path("usage").path("include").asBoolean()).isTrue();
	}

	// ---------------------------------------------------------------------
	// Response format / structured output
	// ---------------------------------------------------------------------

	@Test
	void serializesExplicitResponseFormat() {
		JsonNode json = serializeChat(base().responseFormat(OpenRouterResponseFormat.jsonObject()).build());

		assertThat(json.path("response_format").path("type").stringValue()).isEqualTo("json_object");
	}

	@Test
	void serializesTypedJsonSchemaResponseFormatWithNameAndStrict() {
		String schema = """
				{
					"type": "object",
					"properties": {
						"answer": {
							"type": "string"
						}
					}
				}
				""";
		JsonNode json = serializeChat(
				base().responseFormat(OpenRouterResponseFormat.jsonSchema("weather", true, schema)).build());

		assertThat(json.path("response_format").path("type").stringValue()).isEqualTo("json_schema");
		assertThat(json.path("response_format").path("json_schema").path("name").stringValue()).isEqualTo("weather");
		assertThat(json.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
		assertThat(json.path("response_format").path("json_schema").path("schema").path("type").stringValue())
			.isEqualTo("object");
	}

	@Test
	void serializesOutputSchemaAsJsonSchemaResponseFormat() {
		String schema = """
				{
					"type": "object",
					"properties": {
						"answer": {
							"type": "string"
						}
					}
				}
				""";
		JsonNode json = serializeChat(base().outputSchema(schema).build());

		assertThat(json.path("response_format").path("type").stringValue()).isEqualTo("json_schema");
		assertThat(json.path("response_format").path("json_schema").path("name").stringValue()).isEqualTo("response");
		assertThat(json.path("response_format").path("json_schema").path("schema").path("type").stringValue())
			.isEqualTo("object");
	}

	@Test
	void explicitResponseFormatTakesPrecedenceOverOutputSchema() {
		JsonNode json = serializeChat(base().responseFormat(OpenRouterResponseFormat.jsonObject()).outputSchema("""
				{
				  "type": "object"
				}
				""").build());

		// When both are present, the explicit responseFormat wins (no json_schema
		// wrapper).
		assertThat(json.path("response_format").path("type").stringValue()).isEqualTo("json_object");
	}

	@Test
	void malformedOutputSchemaThrowsTypedExceptionWithUsefulMessage() {
		assertThatThrownBy(() -> serializeChat(base().outputSchema("{not valid json").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Invalid JSON schema");
	}

	@Test
	void malformedToolSchemaThrowsTypedException() {
		ToolDefinition badTool = ToolDefinition.builder()
			.name("broken")
			.description("broken schema")
			.inputSchema("{not valid json")
			.build();

		assertThatThrownBy(() -> serializeChat(base().build(), List.of(new UserMessage("hi")), List.of(badTool)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Invalid JSON schema");
	}

	// ---------------------------------------------------------------------
	// Tools
	// ---------------------------------------------------------------------

	@Test
	void serializesToolsToolChoiceAndParallelToolCalls() {
		JsonNode json = serializeChat(base().toolChoice(Map.of("type", "auto")).parallelToolCalls(true).build(),
				List.of(new UserMessage("hi")), List.of(weatherTool()));

		assertThat(json.path("tools").get(0).path("type").stringValue()).isEqualTo("function");
		assertThat(json.path("tools").get(0).path("function").path("name").stringValue()).isEqualTo("get_weather");
		assertThat(json.path("tools").get(0).path("function").path("parameters").path("type").stringValue())
			.isEqualTo("object");
		assertThat(json.path("tool_choice").path("type").stringValue()).isEqualTo("auto");
		assertThat(json.path(PARALLEL_TOOL_CALLS).asBoolean()).isTrue();
	}

	@Test
	void serializesStringFormToolChoiceVerbatim() {
		// tool_choice accepts either an object ({"type":"auto"}) or a bare string
		// ("none"/"auto"/"required"); the string form must not be wrapped or quoted
		// differently.
		JsonNode json = serializeChat(base().toolChoice("none").build());

		assertThat(json.path("tool_choice").isString()).isTrue();
		assertThat(json.path("tool_choice").stringValue()).isEqualTo("none");
	}

	@Test
	void parallelToolCallsFalseIsNotOmitted() {
		// NON_EMPTY would drop an empty collection, but a Boolean.FALSE is meaningful and
		// must survive serialization.
		JsonNode json = serializeChat(base().parallelToolCalls(false).build());

		assertThat(json.has(PARALLEL_TOOL_CALLS)).isTrue();
		assertThat(json.path(PARALLEL_TOOL_CALLS).asBoolean()).isFalse();
	}

	// ---------------------------------------------------------------------
	// Provider routing, reasoning, service tier, metadata, route
	// ---------------------------------------------------------------------

	@Test
	void serializesProviderRoutingPreferences() {
		OpenRouterProviderPreferences provider = new OpenRouterProviderPreferences(true, false, "deny",
				List.of("openai", "anthropic"), List.of("azure"), List.of("fp16", "int8"), "throughput");
		JsonNode json = serializeChat(base().provider(provider).build()).path("provider");

		assertThat(json.path("allow_fallbacks").asBoolean()).isTrue();
		assertThat(json.path("require_parameters").asBoolean()).isFalse();
		assertThat(json.path("data_collection").stringValue()).isEqualTo("deny");
		assertThat(json.path("order").get(0).stringValue()).isEqualTo("openai");
		assertThat(json.path("ignore").get(0).stringValue()).isEqualTo("azure");
		assertThat(json.path("quantizations").get(1).stringValue()).isEqualTo("int8");
		assertThat(json.path("sort").stringValue()).isEqualTo("throughput");
	}

	@Test
	void serializesReasoningEffortFields() {
		JsonNode json = serializeChat(
				base().reasoning(new OpenRouterReasoningOptions("high", null, false, true)).build())
			.path("reasoning");

		assertThat(json.path("effort").stringValue()).isEqualTo("high");
		assertThat(json.has("max_tokens")).isFalse();
		assertThat(json.path("exclude").asBoolean()).isFalse();
		assertThat(json.path("enabled").asBoolean()).isTrue();
	}

	@Test
	void serializesReasoningTokenBudgetWithoutEffort() {
		JsonNode json = serializeChat(base().reasoning(new OpenRouterReasoningOptions(null, 1024, false, true)).build())
			.path("reasoning");

		assertThat(json.path("max_tokens").asInt()).isEqualTo(1024);
		assertThat(json.has("effort")).isFalse();
	}

	@Test
	void serializesServiceTierAsLowercaseWireValue() {
		JsonNode json = serializeChat(base().serviceTier(OpenRouterServiceTier.FLEX).build());

		assertThat(json.path("service_tier").stringValue()).isEqualTo("flex");
	}

	@Test
	void serializesMetadataAndRoute() {
		JsonNode json = serializeChat(base().metadata(Map.of("trace", "abc")).route("fallback").build());

		assertThat(json.path("metadata").path("trace").stringValue()).isEqualTo("abc");
		assertThat(json.path("route").stringValue()).isEqualTo("fallback");
	}

	// ---------------------------------------------------------------------
	// Null omission
	// ---------------------------------------------------------------------

	@Test
	void omitsNullAndUnsetFields() {
		JsonNode json = serializeChat(base().build());

		assertThat(json.has("temperature")).isFalse();
		assertThat(json.has("tools")).isFalse();
		assertThat(json.has("provider")).isFalse();
		assertThat(json.has("reasoning")).isFalse();
		assertThat(json.has("response_format")).isFalse();
		assertThat(json.has("metadata")).isFalse();
		assertThat(json.has("usage")).isFalse();
		assertThat(json.has("service_tier")).isFalse();
	}

	@Test
	void omitsExplicitlyEmptyCollections() {
		// NON_EMPTY must drop empty lists/maps: OpenRouter rejects some empty arrays
		// (e.g. models) and an empty stop array is noise.
		JsonNode json = serializeChat(base().models(List.of()).stopSequences(List.of()).metadata(Map.of()).build());

		assertThat(json.has("models")).isFalse();
		assertThat(json.has("stop")).isFalse();
		assertThat(json.has("metadata")).isFalse();
	}

	@Test
	void omitsNullProviderPreferenceFields() {
		// ProviderPreferences serializes NON_NULL: a preferences object with only one
		// field set must not spray nulls over the provider block.
		OpenRouterProviderPreferences provider = new OpenRouterProviderPreferences(true, null, null, null, null, null,
				null);
		JsonNode json = serializeChat(base().provider(provider).build()).path("provider");

		assertThat(json.path("allow_fallbacks").asBoolean()).isTrue();
		assertThat(json.has("require_parameters")).isFalse();
		assertThat(json.has("data_collection")).isFalse();
		assertThat(json.has("order")).isFalse();
		assertThat(json.has("ignore")).isFalse();
		assertThat(json.has("quantizations")).isFalse();
		assertThat(json.has("sort")).isFalse();
	}

	// ---------------------------------------------------------------------
	// Message shapes (chat-completions)
	// ---------------------------------------------------------------------

	@Test
	void serializesUserSystemAssistantAndToolMessages() {
		AssistantMessage assistant = AssistantMessage.builder()
			.content("calling tool")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"X\"}")))
			.build();
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "\"sunny\"")))
			.build();
		List<Message> messages = List.of(new SystemMessage("be terse"), new UserMessage("weather?"), assistant,
				toolResponse);
		JsonNode json = serializeChat(base().build(), messages, List.of()).path("messages");

		assertThat(json).hasSize(4);
		assertThat(json.get(0).path("role").stringValue()).isEqualTo("system");
		assertThat(json.get(1).path("role").stringValue()).isEqualTo("user");
		assertThat(json.get(2).path("role").stringValue()).isEqualTo("assistant");
		assertThat(json.get(2).path("tool_calls").get(0).path("function").path("name").stringValue())
			.isEqualTo("get_weather");
		assertThat(json.get(3).path("role").stringValue()).isEqualTo("tool");
		assertThat(json.get(3).path("tool_call_id").stringValue()).isEqualTo("call-1");
	}

	@Test
	void serializesToolResponseMessageWithMultipleResponses() {
		ToolResponseMessage multi = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "a", "\"r1\""),
					new ToolResponseMessage.ToolResponse("call-2", "b", "\"r2\"")))
			.build();
		JsonNode messages = serializeChat(base().build(), List.of(new UserMessage("hi"), multi), List.of())
			.path("messages");

		// One tool message per response, in order.
		List<JsonNode> toolMessages = messages.findValues("tool_call_id");
		assertThat(toolMessages).extracting(JsonNode::stringValue).containsExactly("call-1", "call-2");
	}

	// ---------------------------------------------------------------------
	// Responses-mode option fields (wire names + camelCase guards)
	// ---------------------------------------------------------------------

	@Test
	void serializesResponsesOptionFieldsUnderSnakeCaseNames() {
		JsonNode json = serializeResponses(base().models(List.of(FALLBACK_MODEL))
			.temperature(0.7)
			.topP(0.9)
			.topK(40)
			.frequencyPenalty(0.1)
			.presencePenalty(0.2)
			.maxCompletionTokens(512)
			.metadata(Map.of("trace", "abc"))
			.provider(new OpenRouterProviderPreferences(true, false, "deny", List.of("openai"), List.of("azure"),
					List.of("fp16"), "throughput"))
			.reasoning(new OpenRouterReasoningOptions("high", null, false, true))
			.route("fallback")
			.serviceTier(OpenRouterServiceTier.PRIORITY)
			.user("user-7")
			.parallelToolCalls(true)
			.toolChoice(Map.of("type", "auto"))
			.build(), List.of(new UserMessage("hi")), List.of(weatherTool()));

		assertThat(json.path("model").stringValue()).isEqualTo("openai/gpt-5.4-mini");
		assertThat(json.path("models").get(0).stringValue()).isEqualTo(FALLBACK_MODEL);
		assertThat(json.path("temperature").asDouble()).isEqualTo(0.7);
		assertThat(json.path("top_p").asDouble()).isEqualTo(0.9);
		assertThat(json.path("top_k").asInt()).isEqualTo(40);
		assertThat(json.path("frequency_penalty").asDouble()).isEqualTo(0.1);
		assertThat(json.path("presence_penalty").asDouble()).isEqualTo(0.2);
		assertThat(json.path(MAX_OUTPUT_TOKENS).asInt()).isEqualTo(512);
		assertThat(json.path("metadata").path("trace").stringValue()).isEqualTo("abc");
		assertThat(json.path("provider").path("allow_fallbacks").asBoolean()).isTrue();
		assertThat(json.path("provider").path("data_collection").stringValue()).isEqualTo("deny");
		assertThat(json.path("reasoning").path("effort").stringValue()).isEqualTo("high");
		assertThat(json.path("reasoning").has("max_tokens")).isFalse();
		assertThat(json.path("route").stringValue()).isEqualTo("fallback");
		assertThat(json.path("service_tier").stringValue()).isEqualTo("priority");
		assertThat(json.path("user").stringValue()).isEqualTo("user-7");
		assertThat(json.path(PARALLEL_TOOL_CALLS).asBoolean()).isTrue();
		assertThat(json.path("tool_choice").path("type").stringValue()).isEqualTo("auto");
		assertThat(json.path("tools").get(0).path("type").stringValue()).isEqualTo("function");
		assertThat(json.path("tools").get(0).path("name").stringValue()).isEqualTo("get_weather");
		// camelCase forms must never leak onto the responses wire either
		assertThat(json.has("maxOutputTokens")).isFalse();
		assertThat(json.has("topP")).isFalse();
		assertThat(json.has("serviceTier")).isFalse();
		assertThat(json.has("parallelToolCalls")).isFalse();
		assertThat(json.has("toolChoice")).isFalse();
	}

	@Test
	void serializesResponsesReasoningTokenBudgetWithoutEffort() {
		JsonNode json = serializeResponses(
				base().reasoning(new OpenRouterReasoningOptions(null, 1024, false, true)).build(),
				List.of(new UserMessage("hi")), List.of());

		assertThat(json.path("reasoning").path("max_tokens").asInt()).isEqualTo(1024);
		assertThat(json.path("reasoning").has("effort")).isFalse();
	}

	@Test
	void responsesMaxOutputTokensPrefersMaxCompletionTokensOverMaxTokens() {
		JsonNode fallback = serializeResponses(base().maxTokens(256).build(), List.of(new UserMessage("hi")),
				List.of());
		JsonNode preferred = serializeResponses(base().maxTokens(256).maxCompletionTokens(512).build(),
				List.of(new UserMessage("hi")), List.of());

		assertThat(fallback.path(MAX_OUTPUT_TOKENS).asInt()).isEqualTo(256);
		assertThat(preferred.path(MAX_OUTPUT_TOKENS).asInt()).isEqualTo(512);
	}

	@Test
	void omitsNullAndUnsetResponsesFields() {
		JsonNode json = serializeResponses(base().build(), List.of(new UserMessage("hi")), List.of());

		assertThat(json.has("temperature")).isFalse();
		assertThat(json.has("tools")).isFalse();
		assertThat(json.has("provider")).isFalse();
		assertThat(json.has("reasoning")).isFalse();
		assertThat(json.has("metadata")).isFalse();
		assertThat(json.has("service_tier")).isFalse();
		assertThat(json.has(MAX_OUTPUT_TOKENS)).isFalse();
	}

	@Test
	void omitsOptionsThatHaveNoResponsesModeMapping() {
		JsonNode json = serializeResponses(base().responseFormat(OpenRouterResponseFormat.jsonObject())
			.outputSchema("{\"type\":\"object\"}")
			.stopSequences(List.of("STOP"))
			.seed(42)
			.repetitionPenalty(1.1)
			.minP(0.05)
			.topA(0.8)
			.includeUsage(true)
			.build(), List.of(new UserMessage("hi")), List.of());

		assertThat(json.has("response_format")).isFalse();
		assertThat(json.has("output_schema")).isFalse();
		assertThat(json.has("stop")).isFalse();
		assertThat(json.has("seed")).isFalse();
		assertThat(json.has("repetition_penalty")).isFalse();
		assertThat(json.has("min_p")).isFalse();
		assertThat(json.has("top_a")).isFalse();
		assertThat(json.has("include_usage")).isFalse();
		assertThat(json.has("stream_options")).isFalse();
	}

	// ---------------------------------------------------------------------
	// Responses-mode system message handling
	// ---------------------------------------------------------------------

	@Test
	void joinsMultipleSystemMessagesIntoResponsesInstructions() {
		JsonNode json = serializeResponses(base().build(),
				List.of(new SystemMessage("first"), new SystemMessage("second"), new UserMessage("hi")), List.of());

		assertThat(json.path("instructions").stringValue()).isEqualTo("first\nsecond");
	}

	@Test
	void filtersBlankSystemMessagesFromResponsesInstructions() {
		JsonNode json = serializeResponses(base().build(),
				List.of(new SystemMessage("keep"), new SystemMessage("   "), new UserMessage("hi")), List.of());

		assertThat(json.path("instructions").stringValue()).isEqualTo("keep");
	}

	@Test
	void serializesNonEnglishContent() {
		JsonNode json = serializeChat(base().build(), List.of(new UserMessage("こんにちは世界 🌍 Köln")), List.of());

		assertThat(json.path("messages").get(0).path("content").stringValue()).isEqualTo("こんにちは世界 🌍 Köln");
	}

}
