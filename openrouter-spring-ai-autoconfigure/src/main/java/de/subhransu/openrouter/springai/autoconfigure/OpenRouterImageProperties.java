package de.subhransu.openrouter.springai.autoconfigure;

import de.subhransu.openrouter.springai.image.OpenRouterImageOptions;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(OpenRouterImageProperties.CONFIG_PREFIX)
public class OpenRouterImageProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openrouter.image";

	private String model;

	private Integer n;

	private Integer width;

	private Integer height;

	private String resolution;

	private String aspectRatio;

	private String quality;

	private String outputFormat;

	private String background;

	private Integer outputCompression;

	private Integer seed;

	private List<String> inputReferences;

	private Map<String, Object> providerOptions;

	public OpenRouterImageOptions toOptions() {
		return OpenRouterImageOptions.builder()
			.model(this.model)
			.n(this.n)
			.width(this.width)
			.height(this.height)
			.resolution(this.resolution)
			.aspectRatio(this.aspectRatio)
			.quality(this.quality)
			.outputFormat(this.outputFormat)
			.background(this.background)
			.outputCompression(this.outputCompression)
			.seed(this.seed)
			.inputReferences(this.inputReferences)
			.providerOptions(this.providerOptions)
			.build();
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	public Integer getWidth() {
		return this.width;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}

	public Integer getHeight() {
		return this.height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public String getResolution() {
		return this.resolution;
	}

	public void setResolution(String resolution) {
		this.resolution = resolution;
	}

	public String getAspectRatio() {
		return this.aspectRatio;
	}

	public void setAspectRatio(String aspectRatio) {
		this.aspectRatio = aspectRatio;
	}

	public String getQuality() {
		return this.quality;
	}

	public void setQuality(String quality) {
		this.quality = quality;
	}

	public String getOutputFormat() {
		return this.outputFormat;
	}

	public void setOutputFormat(String outputFormat) {
		this.outputFormat = outputFormat;
	}

	public String getBackground() {
		return this.background;
	}

	public void setBackground(String background) {
		this.background = background;
	}

	public Integer getOutputCompression() {
		return this.outputCompression;
	}

	public void setOutputCompression(Integer outputCompression) {
		this.outputCompression = outputCompression;
	}

	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	public List<String> getInputReferences() {
		return this.inputReferences;
	}

	public void setInputReferences(List<String> inputReferences) {
		this.inputReferences = inputReferences;
	}

	public Map<String, Object> getProviderOptions() {
		return this.providerOptions;
	}

	public void setProviderOptions(Map<String, Object> providerOptions) {
		this.providerOptions = providerOptions;
	}

}
