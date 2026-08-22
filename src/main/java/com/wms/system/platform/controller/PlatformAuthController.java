package com.wms.system.platform.controller;

import com.wms.system.platform.dto.PlatformAuthResponse;
import com.wms.system.platform.dto.PlatformLoginRequest;
import com.wms.system.platform.dto.PlatformMfaVerifyRequest;
import com.wms.system.platform.dto.PlatformReauthenticationChallengeResponse;
import com.wms.system.platform.dto.PlatformSessionResponse;
import com.wms.system.platform.dto.PlatformRecoveryCodeRegenerationRequest;
import com.wms.system.platform.dto.PlatformRecoveryCodeStatusResponse;
import com.wms.system.platform.dto.PlatformRecoveryCodesResponse;
import com.wms.system.platform.service.PlatformMfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformMfaService service;

    @PostMapping("/login")
    public PlatformAuthResponse login(@Valid @RequestBody PlatformLoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/mfa/enroll/confirm")
    public PlatformAuthResponse confirmEnrollment(@Valid @RequestBody PlatformMfaVerifyRequest request,
                                                   HttpServletRequest http) {
        return service.confirmEnrollment(request, http);
    }

    @PostMapping("/mfa/verify")
    public PlatformAuthResponse verify(@Valid @RequestBody PlatformMfaVerifyRequest request,
                                       HttpServletRequest http) {
        return service.verify(request, http);
    }

    @GetMapping("/me")
    public PlatformSessionResponse currentSession(
        @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        return service.currentSession(authorizationHeader);
    }

    @PostMapping("/logout-all/challenge")
    public PlatformReauthenticationChallengeResponse startLogoutAll() {
        return service.startLogoutAll();
    }

    @PostMapping("/logout-all")
    public void logoutAll(@Valid @RequestBody PlatformMfaVerifyRequest request,
                          HttpServletRequest http) {
        service.logoutAll(request, http);
    }

    @GetMapping("/recovery-codes/status")
    public PlatformRecoveryCodeStatusResponse recoveryCodeStatus() {
        return service.recoveryCodeStatus();
    }

    @PostMapping("/recovery-codes/challenge")
    public PlatformReauthenticationChallengeResponse startRecoveryCodeRegeneration() {
        return service.startRecoveryCodeRegeneration();
    }

    @PostMapping("/recovery-codes/regenerate")
    public PlatformRecoveryCodesResponse regenerateRecoveryCodes(
        @Valid @RequestBody PlatformRecoveryCodeRegenerationRequest request,
        HttpServletRequest http
    ) {
        return service.regenerateRecoveryCodes(request, http);
    }
}
