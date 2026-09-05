package de.subhransu.openrouter.springai.autoconfigure;

import de.subhransu.openrouter.springai.api.OpenRouterApi;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(OpenRouterCommonProperties.CONFIG_PREFIX)
public class OpenRouterCommonProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openrouter";

	private String apiKey;

	private String baseUrl = OpenRouterApi.DEFAULT_BASE_URL;

	private App app = new App();

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public App getApp() {
		return this.app;
	}

	public void setApp(App app) {
		this.app = app;
	}

	public static class App {

		private String httpReferer;

		private String title;

		private List<String> categories;

		public String getHttpReferer() {
			return this.httpReferer;
		}

		public void setHttpReferer(String httpReferer) {
			this.httpReferer = httpReferer;
		}

		public String getTitle() {
			return this.title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public List<String> getCategories() {
			return this.categories;
		}

		public void setCategories(List<String> categories) {
			this.categories = categories;
		}

	}

}
