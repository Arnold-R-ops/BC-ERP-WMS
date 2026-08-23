package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformAdminMutationResponse;
import com.wms.system.platform.dto.PlatformAdminStatusChallengeRequest;
import com.wms.system.platform.dto.PlatformAdminStatusChallengeResponse;
import com.wms.system.platform.dto.PlatformAdminStatusMutationRequest;
import com.wms.system.platform.service.PlatformAdminStatusService;
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
public class PlatformAdminStatusController {
    private final PlatformAdminStatusService service;

    @PostMapping("/{targetUserId}/status-change/challenge")
    public PlatformAdminStatusChallengeResponse startChallenge(
        @PathVariable Long targetUserId,
        @Valid @RequestBody PlatformAdminStatusChallengeRequest request,
        HttpServletRequest http
    ) {
        return service.startChallenge(targetUserId, request.desiredEnabled(), http);
    }

    @PostMapping("/{targetUserId}/disable")
    public PlatformAdminMutationResponse disable(
        @PathVariable Long targetUserId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody PlatformAdminStatusMutationRequest request,
        HttpServletRequest http
    ) {
        return service.changeStatus(targetUserId, false, idempotencyKey, request, http);
    }

    @PostMapping("/{targetUserId}/enable")
    public PlatformAdminMutationResponse enable(
        @PathVariable Long targetUserId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody PlatformAdminStatusMutationRequest request,
        HttpServletRequest http
    ) {
        return service.changeStatus(targetUserId, true, idempotencyKey, request, http);
    }
}
