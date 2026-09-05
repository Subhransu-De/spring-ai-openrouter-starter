package de.subhransu.openrouter.springai.errors;

import tools.jackson.databind.JsonNode;

/**
 * Parsed details from OpenRouter's error envelope.
 *
 * @param code provider or HTTP-compatible error code
 * @param message bounded, single-line provider diagnostic excerpt
 * @param errorType OpenRouter's canonical {@code error_type}, when supplied
 * @param providerCode the upstream provider's original code, when supplied
 * @param metadata the complete provider metadata object, when supplied
 * @param category stable application-facing failure category
 * @author Subhransu De
 */
public record OpenRouterErrorDetails(String code, String message, String errorType, String providerCode,
		JsonNode metadata, OpenRouterErrorCategory category) {

	public OpenRouterErrorDetails {
		code = OpenRouterExceptionMessage.sanitize(code);
		message = OpenRouterExceptionMessage.sanitize(message);
		errorType = OpenRouterExceptionMessage.sanitize(errorType);
		providerCode = OpenRouterExceptionMessage.sanitize(providerCode);
		metadata = OpenRouterExceptionMessage.sanitizeMetadata(metadata, null);
		category = category != null ? category : OpenRouterErrorCategory.UNKNOWN;
	}

	public OpenRouterErrorDetails(String code, String message, String errorType, String providerCode,
			JsonNode metadata) {
		this(code, message, errorType, providerCode, metadata,
				OpenRouterErrorClassifier.category(errorType, code, message));
	}
}
