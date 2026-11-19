package com.webhookplatform.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for secrets that must be recoverable (webhook signing secrets).
 * Output format: base64( 12-byte random IV || ciphertext+tag ). A fresh IV is used for every call.
 */
public final class SecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "SECRET_ENCRYPTION_KEY must be base64 of exactly 32 random bytes (generate with: openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public static SecretCipher fromBase64(String base64Key) {
        try {
            return new SecretCipher(Base64.getDecoder().decode(base64Key.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "SECRET_ENCRYPTION_KEY must be base64 of exactly 32 random bytes (generate with: openssl rand -base64 32)");
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            if (in.length <= IV_LENGTH) {
                throw new IllegalStateException("Ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(in, 0, IV_LENGTH);
            byte[] ct = Arrays.copyOfRange(in, IV_LENGTH, in.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Decryption failed (wrong key or corrupted data)", e);
        }
    }
}
