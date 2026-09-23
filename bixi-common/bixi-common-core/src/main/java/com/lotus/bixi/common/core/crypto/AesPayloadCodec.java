package com.lotus.bixi.common.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** AES-CBC codec compatible with bixi-ui/src/utils/other.ts. */
public final class AesPayloadCodec {

	private static final String IV_SALT = "bixi-iv-salt-2025";

	private AesPayloadCodec() {
	}

	public static String encrypt(String plaintext, String password) {
		return crypt(Cipher.ENCRYPT_MODE, plaintext, password);
	}

	public static String decrypt(String ciphertext, String password) {
		return crypt(Cipher.DECRYPT_MODE, ciphertext, password);
	}

	private static String crypt(int mode, String value, String password) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(mode, new SecretKeySpec(digest("SHA-256", password), "AES"),
					new IvParameterSpec(digest("MD5", password + IV_SALT)));
			byte[] input = mode == Cipher.DECRYPT_MODE ? Base64.getDecoder().decode(value)
					: value.getBytes(StandardCharsets.UTF_8);
			byte[] output = cipher.doFinal(input);
			return mode == Cipher.DECRYPT_MODE ? new String(output, StandardCharsets.UTF_8)
					: Base64.getEncoder().encodeToString(output);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid AES payload", ex);
		}
	}

	private static byte[] digest(String algorithm, String value) throws Exception {
		return MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8));
	}

}
