package de.subhransu.openrouter.springai.chat.mapper;

import de.subhransu.openrouter.springai.api.dto.ResponsesContent;
import de.subhransu.openrouter.springai.api.dto.ResponsesOutputItem;
import de.subhransu.openrouter.springai.api.dto.ResponsesResult;
import de.subhransu.openrouter.springai.api.errors.OpenRouterApiExceptionFactory;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public final class OpenRouterResponsesResponseMapper {

	public ChatResponse map(ResponsesResult response) {
		if ("failed".equals(response.status())) {
			// Failed generations arrive with HTTP 200; mapping them to an empty message
			// would make a provider failure look like a valid empty answer.
			throw OpenRouterApiExceptionFactory.create("OpenRouter responses request failed",
					response.error() != null ? response.error().toString() : response.status(), response.error(),
					response.errorType());
		}
		List<AssistantMessage.ToolCall> toolCalls = toolCalls(response);
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content(text(response))
			.toolCalls(toolCalls)
			.media(GeneratedImageMapper.responsesMedia(response.output()))
			.build();
		String finishReason = toolCalls.isEmpty() ? FinishReasonMapper.map(response.status()) : "TOOL_CALLS";
		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
			.finishReason(finishReason)
			.metadata("openrouter.native_finish_reason", response.status())
			.build();
		ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
			.id(response.id())
			.model(response.model())
			.usage(UsageMapper.map(response.usage()))
			.keyValue("openrouter.object", response.object())
			.keyValue("openrouter.created", response.createdAt())
			.build();
		return new ChatResponse(List.of(new Generation(assistantMessage, generationMetadata)), responseMetadata);
	}

	private List<AssistantMessage.ToolCall> toolCalls(ResponsesResult response) {
		if (CollectionUtils.isEmpty(response.output())) {
			return List.of();
		}
		return response.output()
			.stream()
			.filter(item -> "function_call".equals(item.type()))
			.map(item -> new AssistantMessage.ToolCall(item.callId(), "function", item.name(), item.arguments()))
			.toList();
	}

	private String text(ResponsesResult response) {
		if (CollectionUtils.isEmpty(response.output())) {
			return "";
		}
		return response.output()
			.stream()
			.filter(item -> "message".equals(item.type()))
			.map(this::text)
			.filter(StringUtils::hasText)
			.reduce("", String::concat);
	}

	private String text(ResponsesOutputItem item) {
		if (CollectionUtils.isEmpty(item.content())) {
			return "";
		}
		return item.content()
			.stream()
			.filter(content -> "output_text".equals(content.type()) || "text".equals(content.type()))
			.map(ResponsesContent::text)
			.filter(StringUtils::hasText)
			.reduce("", String::concat);
	}

}
