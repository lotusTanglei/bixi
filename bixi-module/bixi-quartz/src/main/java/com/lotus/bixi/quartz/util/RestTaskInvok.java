package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.exception.TaskException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务rest反射实现
 *
 * @author 唐磊
 */
@Slf4j
@Component("restTaskInvok")
public class RestTaskInvok implements TaskInvok {

	static final String ALLOWED_HOSTS_PROPERTY = "bixi.quartz.rest.allowed-hosts";

	static final String TIMEOUT_MILLIS_PROPERTY = "bixi.quartz.rest.timeout-millis";

	static final String MAX_RESPONSE_BYTES_PROPERTY = "bixi.quartz.rest.max-response-bytes";

	private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

	private static final int MAX_TIMEOUT_MILLIS = 60000;

	private static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;

	private static final int MAX_CONFIGURED_RESPONSE_BYTES = 16 * 1024 * 1024;

	private final RestTaskUrlPolicy urlPolicy;

	private final int timeoutMillis;

	private final int maxResponseBytes;

	private final OkHttpClient httpClient;

	public RestTaskInvok() {
		this(System.getProperty(ALLOWED_HOSTS_PROPERTY), System.getProperty(TIMEOUT_MILLIS_PROPERTY),
				System.getProperty(MAX_RESPONSE_BYTES_PROPERTY));
	}

	@Autowired
	public RestTaskInvok(Environment environment) {
		this(environment.getProperty(ALLOWED_HOSTS_PROPERTY), environment.getProperty(TIMEOUT_MILLIS_PROPERTY),
				environment.getProperty(MAX_RESPONSE_BYTES_PROPERTY));
	}

	private RestTaskInvok(String allowedHosts, String timeoutMillis, String maxResponseBytes) {
		this(new RestTaskUrlPolicy(allowedHosts), parseTimeout(timeoutMillis), parseMaxResponseBytes(maxResponseBytes));
	}

	RestTaskInvok(RestTaskUrlPolicy urlPolicy, int timeoutMillis, int maxResponseBytes) {
		this.urlPolicy = Objects.requireNonNull(urlPolicy, "urlPolicy");
		if (timeoutMillis <= 0 || maxResponseBytes <= 0) {
			throw new IllegalArgumentException("REST调用限制必须为正数");
		}
		this.timeoutMillis = timeoutMillis;
		this.maxResponseBytes = maxResponseBytes;
		this.httpClient = new OkHttpClient.Builder()
			.connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
			.readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
			.callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
			.followRedirects(false)
			.followSslRedirects(false)
			.retryOnConnectionFailure(false)
			.proxy(Proxy.NO_PROXY)
			.protocols(List.of(Protocol.HTTP_1_1))
			.connectionPool(new ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
			.build();
	}

	@Override
	public void invokMethod(SysJob sysJob) throws TaskException {
		try (Response response = execute(sysJob)) {
			consumeBodyWithinLimit(response.body());
			int status = response.code();
			if (status < 200 || status >= 300) {
				throw new TaskException("定时任务REST调用返回HTTP " + status + ",任务:" + sysJob.getName());
			}
		}
		catch (TaskException ex) {
			throw ex;
		}
		catch (IllegalArgumentException ex) {
			throw new TaskException(ex.getMessage());
		}
		catch (Exception e) {
			log.error("定时任务restTaskInvok异常,执行任务：{}", sysJob.getExecutePath(), e);
			throw new TaskException("定时任务restTaskInvok业务执行失败,任务：" + sysJob.getExecutePath());
		}
	}

	private Response execute(SysJob sysJob) throws IOException {
		RestTaskUrlPolicy.ResolvedTarget target = urlPolicy.resolve(sysJob.getExecutePath());
		OkHttpClient pinnedClient = httpClient.newBuilder()
			.dns(new PinnedDns(target.host(), target.addresses()))
			.build();
		Request request = new Request.Builder()
			.url(target.uri().toASCIIString())
			.header("Connection", "close")
			.get()
			.build();
		return pinnedClient.newCall(request).execute();
	}

	private void consumeBodyWithinLimit(ResponseBody body) throws IOException, TaskException {
		if (body == null) {
			return;
		}
		if (body.contentLength() > maxResponseBytes) {
			throw responseTooLarge();
		}
		try (InputStream input = body.byteStream()) {
			byte[] buffer = new byte[(int) Math.min((long) maxResponseBytes + 1, 8192L)];
			long total = 0;
			int read;
			while ((read = input.read(buffer)) != -1) {
				if (read == 0) {
					if (input.read() == -1) {
						break;
					}
					total++;
					if (total > maxResponseBytes) {
						throw responseTooLarge();
					}
					continue;
				}
				total += read;
				if (total > maxResponseBytes) {
					throw responseTooLarge();
				}
			}
		}
	}

	private static TaskException responseTooLarge() {
		return new TaskException("定时任务REST响应体超过大小限制");
	}

	private static int parseTimeout(String configuredTimeout) {
		if (configuredTimeout == null || configuredTimeout.isBlank()) {
			return DEFAULT_TIMEOUT_MILLIS;
		}
		try {
			int parsed = Integer.parseInt(configuredTimeout.trim());
			return parsed > 0 && parsed <= MAX_TIMEOUT_MILLIS ? parsed : DEFAULT_TIMEOUT_MILLIS;
		}
		catch (NumberFormatException ex) {
			return DEFAULT_TIMEOUT_MILLIS;
		}
	}

	private static int parseMaxResponseBytes(String configuredMaxResponseBytes) {
		if (configuredMaxResponseBytes == null || configuredMaxResponseBytes.isBlank()) {
			return DEFAULT_MAX_RESPONSE_BYTES;
		}
		try {
			int parsed = Integer.parseInt(configuredMaxResponseBytes.trim());
			return parsed > 0 && parsed <= MAX_CONFIGURED_RESPONSE_BYTES ? parsed : DEFAULT_MAX_RESPONSE_BYTES;
		}
		catch (NumberFormatException ex) {
			return DEFAULT_MAX_RESPONSE_BYTES;
		}
	}

	private static final class PinnedDns implements Dns {

		private final String host;

		private final List<InetAddress> addresses;

		private PinnedDns(String host, List<InetAddress> addresses) {
			this.host = host;
			this.addresses = List.copyOf(addresses);
		}

		@Override
		public List<InetAddress> lookup(String hostname) throws UnknownHostException {
			String canonicalHostname = RestTaskUrlPolicy.canonicalHost(hostname);
			if (canonicalHostname == null || !host.equals(canonicalHostname)) {
				throw new UnknownHostException(hostname);
			}
			return addresses;
		}

	}

}
