package com.wms.system.platform.controller;
import com.wms.system.platform.dto.*; import com.wms.system.platform.service.PlatformAccessGrantService;
import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/platform/access-grants") @RequiredArgsConstructor
public class PlatformAccessGrantController {
 private final PlatformAccessGrantService service;
 @GetMapping("/me") public PlatformEffectiveAccessResponse effectiveAccess(){return service.effectiveAccess();}
 @GetMapping("/users") public List<PlatformManagedUserResponse> users(){return service.users();}
 @GetMapping public List<PlatformAccessGrantResponse> list(@RequestParam Long platformUserId){return service.list(platformUserId);}
 @PostMapping public List<PlatformAccessGrantResponse> create(@Valid @RequestBody PlatformAccessGrantRequest request,HttpServletRequest http){return service.create(request,http);}
 @DeleteMapping("/{id}") public void revoke(@PathVariable Long id,HttpServletRequest http){service.revoke(id,http);}
}
