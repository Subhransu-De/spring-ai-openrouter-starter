package de.subhransu.openrouter.springai.garage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
