package de.subhransu.openrouter.springai.api.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ResponsesOutputItem.Deserializer.class)
public record ResponsesOutputItem(String id, String type, String status, String role, List<ResponsesContent> content,
		String callId, String name, String arguments, String result, @JsonIgnore JsonNode reasoningItem) {

	public ResponsesOutputItem(String id, String type, String status, String role, List<ResponsesContent> content,
			String callId, String name, String arguments, String result) {
		this(id, type, status, role, content, callId, name, arguments, result, null);
	}

	public ResponsesOutputItem(String id, String type, String status, String role, List<ResponsesContent> content) {
		this(id, type, status, role, content, null, null, null, null);
	}

	// Reasoning items are opaque: even explicit nulls and unknown nested fields must
	// survive replay. Other output types retain the existing typed wire contract.
	@JsonValue
	public Object wireValue() {
		if (this.reasoningItem != null) {
			return this.reasoningItem;
		}
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("id", this.id);
		value.put("type", this.type);
		value.put("status", this.status);
		value.put("role", this.role);
		value.put("content", this.content);
		value.put("call_id", this.callId);
		value.put("name", this.name);
		value.put("arguments", this.arguments);
		value.put("result", this.result);
		value.values().removeIf(Objects::isNull);
		return value;
	}

	static final class Deserializer extends ValueDeserializer<ResponsesOutputItem> {

		@Override
		public ResponsesOutputItem deserialize(JsonParser parser, DeserializationContext context) {
			JsonNode node = parser.readValueAsTree();
			String type = text(node, "type");
			List<ResponsesContent> content = !"reasoning".equals(type) && node.hasNonNull("content")
					? Arrays.asList(context.readTreeAsValue(node.get("content"), ResponsesContent[].class)) : null;
			return new ResponsesOutputItem(text(node, "id"), type, text(node, "status"), text(node, "role"), content,
					text(node, "call_id"), text(node, "name"), text(node, "arguments"), text(node, "result"),
					"reasoning".equals(type) ? node.deepCopy() : null);
		}

		private static String text(JsonNode node, String field) {
			return node.hasNonNull(field) ? node.get(field).asText() : null;
		}

	}
}
