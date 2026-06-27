package com.wms.system.controller;

import com.wms.system.dto.outbound.ConfirmPickingRequest;
import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.OutboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/outbound-tasks")
@RequiredArgsConstructor
public class OutboundTaskController {

    private final OutboundService outboundService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('outbound:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<OutboundTaskResponse>> listOutboundTasks(
        @RequestParam(value = "salesOrderId", required = false) Long salesOrderId,
        @RequestParam(value = "status", required = false) String status
    ) {
        log.info("API call: listOutboundTasks - salesOrderId={}, status={}", salesOrderId, status);
        List<OutboundTaskResponse> responses = outboundService.listOutboundTasks(salesOrderId, status);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('outbound:view', 'SUPER_ADMIN')")
    public ResponseEntity<OutboundTaskResponse> getOutboundTask(@PathVariable("id") Long id) {
        log.info("API call: getOutboundTask - id={}", id);
        OutboundTaskResponse response = outboundService.getOutboundTask(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('outbound:pick', 'SUPER_ADMIN')")
    public ResponseEntity<OutboundTaskResponse> confirmPicking(
        @PathVariable("id") Long id,
        @Valid @RequestBody ConfirmPickingRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);

        log.info("API call: confirmPicking - taskId={}, actualQty={}, operator={}",
            id, request.getActualQty(), username);

        OutboundTaskResponse response = outboundService.confirmPicking(
            id,
            request.getActualQty(),
            userId,
            username
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch-confirm")
    @PreAuthorize("hasAnyAuthority('outbound:pick', 'SUPER_ADMIN')")
    public ResponseEntity<List<OutboundTaskResponse>> batchConfirmPicking(
        @RequestBody List<Long> taskIds,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);

        log.info("API call: batchConfirmPicking - taskIds={}, operator={}", taskIds, username);

        List<OutboundTaskResponse> responses = outboundService.batchConfirmPicking(
            taskIds,
            userId,
            username
        );

        return ResponseEntity.ok(responses);
    }
}
