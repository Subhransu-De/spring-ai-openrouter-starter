package de.subhransu.openrouter.springai.garage;

import static org.assertj.core.api.Assertions.assertThat;

import de.subhransu.openrouter.springai.chat.OpenRouterChatOptions;
import de.subhransu.openrouter.springai.garage.evidence.GarageEvidence;
import de.subhransu.openrouter.springai.garage.evidence.GarageTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;

class GarageTelemetryTests {

  @Test
  void awaitsReactiveObservationFinalization() throws InterruptedException {
    GarageTelemetry telemetry =
        new GarageTelemetry(new SimpleMeterRegistry(), new GarageEvidence());
    OpenRouterChatOptions options =
        OpenRouterChatOptions.builder()
            .metadata(Map.of("operationId", "operation-1", "sceneId", "streaming-dispatch"))
            .build();
    ChatModelObservationContext context =
        ChatModelObservationContext.builder()
            .prompt(new Prompt("test", options))
            .provider("openrouter")
            .streaming(true)
            .build();
    telemetry.onStart(context);
    Thread finalizer =
        new Thread(
            () -> {
              LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
              telemetry.onStop(context);
            });
    finalizer.start();

    List<Map<String, Object>> observations =
        telemetry.awaitObservationsFor("operation-1", Duration.ofSeconds(1));
    finalizer.join();

    assertThat(observations)
        .singleElement()
        .satisfies(observation -> assertThat(observation.get("streaming")).isEqualTo(true));
  }
}
