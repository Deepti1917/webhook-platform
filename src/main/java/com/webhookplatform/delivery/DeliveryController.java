package com.webhookplatform.delivery;

import com.webhookplatform.delivery.DeliveryDtos.DeliveryDetailResponse;
import com.webhookplatform.delivery.DeliveryDtos.DeliveryResponse;
import com.webhookplatform.security.TenantPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/events/{eventId}/deliveries")
    List<DeliveryResponse> forEvent(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID eventId) {
        return service.listForEvent(principal.tenantId(), eventId);
    }

    @GetMapping("/api/v1/deliveries/{id}")
    DeliveryDetailResponse get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id) {
        return service.get(principal.tenantId(), id);
    }

    @PostMapping("/api/v1/deliveries/{id}/replay")
    DeliveryResponse replay(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id) {
        return service.replay(principal.tenantId(), id);
    }
}
