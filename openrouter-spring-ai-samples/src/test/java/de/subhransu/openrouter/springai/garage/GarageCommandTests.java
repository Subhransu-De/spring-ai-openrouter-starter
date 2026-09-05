package de.subhransu.openrouter.springai.garage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.subhransu.openrouter.springai.api.OpenRouterRequestMode;
import de.subhransu.openrouter.springai.garage.cli.GarageCommand;
import org.junit.jupiter.api.Test;

class GarageCommandTests {

  private final GarageProperties properties = new GarageProperties();

  @Test
  void fullSelectsEverySceneAndBothModes() {
    GarageCommand command = GarageCommand.from(new String[] {"--full", "--auto"}, this.properties);

    assertThat(command.sceneIds())
        .containsExactly(
            "service-story",
            "streaming-dispatch",
            "digital-inspection",
            "modality-bays",
            "express-invoice",
            "routing-lane",
            "dyno-tuning",
            "attribution-check-in",
            "recovery-road-test");
    assertThat(command.requestModes())
        .containsExactly(
            OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS,
            OpenRouterRequestMode.OPENAI_RESPONSES);
    assertThat(command.auto()).isTrue();
  }

  @Test
  void offlineContractsNeedNoApiKey() {
    GarageCommand command =
        GarageCommand.from(new String[] {"--offline-contracts"}, this.properties);

    assertThat(command.sceneIds()).containsExactly("recovery-road-test", "dyno-tuning");
    assertThat(command.requiresApiKey()).isFalse();
  }

  @Test
  void selectedScenesAndModesAreDeduplicated() {
    GarageCommand command =
        GarageCommand.from(
            new String[] {
              "--scene=dyno-tuning,dyno-tuning",
              "--request-modes=chat,responses,chat"
            },
            this.properties);

    assertThat(command.sceneIds()).containsExactly("dyno-tuning");
    assertThat(command.requestModes())
        .containsExactly(
            OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS,
            OpenRouterRequestMode.OPENAI_RESPONSES);
  }

  @Test
  void unknownOptionsFailFast() {
    assertThatThrownBy(
            () -> GarageCommand.from(new String[] {"--mystery"}, this.properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--mystery");
  }
}
