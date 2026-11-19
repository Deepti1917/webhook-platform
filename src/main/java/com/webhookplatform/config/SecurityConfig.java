package com.webhookplatform.config;

import com.webhookplatform.security.ApiKeyAuthFilter;
import com.webhookplatform.security.ApiKeys;
import com.webhookplatform.security.ExtraHeadersFilter;
import com.webhookplatform.security.IpRateLimitFilter;
import com.webhookplatform.security.JsonErrors;
import com.webhookplatform.security.RateLimiter;
import com.webhookplatform.security.RequestSizeLimitFilter;
import com.webhookplatform.security.TenantRateLimitFilter;
import com.webhookplatform.tenant.TenantRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final long MAX_BODY_BYTES = 1_048_576; // 1 MiB (event payloads are further limited to 256 KB)

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppProperties props,
            TenantRepository tenants,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors)
            throws Exception {

        AppProperties.RateLimit limits = props.rateLimit();
        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(
                tenants,
                ApiKeys.sha256Hex(props.adminApiKey()),
                new RateLimiter(limits.authFailuresPerMinute(), 100_000));
        IpRateLimitFilter ipFilter = new IpRateLimitFilter(new RateLimiter(limits.ipPerMinute(), 100_000));
        TenantRateLimitFilter tenantFilter = new TenantRateLimitFilter(new RateLimiter(limits.tenantPerMinute(), 100_000));

        http
                // Stateless API authenticated by a header, no cookies and no browser sessions, so there is
                // no ambient credential for CSRF to abuse. CSRF protection would add nothing here.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/admin/**", "/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasRole("TENANT")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                JsonErrors.write(res, 401, "unauthorized", "Missing or invalid API key"))
                        .accessDeniedHandler((req, res, ex) ->
                                JsonErrors.write(res, 403, "forbidden", "You do not have access to this resource")))
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(ipFilter, ApiKeyAuthFilter.class)
                .addFilterBefore(new RequestSizeLimitFilter(MAX_BODY_BYTES), IpRateLimitFilter.class)
                .addFilterBefore(new ExtraHeadersFilter(), RequestSizeLimitFilter.class)
                .addFilterAfter(tenantFilter, ApiKeyAuthFilter.class);

        return http.build();
    }

    /**
- CORS: nothing is allowed unless origins are listed explicitly in CORS_ALLOWED_ORIGINS.
- Wildcards are rejected at startup. Credentials are never allowed. Admin routes get no CORS at all.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = props.cors().allowedOrigins();
        for (String origin : origins) {
            if (origin.isBlank() || origin.contains("*")) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list exact origins; wildcards are not allowed");
            }
        }
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
            config.setAllowedHeaders(List.of("X-API-Key", "Idempotency-Key", "Content-Type"));
            config.setAllowCredentials(false);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }

    /**
- There are no username/password users. Defining this bean stops Spring Boot from creating its
- default user with a random password that it prints to the log at startup.
     */
    @Bean
    UserDetailsService noUsers() {
        return username -> {
            throw new UsernameNotFoundException("Password login is not supported");
        };
    }
}