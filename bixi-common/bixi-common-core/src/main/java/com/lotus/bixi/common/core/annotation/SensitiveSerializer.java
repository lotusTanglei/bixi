package com.lotus.bixi.common.core.annotation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;

public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

	private final SensitiveType type;

	public SensitiveSerializer() {
		this(null);
	}

	private SensitiveSerializer(SensitiveType type) {
		this.type = type;
	}

	@Override
	public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeString(mask(value, type));
	}

	@Override
	public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
		Sensitive annotation = property == null ? null : property.getAnnotation(Sensitive.class);
		return new SensitiveSerializer(annotation == null ? null : annotation.value());
	}

	public static String mask(String value, SensitiveType type) {
		if (value == null || value.isEmpty() || type == null) return value;
		return switch (type) {
			case PASSWORD -> "******";
			case PHONE -> value.length() <= 7 ? "***" : value.substring(0, 3) + "****" + value.substring(value.length() - 4);
			case ID_CARD -> value.length() <= 7 ? "***" : value.substring(0, 3) + "***********" + value.substring(value.length() - 4);
			case BANK_CARD -> value.length() <= 8 ? "****" : value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
			case EMAIL -> maskEmail(value);
			case NAME -> value.substring(0, 1) + "*";
		};
	}

	private static String maskEmail(String value) {
		int at = value.indexOf('@');
		if (at <= 0) return "***";
		return value.substring(0, 1) + "***" + value.substring(at);
	}

}
