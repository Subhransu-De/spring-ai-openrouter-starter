package de.subhransu.openrouter.springai.errors;

import org.springframework.http.HttpStatusCode;

/**
 * Common inspectable contract implemented by retryable and non-retryable OpenRouter HTTP
 * failures.
 *
 * @author Subhransu De
 */
public interface OpenRouterHttpException {

	String getMessage();

	HttpStatusCode getStatusCode();

	/**
	 * Return a bounded, single-line, credential-safe excerpt of the untrusted provider
	 * response body.
	 * @return provider diagnostic excerpt, or {@code null}
	 */
	String getResponseBody();

	OpenRouterErrorDetails getErrorDetails();

	default OpenRouterErrorCategory getCategory() {
		OpenRouterErrorDetails details = getErrorDetails();
		return details != null ? details.category() : OpenRouterErrorCategory.UNKNOWN;
	}

	OpenRouterRetryAfter getRetryAfter();

	String getEndpoint();

}
