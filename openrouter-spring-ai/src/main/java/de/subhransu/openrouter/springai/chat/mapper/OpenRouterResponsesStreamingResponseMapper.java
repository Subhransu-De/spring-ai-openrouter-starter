package de.subhransu.openrouter.springai.chat.mapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import de.subhransu.openrouter.springai.api.dto.ResponsesOutputItem;
import de.subhransu.openrouter.springai.api.dto.ResponsesResult;
import de.subhransu.openrouter.springai.api.dto.ResponsesStreamEvent;
import de.subhransu.openrouter.springai.api.dto.StreamError;
import de.subhransu.openrouter.springai.api.errors.OpenRouterApiExceptionFactory;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;

public final class OpenRouterResponsesStreamingResponseMapper {

	public ChatResponse map(ResponsesStreamEvent event) {
		String type = event.type();
		if ("error".equals(type) || type != null && type.endsWith(".error")) {
			StreamError error = eventError(event);
			throw OpenRouterApiExceptionFactory.create("OpenRouter responses stream failed", String.valueOf(event),
					error, event.errorType());
		}

		// Deltas are the single source of streamed text. Terminal events such as
		// response.output_text.done and response.output_item.done repeat the full text of
		// content already streamed as deltas, so emitting them again would duplicate
		// output.
		String text = "";
		String reasoning = null;
		String finishReason = null;
		ResponsesResult result = null;
		List<AssistantMessage.ToolCall> toolCalls = List.of();
		List<Media> media = List.of();
		if ("response.output_text.delta".equals(type)) {
			text = event.delta() != null ? event.delta() : "";
		}
		else if ("response.reasoning_text.delta".equals(type)) {
			reasoning = event.delta();
		}
		else if ("response.output_item.done".equals(type) && event.item() != null
				&& "function_call".equals(event.item().type())) {
			// Function-call arguments are not streamed as text deltas, so the completed
			// item is the single source for the tool call; emitting it here does not
			// duplicate output.
			ResponsesOutputItem item = event.item();
			toolCalls = List
				.of(new AssistantMessage.ToolCall(item.callId(), "function", item.name(), item.arguments()));
			finishReason = "tool_calls";
		}
		else if ("response.output_item.done".equals(type) && event.item() != null
				&& "image_generation_call".equals(event.item().type())) {
			// Image bytes are not streamed as text deltas, so the completed item is the
			// single source for the generated image; emitting it here does not duplicate
			// output.
			media = GeneratedImageMapper.responsesMedia(List.of(event.item()));
		}
		else if ("response.completed".equals(type)) {
			result = event.response();
			finishReason = result != null && result.status() != null ? result.status() : "completed";
		}
		else if ("response.incomplete".equals(type)) {
			result = event.response();
			finishReason = result != null && result.incompleteDetails() != null
					&& result.incompleteDetails().reason() != null ? result.incompleteDetails().reason() : "incomplete";
		}
		else if ("response.failed".equals(type)) {
			// A failed generation ends the stream over HTTP 200; converting it into an
			// empty finish chunk would hide the provider error from consumers.
			ResponsesResult failed = event.response();
			throw OpenRouterApiExceptionFactory.create("OpenRouter responses stream failed", String.valueOf(event),
					failed != null ? failed.error() : null, failed != null ? failed.errorType() : null);
		}

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content(text)
			.toolCalls(toolCalls)
			.media(media)
			.build();
		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
			.finishReason(FinishReasonMapper.map(finishReason))
			.metadata("openrouter.native_finish_reason", finishReason)
			.metadata("openrouter.reasoning", reasoning)
			.build();
		ChatResponseMetadata.Builder responseMetadata = ChatResponseMetadata.builder()
			.keyValue("openrouter.object", type);
		if (result != null) {
			responseMetadata.id(result.id()).model(result.model()).usage(UsageMapper.map(result.usage()));
		}
		return new ChatResponse(List.of(new Generation(assistantMessage, generationMetadata)),
				responseMetadata.build());
	}

	private StreamError eventError(ResponsesStreamEvent event) {
		StreamError nested = event.error();
		String code = nested != null && nested.code() != null ? nested.code() : event.code();
		String message = nested != null && nested.message() != null ? nested.message() : event.message();
		JsonNode metadata = mergeMetadata(event.metadata(), nested != null ? nested.metadata() : null);
		String errorType = nested != null ? nested.errorType() : null;
		return code != null || message != null || metadata != null || errorType != null
				? new StreamError(code, message, metadata, errorType) : null;
	}

	private JsonNode mergeMetadata(JsonNode root, JsonNode nested) {
		if (root == null) {
			return nested;
		}
		if (nested == null) {
			return root;
		}
		if (root.isObject() && nested.isObject()) {
			ObjectNode merged = (ObjectNode) nested.deepCopy();
			root.properties().forEach(entry -> merged.set(entry.getKey(), entry.getValue().deepCopy()));
			return merged;
		}
		return nested;
	}

}
