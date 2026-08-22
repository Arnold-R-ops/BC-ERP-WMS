package com.wms.system.platform.controller;

import com.wms.system.platform.dto.*;
import com.wms.system.platform.service.PlatformOperationAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/companies/{companyId}/operations")
@RequiredArgsConstructor
public class PlatformOperationController {
    private final PlatformOperationAuthorizationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformOperationResponse request(@PathVariable Long companyId,
                                             @Valid @RequestBody PlatformOperationRequest request,
                                             HttpServletRequest http) {
        return service.request(companyId, request, http);
    }

    @PostMapping("/{authorizationId}/execute")
    public PlatformOperationResponse execute(@PathVariable Long companyId,
                                             @PathVariable String authorizationId,
                                             @RequestHeader("Idempotency-Key") String executionKey,
                                             HttpServletRequest http) {
        return service.execute(authorizationId, executionKey, http);
    }

    @GetMapping("/{authorizationId}")
    public PlatformOperationResponse get(@PathVariable Long companyId, @PathVariable String authorizationId) {
        PlatformOperationResponse response = service.platformRequest(authorizationId);
        if (!companyId.equals(response.companyId())) throw new IllegalArgumentException("Operation request does not belong to this company");
        return response;
    }
}
