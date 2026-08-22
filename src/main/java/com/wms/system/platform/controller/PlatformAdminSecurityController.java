package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformAdminMfaResetRequest;
import com.wms.system.platform.dto.PlatformReauthenticationChallengeResponse;
import com.wms.system.platform.service.PlatformMfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminSecurityController {
    private final PlatformMfaService service;

    @PostMapping("/{targetUserId}/mfa-reset/challenge")
    public PlatformReauthenticationChallengeResponse startMfaReset(
        @PathVariable Long targetUserId
    ) {
        return service.startAdminMfaReset(targetUserId);
    }

    @PostMapping("/{targetUserId}/mfa-reset")
    public void resetMfa(
        @PathVariable Long targetUserId,
        @Valid @RequestBody PlatformAdminMfaResetRequest request,
        HttpServletRequest http
    ) {
        service.adminResetMfa(targetUserId, request, http);
    }
}
