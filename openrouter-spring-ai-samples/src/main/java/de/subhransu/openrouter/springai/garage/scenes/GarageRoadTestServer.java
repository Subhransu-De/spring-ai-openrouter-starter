package de.subhransu.openrouter.springai.garage.scenes;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Small deterministic HTTP/SSE provider used by the recovery road test. */
final class GarageRoadTestServer implements AutoCloseable {

  private static final String SUCCESS =
      """
      {"id":"garage-offline","object":"chat.completion","created":1,
       "model":"garage/offline-fallback","provider":"garage-stub",
       "choices":[{"index":0,"message":{"role":"assistant","content":"road test passed"},
       "finish_reason":"stop","native_finish_reason":"stop"}],
       "usage":{"prompt_tokens":3,"completion_tokens":3,"total_tokens":6,"cost":0.0}}
      """;

  // Must stay well above RecoveryRoadTestScene.CLIENT_TIMEOUT so the client's
  // timeout always fires before this delay elapses, even on a loaded machine.
  private static final Duration DELIBERATE_DELAY = Duration.ofSeconds(3);

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.SUCCESS);
  private final AtomicInteger attempts = new AtomicInteger();
  private final List<Map<String, Object>> requests = new java.util.concurrent.CopyOnWriteArrayList<>();

  GarageRoadTestServer() throws IOException {
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext("/api/v1/chat/completions", this::handle);
    this.server.setExecutor(this.executor);
    this.server.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/api/v1";
  }

  void mode(Mode mode) {
    this.mode.set(mode);
    this.attempts.set(0);
  }

  int attempts() {
    return this.attempts.get();
  }

  List<Map<String, Object>> requests() {
    return List.copyOf(this.requests);
  }

  @Override
  public void close() {
    this.server.stop(0);
    this.executor.shutdownNow();
  }

  private void handle(HttpExchange exchange) throws IOException {
    int attempt = this.attempts.incrementAndGet();
    capture(exchange, attempt);
    switch (this.mode.get()) {
      case SUCCESS -> json(exchange, 200, SUCCESS);
      case RETRY -> {
        if (attempt == 1) {
          json(exchange, 503, "{\"error\":\"retry-me\"}");
        } else {
          json(exchange, 200, SUCCESS);
        }
      }
      case SYNC_TIMEOUT -> {
        pause(DELIBERATE_DELAY);
        json(exchange, 200, SUCCESS);
      }
      case HTTP_ERROR ->
          json(exchange, 422, "{\"error\":\"invalid garage request\",\"authorization\":\"Bearer secret\"}");
      case STREAM_TIMEOUT -> {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        pause(DELIBERATE_DELAY);
        write(exchange, "data: " + streamChunk("late") + "\n\n");
      }
      case MALFORMED_STREAM -> sse(exchange, "data: {definitely-not-json}\n\n");
      case TERMINAL_STREAM_ERROR ->
          sse(
              exchange,
              "data: "
                  + streamChunk("first")
                  + "\n\ndata: {\"id\":\"garage-offline\",\"object\":\"chat.completion.chunk\",\"model\":\"garage/offline\",\"error\":{\"code\":\"provider_error\",\"message\":\"provider dropped\"}}\n\n");
      case CANCELLATION_STREAM -> {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
          output.write(("data: " + streamChunk("first") + "\n\n").getBytes(StandardCharsets.UTF_8));
          output.flush();
          pause(Duration.ofMillis(500));
          output.write(("data: " + streamChunk("second") + "\n\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
          // A closed socket is the expected proof that the client cancelled after one item.
        }
      }
    }
  }

  private void capture(HttpExchange exchange, int attempt) throws IOException {
    exchange.getRequestBody().readAllBytes();
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("attempt", attempt);
    item.put("path", exchange.getRequestURI().getPath());
    Map<String, Boolean> headers = new LinkedHashMap<>();
    for (String name :
        List.of("HTTP-Referer", "X-OpenRouter-Title", "X-OpenRouter-Categories")) {
      headers.put(name, exchange.getRequestHeaders().containsKey(name));
    }
    item.put("headerPresence", headers);
    this.requests.add(item);
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private void sse(HttpExchange exchange, String body) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
    exchange.sendResponseHeaders(200, 0);
    write(exchange, body);
  }

  private void write(HttpExchange exchange, String body) throws IOException {
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(body.getBytes(StandardCharsets.UTF_8));
    }
  }

  private String streamChunk(String text) {
    return "{\"id\":\"garage-offline\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"garage/offline\",\"provider\":\"garage-stub\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\""
        + text
        + "\"},\"finish_reason\":\"stop\",\"native_finish_reason\":\"stop\"}]}";
  }

  private void pause(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  enum Mode {
    SUCCESS,
    RETRY,
    SYNC_TIMEOUT,
    HTTP_ERROR,
    STREAM_TIMEOUT,
    MALFORMED_STREAM,
    TERMINAL_STREAM_ERROR,
    CANCELLATION_STREAM
  }
}
