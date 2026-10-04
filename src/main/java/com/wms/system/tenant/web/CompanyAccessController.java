package com.wms.system.tenant.web;

import com.wms.system.security.SecurityUser;
import com.wms.system.tenant.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/company-access")
public class CompanyAccessController {
    private final TenantSecurityStateService states;
    @GetMapping("/status") public Map<String,Object> status(Authentication authentication) {
        if (authentication==null || !(authentication.getPrincipal() instanceof SecurityUser user)) throw new TenantAccessException("AUTH_TOKEN_INVALID",401);
        var scope=TenantExecutionScope.current();
        var state=states.require(user.getUser().getCompanyId(),TenantOperationPurpose.AUTHENTICATION,scope==null?null:scope.sessionVersion());
        return Map.of("status",state.status().name(),"businessAvailable",state.status()==com.wms.system.tenant.model.TenantStatus.ACTIVE,
            "message",state.status()==com.wms.system.tenant.model.TenantStatus.ACTIVE?"服务正常":"租户服务已暂停，请联系平台处理。");
    }
}
