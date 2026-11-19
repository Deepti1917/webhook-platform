package com.webhookplatform.security;

import java.util.UUID;

/**
 * The authenticated tenant. Controllers take the tenant id from here and NEVER from the URL or body,
 * which is what stops one tenant from reading another's data (IDOR).
 */
public record TenantPrincipal(UUID tenantId) {}
