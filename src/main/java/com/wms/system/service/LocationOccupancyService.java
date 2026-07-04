package com.wms.system.service;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationOccupancyService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public void validateCanStore(Location location, Product product, String batchCode) {
        if (location == null || product == null || batchCode == null || batchCode.isBlank()) {
            return;
        }

        List<InventoryBatch> existingBatches = inventoryBatchRepository
            .findPositiveActiveBatchesByLocationId(location.getId());

        for (InventoryBatch existing : existingBatches) {
            boolean sameProduct = existing.getProduct() != null
                && existing.getProduct().getId().equals(product.getId());
            boolean sameBatch = batchCode.equals(existing.getBatchCode());

            if (!sameProduct || !sameBatch) {
                Long existingProductId = existing.getProduct() == null ? -1L : existing.getProduct().getId();
                throw new BusinessException(
                    ErrorKeys.LOCATION_BATCH_MIXING_FORBIDDEN,
                    Map.of(
                        "locationId", location.getId(),
                        "locationCode", location.getLocationCode(),
                        "existingProductId", existingProductId,
                        "incomingProductId", product.getId(),
                        "existingBatchCode", existing.getBatchCode(),
                        "incomingBatchCode", batchCode
                    )
                );
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markOccupied(Location location) {
        if (location == null) {
            return;
        }
        location.markOccupied();
        locationRepository.save(location);
    }

    @Transactional(rollbackFor = Exception.class)
    public void refreshLocationStatus(Long locationId) {
        if (locationId == null) {
            return;
        }

        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.LOCATION_NOT_FOUND,
                Map.of("locationId", locationId)
            ));

        Integer totalQuantity = inventoryBatchRepository.sumActiveQuantityByLocationId(locationId);
        if (totalQuantity == null || totalQuantity <= 0) {
            location.markEmpty();
        } else {
            location.markOccupied();
        }
        locationRepository.save(location);
    }

    @Transactional(readOnly = true)
    public InventoryBatch resolveSingleVisualBatch(Long locationId, Long productId) {
        List<InventoryBatch> batches = inventoryBatchRepository
            .findPositiveActiveBatchesByLocationAndProduct(locationId, productId);

        if (batches.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("locationId", locationId, "productId", productId)
            );
        }

        if (batches.size() > 1) {
            throw new BusinessException(
                ErrorKeys.LOCATION_VISUAL_BATCH_AMBIGUOUS,
                Map.of("locationId", locationId, "productId", productId, "batchCount", batches.size())
            );
        }

        return batches.get(0);
    }
}
