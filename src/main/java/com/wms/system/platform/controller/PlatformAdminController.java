package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformAdminDetailResponse;
import com.wms.system.platform.dto.PlatformAdminSummaryResponse;
import com.wms.system.platform.service.PlatformAdminDirectoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminController {
    private final PlatformAdminDirectoryService service;

    @GetMapping
    public Page<PlatformAdminSummaryResponse> admins(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String mfaStatus,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest request
    ) {
        return service.list(keyword, enabled, role, mfaStatus, page, size, request);
    }

    @GetMapping("/{targetUserId}")
    public PlatformAdminDetailResponse admin(
        @PathVariable Long targetUserId,
        HttpServletRequest request
    ) {
        return service.detail(targetUserId, request);
    }
}
