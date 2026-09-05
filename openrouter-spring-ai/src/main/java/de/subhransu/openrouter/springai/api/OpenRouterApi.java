package de.subhransu.openrouter.springai.api;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionChunk;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionRequest;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionResponse;
import de.subhransu.openrouter.springai.api.dto.EmbeddingsRequest;
import de.subhransu.openrouter.springai.api.dto.EmbeddingsResponse;
import de.subhransu.openrouter.springai.api.dto.ImagesRequest;
import de.subhransu.openrouter.springai.api.dto.ImagesResponse;
import de.subhransu.openrouter.springai.api.dto.ImagesStreamEvent;
import de.subhransu.openrouter.springai.api.dto.ResponsesRequest;
import de.subhransu.openrouter.springai.api.dto.ResponsesResult;
import de.subhransu.openrouter.springai.api.dto.ResponsesStreamEvent;
import de.subhransu.openrouter.springai.errors.OpenRouterHttpExceptionFactory;
import de.subhransu.openrouter.springai.errors.OpenRouterLimitExceededException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

public class OpenRouterApi {

	public static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

	public static final int DEFAULT_MAX_RESPONSE_BODY_BYTES = 64 * 1024 * 1024;

	public static final int DEFAULT_MAX_ERROR_BODY_BYTES = 64 * 1024;

	private static final ParameterizedTypeReference<ServerSentEvent<String>> STRING_SSE_TYPE = new ParameterizedTypeReference<>() {
	};

	// Streamed image events deliver a whole base64-encoded image in a single SSE data
	// line, which far exceeds WebClient's 256 KB default codec limit (found live by the
	// Garage paint bay as a DataBufferLimitException). Chat deltas are tiny, so one
	// generous limit covers both streaming surfaces.
	private static final int SSE_MAX_IN_MEMORY_SIZE = 32 * 1024 * 1024;

	private final RestClient restClient;

	private final WebClient webClient;

	private final ObjectMapper objectMapper;

	private final OpenRouterHttpExceptionFactory httpExceptionFactory;

	private final Duration timeout;

	private final int maxResponseBodyBytes;

	private final int maxErrorBodyBytes;

	private OpenRouterApi(Builder builder) {
		Assert.hasText(builder.apiKey, "OpenRouter API key must not be empty");
		Assert.notNull(builder.objectMapper, "ObjectMapper must not be null");

		String baseUrl = StringUtils.hasText(builder.baseUrl) ? builder.baseUrl : DEFAULT_BASE_URL;
		this.objectMapper = builder.objectMapper;
		this.httpExceptionFactory = new OpenRouterHttpExceptionFactory(this.objectMapper, builder.apiKey);
		this.timeout = builder.timeout;
		Assert.isTrue(builder.maxResponseBodyBytes > 0,
				"Maximum blocking response body size must be greater than zero");
		Assert.isTrue(builder.maxErrorBodyBytes > 0, "Maximum blocking error body size must be greater than zero");
		Assert.isTrue(builder.maxResponseBodyBytes < Integer.MAX_VALUE,
				"Maximum blocking response body size must be less than Integer.MAX_VALUE");
		Assert.isTrue(builder.maxErrorBodyBytes < Integer.MAX_VALUE,
				"Maximum blocking error body size must be less than Integer.MAX_VALUE");
		this.maxResponseBodyBytes = builder.maxResponseBodyBytes;
		this.maxErrorBodyBytes = builder.maxErrorBodyBytes;

		// The RestClient's connect/read timeout is a transport concern carried by its
		// request factory.
		// The factory varies by classpath (Apache/Jetty/Reactor/JDK), so the
		// auto-configuration builds
		// a timeout-configured factory onto the supplied builder rather than this
		// transport-agnostic
		// core. The timeout field here drives only the streaming WebClient guard (see
		// applyTimeout).
		RestClient.Builder restClientBuilder = builder.restClientBuilder != null ? builder.restClientBuilder
				: RestClient.builder();
		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + builder.apiKey)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.requestInterceptor(new OpenRouterAttributionInterceptor(builder.httpReferer, builder.applicationTitle,
					builder.applicationCategories))
			.build();

