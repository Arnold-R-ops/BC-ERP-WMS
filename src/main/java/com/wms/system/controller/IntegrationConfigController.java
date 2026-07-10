package com.wms.system.controller;

import com.wms.system.dto.integration.IntegrationConfigRequest;
import com.wms.system.dto.integration.IntegrationConfigResponse;
import com.wms.system.service.IntegrationConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 店铺集成配置 Controller（P1 批次1）
 *
 * 告别手写 SQL 维护 integration_configs。
 *
 * API Endpoints:
 * - GET    /api/integration/configs                  列表（密钥脱敏）
 * - GET    /api/integration/configs/{id}             详情（密钥脱敏）
 * - POST   /api/integration/configs                  新建店铺配置
 * - PUT    /api/integration/configs/{id}             更新（密钥留空=保留原值）
 * - PUT    /api/integration/configs/{id}/activate    启用同步
 * - PUT    /api/integration/configs/{id}/deactivate  停用同步
 * - DELETE /api/integration/configs/{id}             删除（须先停用）
 * - POST   /api/integration/configs/{id}/test-connection  只读连通性测试
 *
 * Security: system:admin / SUPER_ADMIN（与 Outbox 管理端点同级）
 *
 * @author WMS Team
 * @since 2026-07-10
 * @version P1-B1 (Channel Integration Foundation)
 */
@Slf4j
@RestController
@RequestMapping("/api/integration/configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('system:admin', 'SUPER_ADMIN')")
public class IntegrationConfigController {

    private final IntegrationConfigService service;

    @GetMapping
    public ResponseEntity<List<IntegrationConfigResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IntegrationConfigResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<IntegrationConfigResponse> create(@Valid @RequestBody IntegrationConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntegrationConfigResponse> update(
        @PathVariable("id") Long id,
        @RequestBody IntegrationConfigRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<IntegrationConfigResponse> activate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.setActive(id, true));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<IntegrationConfigResponse> deactivate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.setActive(id, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 只读连通性测试：验证凭据可换令牌、店铺可访问，返回店铺概要。
     */
    @PostMapping("/{id}/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.testConnection(id));
    }
}
