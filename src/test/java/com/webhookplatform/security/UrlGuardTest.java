package com.webhookplatform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UrlGuardTest {

    private static final Map<String, String> DNS = Map.of(
            "public.example", "93.184.216.34",
            "localhost", "127.0.0.1",
            "internal.example", "10.1.2.3",
            "metadata.example", "169.254.169.254",
            "lan.example", "192.168.1.10",
            "v6local.example", "fd00::1",
            "v6loop.example", "::1",
            "cgnat.example", "100.64.0.1",
            "zero.example", "0.0.0.0",
            "docker.example", "172.17.0.1");

    private static UrlGuard guard(boolean allowPrivate) {
        return new UrlGuard(allowPrivate, host -> {
            String ip = DNS.get(host);
            if (ip == null) {
                throw new UnknownHostException(host);
            }
            return new InetAddress[] {InetAddress.getByName(ip)};
        });
    }

    private static void rejected(String url) {
        assertThrows(UnsafeUrlException.class, () -> guard(false).validate(url), url);
    }

    @Test
    void acceptsPublicHttps() {
        assertEquals("https://public.example/hook", guard(false).validate("https://public.example/hook").toString());
        assertEquals("https://public.example:8443/x", guard(false).validate(" https://public.example:8443/x ").toString());
    }

    @Test
    void rejectsPlainHttpAndOtherSchemes() {
        rejected("http://public.example/hook");
        rejected("ftp://public.example/hook");
        rejected("file:///etc/passwd");
        rejected("javascript:alert(1)");
    }

    @Test
    void rejectsInternalTargets() {
        rejected("https://localhost/hook");
        rejected("https://internal.example/hook");
        rejected("https://metadata.example/latest/meta-data");
        rejected("https://lan.example/");
        rejected("https://v6local.example/");
        rejected("https://v6loop.example/");
        rejected("https://cgnat.example/");
        rejected("https://zero.example/");
        rejected("https://docker.example/");
    }

    @Test
    void rejectsLiteralAddressesAndTricks() {
        rejected("https://127.0.0.1/");
        rejected("https://169.254.169.254/");
        rejected("https://[::1]/");
        rejected("https://[::ffff:127.0.0.1]/");
        rejected("https://user:pass@public.example/");
        rejected("https:///nohost");
        rejected("not a url");
        rejected("");
        rejected(null);
        rejected("https://" + "a".repeat(2100) + ".example/");
    }

    @Test
    void rejectsUnresolvableHosts() {
        rejected("https://does-not-exist.example/");
    }

    @Test
    void rejectsHostWithAnyPrivateAddress() {
        UrlGuard mixed = new UrlGuard(false, host -> new InetAddress[] {
            InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1")
        });
        assertThrows(UnsafeUrlException.class, () -> mixed.validate("https://mixed.example/"));
    }

    @Test
    void devModeAllowsLocalHttp() {
        assertEquals("http://localhost:9000/hook", guard(true).validate("http://localhost:9000/hook").toString());
    }
}
