package de.subhransu.openrouter.springai.chat.mapper;

import de.subhransu.openrouter.springai.api.dto.ChatCompletionResponse;
import de.subhransu.openrouter.springai.api.dto.Choice;
import de.subhransu.openrouter.springai.api.dto.ToolCall;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.util.CollectionUtils;

public final class OpenRouterChatResponseMapper {

	private final OpenRouterChoiceErrorExceptionFactory choiceErrorExceptionFactory = new OpenRouterChoiceErrorExceptionFactory();

	public ChatResponse map(ChatCompletionResponse response) {
		throwIfChoiceFailed(response);
		List<Generation> generations = CollectionUtils.isEmpty(response.choices()) ? List.of()
				: response.choices().stream().map(choice -> mapGeneration(choice, response.model())).toList();
		return new ChatResponse(generations, mapMetadata(response));
	}

	private void throwIfChoiceFailed(ChatCompletionResponse response) {
		if (CollectionUtils.isEmpty(response.choices())) {
			return;
		}
		for (Choice choice : response.choices()) {
			if (choice != null && OpenRouterChoiceErrorExceptionFactory.isFailure(choice)) {
				throw this.choiceErrorExceptionFactory.create(response, choice);
			}
		}
	}

	private Generation mapGeneration(Choice choice, String model) {
		AssistantContentMapper.MappedContent content = AssistantContentMapper
			.map(choice.message() != null ? choice.message().content() : null);
		List<Media> media = new ArrayList<>(content.media());
		media.addAll(GeneratedImageMapper.media(choice.message() != null ? choice.message().images() : null));
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content(content.text())
			.toolCalls(mapToolCalls(choice.message() != null ? choice.message().toolCalls() : null))
			.media(media)
			.build();

		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
			.finishReason(FinishReasonMapper.map(choice.finishReason()))
			.metadata("openrouter.model", model)
			.metadata("openrouter.native_finish_reason", choice.nativeFinishReason())
			.build();
		return new Generation(assistantMessage, metadata);
	}

	private List<AssistantMessage.ToolCall> mapToolCalls(List<ToolCall> toolCalls) {
		if (toolCalls == null || toolCalls.isEmpty()) {
			return List.of();
		}
		return toolCalls.stream()
			.filter(toolCall -> toolCall != null)
			.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), toolCall.type(),
					toolCall.function() != null ? toolCall.function().name() : null,
					toolCall.function() != null ? toolCall.function().arguments() : null))
			.toList();
	}

	private ChatResponseMetadata mapMetadata(ChatCompletionResponse response) {
		ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
			.id(response.id())
			.model(response.model())
			.usage(UsageMapper.map(response.usage()));
		builder.keyValue("openrouter.provider", response.provider());
		builder.keyValue("openrouter.object", response.object());
		builder.keyValue("openrouter.created", response.created());
		return builder.build();
	}

}
