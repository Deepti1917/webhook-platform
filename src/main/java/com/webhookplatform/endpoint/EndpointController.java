package com.webhookplatform.endpoint;

import com.webhookplatform.endpoint.EndpointDtos.CreateEndpointRequest;
import com.webhookplatform.endpoint.EndpointDtos.EndpointResponse;
import com.webhookplatform.endpoint.EndpointDtos.EndpointSecretResponse;
import com.webhookplatform.endpoint.EndpointDtos.SetActiveRequest;
import com.webhookplatform.security.TenantPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The tenant comes from the authenticated principal, never from the URL or the request body. */
@RestController
@RequestMapping("/api/v1/endpoints")
public class EndpointController {

    private final EndpointService service;

    public EndpointController(EndpointService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<EndpointSecretResponse> create(
            @AuthenticationPrincipal TenantPrincipal principal, @Valid @RequestBody CreateEndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EndpointSecretResponse.from(service.create(principal.tenantId(), request.url())));
    }

    @GetMapping
    List<EndpointResponse> list(@AuthenticationPrincipal TenantPrincipal principal) {
        return service.list(principal.tenantId()).stream().map(EndpointResponse::from).toList();
    }

    @GetMapping("/{id}")
    EndpointResponse get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id) {
        return EndpointResponse.from(service.get(principal.tenantId(), id));
    }

    @PatchMapping("/{id}")
    EndpointResponse setActive(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        return EndpointResponse.from(service.setActive(principal.tenantId(), id, request.active()));
    }

    @PostMapping("/{id}/rotate-secret")
    EndpointSecretResponse rotateSecret(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID id) {
        return EndpointSecretResponse.from(service.rotateSecret(principal.tenantId(), id));
    }
}
