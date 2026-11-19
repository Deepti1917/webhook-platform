package com.webhookplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security headers that Spring Security does not add by default. Registered first in the chain so that
 * they are present even on responses rejected by later filters (401, 413, 429).
 * (CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy and Cache-Control come from SecurityConfig.)
 */
public class ExtraHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        chain.doFilter(request, response);
    }
}
