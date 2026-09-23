package com.lotus.bixi.common.security.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.crypto.AesPayloadCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class EncryptionFilter extends OncePerRequestFilter {

	private final ObjectMapper objectMapper;

	@Value("${bixi.encrypt.key:${security.encode-key:}}")
	private String encryptionKey;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, jakarta.servlet.ServletException {
		if (!"aes".equalsIgnoreCase(request.getHeader("Enc-Flag"))) {
			chain.doFilter(request, response);
			return;
		}
		if (encryptionKey == null || encryptionKey.isBlank()) {
			writeError(response, "decrypt_error");
			return;
		}
		HttpServletRequest effectiveRequest = request;
		try {
			byte[] body = request.getInputStream().readAllBytes();
			if (body.length > 0) {
				JsonNode envelope = objectMapper.readTree(body);
				if (envelope == null || envelope.get("encryption") == null) throw new IllegalArgumentException("Missing encryption envelope");
				byte[] plaintext = AesPayloadCodec.decrypt(envelope.get("encryption").asText(), encryptionKey)
						.getBytes(StandardCharsets.UTF_8);
				effectiveRequest = new BodyRequestWrapper(request, plaintext);
			}
		}
		catch (RuntimeException ex) {
			writeError(response, "decrypt_error");
			return;
		}
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
		chain.doFilter(effectiveRequest, wrappedResponse);
		byte[] output = wrappedResponse.getContentAsByteArray();
		if (output.length == 0 || isStreaming(wrappedResponse)) {
			wrappedResponse.copyBodyToResponse();
			return;
		}
		String encrypted = AesPayloadCodec.encrypt(new String(output, StandardCharsets.UTF_8), encryptionKey);
		byte[] envelope = objectMapper.createObjectNode().put("encryption", encrypted).toString()
				.getBytes(StandardCharsets.UTF_8);
		response.resetBuffer();
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setContentLength(envelope.length);
		try (ServletOutputStream stream = response.getOutputStream()) {
			stream.write(envelope);
		}
	}

	private boolean isStreaming(ContentCachingResponseWrapper response) {
		String contentType = response.getContentType();
		return contentType != null && (contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)
				|| contentType.startsWith(MediaType.APPLICATION_OCTET_STREAM_VALUE));
	}

	private void writeError(HttpServletResponse response, String code) throws IOException {
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"" + code + "\"}");
	}

	private static final class BodyRequestWrapper extends HttpServletRequestWrapper {
		private final byte[] body;

		private BodyRequestWrapper(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream input = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override public int read() { return input.read(); }
				@Override public boolean isFinished() { return input.available() == 0; }
				@Override public boolean isReady() { return true; }
				@Override public void setReadListener(ReadListener listener) { }
			};
		}
	}

}
