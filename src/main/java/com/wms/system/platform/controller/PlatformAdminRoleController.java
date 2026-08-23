package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformAdminRoleChangeChallengeRequest;
import com.wms.system.platform.dto.PlatformAdminRoleChangeMutationRequest;
import com.wms.system.platform.dto.PlatformAdminRoleChangeResponse;
import com.wms.system.platform.dto.PlatformAdminStatusChallengeResponse;
import com.wms.system.platform.service.PlatformAdminRoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminRoleController {
    private final PlatformAdminRoleService service;

    @PostMapping("/{targetUserId}/roles/change/challenge")
    public PlatformAdminStatusChallengeResponse startChallenge(
        @PathVariable Long targetUserId,
        @Valid @RequestBody PlatformAdminRoleChangeChallengeRequest request,
        HttpServletRequest http
    ) {
        return service.startChallenge(targetUserId, request.jobRoleCode(), http);
    }

    @PutMapping("/{targetUserId}/roles")
    public PlatformAdminRoleChangeResponse changeRole(
        @PathVariable Long targetUserId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody PlatformAdminRoleChangeMutationRequest request,
        HttpServletRequest http
    ) {
        return service.changeRole(targetUserId, idempotencyKey, request, http);
    }
}
