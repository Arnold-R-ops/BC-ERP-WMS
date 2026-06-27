package com.wms.system.service;

import com.wms.system.dto.inventory.InventoryDetailDto;
import com.wms.system.dto.inventory.InventorySummaryDto;
import com.wms.system.dto.inventory.LocationViewDto;
import com.wms.system.dto.inventory.SkuInfoDto;
import com.wms.system.entity.Location;
import com.wms.system.entity.enums.StockStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.util.PackageStatusFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Inventory query service for the three-level inventory views.
 *
 * The heavy summary work is delegated to SQL/JPQL projections so API calls do
 * not load all products or all batches into memory.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final LocationRepository locationRepository;

    public Page<InventorySummaryDto> getSummary(Pageable pageable, String searchKeyword) {
        String search = searchKeyword == null ? "" : searchKeyword.trim();
        return inventoryBatchRepository.findInventorySummaryRows(search, pageable)
            .map(this::buildInventorySummary);
    }

    public List<InventoryDetailDto> getDetailsBySkuId(Long skuId) {
        return inventoryBatchRepository.findActiveDetailRowsByProductId(skuId).stream()
            .map(this::buildInventoryDetail)
            .toList();
    }

    public LocationViewDto getLocationView(String locationCode) {
        Location location = locationRepository.findByLocationCode(locationCode)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.LOCATION_NOT_FOUND,
                Map.of("locationCode", locationCode)
            ));

        List<InventoryDetailDto> batchDetails = inventoryBatchRepository
            .findActiveDetailRowsByLocationCode(locationCode)
            .stream()
            .map(this::buildInventoryDetail)
            .toList();

        return LocationViewDto.builder()
            .locationCode(locationCode)
            .warehouseName(location.getWarehouse() != null ? location.getWarehouse().getName() : "未知仓库")
            .batches(batchDetails)
            .build();
    }

    private InventorySummaryDto buildInventorySummary(InventoryBatchRepository.InventorySummaryRow row) {
        int totalQuantity = numberToInt(row.getTotalQuantity());
        int reservedQuantity = numberToInt(row.getTotalReservedQuantity());
        int availableQuantity = numberToInt(row.getTotalAvailableQuantity());
        int conversionRate = Math.max(numberToInt(row.getConversionRate()), 1);
        int safetyStock = numberToInt(row.getSafetyStock());

        SkuInfoDto skuInfo = SkuInfoDto.builder()
            .image(null)
            .name(row.getProductName())
            .skuCode(row.getSkuCode())
            .specs(row.getSpecs())
            .build();

        return InventorySummaryDto.builder()
            .productId(row.getProductId())
            .skuInfo(skuInfo)
            .warehouseNames(parseWarehouseNames(row.getWarehouseNames()))
            .displayQuantity(formatQuantity(totalQuantity, conversionRate, row.getPackUnit()))
            .totalQuantity(totalQuantity)
            .reservedQuantity(reservedQuantity)
            .availableQuantity(availableQuantity)
            .displayAvailableQuantity(formatQuantity(availableQuantity, conversionRate, row.getPackUnit()))
            .stockStatus(availableQuantity < safetyStock ? StockStatus.LOW_STOCK : StockStatus.SUFFICIENT)
            .furthestExpiryDate(row.getFurthestExpiryDate())
            .build();
    }

    private InventoryDetailDto buildInventoryDetail(InventoryBatchRepository.InventoryDetailRow row) {
        return InventoryDetailDto.builder()
            .batchCode(row.getBatchCode())
            .traceCode(row.getBatchCode())
            .warehouseName(row.getWarehouseName())
            .locationCode(row.getLocationCode())
            .quantity(numberToInt(row.getQuantity()))
            .reservedQuantity(numberToInt(row.getReservedQuantity()))
            .availableQuantity(numberToInt(row.getAvailableQuantity()))
            .packageStatus(PackageStatusFormatter.plain(row.getQuantity(), row.getConversionRate()))
            .expiryDate(row.getExpiryDate())
            .build();
    }

    private List<String> parseWarehouseNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }

        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .distinct()
            .toList();
    }

    private String formatQuantity(int totalQuantity, int conversionRate, String packUnit) {
        if (totalQuantity <= 0) {
            return "0件";
        }

        if (conversionRate <= 1) {
            return totalQuantity + "件";
        }

        String safePackUnit = (packUnit == null || packUnit.isBlank()) ? "箱" : packUnit;
        int packCount = totalQuantity / conversionRate;
        int looseCount = totalQuantity % conversionRate;

        return packCount + safePackUnit + " + " + looseCount + "件";
    }

    private int numberToInt(Number number) {
        return number == null ? 0 : number.intValue();
    }
}
