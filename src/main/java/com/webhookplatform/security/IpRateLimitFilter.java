package com.webhookplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Coarse per-IP limit, applied before authentication so unauthenticated floods are cheap to reject. */
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;

    public IpRateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.tryAcquire(request.getRemoteAddr())) {
            JsonErrors.tooManyRequests(response, limiter.retryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }
}
