package de.subhransu.openrouter.springai.errors;

/**
 * Stable application-facing categories for OpenRouter and upstream provider failures.
 *
 * <p>
 * The category intentionally groups OpenRouter's more detailed {@code error_type}
 * vocabulary into handling decisions that are useful to applications. The original error
 * type, code, message, metadata, status, and response body remain available on the
 * associated exception.
 *
 * @author Subhransu De
 */
public enum OpenRouterErrorCategory {

	AUTHENTICATION,

	AUTHORIZATION,

	BILLING_CREDITS,

	RATE_LIMIT,

	INVALID_REQUEST,

	UNSUPPORTED_PARAMETER,

	PROVIDER_UNAVAILABLE,

	CONTENT_FILTER_REFUSAL,

	TIMEOUT,

	UNKNOWN

}
