package com.webhookplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-tenant limit, applied after authentication so one noisy tenant cannot starve the others. */
public class TenantRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;

    public TenantRateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal) {
            if (!limiter.tryAcquire(principal.tenantId().toString())) {
                JsonErrors.tooManyRequests(response, limiter.retryAfterSeconds());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
