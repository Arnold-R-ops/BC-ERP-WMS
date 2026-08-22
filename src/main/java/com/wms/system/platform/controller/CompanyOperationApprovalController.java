package com.wms.system.platform.controller;

import com.wms.system.platform.dto.*;
import com.wms.system.platform.service.PlatformOperationAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Company-facing approval surface. It exposes only application state, never platform audit evidence. */
@RestController
@RequestMapping("/api/company-access/operations")
@RequiredArgsConstructor
public class CompanyOperationApprovalController {
    private final PlatformOperationAuthorizationService service;
    @GetMapping public List<PlatformOperationResponse> list() { return service.companyRequests(); }
    @PostMapping("/{authorizationId}/decision")
    public PlatformOperationResponse decide(@PathVariable String authorizationId,
                                            @Valid @RequestBody CompanyOperationDecisionRequest request,
                                            HttpServletRequest http) {
        return service.decide(authorizationId, request.decision(), http);
    }
}
