package com.wms.system.controller;

import com.wms.system.dto.integration.ChannelSkuMappingRequest;
import com.wms.system.dto.integration.ChannelSkuMappingResponse;
import com.wms.system.dto.integration.ResolvePendingSkuRequest;
import com.wms.system.entity.PendingSkuMapping;
import com.wms.system.entity.Product;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.ChannelSkuMappingService;
import com.wms.system.service.PendingSkuMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 渠道 SKU 映射 Controller（P1 批次2）
 *
 * API Endpoints:
 * - GET  /api/integration/sku-mappings?channel=            映射表列表
 * - POST /api/integration/sku-mappings                     手工建映射
 * - PUT  /api/integration/sku-mappings/{id}                更新映射（商品/换算比/启停）
 * - GET  /api/integration/sku-mappings/pending?status=     待映射队列（按卡单次数排序）
 * - POST /api/integration/sku-mappings/pending/{id}/resolve 人工处理（MAP/VIRTUAL/IGNORE）
 * - GET  /api/integration/sku-mappings/pending/{id}/suggestions 候选商品推荐
 *
 * 处理闭环：resolve 写入映射表 → 被卡订单（FAILED 报文）下一轮同步自动重试放行。
 *
 * Security: system:admin / SUPER_ADMIN
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Slf4j
@RestController
@RequestMapping("/api/integration/sku-mappings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('system:admin', 'SUPER_ADMIN')")
public class ChannelSkuMappingController {

    private final ChannelSkuMappingService mappingService;
    private final PendingSkuMappingService pendingService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<ChannelSkuMappingResponse>> list(
        @RequestParam(value = "channel", required = false) String channel
    ) {
        return ResponseEntity.ok(mappingService.list(channel));
    }

    @PostMapping
    public ResponseEntity<ChannelSkuMappingResponse> create(@Valid @RequestBody ChannelSkuMappingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChannelSkuMappingResponse> update(
        @PathVariable("id") Long id,
        @RequestBody ChannelSkuMappingRequest request
    ) {
        return ResponseEntity.ok(mappingService.update(id, request));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PendingSkuMapping>> pending(
        @RequestParam(value = "channel", required = false) String channel,
        @RequestParam(value = "status", required = false) String status
    ) {
        return ResponseEntity.ok(pendingService.list(channel, status));
    }

    @PostMapping("/pending/{id}/resolve")
    public ResponseEntity<PendingSkuMapping> resolve(
        @PathVariable("id") Long id,
        @Valid @RequestBody ResolvePendingSkuRequest request,
        Authentication authentication
    ) {
        Long operatorId = AuthUserResolver.resolveUserId(authentication);
        if (operatorId == null || operatorId == 0L) {
            operatorId = userRepository.findByUsername(AuthUserResolver.resolveUsername(authentication))
                .map(u -> u.getId()).orElse(0L);
        }
        return ResponseEntity.ok(pendingService.resolve(
            id, request.getAction(), request.getProductId(), request.getQuantityRatio(), operatorId));
    }

    /**
     * 候选推荐：按 SKU 首个编码片段做条码前缀/名称关键词匹配（人工确认辅助）
     */
    @GetMapping("/pending/{id}/suggestions")
    public ResponseEntity<List<Map<String, Object>>> suggestions(@PathVariable("id") Long id) {
        List<Product> candidates = pendingService.suggestions(id);
        List<Map<String, Object>> result = candidates.stream()
            .<Map<String, Object>>map(p -> Map.of(
                "productId", p.getId(),
                "barcode", p.getBarcode() == null ? "" : p.getBarcode(),
                "skuName", p.getSkuName() == null ? "" : p.getSkuName(),
                "name", p.getName() == null ? "" : p.getName()))
            .toList();
        return ResponseEntity.ok(result);
    }
}
