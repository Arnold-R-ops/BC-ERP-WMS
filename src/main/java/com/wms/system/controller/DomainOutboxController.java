package com.wms.system.controller;

import com.wms.system.entity.DomainOutbox;
import com.wms.system.service.DomainOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outbox/events")
@RequiredArgsConstructor
public class DomainOutboxController {

    private final DomainOutboxService outboxService;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('system:admin', 'TENANT_ADMIN')")
    public ResponseEntity<List<DomainOutbox>> listPending() {
        return ResponseEntity.ok(outboxService.listPending());
    }

    @PostMapping("/{id}/published")
    @PreAuthorize("hasAnyAuthority('system:admin', 'TENANT_ADMIN')")
    public ResponseEntity<DomainOutbox> markPublished(@PathVariable("id") Long id) {
        return ResponseEntity.ok(outboxService.markPublished(id));
    }
}
