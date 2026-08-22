package com.wms.system.signup.controller;

import com.wms.system.signup.dto.*;
import com.wms.system.signup.service.SignupVerificationService;
import com.wms.system.signup.service.TenantProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/v1")
@RequiredArgsConstructor
public class PublicSignupController {
    private final SignupVerificationService verificationService;
    private final TenantProvisioningService provisioningService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> begin(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BeginSignupRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(verificationService.begin(request, idempotencyKey));
    }

    @PostMapping("/signup/{signupId}/resend")
    public ResponseEntity<SignupResponse> resend(@PathVariable String signupId) {
        return ResponseEntity.accepted().body(verificationService.resend(signupId));
    }

    @PostMapping("/email-verification")
    public SignupResponse verify(@Valid @RequestBody VerifyEmailRequest request) {
        return verificationService.verify(request);
    }

    @PostMapping("/signup/{signupId}/complete")
    public SignupResponse complete(
            @PathVariable String signupId,
            @Valid @RequestBody CompleteSignupRequest request) {
        return provisioningService.complete(signupId, request);
    }
}
