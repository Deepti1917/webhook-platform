package com.webhookplatform.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs webhook deliveries. The signed string is "{timestamp}.{body}" so receivers can also reject
 * old timestamps (replay protection), the same scheme Stripe uses. Header value: "v1=" + hex(HMAC-SHA256).
 */
public final class HmacSigner {

    private HmacSigner() {}

    public static String sign(String secret, long timestampSeconds, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((timestampSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(sig);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    public static boolean verify(String secret, long timestampSeconds, String body, String header) {
        if (header == null) {
            return false;
        }
        String expected = sign(secret, timestampSeconds, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }
}
