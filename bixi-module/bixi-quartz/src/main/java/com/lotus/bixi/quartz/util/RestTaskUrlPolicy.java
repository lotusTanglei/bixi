package com.lotus.bixi.quartz.util;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates REST task destinations before a network connection is opened.
 */
final class RestTaskUrlPolicy {

	private static final Set<String> FORBIDDEN_METADATA_HOSTS = Set.of(
			"169.254.169.254",
			"100.100.100.200",
			"metadata",
			"metadata.google.internal",
			"instance-data.ec2.internal",
			"metadata.azure.com");

	private final Set<String> allowedHosts;

	private final AddressResolver addressResolver;

	RestTaskUrlPolicy(String allowedHosts) {
		this(allowedHosts, InetAddress::getAllByName);
	}

	RestTaskUrlPolicy(String allowedHosts, AddressResolver addressResolver) {
		this.allowedHosts = parseAllowedHosts(allowedHosts);
		this.addressResolver = addressResolver;
	}

	URI validate(String target) {
		return resolve(target).uri();
	}

	ResolvedTarget resolve(String target) {
		if (target == null || target.isBlank()) {
			throw invalid("不能为空");
		}

		final URI uri;
		try {
			uri = new URI(target.trim());
		}
		catch (URISyntaxException ex) {
			throw invalid("格式无效");
		}

		String scheme = uri.getScheme();
		if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
			throw invalid("仅支持HTTP或HTTPS协议");
		}
		if (uri.getRawUserInfo() != null) {
			throw invalid("不能包含用户凭据");
		}

		String host = canonicalHost(uri.getHost());
		if (host == null || host.isBlank()) {
			throw invalid("缺少有效主机名");
		}
		if (FORBIDDEN_METADATA_HOSTS.contains(host)) {
			throw invalid("禁止访问云实例元数据地址");
		}

		InetAddress[] addresses;
		try {
			addresses = addressResolver.resolve(host);
		}
		catch (UnknownHostException ex) {
			throw invalid("主机名无法解析");
		}
		if (addresses == null || addresses.length == 0 || Arrays.stream(addresses).anyMatch(Objects::isNull)) {
			throw invalid("主机名无法解析");
		}

		if (!allowedHosts.contains(host) && Arrays.stream(addresses).anyMatch(RestTaskUrlPolicy::isRestricted)) {
			throw invalid("禁止访问本机或非公网地址");
		}
		return new ResolvedTarget(uri, host, List.of(addresses));
	}

	private static Set<String> parseAllowedHosts(String configuredHosts) {
		if (configuredHosts == null || configuredHosts.isBlank()) {
			return Collections.emptySet();
		}
		return Arrays.stream(configuredHosts.split(","))
			.map(RestTaskUrlPolicy::canonicalHost)
			.filter(host -> host != null && !host.isBlank())
			.collect(Collectors.toUnmodifiableSet());
	}

	static String canonicalHost(String host) {
		if (host == null) {
			return null;
		}
		String normalized = host.trim();
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		while (normalized.endsWith(".")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.indexOf(':') >= 0) {
			return normalized.toLowerCase(Locale.ROOT);
		}
		try {
			return IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static boolean isRestricted(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}

		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			return isRestrictedIpv4(bytes, 0);
		}
		if (bytes.length != 16) {
			return true;
		}

		int first = unsigned(bytes[0]);
		int second = unsigned(bytes[1]);
		if ((first & 0xfe) == 0xfc || (first == 0x20 && second == 0x01
				&& unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8)) {
			return true;
		}

		boolean ipv4Mapped = true;
		for (int index = 0; index < 10; index++) {
			ipv4Mapped &= bytes[index] == 0;
		}
		ipv4Mapped &= unsigned(bytes[10]) == 0xff && unsigned(bytes[11]) == 0xff;
		return ipv4Mapped && isRestrictedIpv4(bytes, 12);
	}

	private static boolean isRestrictedIpv4(byte[] bytes, int offset) {
		int first = unsigned(bytes[offset]);
		int second = unsigned(bytes[offset + 1]);
		int third = unsigned(bytes[offset + 2]);

		return first == 0
				|| first == 10
				|| first == 127
				|| (first == 100 && second >= 64 && second <= 127)
				|| (first == 169 && second == 254)
				|| (first == 172 && second >= 16 && second <= 31)
				|| (first == 192 && second == 0 && third == 0)
				|| (first == 192 && second == 0 && third == 2)
				|| (first == 192 && second == 88 && third == 99)
				|| (first == 192 && second == 168)
				|| (first == 198 && (second == 18 || second == 19))
				|| (first == 198 && second == 51 && third == 100)
				|| (first == 203 && second == 0 && third == 113)
				|| first >= 224;
	}

	private static int unsigned(byte value) {
		return value & 0xff;
	}

	private static IllegalArgumentException invalid(String reason) {
		return new IllegalArgumentException("定时任务REST地址" + reason);
	}

	@FunctionalInterface
	interface AddressResolver {

		InetAddress[] resolve(String host) throws UnknownHostException;

	}

	record ResolvedTarget(URI uri, String host, List<InetAddress> addresses) {

		ResolvedTarget {
			addresses = List.copyOf(addresses);
		}

	}

}
