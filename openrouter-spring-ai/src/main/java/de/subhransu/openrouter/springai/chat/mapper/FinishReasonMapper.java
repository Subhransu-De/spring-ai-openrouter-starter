package de.subhransu.openrouter.springai.chat.mapper;

import org.springframework.util.StringUtils;

final class FinishReasonMapper {

	private FinishReasonMapper() {
	}

	static String map(String finishReason) {
		if (!StringUtils.hasText(finishReason)) {
			return finishReason;
		}
		return switch (finishReason) {
			case "stop" -> "STOP";
			case "completed" -> "STOP";
			case "length" -> "LENGTH";
			case "max_output_tokens" -> "LENGTH";
			case "tool_calls" -> "TOOL_CALLS";
			// Legacy OpenAI name still emitted by some providers routed through
			// OpenRouter.
			case "function_call" -> "TOOL_CALLS";
			case "content_filter" -> "CONTENT_FILTER";
			default -> finishReason;
		};
	}

}
