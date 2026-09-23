package com.lotus.bixi.common.core.sensitive;

import com.lotus.bixi.common.core.context.TenantContextHolder;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Tenant-isolated DFA trie with a bounded in-memory cache. */
@Component
public class SensitiveWordEngine {

	private static final int MAX_TENANTS = 100;
	private final Map<Long, TrieNode> roots = new LinkedHashMap<>(16, .75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, TrieNode> eldest) {
			return size() > MAX_TENANTS;
		}
	};

	public synchronized void reload(Long tenantId, Collection<String> words) {
		if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
		TrieNode root = new TrieNode();
		for (String word : words) {
			if (word == null || word.isBlank()) continue;
			TrieNode node = root;
			for (char c : word.trim().toLowerCase().toCharArray()) {
				node = node.children.computeIfAbsent(c, ignored -> new TrieNode());
			}
			node.terminal = true;
		}
		roots.put(tenantId, root);
	}

	public boolean containsSensitiveWord(String text) {
		Long tenantId = TenantContextHolder.get();
		return tenantId != null && containsSensitiveWord(tenantId, text);
	}

	public synchronized boolean containsSensitiveWord(Long tenantId, String text) {
		if (tenantId == null || text == null || text.isEmpty()) return false;
		TrieNode root = roots.get(tenantId);
		if (root == null) return false;
		for (int start = 0; start < text.length(); start++) {
			TrieNode node = root;
			for (int index = start; index < text.length(); index++) {
				node = node.children.get(Character.toLowerCase(text.charAt(index)));
				if (node == null) break;
				if (node.terminal) return true;
			}
		}
		return false;
	}

	public String replaceSensitiveWords(String text, String replacement) {
		Long tenantId = TenantContextHolder.get();
		return tenantId == null ? text : replaceSensitiveWords(tenantId, text, replacement);
	}

	public synchronized String replaceSensitiveWords(Long tenantId, String text, String replacement) {
		if (tenantId == null || text == null || text.isEmpty()) return text;
		TrieNode root = roots.get(tenantId);
		if (root == null) return text;
		StringBuilder result = new StringBuilder(text.length());
		for (int start = 0; start < text.length();) {
			TrieNode node = root;
			int matchEnd = -1;
			for (int index = start; index < text.length(); index++) {
				node = node.children.get(Character.toLowerCase(text.charAt(index)));
				if (node == null) break;
				if (node.terminal) matchEnd = index + 1;
			}
			if (matchEnd > start) {
				result.append(replacement);
				start = matchEnd;
			}
			else {
				result.append(text.charAt(start++));
			}
		}
		return result.toString();
	}

	private static final class TrieNode {
		private final Map<Character, TrieNode> children = new LinkedHashMap<>();
		private boolean terminal;
	}

}
