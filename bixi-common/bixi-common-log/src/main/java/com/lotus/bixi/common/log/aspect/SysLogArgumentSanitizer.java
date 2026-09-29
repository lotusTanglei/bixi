package com.lotus.bixi.common.log.aspect;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

final class SysLogArgumentSanitizer {

    private static final int MAX_TEXT_CODE_POINTS = 128;
    private static final int MAX_CONTAINER_ITEMS = 20;
    private static final int MAX_NESTING_DEPTH = 8;
    private static final int MAX_TOTAL_ITEMS = 100;
    private static final Object EXCLUDED = new Object();

    private SysLogArgumentSanitizer() {
    }

    static Object[] sanitize(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return new Object[0];
        }
        SanitizationContext context = new SanitizationContext();
        List<Object> sanitized = new ArrayList<>(Math.min(arguments.length, MAX_CONTAINER_ITEMS));
        for (Object argument : arguments) {
            if (sanitized.size() >= MAX_CONTAINER_ITEMS || !context.hasCapacity()) {
                break;
            }
            Object value = sanitize(argument, 0, context);
            if (value != EXCLUDED) {
                sanitized.add(value);
            }
        }
        return sanitized.toArray();
    }

    private static Object sanitize(Object value, int depth, SanitizationContext context) {
        if (value == null) {
            return null;
        }
        if (value instanceof MultipartFile file) {
            return multipartMetadata(file);
        }
        if (isUnsafe(value)) {
            return EXCLUDED;
        }
        if (depth >= MAX_NESTING_DEPTH || !context.hasCapacity()) {
            return isContainer(value) ? EXCLUDED : value;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map, depth, context);
        }
        if (value instanceof Iterable<?> iterable) {
            return sanitizeIterable(iterable, depth, context);
        }
        if (value.getClass().isArray()) {
            return sanitizeArray(value, depth, context);
        }
        return value;
    }

    private static Object sanitizeMap(Map<?, ?> map, int depth, SanitizationContext context) {
        if (!context.enter(map)) {
            return EXCLUDED;
        }
        try {
            Map<Object, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (sanitized.size() >= MAX_CONTAINER_ITEMS || !context.claimItem()) {
                    break;
                }
                Object key = sanitizeMapKey(entry.getKey());
                if (key == EXCLUDED) {
                    continue;
                }
                Object value = sanitize(entry.getValue(), depth + 1, context);
                if (value != EXCLUDED) {
                    sanitized.put(key, value);
                }
            }
            return sanitized;
        } finally {
            context.exit(map);
        }
    }

    private static Object sanitizeIterable(Iterable<?> iterable, int depth, SanitizationContext context) {
        if (!context.enter(iterable)) {
            return EXCLUDED;
        }
        try {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                if (sanitized.size() >= MAX_CONTAINER_ITEMS || !context.claimItem()) {
                    break;
                }
                Object value = sanitize(item, depth + 1, context);
                if (value != EXCLUDED) {
                    sanitized.add(value);
                }
            }
            return sanitized;
        } finally {
            context.exit(iterable);
        }
    }

    private static Object sanitizeArray(Object array, int depth, SanitizationContext context) {
        if (!context.enter(array)) {
            return EXCLUDED;
        }
        try {
            List<Object> sanitized = new ArrayList<>();
            int length = Math.min(Array.getLength(array), MAX_CONTAINER_ITEMS);
            for (int index = 0; index < length && context.claimItem(); index++) {
                Object value = sanitize(Array.get(array, index), depth + 1, context);
                if (value != EXCLUDED) {
                    sanitized.add(value);
                }
            }
            return sanitized;
        } finally {
            context.exit(array);
        }
    }

    private static boolean isUnsafe(Object value) {
        return value instanceof ServletRequest || value instanceof ServletResponse || value instanceof Errors
                || value instanceof InputStream || value instanceof OutputStream
                || value instanceof Reader || value instanceof Writer
                || value instanceof byte[] || value instanceof Byte[] || value instanceof ByteBuffer
                || value instanceof File || value instanceof Path || value instanceof HttpHeaders;
    }

    private static boolean isContainer(Object value) {
        return value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray();
    }

    private static Object sanitizeMapKey(Object key) {
        if (key instanceof CharSequence text) {
            return boundedText(text.toString());
        }
        if (key instanceof Number || key instanceof Boolean || key instanceof Character
                || key instanceof Enum<?> || key instanceof UUID) {
            return key;
        }
        return EXCLUDED;
    }

    private static MultipartMetadata multipartMetadata(MultipartFile file) {
        String fieldName = boundedText(safeValue(file::getName));
        String originalFilename = basename(safeValue(file::getOriginalFilename));
        String contentType = boundedText(safeValue(file::getContentType));
        Long size = safeValue(file::getSize);
        return new MultipartMetadata(fieldName, originalFilename, size, contentType);
    }

    private static String basename(String filename) {
        if (filename == null) {
            return null;
        }
        String normalized = filename.replace('\\', '/');
        return boundedFilename(normalized.substring(normalized.lastIndexOf('/') + 1));
    }

    private static String boundedFilename(String filename) {
        if (codePointCount(filename) <= MAX_TEXT_CODE_POINTS) {
            return filename;
        }
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex > 0) {
            String extension = filename.substring(extensionIndex);
            int extensionLength = codePointCount(extension);
            if (extensionLength <= MAX_TEXT_CODE_POINTS) {
                return firstCodePoints(filename.substring(0, extensionIndex), MAX_TEXT_CODE_POINTS - extensionLength)
                        + extension;
            }
        }
        return firstCodePoints(filename, MAX_TEXT_CODE_POINTS);
    }

    private static String boundedText(String value) {
        return value == null ? null : firstCodePoints(value, MAX_TEXT_CODE_POINTS);
    }

    private static String firstCodePoints(String value, int maximum) {
        return codePointCount(value) <= maximum ? value : value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private static int codePointCount(String value) {
        return value.codePointCount(0, value.length());
    }

    private static <T> T safeValue(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record MultipartMetadata(String fieldName, String originalFilename, Long size, String contentType) {
    }

    private static final class SanitizationContext {
        private final IdentityHashMap<Object, Boolean> containers = new IdentityHashMap<>();
        private int remainingItems = MAX_TOTAL_ITEMS;

        private boolean hasCapacity() {
            return remainingItems > 0;
        }

        private boolean claimItem() {
            if (!hasCapacity()) {
                return false;
            }
            remainingItems--;
            return true;
        }

        private boolean enter(Object container) {
            return containers.put(container, Boolean.TRUE) == null;
        }

        private void exit(Object container) {
            containers.remove(container);
        }
    }
}
