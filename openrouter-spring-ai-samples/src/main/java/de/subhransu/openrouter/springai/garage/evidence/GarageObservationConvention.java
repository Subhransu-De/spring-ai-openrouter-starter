package de.subhransu.openrouter.springai.garage.evidence;

import de.subhransu.openrouter.springai.chat.OpenRouterChatOptions;
import io.micrometer.common.KeyValues;
import java.util.Map;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;

/** Adds correlation and OpenRouter-specific option tags omitted by Spring AI's defaults. */
public final class GarageObservationConvention extends DefaultChatModelObservationConvention {

  @Override
  public KeyValues getHighCardinalityKeyValues(ChatModelObservationContext context) {
    KeyValues values = super.getHighCardinalityKeyValues(context);
    if (!(context.getRequest().getOptions() instanceof OpenRouterChatOptions options)) {
      return values;
    }
    Map<String, Object> metadata = options.getMetadata();
    values = add(values, "garage.operation.id", metadataValue(metadata, "operationId"));
    values = add(values, "garage.scene", metadataValue(metadata, "sceneId"));
    values =
        add(
            values,
            "openrouter.request.mode",
            options.getRequestMode() != null ? options.getRequestMode().name() : null);
    values = add(values, "openrouter.route", options.getRoute());
    values =
        add(
            values,
            "openrouter.service.tier",
            options.getServiceTier() != null ? options.getServiceTier().value() : null);
    values = add(values, "openrouter.user", options.getUser());
    return values;
  }

  private KeyValues add(KeyValues values, String key, String value) {
    return value != null && !value.isBlank() ? values.and(key, value) : values;
  }

  private String metadataValue(Map<String, Object> metadata, String key) {
    Object value = metadata != null ? metadata.get(key) : null;
    return value != null ? value.toString() : null;
  }
}
