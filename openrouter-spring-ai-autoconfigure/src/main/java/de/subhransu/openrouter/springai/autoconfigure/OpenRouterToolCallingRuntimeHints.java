package de.subhransu.openrouter.springai.autoconfigure;

import java.lang.reflect.Field;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ReflectionUtils;

final class OpenRouterToolCallingRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		registerField(hints, DefaultToolCallingManager.class, "toolExecutionExceptionProcessor");
		registerField(hints, DefaultToolExecutionExceptionProcessor.class, "alwaysThrow");
	}

	private static void registerField(RuntimeHints hints, Class<?> type, String fieldName) {
		Field field = ReflectionUtils.findField(type, fieldName);
		if (field == null) {
			throw new IllegalStateException("Required Spring AI field not found: " + type.getName() + "." + fieldName);
		}
		hints.reflection().registerField(field);
	}

}
