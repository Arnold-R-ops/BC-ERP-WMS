package com.wms.system.tenant.web;

import com.wms.system.tenant.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/platform/tenants/{tenantId}/security")
public class PlatformTenantSecurityController {
    private final PlatformTenantSecurityService service;
    @GetMapping public Map<String,Object> overview(@PathVariable long tenantId,HttpServletRequest request) { return service.overview(tenantId,request); }
    @GetMapping("/commands/{key}") public Map<String,Object> result(@PathVariable long tenantId,@PathVariable String key,HttpServletRequest request) { return service.result(tenantId,key,request); }
    @PostMapping("/challenges") public Map<String,Object> challenge(@PathVariable long tenantId,@Valid @RequestBody TenantSecurityCommand.Draft draft,HttpServletRequest request) { return service.challenge(tenantId,draft,request); }
    @PostMapping("/commands") public Map<String,Object> execute(@PathVariable long tenantId,@Valid @RequestBody TenantSecurityCommand.Execute command,HttpServletRequest request) { return service.execute(tenantId,command,request); }
}
