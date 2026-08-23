package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformAdminMfaResetRequest;
import com.wms.system.platform.dto.PlatformAdminSecurityChallengeResponse;
import com.wms.system.platform.dto.PlatformAdminSecurityMutationResponse;
import com.wms.system.platform.dto.PlatformAdminSessionRevokeRequest;
import com.wms.system.platform.service.PlatformAdminSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminSecurityController {
    private final PlatformAdminSecurityService service;

    @PostMapping("/{targetUserId}/sessions/revoke/challenge")
    public PlatformAdminSecurityChallengeResponse startSessionRevoke(
        @PathVariable Long targetUserId,
        HttpServletRequest http
    ) {
        return service.startSessionRevoke(targetUserId, http);
    }

    @PostMapping("/{targetUserId}/sessions/revoke")
    public PlatformAdminSecurityMutationResponse revokeSessions(
        @PathVariable Long targetUserId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody PlatformAdminSessionRevokeRequest request,
        HttpServletRequest http
    ) {
        return service.revokeSessions(targetUserId, idempotencyKey, request, http);
    }

    @PostMapping("/{targetUserId}/mfa-reset/challenge")
    public PlatformAdminSecurityChallengeResponse startMfaReset(
        @PathVariable Long targetUserId,
        HttpServletRequest http
    ) {
        return service.startMfaReset(targetUserId, http);
    }

    @PostMapping("/{targetUserId}/mfa-reset")
    public PlatformAdminSecurityMutationResponse resetMfa(
        @PathVariable Long targetUserId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody PlatformAdminMfaResetRequest request,
        HttpServletRequest http
    ) {
        return service.resetMfa(targetUserId, idempotencyKey, request, http);
    }
}
