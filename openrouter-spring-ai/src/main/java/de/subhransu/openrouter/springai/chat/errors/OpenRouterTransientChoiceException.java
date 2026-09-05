package de.subhransu.openrouter.springai.chat.errors;

import org.springframework.ai.retry.TransientAiException;

/**
 * A choice-level OpenRouter failure that Spring AI's default retry policy may retry.
 *
 * @author Subhransu De
 */
public final class OpenRouterTransientChoiceException extends TransientAiException implements OpenRouterChoiceFailure {

	private final OpenRouterChoiceErrorDetails errorDetails;

	public OpenRouterTransientChoiceException(String message, OpenRouterChoiceErrorDetails errorDetails) {
		super(message);
		this.errorDetails = errorDetails;
	}

	@Override
	public OpenRouterChoiceErrorDetails getErrorDetails() {
		return this.errorDetails;
	}

}
