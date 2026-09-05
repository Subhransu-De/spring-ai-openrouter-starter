package de.subhransu.openrouter.springai.autoconfigure;

import de.subhransu.openrouter.springai.chat.OpenRouterToolExecutionExceptionProcessor;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

final class OpenRouterToolCallingManagerGuard implements BeanPostProcessor, SmartInitializingSingleton {

	private static final String SPRING_TOOL_CALLING_AUTO_CONFIGURATION = "org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration";

	private static final Field PROCESSOR_FIELD = requiredField(DefaultToolCallingManager.class,
			"toolExecutionExceptionProcessor");

	private static final Field ALWAYS_THROW_FIELD = requiredField(DefaultToolExecutionExceptionProcessor.class,
			"alwaysThrow");

	private final ConfigurableListableBeanFactory beanFactory;

	private final Set<ToolExecutionExceptionProcessor> singletonDeclaredProcessors = Collections
		.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

	private final ReferenceQueue<ToolExecutionExceptionProcessor> transientProcessorQueue = new ReferenceQueue<>();

	private final Set<IdentityWeakReference<ToolExecutionExceptionProcessor>> transientDeclaredProcessors = new HashSet<>();

	OpenRouterToolCallingManagerGuard(ConfigurableListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		Arrays.stream(beanFactory.getSingletonNames())
			.map(beanFactory::getSingleton)
			.filter(ToolExecutionExceptionProcessor.class::isInstance)
			.map(ToolExecutionExceptionProcessor.class::cast)
			.forEach(this.singletonDeclaredProcessors::add);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof ToolExecutionExceptionProcessor processor) {
			rememberProcessor(beanName, processor);
		}
		if (bean instanceof ToolCallingManager manager && !(bean instanceof ScopedObject)
				&& !this.beanFactory.isSingleton(beanName) && isCustomManager(beanName)) {
			validate(manager);
		}
		return bean;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.singletonDeclaredProcessors
			.addAll(this.beanFactory.getBeansOfType(ToolExecutionExceptionProcessor.class, false, false).values());
		this.beanFactory.getBeansOfType(ToolCallingManager.class, false, false)
			.forEach((name, manager) -> validateIfCustom(name, manager));
	}

	private void validateIfCustom(String beanName, ToolCallingManager manager) {
		if (!(manager instanceof ScopedObject) && isCustomManager(beanName)) {
			validate(manager);
		}
	}

	private void rememberProcessor(String beanName, ToolExecutionExceptionProcessor processor) {
		if (this.beanFactory.isSingleton(beanName)) {
			this.singletonDeclaredProcessors.add(processor);
			return;
		}
		synchronized (this.transientDeclaredProcessors) {
			purgeCollectedProcessors();
			this.transientDeclaredProcessors.add(new IdentityWeakReference<>(processor, this.transientProcessorQueue));
		}
	}

	private boolean isCustomManager(String beanName) {
		if (!this.beanFactory.containsBeanDefinition(beanName)) {
			return true;
		}
		BeanDefinition definition = this.beanFactory.getBeanDefinition(beanName);
		return !SPRING_TOOL_CALLING_AUTO_CONFIGURATION.equals(definition.getFactoryBeanName());
	}

	private void validate(ToolCallingManager manager) {
		if (!(manager instanceof DefaultToolCallingManager)) {
			throw unsafeManager(manager);
		}
		ToolExecutionExceptionProcessor actualProcessor = (ToolExecutionExceptionProcessor) ReflectionUtils
			.getField(PROCESSOR_FIELD, manager);
		boolean declaredPolicy = isDeclaredProcessor(actualProcessor);
		boolean safePolicy = actualProcessor instanceof OpenRouterToolExecutionExceptionProcessor
				|| throwsInsteadOfReturning(actualProcessor);
		if (!declaredPolicy && !safePolicy) {
			throw unsafeManager(manager);
		}
	}

	private boolean isDeclaredProcessor(ToolExecutionExceptionProcessor processor) {
		if (this.singletonDeclaredProcessors.contains(processor)) {
			return true;
		}
		synchronized (this.transientDeclaredProcessors) {
			purgeCollectedProcessors();
			return this.transientDeclaredProcessors.contains(new IdentityWeakReference<>(processor));
		}
	}

	private void purgeCollectedProcessors() {
		Reference<? extends ToolExecutionExceptionProcessor> reference;
		while ((reference = this.transientProcessorQueue.poll()) != null) {
			this.transientDeclaredProcessors.remove(reference);
		}
	}

	private static boolean throwsInsteadOfReturning(ToolExecutionExceptionProcessor processor) {
		return processor instanceof DefaultToolExecutionExceptionProcessor
				&& Boolean.TRUE.equals(ReflectionUtils.getField(ALWAYS_THROW_FIELD, processor));
	}

	private static IllegalStateException unsafeManager(ToolCallingManager manager) {
		return new IllegalStateException("Custom ToolCallingManager " + manager.getClass().getName()
				+ " does not expose a verifiable provider-visible failure policy. Install the declared "
				+ "ToolExecutionExceptionProcessor in the manager, or explicitly set "
				+ "spring.ai.openrouter.chat.allow-unsafe-tool-failure-results=true after auditing its behavior.");
	}

	private static Field requiredField(Class<?> type, String name) {
		Field field = ReflectionUtils.findField(type, name);
		if (field == null) {
			throw new IllegalStateException("Spring AI changed the " + type.getSimpleName() + " failure policy field");
		}
		ReflectionUtils.makeAccessible(field);
		return field;
	}

	private static final class IdentityWeakReference<T> extends WeakReference<T> {

		private final int identityHashCode;

		IdentityWeakReference(T referent) {
			super(referent);
			this.identityHashCode = System.identityHashCode(referent);
		}

		IdentityWeakReference(T referent, ReferenceQueue<T> queue) {
			super(referent, queue);
			this.identityHashCode = System.identityHashCode(referent);
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			return other instanceof IdentityWeakReference<?> reference && get() != null && get() == reference.get();
		}

		@Override
		public int hashCode() {
			return this.identityHashCode;
		}

	}

}
