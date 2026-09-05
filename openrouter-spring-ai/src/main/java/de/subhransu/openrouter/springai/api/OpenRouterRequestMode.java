package de.subhransu.openrouter.springai.api;

/**
 * OpenRouter wire protocol used for chat requests.
 *
 * @author Subhransu De
 */
public enum OpenRouterRequestMode {

	/**
	 * OpenAI-compatible Chat Completions. This is the production-supported default.
	 */
	OPENAI_CHAT_COMPLETIONS,

	/**
	 * OpenAI Responses. This is an experimental, explicitly selected compatibility mode.
	 */
	OPENAI_RESPONSES

}
