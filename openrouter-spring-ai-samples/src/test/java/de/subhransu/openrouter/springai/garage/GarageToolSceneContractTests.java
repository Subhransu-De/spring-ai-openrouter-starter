package de.subhransu.openrouter.springai.garage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import de.subhransu.openrouter.springai.api.OpenRouterApi;
import de.subhransu.openrouter.springai.api.OpenRouterRequestMode;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionChunk;
import de.subhransu.openrouter.springai.api.dto.ChatCompletionResponse;
import de.subhransu.openrouter.springai.api.dto.ChatMessage;
import de.subhransu.openrouter.springai.api.dto.Choice;
import de.subhransu.openrouter.springai.api.dto.Delta;
import de.subhransu.openrouter.springai.api.dto.FunctionCall;
import de.subhransu.openrouter.springai.api.dto.ToolCall;
import de.subhransu.openrouter.springai.chat.OpenRouterChatModel;
import de.subhransu.openrouter.springai.garage.cli.GarageCommand;
import de.subhransu.openrouter.springai.garage.evidence.GarageEvidence;
import de.subhransu.openrouter.springai.garage.evidence.GarageObservationConvention;
import de.subhransu.openrouter.springai.garage.evidence.GarageTelemetry;
import de.subhransu.openrouter.springai.garage.evidence.GarageTransportEvidence;
import de.subhransu.openrouter.springai.garage.scenes.ExpressInvoiceScene;
import de.subhransu.openrouter.springai.garage.scenes.SceneContext;
import de.subhransu.openrouter.springai.garage.scenes.SceneResult;
import de.subhransu.openrouter.springai.garage.scenes.StreamingDispatchScene;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

class GarageToolSceneContractTests {

  @TempDir Path output;

  @Test
  void returnDirectInvoiceUsesExactlyOneModelCall() {
    OpenRouterApi api = mock(OpenRouterApi.class);
    when(api.chatCompletion(any()))
        .thenReturn(
            new ChatCompletionResponse(
                "invoice-1",
                "chat.completion",
                1L,
                "garage/model",
                "garage-stub",
                List.of(
                    new Choice(
                        0,
                        new ChatMessage(
                            "assistant",
                            "",
                            null,
                            null,
                            List.of(
                                new ToolCall(
                                    "call-1",
                                    "function",
                                    new FunctionCall(
                                        "generate_express_invoice",
                                        "{\"item\":\"diagnostic inspection\",\"amount\":89}")))),
                        null,
                        "tool_calls",
                        "tool_calls")),
                null));
    TestContext test = context(api, "express-invoice");

    SceneResult result = new ExpressInvoiceScene().execute(test.context());

    assertThat(result.status()).isEqualTo(SceneResult.Status.PASSED);
    verify(api, times(1)).chatCompletion(any());
  }

  @Test
  void fragmentedStreamingArgumentsMergeIntoOneGarageCallback() throws Exception {
    OpenRouterApi api = mock(OpenRouterApi.class);
    when(api.chatCompletionStream(any()))
        .thenReturn(
            Flux.just(textChunk("plain intake", "stop")),
            Flux.just(
                toolChunk("call-2", "lookup_service_bulletin", "{\"vinPrefix\":\"TR", null),
                toolChunk(null, null, "K7\",\"modelYear\":19", null),
                toolChunk(null, null, "72,\"symptom\":\"overheating\"}", "tool_calls")),
            Flux.just(textChunk("bulletin dispatched", "stop")));
    TestContext test = context(api, "streaming-dispatch");

    SceneResult result = new StreamingDispatchScene().execute(test.context());

    assertThat(result.status()).isEqualTo(SceneResult.Status.PASSED);
    assertThat(result.details()).containsEntry("bulletinCalls", 1L);
    verify(api, times(3)).chatCompletionStream(any());
  }

  private TestContext context(OpenRouterApi api, String sceneId) {
    GarageProperties properties = new GarageProperties();
    properties.setReasoningEnabled(false);
    GarageEvidence evidence = new GarageEvidence();
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    GarageTelemetry telemetry = new GarageTelemetry(meters, evidence);
    ObservationRegistry registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meters))
        .observationHandler(telemetry);
    OpenRouterChatModel model =
        OpenRouterChatModel.builder()
            .openRouterApi(api)
            .observationRegistry(registry)
            .objectMapper(new ObjectMapper())
            .build();
    model.setObservationConvention(new GarageObservationConvention());
    GarageCommand command =
        GarageCommand.from(
            new String[] {"--scene=" + sceneId, "--foreman-model=garage/model"},
            properties);
    GarageTransportEvidence transport = new GarageTransportEvidence(evidence);
    return new TestContext(
        new SceneContext(
            command,
            OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS,
            this.output.resolve(sceneId),
            model,
            ChatClient.builder(model).build(),
            properties,
            new GarageOptionsFactory(properties),
            new ObjectMapper(),
            evidence,
            telemetry,
            transport,
            registry));
  }

  private ChatCompletionChunk textChunk(String text, String finishReason) {
    return new ChatCompletionChunk(
        "chunk-1",
        "chat.completion.chunk",
        1L,
        "garage/model",
        "garage-stub",
        List.of(
            new Choice(
                0,
                null,
                new Delta("assistant", text, null, null),
                finishReason,
                finishReason)),
        null,
        null);
  }

  private ChatCompletionChunk toolChunk(
      String id, String name, String arguments, String finishReason) {
    return new ChatCompletionChunk(
        "chunk-tool",
        "chat.completion.chunk",
        1L,
        "garage/model",
        "garage-stub",
        List.of(
            new Choice(
                0,
                null,
                new Delta(
                    "assistant",
                    null,
                    null,
                    List.of(
                        new ToolCall(
                            id,
                            "function",
                            new FunctionCall(name, arguments),
                            0))),
                finishReason,
                finishReason)),
        null,
        null);
  }

  private record TestContext(SceneContext context) {}
}
