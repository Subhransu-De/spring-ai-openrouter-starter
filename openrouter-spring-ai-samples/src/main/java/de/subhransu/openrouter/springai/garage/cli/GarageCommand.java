package de.subhransu.openrouter.springai.garage.cli;

import de.subhransu.openrouter.springai.api.OpenRouterRequestMode;
import de.subhransu.openrouter.springai.garage.GarageProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Typed Garage CLI command. */
public record GarageCommand(
    String topic,
    Path outputRoot,
    boolean auto,
    boolean full,
    boolean help,
    boolean listScenes,
    boolean offlineContracts,
    Profile profile,
    ImageSurface imageSurface,
    String imageQuality,
    Double maxCostUsd,
    String foremanModel,
    String specialistModel,
    String embeddingModel,
    String visionModel,
    String imageModel,
    List<String> fallbackModels,
    List<OpenRouterRequestMode> requestModes,
    List<String> sceneIds,
    List<String> embeddingSweepModels,
    List<String> imageSweepModels) {

  private static final List<OpenRouterRequestMode> ALL_REQUEST_MODES =
      List.of(
          OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS,
          OpenRouterRequestMode.OPENAI_RESPONSES);

  private static final List<String> FULL_SCENES =
      List.of(
          "service-story",
          "streaming-dispatch",
          "digital-inspection",
          "modality-bays",
          "express-invoice",
          "routing-lane",
          "dyno-tuning",
          "attribution-check-in",
          "recovery-road-test");

  private static final List<String> PR_FREE_SCENES =
      List.of(
          "streaming-dispatch",
          "dyno-tuning",
          "attribution-check-in",
          "recovery-road-test");

  private static final List<String> NIGHTLY_LOW_COST_SCENES =
      List.of(
          "service-story",
          "streaming-dispatch",
          "digital-inspection",
          "modality-bays",
          "express-invoice",
          "dyno-tuning",
          "attribution-check-in",
          "recovery-road-test");

  private static final List<String> MODALITY_SCENE = List.of("modality-bays");

  public static GarageCommand from(String[] args, GarageProperties properties) {
    Profile profile = profile(args);
    String topic = properties.getTopic();
    Path outputRoot = properties.getOutputDir();
    boolean auto = properties.isAuto();
    boolean full = properties.isFull();
    boolean help = false;
    boolean listScenes = false;
    boolean offlineContracts = false;
    boolean modesExplicit = false;
    boolean scenesExplicit = false;
    String foremanModel = properties.getForemanModel();
    String specialistModel = properties.getSpecialistModel();
    String embeddingModel = properties.getEmbeddingModel();
    String visionModel = properties.getVisionModel();
    String imageModel = properties.getImageModel();
    List<String> fallbackModels = sanitize(properties.getFallbackModels());
    List<OpenRouterRequestMode> requestModes = sanitizeModes(properties.getRequestModes());
    List<String> sceneIds = new ArrayList<>(List.of("service-story"));
    List<String> embeddingSweepModels = List.of();
    List<String> imageSweepModels = List.of();
    ImageSurface imageSurface = ImageSurface.ALL;
    String imageQuality = null;
    Double maxCostUsd = null;

    switch (profile) {
      case PR_FREE -> {
        foremanModel = "nex-agi/nex-n2.5-mini:free";
        specialistModel = foremanModel;
        embeddingModel = "liquid/lfm-2.5-embedding-350m:free";
        visionModel = foremanModel;
        fallbackModels = List.of();
        requestModes = ALL_REQUEST_MODES;
        sceneIds = new ArrayList<>(PR_FREE_SCENES);
        imageSurface = ImageSurface.NONE;
        maxCostUsd = 0.0;
      }
      case NIGHTLY_LOW_COST -> {
        foremanModel = "google/gemini-2.5-flash-lite";
        specialistModel = foremanModel;
        embeddingModel = "openai/text-embedding-3-small";
        visionModel = foremanModel;
        fallbackModels = List.of(foremanModel);
        requestModes = ALL_REQUEST_MODES;
        sceneIds = new ArrayList<>(NIGHTLY_LOW_COST_SCENES);
        imageSurface = ImageSurface.NONE;
        maxCostUsd = 0.002;
      }
      case WEEKLY_MEDIA -> {
        imageModel = "black-forest-labs/flux.2-klein-4b";
        requestModes = List.of(OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS);
        sceneIds = new ArrayList<>(MODALITY_SCENE);
        imageSurface = ImageSurface.SYNC;
        maxCostUsd = 0.05;
      }
      case CUSTOM -> {
        // Preserve the original command defaults and --full behavior.
      }
    }
    applyProfileRuntimeLimits(profile, properties);

    for (String arg : args) {
      if (arg.startsWith("--profile=")) {
        // Applied before parsing so explicit options can override profile defaults.
      } else if ("--auto".equals(arg)) {
        auto = true;
      } else if ("--full".equals(arg)) {
        full = true;
      } else if ("--offline-contracts".equals(arg)) {
        offlineContracts = true;
      } else if ("--list-scenes".equals(arg)) {
        listScenes = true;
      } else if ("--help".equals(arg) || "-h".equals(arg)) {
        help = true;
      } else if (arg.startsWith("--topic=")) {
        topic = value(arg);
      } else if (arg.startsWith("--output=")) {
        outputRoot = Path.of(value(arg));
      } else if (arg.startsWith("--foreman-model=")) {
        foremanModel = value(arg);
      } else if (arg.startsWith("--specialist-model=")) {
        specialistModel = value(arg);
      } else if (arg.startsWith("--embedding-model=")) {
        embeddingModel = value(arg);
      } else if (arg.startsWith("--vision-model=")) {
        visionModel = value(arg);
      } else if (arg.startsWith("--image-model=")) {
        imageModel = value(arg);
      } else if (arg.startsWith("--image-surface=")) {
        imageSurface = ImageSurface.parse(value(arg));
      } else if (arg.startsWith("--image-quality=")) {
        imageQuality = value(arg);
      } else if (arg.startsWith("--max-cost-usd=")) {
        maxCostUsd = Double.valueOf(value(arg));
      } else if (arg.startsWith("--embedding-sweep=")) {
        embeddingSweepModels = parseList(value(arg));
      } else if (arg.startsWith("--image-sweep=")) {
        imageSweepModels = parseList(value(arg));
      } else if (arg.startsWith("--fallback-models=")) {
        fallbackModels = parseList(value(arg));
      } else if (arg.startsWith("--request-mode=") || arg.startsWith("--request-modes=")) {
        requestModes = parseModes(value(arg));
        modesExplicit = true;
      } else if (arg.startsWith("--scene=") || arg.startsWith("--scenes=")) {
        sceneIds = parseList(value(arg));
        scenesExplicit = true;
      } else if ("--stream".equals(arg)) {
        sceneIds = add(sceneIds, "streaming-dispatch");
        scenesExplicit = true;
      } else {
        throw new IllegalArgumentException("Unknown Garage option: " + arg);
      }
    }

    if (full && !modesExplicit) {
      requestModes = ALL_REQUEST_MODES;
    }
    if (full && !scenesExplicit) {
      sceneIds = FULL_SCENES;
    }
    if (offlineContracts && !scenesExplicit) {
      sceneIds = List.of("recovery-road-test", "dyno-tuning");
    }
    return new GarageCommand(
        topic,
        outputRoot,
        auto,
        full,
        help,
        listScenes,
        offlineContracts,
        profile,
        imageSurface,
        imageQuality,
        maxCostUsd,
        foremanModel,
        specialistModel,
        embeddingModel,
        visionModel,
        imageModel,
        List.copyOf(fallbackModels),
        List.copyOf(requestModes),
        List.copyOf(sceneIds),
        List.copyOf(embeddingSweepModels),
        List.copyOf(imageSweepModels));
  }

  public boolean runsEmbeddings() {
    return this.full || (this.profile != Profile.PR_FREE && this.profile != Profile.WEEKLY_MEDIA);
  }

  public boolean runsImageInput() {
    return this.full || (this.profile != Profile.PR_FREE && this.profile != Profile.WEEKLY_MEDIA);
  }

  public boolean runsImageGeneration() {
    return this.imageSurface != ImageSurface.NONE;
  }

  public boolean requiresApiKey() {
    return this.sceneIds.stream()
        .anyMatch(scene -> !"recovery-road-test".equals(scene) && !"dyno-tuning".equals(scene));
  }

  private static String value(String arg) {
    return arg.substring(arg.indexOf('=') + 1);
  }

  private static Profile profile(String[] args) {
    Profile selected = Profile.CUSTOM;
    for (String arg : args) {
      if (arg.startsWith("--profile=")) {
        selected = Profile.parse(value(arg));
      }
    }
    return selected;
  }

  private static void applyProfileRuntimeLimits(Profile profile, GarageProperties properties) {
    if (profile == Profile.PR_FREE || profile == Profile.NIGHTLY_LOW_COST) {
      properties.setMaxCompletionTokens(profile == Profile.PR_FREE ? 256 : 192);
      properties.setSpecialistMaxCompletionTokens(128);
      properties.setReasoningEffort("low");
      properties.setProviderSort("price");
      properties.setProviderOrder(List.of());
      properties.setProviderIgnore(List.of());
      properties.setProviderQuantizations(List.of());
    }
  }

  private static List<String> parseList(String raw) {
    return !StringUtils.hasText(raw) ? List.of() : sanitize(Arrays.asList(raw.split(",")));
  }

  private static List<String> sanitize(List<String> values) {
    if (values == null) {
      return List.of();
    }
    Set<String> sanitized = new LinkedHashSet<>();
    values.stream().filter(StringUtils::hasText).map(String::strip).forEach(sanitized::add);
    return new ArrayList<>(sanitized);
  }

  private static List<String> add(List<String> values, String value) {
    List<String> copy = new ArrayList<>(values);
    if (!copy.contains(value)) {
      copy.add(value);
    }
    return copy;
  }

  private static List<OpenRouterRequestMode> parseModes(String raw) {
    if (!StringUtils.hasText(raw)) {
      return List.of(OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS);
    }
    if ("all".equalsIgnoreCase(raw) || "both".equalsIgnoreCase(raw)) {
      return ALL_REQUEST_MODES;
    }
    List<OpenRouterRequestMode> modes = new ArrayList<>();
    for (String value : raw.split(",")) {
      String normalized = value.strip().toLowerCase(Locale.ROOT).replace('-', '_');
      modes.add(
          switch (normalized) {
            case "chat", "chat_completions", "openai_chat_completions" ->
                OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS;
            case "responses", "openai_responses" -> OpenRouterRequestMode.OPENAI_RESPONSES;
            default -> OpenRouterRequestMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
          });
    }
    return sanitizeModes(modes);
  }

  private static List<OpenRouterRequestMode> sanitizeModes(
      List<OpenRouterRequestMode> modes) {
    if (modes == null || modes.isEmpty()) {
      return List.of(OpenRouterRequestMode.OPENAI_CHAT_COMPLETIONS);
    }
    return new ArrayList<>(new LinkedHashSet<>(modes));
  }

  public enum Profile {
    CUSTOM,
    PR_FREE,
    NIGHTLY_LOW_COST,
    WEEKLY_MEDIA;

    static Profile parse(String value) {
      return valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public String cliName() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  public enum ImageSurface {
    NONE,
    SYNC,
    STREAMING,
    CHAT,
    ALL;

    static ImageSurface parse(String value) {
      String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
      if ("STREAM".equals(normalized)) {
        normalized = "STREAMING";
      }
      return valueOf(normalized);
    }
  }
}
