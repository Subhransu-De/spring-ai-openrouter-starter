package de.subhransu.openrouter.springai.chat;

public enum OpenRouterServiceTier {

	AUTO("auto"), DEFAULT("default"), FLEX("flex"), PRIORITY("priority");

	private final String value;

	OpenRouterServiceTier(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}

}
