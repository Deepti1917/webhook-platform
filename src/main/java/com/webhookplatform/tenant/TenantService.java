package com.webhookplatform.tenant;

import com.webhookplatform.common.ConflictException;
import com.webhookplatform.common.NotFoundException;
import com.webhookplatform.security.ApiKeys;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private static final String KEY_PREFIX = "whk_";

    /** The raw API key exists only in this object and in the one HTTP response that returns it. */
    public record Created(Tenant tenant, String apiKey) {}

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional
    public Created create(String name) {
        if (tenants.existsByName(name)) {
            throw new ConflictException("A tenant with this name already exists");
        }
        String apiKey = ApiKeys.generate(KEY_PREFIX);
        Tenant tenant = new Tenant(name, ApiKeys.sha256Hex(apiKey), ApiKeys.displayPrefix(apiKey));
        try {
            tenants.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A tenant with this name already exists");
        }
        return new Created(tenant, apiKey);
    }

    @Transactional
    public Created rotateKey(UUID id) {
        Tenant tenant = tenants.findById(id).orElseThrow(() -> new NotFoundException("Tenant not found"));
        String apiKey = ApiKeys.generate(KEY_PREFIX);
        tenant.replaceApiKey(ApiKeys.sha256Hex(apiKey), ApiKeys.displayPrefix(apiKey));
        return new Created(tenant, apiKey);
    }

    @Transactional
    public Tenant setActive(UUID id, boolean active) {
        Tenant tenant = tenants.findById(id).orElseThrow(() -> new NotFoundException("Tenant not found"));
        tenant.setActive(active);
        return tenant;
    }

    @Transactional(readOnly = true)
    public List<Tenant> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100); // never let a caller request an unbounded page
        return tenants.findAll(PageRequest.of(safePage, safeSize, Sort.by("createdAt"))).getContent();
    }
}
