package com.wms.system.controller;

import com.wms.system.dto.inventory.InventoryDetailDto;
import com.wms.system.dto.inventory.InventorySummaryDto;
import com.wms.system.dto.inventory.LocationViewDto;
import com.wms.system.service.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存查询控制器 - 三层穿透式库存视图
 *
 * 功能：
 * 1. 第一层：SKU 聚合视图 - GET /api/inventory/summary
 * 2. 第二层：批次详情视图 - GET /api/inventory/details/{skuId}
 * 3. 第三层：库位反查视图 - GET /api/inventory/location/{locationCode}
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.6
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryQueryController {

    private final InventoryQueryService inventoryQueryService;

    /**
     * 第一层：SKU 聚合视图
     *
     * 功能：
     * - 按 SKU 汇总库存
     * - 显示智能数量（整箱 + 散货）
     * - 显示预警状态（红色/绿色）
     * - 显示最远有效期
     * - 支持分页和搜索
     *
     * @param pageable 分页参数（默认每页 20 条）
     * @param search 搜索关键词（SKU 名称/条形码）
     * @return SKU 聚合视图分页结果
     */
    @GetMapping("/summary")
    public ResponseEntity<Page<InventorySummaryDto>> getSummary(
        @PageableDefault(size = 20, page = 0) Pageable pageable,
        @RequestParam(required = false) String search
    ) {
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(pageable, search);
        return ResponseEntity.ok(result);
    }

    /**
     * 第二层：批次详情视图
     *
     * 功能：
     * - 显示某个 SKU 的所有批次
     * - 按有效期降序排序（最新鲜的在最上面）
     * - 显示批次码、库位、数量、整箱/散货状态、有效期
     *
     * @param skuId SKU ID（产品 ID）
     * @return 批次详情列表
     */
    @GetMapping("/details/{skuId}")
    public ResponseEntity<List<InventoryDetailDto>> getDetails(
        @PathVariable Long skuId
    ) {
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(skuId);
        return ResponseEntity.ok(result);
    }

    /**
     * 第三层：库位反查视图
     *
     * 功能：
     * - 扫描库位码，显示该库位的所有批次
     * - 显示仓库名称、库位编码、批次列表
     *
     * @param locationCode 库位编码（如 "A-1-101"）
     * @return 库位视图
     */
    @GetMapping("/location/{locationCode}")
    public ResponseEntity<LocationViewDto> getLocationView(
        @PathVariable String locationCode
    ) {
        LocationViewDto result = inventoryQueryService.getLocationView(locationCode);
        return ResponseEntity.ok(result);
    }
}
