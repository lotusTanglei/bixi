package com.lotus.bixi.common.mq.reliable;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Applies the shared idempotency protocol to explicitly marked HTTP writes. */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public final class IdempotencyAspect {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final IdempotencyExecutor executor;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(IdempotencyExecutor executor, ObjectMapper objectMapper) {
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.lotus.bixi.common.mq.reliable.Idempotent)")
    public Object execute(ProceedingJoinPoint point) throws Throwable {
        String key = requestKey();
        if (key == null) return point.proceed();

        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String className = method.getDeclaringClass().getName();
        if (className.startsWith("com.lotus.bixi.workflow.")
                || Arrays.stream(point.getArgs()).anyMatch(MultipartFile.class::isInstance)) {
            return point.proceed();
        }
        Idempotent annotation = method.getAnnotation(Idempotent.class);
        Long tenantId = TenantContextHolder.get();
        if (tenantId == null) throw new IllegalArgumentException("tenant_required");

        JavaType resultType = objectMapper.getTypeFactory().constructType(method.getGenericReturnType());
        String scope = annotation == null
                ? "http." + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : annotation.scope();
        return executor.execute(tenantId, scope, key, Arrays.asList(point.getArgs()), resultType,
                () -> proceed(point));
    }

    private Object proceed(ProceedingJoinPoint point) throws Exception {
        try {
            return point.proceed();
        }
        catch (Throwable failure) {
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        }
    }

    private String requestKey() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) return null;
        HttpServletRequest request = servletAttributes.getRequest();
        String key = request.getHeader(IDEMPOTENCY_KEY);
        return key == null || key.isBlank() ? null : key;
    }
}
