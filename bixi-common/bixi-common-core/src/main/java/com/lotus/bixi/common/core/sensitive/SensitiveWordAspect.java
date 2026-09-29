package com.lotus.bixi.common.core.sensitive;

import com.lotus.bixi.common.core.exception.SensitiveWordException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class SensitiveWordAspect {

	private final SensitiveWordEngine engine;

	@Around("@annotation(com.lotus.bixi.common.core.sensitive.SensitiveWordCheck)")
	public Object check(ProceedingJoinPoint point) throws Throwable {
		for (Object arg : point.getArgs()) {
			if (contains(arg, Collections.newSetFromMap(new IdentityHashMap<>()))) {
				log.warn("Sensitive word rejected in {}", point.getSignature().toShortString());
				throw new SensitiveWordException("sensitive_word_detected");
			}
		}
		return point.proceed();
	}

	private boolean contains(Object value, java.util.Set<Object> visited) {
		if (value == null) return false;
		if (value instanceof CharSequence text) return engine.containsSensitiveWord(text.toString());
		if (value instanceof Map<?, ?> map) {
			if (!visited.add(value)) return false;
			return map.values().stream().anyMatch(item -> contains(item, visited));
		}
		if (value instanceof Iterable<?> iterable) {
			if (!visited.add(value)) return false;
			for (Object item : iterable) if (contains(item, visited)) return true;
			return false;
		}
		if (value.getClass().isArray()) {
			if (!visited.add(value)) return false;
			for (int index = 0; index < Array.getLength(value); index++) {
				if (contains(Array.get(value, index), visited)) return true;
			}
			return false;
		}
		Class<?> type = value.getClass();
		if (type.isPrimitive() || type.isEnum() || type.getName().startsWith("java.")) return false;
		if (!visited.add(value)) return false;
		for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
				try {
					field.setAccessible(true);
					if (contains(field.get(value), visited)) return true;
				}
				catch (IllegalAccessException | RuntimeException ignored) {
					// Inaccessible implementation fields are not request content.
				}
			}
		}
		return false;
	}

}
