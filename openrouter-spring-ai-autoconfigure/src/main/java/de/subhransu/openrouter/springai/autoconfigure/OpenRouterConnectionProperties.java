package de.subhransu.openrouter.springai.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(OpenRouterConnectionProperties.CONFIG_PREFIX)
public class OpenRouterConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openrouter.connection";

	private Duration timeout = Duration.ofMinutes(2);

	private DataSize maxResponseBodySize = DataSize.ofMegabytes(64);

	private DataSize maxErrorBodySize = DataSize.ofKilobytes(64);

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	public DataSize getMaxResponseBodySize() {
		return this.maxResponseBodySize;
	}

	public void setMaxResponseBodySize(DataSize maxResponseBodySize) {
		this.maxResponseBodySize = maxResponseBodySize;
	}

	public DataSize getMaxErrorBodySize() {
		return this.maxErrorBodySize;
	}

	public void setMaxErrorBodySize(DataSize maxErrorBodySize) {
		this.maxErrorBodySize = maxErrorBodySize;
	}

}
