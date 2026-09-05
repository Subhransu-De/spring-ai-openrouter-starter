package de.subhransu.openrouter.springai.garage.evidence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Correlated, incrementally populated evidence for one feature in one operation. */
public final class FeatureEvidence {

  private final GarageFeature feature;
  private final String operationId;
  private final String sceneId;
  private final String requestMode;
  private final Instant createdAt = Instant.now();
  private final EnumSet<EvidenceLevel> levels = EnumSet.noneOf(EvidenceLevel.class);
  private final Map<String, Object> details = new LinkedHashMap<>();
  private final List<Map<String, Object>> errors = new ArrayList<>();

  FeatureEvidence(
      GarageFeature feature, String operationId, String sceneId, String requestMode) {
    this.feature = feature;
    this.operationId = operationId;
    this.sceneId = sceneId;
    this.requestMode = requestMode;
  }

  synchronized void record(EvidenceLevel level, String key, Object value) {
    this.levels.add(level);
    if (key != null) {
      this.details.put(key, value);
    }
  }

  synchronized void error(Map<String, Object> error) {
    this.errors.add(error);
  }

  public synchronized boolean complete() {
    return this.levels.containsAll(EnumSet.allOf(EvidenceLevel.class)) && this.errors.isEmpty();
  }

  public synchronized Map<String, Object> asMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("featureId", this.feature.id());
    values.put("feature", this.feature.title());
    values.put("kind", this.feature.kind().name());
    values.put("sceneId", this.sceneId);
    values.put("operationId", this.operationId);
    values.put("requestMode", this.requestMode);
    values.put("createdAt", this.createdAt.toString());
    values.put("levels", this.levels.stream().map(Enum::name).toList());
    values.put("complete", complete());
    values.put("details", new LinkedHashMap<>(this.details));
    values.put("errors", List.copyOf(this.errors));
    return values;
  }
}
