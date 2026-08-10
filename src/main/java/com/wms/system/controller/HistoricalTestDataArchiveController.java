package com.wms.system.controller;

import com.wms.system.dto.sales.HistoricalTestDataArchivePreview;
import com.wms.system.dto.sales.HistoricalTestDataArchiveRequest;
import com.wms.system.dto.sales.HistoricalTestDataArchiveResult;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.HistoricalTestDataArchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/historical-test-data/sales-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class HistoricalTestDataArchiveController {

    private final HistoricalTestDataArchiveService archiveService;

    @GetMapping("/{id}/archive-preview")
    public ResponseEntity<HistoricalTestDataArchivePreview> preview(@PathVariable("id") Long id) {
        return ResponseEntity.ok(archiveService.preview(id));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<HistoricalTestDataArchiveResult> archive(
        @PathVariable("id") Long id,
        @Valid @RequestBody HistoricalTestDataArchiveRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(archiveService.archive(
            id,
            request,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }
}
