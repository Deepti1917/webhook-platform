package com.webhookplatform.endpoint;

import com.webhookplatform.common.ConflictException;
import com.webhookplatform.common.NotFoundException;
import com.webhookplatform.security.ApiKeys;
import com.webhookplatform.security.SecretCipher;
import com.webhookplatform.security.UrlGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {

    static final int MAX_ENDPOINTS_PER_TENANT = 20;

    public record WithSecret(WebhookEndpoint endpoint, String secret) {}

    private final WebhookEndpointRepository endpoints;
    private final UrlGuard urlGuard;
    private final SecretCipher cipher;

    public EndpointService(WebhookEndpointRepository endpoints, UrlGuard urlGuard, SecretCipher cipher) {
        this.endpoints = endpoints;
        this.urlGuard = urlGuard;
        this.cipher = cipher;
    }

    @Transactional
    public WithSecret create(UUID tenantId, String url) {
        if (endpoints.countByTenantId(tenantId) >= MAX_ENDPOINTS_PER_TENANT) {
            throw new ConflictException("Endpoint limit reached (" + MAX_ENDPOINTS_PER_TENANT + ")");
        }
        String safeUrl = urlGuard.validate(url).toString(); // SSRF check; throws 400 on unsafe URLs
        String secret = ApiKeys.generate("whsec_");
        WebhookEndpoint endpoint = endpoints.save(new WebhookEndpoint(tenantId, safeUrl, cipher.encrypt(secret)));
        return new WithSecret(endpoint, secret);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> list(UUID tenantId) {
        return endpoints.findByTenantIdOrderByCreatedAtAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint get(UUID tenantId, UUID id) {
        return endpoints.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Endpoint not found"));
    }

    @Transactional
    public WebhookEndpoint setActive(UUID tenantId, UUID id, boolean active) {
        WebhookEndpoint endpoint = get(tenantId, id);
        endpoint.setActive(active);
        return endpoint;
    }

    @Transactional
    public WithSecret rotateSecret(UUID tenantId, UUID id) {
        WebhookEndpoint endpoint = get(tenantId, id);
        String secret = ApiKeys.generate("whsec_");
        endpoint.replaceSecret(cipher.encrypt(secret));
        return new WithSecret(endpoint, secret);
    }
}
