package com.webhookplatform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * API key helpers.
 *
 * Keys are 256 bits of randomness, so a fast hash (SHA-256) is the correct tool: the input cannot
 * be brute-forced. Slow password hashes (bcrypt/argon2) are for low-entropy, human-chosen passwords.
 * Only the hash is stored; the raw key is shown to the caller exactly once.
 */
public final class ApiKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {}

    public static String generate(String prefix) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Short, non-secret identifier so a key can be recognised in lists and logs. */
    public static String displayPrefix(String key) {
        return key.substring(0, Math.min(key.length(), 12));
    }
}
