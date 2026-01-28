package com.wms.system.service;

import com.wms.system.dto.inventory.InventoryDetailDto;
import com.wms.system.dto.inventory.InventorySummaryDto;
import com.wms.system.dto.inventory.LocationViewDto;
import com.wms.system.dto.inventory.SkuInfoDto;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.enums.StockStatus;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存查询服务 - 三层穿透式库存视图
 *
 * 功能：
 * 1. 第一层：SKU 聚合视图 - 按 SKU 汇总，显示智能数量、预警状态、最远有效期
 * 2. 第二层：批次详情视图 - 按有效期降序显示某个 SKU 的所有批次
 * 3. 第三层：库位反查视图 - 扫描库位码，显示该库位的所有批次
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.6
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    /**
     * 第一层：SKU 聚合视图
     *
     * @param pageable 分页参数
     * @param searchKeyword 搜索关键词（SKU 名称/条形码）
     * @return SKU 聚合视图分页结果
     */
    public Page<InventorySummaryDto> getSummary(Pageable pageable, String searchKeyword) {
        // 1. 查询所有产品（支持搜索）
        List<Product> products;
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            products = productRepository.findByNameContaining(searchKeyword.trim());
        } else {
            products = productRepository.findAll();
        }

        // 2. 为每个产品构建汇总信息
        List<InventorySummaryDto> summaries = products.stream()
            .map(this::buildInventorySummary)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // 3. 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), summaries.size());
        List<InventorySummaryDto> pageContent = summaries.subList(start, end);

        return new PageImpl<>(pageContent, pageable, summaries.size());
    }

    /**
     * 第二层：批次详情视图
     *
     * @param skuId SKU ID（产品 ID）
     * @return 批次详情列表（按有效期降序）
     */
    public List<InventoryDetailDto> getDetailsBySkuId(Long skuId) {
        // 1. 查询该 SKU 的所有活跃批次
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByProductIdAndActive(skuId, true);

        // 2. 按有效期降序排序（最新鲜的在最上面）
        batches.sort((b1, b2) -> {
            if (b1.getExpiryDate() == null && b2.getExpiryDate() == null) return 0;
            if (b1.getExpiryDate() == null) return 1;
            if (b2.getExpiryDate() == null) return -1;
            return b2.getExpiryDate().compareTo(b1.getExpiryDate());
        });

        // 3. 转换为 DTO
        return batches.stream()
            .map(this::buildInventoryDetail)
            .collect(Collectors.toList());
    }

    /**
     * 第三层：库位反查视图
     *
     * @param locationCode 库位编码
     * @return 库位视图
     */
    public LocationViewDto getLocationView(String locationCode) {
        // 1. 查询库位信息
        Optional<Location> locationOpt = locationRepository.findByLocationCode(locationCode);
        if (locationOpt.isEmpty()) {
            throw new IllegalArgumentException("库位不存在: " + locationCode);
        }

        Location location = locationOpt.get();

        // 2. 查询该库位的所有活跃批次
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByLocationCodeAndActive(locationCode, true);

        // 3. 转换为 DTO
        List<InventoryDetailDto> batchDetails = batches.stream()
            .map(this::buildInventoryDetail)
            .collect(Collectors.toList());

        return LocationViewDto.builder()
            .locationCode(locationCode)
            .warehouseName(location.getWarehouse() != null ? location.getWarehouse().getName() : "未知仓库")
            .batches(batchDetails)
            .build();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 构建 SKU 汇总信息
     */
    private InventorySummaryDto buildInventorySummary(Product product) {
        // 1. 构建 SKU 基本信息
        SkuInfoDto skuInfo = SkuInfoDto.builder()
            .image(null)  // 预留字段，未来可添加图片 URL
            .name(product.getName())
            .skuCode(product.getBarcode())
            .specs(product.getSpecs())
            .build();

        // 2. 查询分布的仓库列表
        List<String> warehouseNames = getWarehouseNamesByProduct(product.getId());

        // 3. 计算智能数量显示
        String displayQuantity = calculateDisplayQuantity(product.getId(), product);

        // 4. 计算预警状态
        StockStatus stockStatus = calculateStockStatus(product.getId(), product.getSafetyStock());

        // 5. 查询最远有效期
        LocalDate furthestExpiryDate = getFurthestExpiryDate(product.getId());

        return InventorySummaryDto.builder()
            .productId(product.getId())
            .skuInfo(skuInfo)
            .warehouseNames(warehouseNames)
            .displayQuantity(displayQuantity)
            .stockStatus(stockStatus)
            .furthestExpiryDate(furthestExpiryDate)
            .build();
    }

    /**
     * 构建批次详情信息
     */
    private InventoryDetailDto buildInventoryDetail(InventoryBatch batch) {
        // 1. 获取仓库名称
        String warehouseName = "未知仓库";
        if (batch.getLocation() != null && batch.getLocation().getWarehouse() != null) {
            warehouseName = batch.getLocation().getWarehouse().getName();
        }

        // 2. 判断整箱/散货状态
        String packageStatus = determinePackageStatus(batch);

        return InventoryDetailDto.builder()
            .batchCode(batch.getBatchCode())
            .traceCode(batch.getBatchCode())  // 追溯码与批次码相同
            .warehouseName(warehouseName)
            .locationCode(batch.getLocationCode())
            .quantity(batch.getQuantity())
            .packageStatus(packageStatus)
            .expiryDate(batch.getExpiryDate())
            .build();
    }

    /**
     * 查询产品分布的仓库列表
     */
    private List<String> getWarehouseNamesByProduct(Long productId) {
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByProductIdAndActive(productId, true);

        return batches.stream()
            .map(InventoryBatch::getLocation)
            .filter(Objects::nonNull)
            .map(Location::getWarehouse)
            .filter(Objects::nonNull)
            .map(warehouse -> warehouse.getName())
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 计算智能数量显示
     *
     * 逻辑：
     * 1. 统计整箱数量（quantity % conversionRate == 0）
     * 2. 统计散货数量（quantity % conversionRate != 0）
     * 3. 格式化输出：统一格式 "{箱数}箱 + {散数}{单位}"
     *    - 有整箱有散货：50箱 + 12袋
     *    - 只有整箱：50箱 + 0袋
     *    - 只有散货：0箱 + 12袋
     */
    private String calculateDisplayQuantity(Long productId, Product product) {
        // 1. 查询所有活跃批次
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByProductIdAndActive(productId, true);

        if (batches.isEmpty()) {
            return String.format("0箱 + 0%s", product.getPackUnit());
        }

        // 2. 统计整箱数量（SEALED 状态）
        int sealedQty = batches.stream()
            .filter(b -> b.getQuantity() % product.getConversionRate() == 0)
            .mapToInt(InventoryBatch::getQuantity)
            .sum();

        // 3. 统计散货数量（OPENED 状态）
        int openedQty = batches.stream()
            .filter(b -> b.getQuantity() % product.getConversionRate() != 0)
            .mapToInt(InventoryBatch::getQuantity)
            .sum();

        // 4. 计算箱数和散数
        int boxCount = sealedQty / product.getConversionRate();
        int looseCount = openedQty;

        // 5. 格式化输出（统一格式）
        return String.format("%d箱 + %d%s", boxCount, looseCount, product.getPackUnit());
    }

    /**
     * 计算预警状态
     *
     * 逻辑：
     * - totalQty < safetyStock → LOW_STOCK（红色）
     * - totalQty >= safetyStock → SUFFICIENT（绿色）
     */
    private StockStatus calculateStockStatus(Long productId, Integer safetyStock) {
        Integer totalQty = inventoryBatchRepository.sumQuantityByProduct(productId);
        if (totalQty == null) {
            totalQty = 0;
        }

        return totalQty < safetyStock ? StockStatus.LOW_STOCK : StockStatus.SUFFICIENT;
    }

    /**
     * 查询最远有效期
     *
     * 逻辑：
     * - 查询所有活跃批次的有效期
     * - 返回最远的有效期（最新鲜的）
     */
    private LocalDate getFurthestExpiryDate(Long productId) {
        return inventoryBatchRepository
            .findByProductIdAndActive(productId, true)
            .stream()
            .map(InventoryBatch::getExpiryDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(null);
    }

    /**
     * 判断整箱/散货状态
     *
     * 逻辑：
     * - quantity % conversionRate == 0 → "📦 整箱"
     * - quantity % conversionRate != 0 → "📭 散货"
     */
    private String determinePackageStatus(InventoryBatch batch) {
        Product product = batch.getProduct();
        if (product == null) {
            return "未知";
        }

        int conversionRate = product.getConversionRate();
        if (batch.getQuantity() % conversionRate == 0) {
            return "📦 整箱";
        } else {
            return "📭 散货";
        }
    }
}
