package de.subhransu.openrouter.springai.garage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.subhransu.openrouter.springai.chat.OpenRouterUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;

class GarageModalityBaysTests {

  @Test
  void recognizesAWebpContainerWithoutAnImageIoPlugin() {
    byte[] webp = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'};

    assertThat(GarageModalityBays.hasWebpSignature(webp)).isTrue();
    assertThat(
            GarageModalityBays.hasWebpSignature(
                new byte[] {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'N', 'O', 'P', 'E'}))
        .isFalse();
    assertThat(GarageModalityBays.hasWebpSignature(new byte[0])).isFalse();
  }

  @Test
  void retainsImageApiCostWhenThePayloadIsMissing() {
    ImageModel imageModel = mock(ImageModel.class);
    ImageResponseMetadata metadata = new ImageResponseMetadata();
    metadata.put(
        "openrouter.usage", new OpenRouterUsage(1, 1, 2, 0, 0, 0.03, Map.of()));
    when(imageModel.call(any(ImagePrompt.class)))
        .thenReturn(new ImageResponse(List.of(), metadata));
    GarageModalityBays bays =
        new GarageModalityBays(
            null, null, imageModel, Path.of("target"), "embedding", "vision", "image", null);

    Map<String, Object> probe = bays.runPaintBay("cost accounting");

    assertThat(probe.get("status")).isEqualTo("failed");
    assertThat(GarageCosts.usageMaps(probe)).isEqualTo(0.03);
  }
}
