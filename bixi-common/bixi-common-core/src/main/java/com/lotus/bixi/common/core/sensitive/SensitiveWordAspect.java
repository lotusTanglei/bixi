package com.lotus.bixi.common.core.sensitive;

import com.lotus.bixi.common.core.exception.SensitiveWordException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class SensitiveWordAspect {

	private final SensitiveWordEngine engine;

	@Around("@annotation(com.lotus.bixi.common.core.sensitive.SensitiveWordCheck)")
	public Object check(ProceedingJoinPoint point) throws Throwable {
		for (Object arg : point.getArgs()) {
			if (contains(arg)) {
				log.warn("Sensitive word rejected in {}", point.getSignature().toShortString());
				throw new SensitiveWordException("sensitive_word_detected");
			}
		}
		return point.proceed();
	}

	private boolean contains(Object value) {
		if (value == null) return false;
		if (value instanceof CharSequence text) return engine.containsSensitiveWord(text.toString());
		if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(this::contains);
		if (value instanceof Iterable<?> iterable) {
			for (Object item : iterable) if (contains(item)) return true;
		}
		return false;
	}

}
