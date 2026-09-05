package de.subhransu.openrouter.springai.chat;

import java.util.List;

public record OpenRouterProviderPreferences(Boolean allowFallbacks, Boolean requireParameters, String dataCollection,
		List<String> order, List<String> ignore, List<String> quantizations, String sort) {
}
