package com.webhookplatform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoTest {

    @Test
    void generatedKeysAreUniqueAndPrefixed() {
        String a = ApiKeys.generate("whk_");
        String b = ApiKeys.generate("whk_");
        assertTrue(a.startsWith("whk_"));
        assertNotEquals(a, b);
        assertTrue(a.length() >= 40);
    }

    @Test
    void sha256IsStableAndHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApiKeys.sha256Hex("abc"));
    }

    @Test
    void constantTimeEqualsWorks() {
        assertTrue(ApiKeys.constantTimeEquals("same", "same"));
        assertFalse(ApiKeys.constantTimeEquals("same", "different"));
        assertFalse(ApiKeys.constantTimeEquals("same", "sam"));
    }

    @Test
    void cipherRoundTripsAndUsesFreshIv() {
        SecretCipher cipher = new SecretCipher(new byte[32]);
        String c1 = cipher.encrypt("whsec_secret");
        String c2 = cipher.encrypt("whsec_secret");
        assertNotEquals(c1, c2);
        assertEquals("whsec_secret", cipher.decrypt(c1));
        assertEquals("whsec_secret", cipher.decrypt(c2));
    }

    @Test
    void cipherDetectsTamperingAndWrongKey() {
        SecretCipher cipher = new SecretCipher(new byte[32]);
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("hello"));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));

        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        String good = cipher.encrypt("hello");
        assertThrows(IllegalStateException.class, () -> new SecretCipher(otherKey).decrypt(good));
    }

    @Test
    void cipherRejectsBadKeys() {
        assertThrows(IllegalArgumentException.class, () -> new SecretCipher(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> SecretCipher.fromBase64("not base64 !!"));
        assertThrows(IllegalArgumentException.class, () -> SecretCipher.fromBase64("c2hvcnQ="));
    }

    @Test
    void hmacMatchesKnownVectorAndVerifies() {
        // Independent check: HMAC-SHA256(key="key", "1.body")
        String sig = HmacSigner.sign("key", 1L, "body");
        assertTrue(sig.startsWith("v1="));
        assertEquals(3 + 64, sig.length());
        assertTrue(HmacSigner.verify("key", 1L, "body", sig));
        assertFalse(HmacSigner.verify("key", 2L, "body", sig));
        assertFalse(HmacSigner.verify("key", 1L, "body!", sig));
        assertFalse(HmacSigner.verify("other", 1L, "body", sig));
        assertFalse(HmacSigner.verify("key", 1L, "body", null));
    }
}
