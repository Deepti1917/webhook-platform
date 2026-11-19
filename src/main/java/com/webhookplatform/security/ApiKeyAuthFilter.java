package com.webhookplatform.security;

import com.webhookplatform.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the X-API-Key header.
 *  - the admin key (from the environment, kept only as a SHA-256 hash in memory) grants ROLE_ADMIN
 *  - a tenant key (looked up by its SHA-256 hash) grants ROLE_TENANT bound to that tenant's id
 * Repeated failures from one IP are throttled, and while throttled keys are not even checked,
 * so brute-forcing is impractical (and with 256-bit keys, impossible anyway).
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final TenantRepository tenants;
    private final String adminKeyHash;
    private final RateLimiter failureLimiter;

    public ApiKeyAuthFilter(TenantRepository tenants, String adminKeyHash, RateLimiter failureLimiter) {
        this.tenants = tenants;
        this.adminKeyHash = adminKeyHash;
        this.failureLimiter = failureLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null) {
            chain.doFilter(request, response); // no credentials: the entry point will answer 401
            return;
        }

        String ip = request.getRemoteAddr();
        if (!failureLimiter.hasCapacity(ip)) {
            JsonErrors.tooManyRequests(response, failureLimiter.retryAfterSeconds());
            return;
        }

        Authentication authentication = null;
        if (key.length() >= 20 && key.length() <= 200) {
            String hash = ApiKeys.sha256Hex(key);
            if (ApiKeys.constantTimeEquals(hash, adminKeyHash)) {
                authentication = new UsernamePasswordAuthenticationToken(
                        "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            } else {
                authentication = tenants.findByApiKeyHashAndActiveTrue(hash)
                        .<Authentication>map(t -> new UsernamePasswordAuthenticationToken(
                                new TenantPrincipal(t.getId()), null, List.of(new SimpleGrantedAuthority("ROLE_TENANT"))))
                        .orElse(null);
            }
        }

        if (authentication == null) {
            failureLimiter.tryAcquire(ip);
            JsonErrors.write(response, 401, "unauthorized", "Missing or invalid API key");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
