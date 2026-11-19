package com.webhookplatform.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * SSRF protection. Webhook URLs are supplied by tenants, and the server will make requests to them,
 * so without this a tenant could point the platform at http://169.254.169.254 (cloud metadata),
 * localhost services, or the internal network.
 *
 * Rules: https only, no credentials in the URL, and every address the host resolves to must be public.
 * It is checked when an endpoint is registered AND again immediately before every delivery.
 *
 * Residual risk (documented in SECURITY.md): DNS can change between this check and the connection
 * (DNS rebinding). The window is milliseconds, redirects are disabled, and the request body only
 * contains the tenant's own event data, but run workers in a network segment that cannot reach
 * internal services if you need a hard guarantee.
 */
public final class UrlGuard {

    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final boolean allowPrivateNetworks;
    private final Resolver resolver;

    public UrlGuard(boolean allowPrivateNetworks) {
        this(allowPrivateNetworks, InetAddress::getAllByName);
    }

    public UrlGuard(boolean allowPrivateNetworks, Resolver resolver) {
        this.allowPrivateNetworks = allowPrivateNetworks;
        this.resolver = resolver;
    }

    public URI validate(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 2048) {
            throw new UnsafeUrlException("URL is missing or too long");
        }
        URI uri;
        try {
            uri = new URI(raw.strip());
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("URL is malformed");
        }
        String scheme = uri.getScheme();
        boolean httpsOk = "https".equalsIgnoreCase(scheme);
        boolean httpOk = allowPrivateNetworks && "http".equalsIgnoreCase(scheme);
        if (!httpsOk && !httpOk) {
            throw new UnsafeUrlException("URL must use https");
        }
        if (uri.getUserInfo() != null) {
            throw new UnsafeUrlException("URL must not contain credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeUrlException("URL must contain a host");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new UnsafeUrlException("Host could not be resolved");
        }
        if (addresses == null || addresses.length == 0) {
            throw new UnsafeUrlException("Host could not be resolved");
        }
        if (!allowPrivateNetworks) {
            for (InetAddress address : addresses) {
                if (isNonPublic(address)) {
                    throw new UnsafeUrlException("URL resolves to a non-public address");
                }
            }
        }
        return uri;
    }

    static boolean isNonPublic(InetAddress a) {
        if (a.isAnyLocalAddress()
                || a.isLoopbackAddress()
                || a.isLinkLocalAddress()
                || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            int b2 = b[2] & 0xFF;
            return b0 == 0 // 0.0.0.0/8
                    || (b0 == 100 && (b1 & 0xC0) == 64) // 100.64.0.0/10 carrier-grade NAT
                    || (b0 == 192 && b1 == 0 && b2 == 0) // 192.0.0.0/24 IETF protocol assignments
                    || (b0 == 198 && (b1 & 0xFE) == 18) // 198.18.0.0/15 benchmarking
                    || b0 >= 240; // 240.0.0.0/4 reserved + broadcast
        }
        if (b.length == 16) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            return (b0 & 0xFE) == 0xFC // fc00::/7 unique local
                    || (b0 == 0x20 && b1 == 0x02) // 2002::/16 6to4 (embeds an IPv4 address)
                    || (b0 == 0x00 && b1 == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B); // 64:ff9b::/96 NAT64
        }
        return true;
    }
}
