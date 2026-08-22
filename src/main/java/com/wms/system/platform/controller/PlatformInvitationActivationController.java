package com.wms.system.platform.controller;
import com.wms.system.platform.dto.*; import com.wms.system.platform.service.PlatformAdminInvitationService;
import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/platform/auth/invitations") @RequiredArgsConstructor
public class PlatformInvitationActivationController {
    private final PlatformAdminInvitationService service;
    @PostMapping("/status") public PlatformInvitationStatusResponse status(@Valid @RequestBody PlatformInvitationTokenRequest r){return service.status(r);}
    @PostMapping("/activate") public PlatformAuthResponse activate(@Valid @RequestBody PlatformInvitationActivateRequest r,HttpServletRequest h){return service.activate(r,h);}
}
