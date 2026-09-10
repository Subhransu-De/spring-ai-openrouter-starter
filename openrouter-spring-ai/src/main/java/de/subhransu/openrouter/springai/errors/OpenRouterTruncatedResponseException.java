package de.subhransu.openrouter.springai.errors;

import org.springframework.ai.retry.NonTransientAiException;

/**
 * A response ended before protocol completion. Not retryable because earlier tool calls
 * may already have caused side effects.
 *
 * @author Subhransu De
 */
public final class OpenRouterTruncatedResponseException extends NonTransientAiException {

	public OpenRouterTruncatedResponseException(String message) {
		super(message);
	}

}
