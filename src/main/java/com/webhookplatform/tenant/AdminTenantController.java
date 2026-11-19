package com.webhookplatform.tenant;

import com.webhookplatform.tenant.TenantDtos.CreateTenantRequest;
import com.webhookplatform.tenant.TenantDtos.SetActiveRequest;
import com.webhookplatform.tenant.TenantDtos.TenantCredentialsResponse;
import com.webhookplatform.tenant.TenantDtos.TenantResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (ROLE_ADMIN enforced in SecurityConfig). Authenticated with the ADMIN_API_KEY. */
@RestController
@RequestMapping("/admin/tenants")
public class AdminTenantController {

    private final TenantService service;

    public AdminTenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TenantCredentialsResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TenantCredentialsResponse.from(service.create(request.name())));
    }

    @GetMapping
    List<TenantResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return service.list(page, size).stream().map(TenantResponse::from).toList();
    }

    @PostMapping("/{id}/rotate-key")
    TenantCredentialsResponse rotateKey(@PathVariable UUID id) {
        return TenantCredentialsResponse.from(service.rotateKey(id));
    }

    @PatchMapping("/{id}")
    TenantResponse setActive(@PathVariable UUID id, @Valid @RequestBody SetActiveRequest request) {
        return TenantResponse.from(service.setActive(id, request.active()));
    }
}
