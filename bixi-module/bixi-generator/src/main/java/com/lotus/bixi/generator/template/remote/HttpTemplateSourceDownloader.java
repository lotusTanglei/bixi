package com.lotus.bixi.generator.template.remote;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public final class HttpTemplateSourceDownloader implements TemplateSourceDownloader {

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	@Override
	public byte[] download(URI uri, int maximumBytes) {
		if (maximumBytes <= 0) throw new IllegalArgumentException("模板下载大小限制无效");
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(30))
				.header("Accept", "application/octet-stream, application/json")
				.GET()
				.build();
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				if (response.statusCode() != 200) {
					throw new IllegalStateException("在线模板下载失败，HTTP状态: " + response.statusCode());
				}
				long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
				if (declaredLength > maximumBytes) throw new IllegalArgumentException("在线模板文件超出大小限制");
				byte[] bytes = body.readNBytes(maximumBytes + 1);
				if (bytes.length > maximumBytes) throw new IllegalArgumentException("在线模板文件超出大小限制");
				return bytes;
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("在线模板下载被中断", interrupted);
		} catch (IOException failure) {
			throw new IllegalStateException("在线模板下载失败", failure);
		}
	}
}
