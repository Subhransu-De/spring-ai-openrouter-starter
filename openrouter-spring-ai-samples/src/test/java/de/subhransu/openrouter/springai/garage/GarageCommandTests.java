package de.subhransu.openrouter.springai.garage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.subhransu.openrouter.springai.api.OpenRouterRequestMode;
import de.subhransu.openrouter.springai.garage.cli.GarageCommand;
import de.subhransu.openrouter.springai.garage.cli.GarageCommand.ImageSurface;
import de.subhransu.openrouter.springai.garage.cli.GarageCommand.Profile;
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

  @Test
  void pullRequestProfileUsesOnlyFreeModelsAndSkipsGeneration() {
    GarageCommand command =
        GarageCommand.from(new String[] {"--profile=pr-free", "--auto"}, this.properties);

    assertThat(command.profile()).isEqualTo(Profile.PR_FREE);
    assertThat(command.foremanModel()).isEqualTo("nex-agi/nex-n2.5-mini:free");
    assertThat(command.specialistModel()).isEqualTo("liquid/lfm-2.5-2.6b:free");
    assertThat(command.embeddingModel()).isEqualTo("liquid/lfm-2.5-embedding-350m:free");
    assertThat(command.imageSurface()).isEqualTo(ImageSurface.NONE);
    assertThat(command.maxCostUsd()).isZero();
    assertThat(command.runsEmbeddings()).isTrue();
    assertThat(command.runsImageInput()).isTrue();
    assertThat(command.runsImageGeneration()).isFalse();
    assertThat(this.properties.getMaxCompletionTokens()).isEqualTo(256);
    assertThat(this.properties.getSpecialistMaxCompletionTokens()).isEqualTo(128);
    assertThat(this.properties.getProviderOrder()).isEmpty();
  }

  @Test
  void nightlyProfilePinsLowCostTextAndEmbeddingModels() {
    GarageCommand command =
        GarageCommand.from(new String[] {"--profile=nightly-low-cost"}, this.properties);

    assertThat(command.profile()).isEqualTo(Profile.NIGHTLY_LOW_COST);
    assertThat(command.foremanModel()).isEqualTo("google/gemini-2.5-flash-lite");
    assertThat(command.embeddingModel()).isEqualTo("openai/text-embedding-3-small");
    assertThat(command.imageSurface()).isEqualTo(ImageSurface.NONE);
    assertThat(command.maxCostUsd()).isEqualTo(0.002);
    assertThat(this.properties.getMaxCompletionTokens()).isEqualTo(192);
  }

  @Test
  void weeklyProfileRunsOneSynchronousImageGenerationOnly() {
    GarageCommand command =
        GarageCommand.from(new String[] {"--profile=weekly-media"}, this.properties);

    assertThat(command.profile()).isEqualTo(Profile.WEEKLY_MEDIA);
    assertThat(command.sceneIds()).containsExactly("modality-bays");
    assertThat(command.requestModes())
        .containsExactly(OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS);
    assertThat(command.imageModel()).isEqualTo("black-forest-labs/flux.2-klein-4b");
    assertThat(command.imageSurface()).isEqualTo(ImageSurface.SYNC);
    assertThat(command.maxCostUsd()).isEqualTo(0.05);
    assertThat(command.runsEmbeddings()).isFalse();
    assertThat(command.runsImageInput()).isFalse();
    assertThat(command.runsImageGeneration()).isTrue();
  }

  @Test
  void explicitImageOptionsOverrideWeeklyDefaults() {
    GarageCommand command =
        GarageCommand.from(
            new String[] {
              "--profile=weekly-media",
              "--image-surface=streaming",
              "--image-model=openai/gpt-image-1-mini",
              "--image-quality=low"
            },
            this.properties);

    assertThat(command.imageSurface()).isEqualTo(ImageSurface.STREAMING);
    assertThat(command.imageModel()).isEqualTo("openai/gpt-image-1-mini");
    assertThat(command.imageQuality()).isEqualTo("low");
  }
}
