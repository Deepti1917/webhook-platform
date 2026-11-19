package com.webhookplatform.event;

import com.webhookplatform.event.EventDtos.CreateEventRequest;
import com.webhookplatform.event.EventDtos.EventResponse;
import com.webhookplatform.security.TenantPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    /** 202 = accepted for delivery. 200 = this Idempotency-Key was already processed (nothing new created). */
    @PostMapping
    ResponseEntity<EventResponse> create(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "^[A-Za-z0-9_-]{1,100}$", message = "1-100 characters: letters, digits, _ -")
                    String idempotencyKey,
            @Valid @RequestBody CreateEventRequest request) {
        EventService.IngestResult result =
                service.ingest(principal.tenantId(), idempotencyKey, request.type(), request.payload());
        EventResponse body = new EventResponse(
                result.event().getId(),
                result.event().getType(),
                result.event().getCreatedAt(),
                result.deliveries(),
                result.duplicate());
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
    }
}
