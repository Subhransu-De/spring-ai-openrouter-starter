package de.subhransu.openrouter.springai.autoconfigure;

import de.subhransu.openrouter.springai.chat.OpenRouterProviderPreferences;
import de.subhransu.openrouter.springai.embedding.OpenRouterEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(OpenRouterEmbeddingProperties.CONFIG_PREFIX)
public class OpenRouterEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openrouter.embedding";

	private String model;

	private Integer dimensions;

	private String encodingFormat;

	private String user;

	private OpenRouterProviderPreferences provider;

	public OpenRouterEmbeddingOptions toOptions() {
		return OpenRouterEmbeddingOptions.builder()
			.model(this.model)
			.dimensions(this.dimensions)
			.encodingFormat(this.encodingFormat)
			.user(this.user)
			.provider(this.provider)
			.build();
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public String getEncodingFormat() {
		return this.encodingFormat;
	}

	public void setEncodingFormat(String encodingFormat) {
		this.encodingFormat = encodingFormat;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public OpenRouterProviderPreferences getProvider() {
		return this.provider;
	}

	public void setProvider(OpenRouterProviderPreferences provider) {
		this.provider = provider;
	}

}