		WebClient.Builder webClientBuilder = builder.webClientBuilder != null ? builder.webClientBuilder
				: WebClient.builder();
		this.webClient = webClientBuilder.baseUrl(baseUrl)
			.codecs((codecs) -> codecs.defaultCodecs().maxInMemorySize(SSE_MAX_IN_MEMORY_SIZE))
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + builder.apiKey)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
			.defaultHeaders(headers -> {
				addIfPresent(headers, "HTTP-Referer", builder.httpReferer);
				addIfPresent(headers, "X-OpenRouter-Title", builder.applicationTitle);
				addIfPresent(headers, "X-OpenRouter-Categories", builder.applicationCategories);
			})
			.build();
	}

	private static void addIfPresent(HttpHeaders headers, String name, String value) {
		if (StringUtils.hasText(value)) {
			headers.set(name, value);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
		return blockingPost("/chat/completions", request, ChatCompletionResponse.class);
	}

	public ResponsesResult responses(ResponsesRequest request) {
		return blockingPost("/responses", request, ResponsesResult.class);
	}

	public EmbeddingsResponse embeddings(EmbeddingsRequest request) {
		return blockingPost("/embeddings", request, EmbeddingsResponse.class);
	}

	public ImagesResponse images(ImagesRequest request) {
		return blockingPost("/images", request, ImagesResponse.class);
	}

	private <T> T blockingPost(String uri, Object request, Class<T> responseType) {
		return this.restClient.post()
			.uri(uri)
			.body(request)
			.exchange((httpRequest, response) -> readBlockingResponse(uri, response, responseType));
	}

	private <T> T readBlockingResponse(String uri, ClientHttpResponse response, Class<T> responseType)
			throws IOException {
		HttpStatusCode statusCode = response.getStatusCode();
		int limit = statusCode.isError() ? this.maxErrorBodyBytes : this.maxResponseBodyBytes;
		BoundedBody body = readBounded(response.getBody(), limit);
		if (body.exceeded()) {
			if (statusCode.isError()) {
				String excerpt = new String(body.bytes(), StandardCharsets.UTF_8);
				throw this.httpExceptionFactory.createErrorBodyLimit(uri, statusCode, excerpt, limit, limit + 1L);
			}
			throw new OpenRouterLimitExceededException(
					OpenRouterLimitExceededException.Limit.BLOCKING_RESPONSE_BODY_BYTES, limit, limit + 1L, uri,
					statusCode, null, null);
		}
		if (statusCode.isError()) {
			String errorBody = new String(body.bytes(), StandardCharsets.UTF_8);
			throw this.httpExceptionFactory.create(uri, statusCode, response.getHeaders(), errorBody);
		}
		if (body.bytes().length == 0) {
			return null;
		}
		try {
			return this.objectMapper.readValue(body.bytes(), responseType);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Failed to decode OpenRouter " + uri + " response", ex);
		}
	}

	private BoundedBody readBounded(InputStream input, int limit) throws IOException {
		byte[] bytes = input.readNBytes(limit + 1);
		return bytes.length > limit ? new BoundedBody(Arrays.copyOf(bytes, limit), true)
				: new BoundedBody(bytes, false);
	}

	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest request) {
		return stream("/chat/completions", request, ChatCompletionChunk.class);
	}

	// Providers without native image streaming make OpenRouter ignore stream=true and
	// answer with one complete application/json generation instead of an SSE stream
	// (found live by the Garage paint bay). Branching on the response content type turns
	// that answer into a single completed event, so callers see one uniform contract.
	public Flux<ImagesStreamEvent> imagesStream(ImagesRequest request) {
		return this.webClient.post()
			.uri("/images")
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(request)
			.exchangeToFlux((response) -> {
				if (response.statusCode().isError()) {
					return response.bodyToMono(String.class)
						.defaultIfEmpty("")
						.flatMapMany((body) -> Flux.error(this.httpExceptionFactory.create("/images",
								response.statusCode(), response.headers().asHttpHeaders(), body)));
				}
				MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_JSON);
				if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
					return response.bodyToFlux(STRING_SSE_TYPE)
						.transform(this::applyTimeout)
						.transform(this::eventData)
						.flatMap((payload) -> parseStreamPayload(payload, ImagesStreamEvent.class));
				}
				return response.bodyToMono(ImagesResponse.class)
					.flux()
					.transform(this::applyTimeout)
					.flatMap((images) -> Flux.fromIterable(completedEvents(images)));
			});
	}

	private List<ImagesStreamEvent> completedEvents(ImagesResponse images) {
		if (images.data() == null || images.data().isEmpty()) {
			return List.of();
		}
		List<ImagesStreamEvent> events = new ArrayList<>(images.data().size());
		for (int i = 0; i < images.data().size(); i++) {
			ImagesResponse.ImageData data = images.data().get(i);
			boolean last = i == images.data().size() - 1;
			events.add(new ImagesStreamEvent(ImagesStreamEvent.COMPLETED, null, data.b64Json(), data.mediaType(),
					images.created(), last ? images.usage() : null, null));
		}
		return events;
	}

	public Flux<ResponsesStreamEvent> responsesStream(ResponsesRequest request) {
		return stream("/responses", request, ResponsesStreamEvent.class);
	}

	private <T> Flux<T> stream(String uri, Object request, Class<T> eventType) {
		return this.webClient.post()
			.uri(uri)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(request)
			.retrieve()
			.onStatus(HttpStatusCode::isError,
					response -> response.bodyToMono(String.class)
						.defaultIfEmpty("")
						.map(body -> this.httpExceptionFactory.create(uri, response.statusCode(),
								response.headers().asHttpHeaders(), body)))
			.bodyToFlux(STRING_SSE_TYPE)
			.transform(this::applyTimeout)
			.transform(this::eventData)
			.flatMap(payload -> parseStreamPayload(payload, eventType));
	}

	// Reactor's timeout operator caps the gap between elements, so a stalled stream fails
	// rather
	// than hanging forever. It is connector-agnostic, unlike a netty-specific
	// responseTimeout.
	// Applied to decoded SSE events before comment-only events are filtered, so
	// OpenRouter's
	// keep-alive comments (": OPENROUTER PROCESSING") still reset the timer -- otherwise
	// a
	// slow-but-healthy generation emitting only keep-alives would wrongly time out.
	private <T> Flux<T> applyTimeout(Flux<T> flux) {
		return this.timeout != null ? flux.timeout(this.timeout) : flux;
	}

	private Flux<String> eventData(Flux<ServerSentEvent<String>> events) {
		return events.<String>handle((event, sink) -> {
			String data = event.data();
			if (StringUtils.hasText(data)) {
				sink.next(data);
			}
		});
	}

	// The SSE codec has already split events and stripped "data:" prefixes, so a
	// spec-conformant stream arrives here as one JSON document per payload. The
	// re-split and prefix re-strip below defend against proxies and providers that
	// coalesce several complete JSON events into a single SSE data payload -- each
	// line is then a self-contained document. Pinned by the coalesced-payload
	// contract test.
	private <T> Flux<T> parseStreamPayload(String payload, Class<T> eventType) {
		if (!StringUtils.hasText(payload)) {
			return Flux.empty();
		}
		return Flux.fromArray(payload.split("\\R"))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.filter(this::isStreamDataLine)
			.map(line -> line.startsWith("data:") ? line.substring(5).trim() : line)
			.filter(line -> !"[DONE]".equals(line))
			.map(line -> readEvent(line, eventType));
	}

	private boolean isStreamDataLine(String line) {
		return line.startsWith("data:") || line.startsWith("{");
	}

	private <T> T readEvent(String line, Class<T> eventType) {
		try {
			return this.objectMapper.readValue(line, eventType);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Failed to decode OpenRouter stream chunk", ex);
		}
	}

	private record BoundedBody(byte[] bytes, boolean exceeded) {
	}

	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private String apiKey;

		private String httpReferer;

		private String applicationTitle;

		private String applicationCategories;

		private RestClient.Builder restClientBuilder;

		private WebClient.Builder webClientBuilder;

		private ObjectMapper objectMapper = new ObjectMapper();

		private Duration timeout;

		private int maxResponseBodyBytes = DEFAULT_MAX_RESPONSE_BODY_BYTES;

		private int maxErrorBodyBytes = DEFAULT_MAX_ERROR_BODY_BYTES;

		private Builder() {
		}

		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder httpReferer(String httpReferer) {
			this.httpReferer = httpReferer;
			return this;
		}

		public Builder applicationTitle(String applicationTitle) {
			this.applicationTitle = applicationTitle;
			return this;
		}

		public Builder applicationCategories(String applicationCategories) {
			this.applicationCategories = applicationCategories;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		public Builder objectMapper(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
			return this;
		}

		/**
		 * The timeout applied to the streaming {@link WebClient} as a non-destructive
		 * reactor operator that caps the gap between SSE chunks. The blocking
		 * {@link RestClient}'s connect/read timeout is a transport concern configured on
		 * its request factory by the caller (the auto-configuration builds a
		 * timeout-aware factory onto the supplied builder).
		 */
		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Maximum decoded bytes retained for a successful blocking response before JSON
		 * deserialization.
		 */
		public Builder maxResponseBodyBytes(int maxResponseBodyBytes) {
			this.maxResponseBodyBytes = maxResponseBodyBytes;
			return this;
		}

		/** Maximum decoded bytes retained from a blocking HTTP error response. */
		public Builder maxErrorBodyBytes(int maxErrorBodyBytes) {
			this.maxErrorBodyBytes = maxErrorBodyBytes;
			return this;
		}

		public OpenRouterApi build() {
			return new OpenRouterApi(this);
		}

	}

}
