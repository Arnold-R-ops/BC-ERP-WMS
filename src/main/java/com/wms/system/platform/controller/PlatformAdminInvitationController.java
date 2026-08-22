package com.wms.system.platform.controller;
import com.wms.system.platform.dto.*; import com.wms.system.platform.service.PlatformAdminInvitationService;
import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/platform/admins/invitations") @RequiredArgsConstructor
public class PlatformAdminInvitationController {
    private final PlatformAdminInvitationService service;
    @PostMapping("/challenge") public PlatformReauthenticationChallengeResponse challenge(){return service.startChallenge();}
    @PostMapping public PlatformAdminInvitationResponse create(@Valid @RequestBody PlatformAdminInvitationCreateRequest r,HttpServletRequest h){return service.create(r,h);}
    @GetMapping public List<PlatformAdminInvitationResponse> list(){return service.list();}
    @PostMapping("/{id}/revoke") public void revoke(@PathVariable Long id,HttpServletRequest h){service.revoke(id,h);}
}
