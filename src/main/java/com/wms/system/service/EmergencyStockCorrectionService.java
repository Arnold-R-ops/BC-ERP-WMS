package com.wms.system.service;

import com.wms.system.dto.v45.EmergencyStockCorrectionRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyStockCorrectionService {

    private final EmergencyStockCorrectionRepository correctionRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductSkuRepository productSkuRepository;
    private final LocationRepository locationRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final DomainOutboxService domainOutboxService;
    private final BackorderService backorderService;
    private final LocationOccupancyService locationOccupancyService;

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse create(EmergencyStockCorrectionRequest request, Long operatorId) {
        ProductSku product = productSkuRepository.findById(request.getProductSkuId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND, Map.of("productSkuId", request.getProductSkuId())));
        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.LOCATION_NOT_FOUND, Map.of("locationId", request.getLocationId())));

        InventoryBatch batch = null;
        int systemQty = 0;
        if (request.getInventoryBatchId() != null) {
            batch = inventoryBatchRepository.findById(request.getInventoryBatchId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.BATCH_NOT_FOUND, Map.of("batchId", request.getInventoryBatchId())));
            systemQty = batch.getQuantity();
        } else if (request.getBatchCode() == null || request.getBatchCode().isBlank()) {
            throw new BusinessException(ErrorKeys.PARAMETER_REQUIRED, Map.of("parameter", "batchCode"));
        } else if (request.getExpiryDate() == null) {
            throw new BusinessException(ErrorKeys.PARAMETER_REQUIRED, Map.of("parameter", "expiryDate"));
        }

        EmergencyStockCorrection correction = EmergencyStockCorrection.builder()
            .correctionNo(nextCorrectionNo())
            .productSkuId(product.getId())
            .locationId(location.getId())
            .inventoryBatchId(batch == null ? null : batch.getId())
            .batchCode(batch == null ? request.getBatchCode() : batch.getBatchCode())
            .productionDate(batch == null ? request.getProductionDate() : batch.getProductionDate())
            .expiryDate(batch == null ? request.getExpiryDate() : batch.getExpiryDate())
            .systemQty(systemQty)
            .countedQty(request.getCountedQty())
            .adjustmentQty(request.getCountedQty() - systemQty)
            .reasonCode(request.getReasonCode())
            .reasonDetail(request.getReasonDetail())
            .evidenceUrl(request.getEvidenceUrl())
            .relatedSalesOrderId(request.getRelatedSalesOrderId())
            .status(EmergencyCorrectionStatus.DRAFT)
            .submittedBy(operatorId)
            .build();

        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse submit(Long id, Long operatorId, String comment) {
        EmergencyStockCorrection correction = load(id);
        requireStatus(correction, EmergencyCorrectionStatus.DRAFT);
        correction.setStatus(EmergencyCorrectionStatus.PENDING_REVIEW);
        correction.setSubmittedBy(operatorId);
        correction.setSubmittedAt(LocalDateTime.now());
        correction.setReviewComment(comment);
        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse review(Long id, Long reviewerId, String comment) {
        EmergencyStockCorrection correction = load(id);
        requireStatus(correction, EmergencyCorrectionStatus.PENDING_REVIEW);
        correction.setStatus(EmergencyCorrectionStatus.PENDING_APPROVAL);
        correction.setReviewedBy(reviewerId);
        correction.setReviewedAt(LocalDateTime.now());
        correction.setReviewComment(comment);
        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse approve(Long id, Long approverId, String comment) {
        EmergencyStockCorrection correction = load(id);
        requireStatus(correction, EmergencyCorrectionStatus.PENDING_APPROVAL);
        correction.setStatus(EmergencyCorrectionStatus.APPROVED);
        correction.setApprovedBy(approverId);
        correction.setApprovedAt(LocalDateTime.now());
        correction.setApprovalComment(comment);
        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse reject(Long id, Long reviewerId, String comment) {
        EmergencyStockCorrection correction = load(id);
        if (correction.getStatus() != EmergencyCorrectionStatus.PENDING_REVIEW
            && correction.getStatus() != EmergencyCorrectionStatus.PENDING_APPROVAL) {
            throw invalidStatus(correction, "PENDING_REVIEW or PENDING_APPROVAL");
        }
        correction.setStatus(EmergencyCorrectionStatus.REJECTED);
        correction.setReviewedBy(reviewerId);
        correction.setReviewedAt(LocalDateTime.now());
        correction.setReviewComment(comment);
        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse apply(Long id, Long operatorId) {
        EmergencyStockCorrection correction = load(id);
        requireStatus(correction, EmergencyCorrectionStatus.APPROVED);

        ProductSku product = productSkuRepository.findById(correction.getProductSkuId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND, Map.of("productSkuId", correction.getProductSkuId())));
        Location location = locationRepository.findById(correction.getLocationId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.LOCATION_NOT_FOUND, Map.of("locationId", correction.getLocationId())));

        InventoryBatch batch = correction.getInventoryBatchId() == null
            ? createCorrectionBatch(correction, product, location)
            : inventoryBatchRepository.findById(correction.getInventoryBatchId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.BATCH_NOT_FOUND, Map.of("batchId", correction.getInventoryBatchId())));

        locationOccupancyService.validateCanStore(location, product, batch.getBatchCode());

        int before = batch.getQuantity();
        batch.setQuantity(correction.getCountedQty());
        batch.setActive(correction.getCountedQty() > 0);
        batch.setLocation(location);
        batch.setLocationCode(location.getLocationCode());
        inventoryBatchRepository.save(batch);
        locationOccupancyService.refreshLocationStatus(location.getId());

        SourceType stockTransactionSourceType = correction.getAdjustmentQty() >= 0
            ? SourceType.INVENTORY_GAIN
            : SourceType.INVENTORY_LOSS;

        StockTransaction transaction = StockTransaction.builder()
            .productSku(product)
            .location(location)
            .transactionType(TransactionType.ADJUST)
            .sourceType(stockTransactionSourceType)
            .quantity(correction.getAdjustmentQty())
            .quantityBefore(before)
            .quantityAfter(correction.getCountedQty())
            .sourceOrderId(correction.getCorrectionNo())
            .operatorId(operatorId)
            .operatorName("User-" + operatorId)
            .reasonCode(correction.getReasonCode())
            .remarks(correction.getReasonDetail())
            .remark("Emergency stock correction")
            .build();
        stockTransactionRepository.save(transaction);

        correction.setInventoryBatchId(batch.getId());
        correction.setStatus(EmergencyCorrectionStatus.APPLIED);
        correction.setAppliedBy(operatorId);
        correction.setAppliedAt(LocalDateTime.now());
        EmergencyStockCorrection saved = correctionRepository.save(correction);

        domainOutboxService.append("INVENTORY_AVAILABLE", "ProductSku", product.getId(), Map.of(
            "schemaVersion", 2,
            "productSkuId", product.getId(),
            "source", "EMERGENCY_CORRECTION",
            "correctionId", saved.getId()
        ));
        backorderService.wakeProduct(product.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EmergencyStockCorrectionResponse> list(EmergencyCorrectionStatus status) {
        List<EmergencyStockCorrection> corrections = status == null
            ? correctionRepository.findAll()
            : correctionRepository.findByStatusOrderByCreatedAtDesc(status);
        return corrections.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmergencyStockCorrectionResponse get(Long id) {
        return toResponse(load(id));
    }

    private EmergencyStockCorrection load(Long id) {
        return correctionRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND, Map.of("resourceType", "EmergencyStockCorrection", "resourceId", id)));
    }

    private void requireStatus(EmergencyStockCorrection correction, EmergencyCorrectionStatus required) {
        if (correction.getStatus() != required) {
            throw invalidStatus(correction, required.name());
        }
    }

    private BusinessException invalidStatus(EmergencyStockCorrection correction, String required) {
        return new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED, Map.of(
            "operation", "EmergencyStockCorrection",
            "currentStatus", correction.getStatus().name(),
            "requiredStatus", required
        ));
    }

    private InventoryBatch createCorrectionBatch(EmergencyStockCorrection correction, ProductSku product, Location location) {
        InventoryBatch batch = InventoryBatch.builder()
            .batchCode(correction.getBatchCode())
            .productSku(product)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(0)
            .reservedQuantity(0)
            .initialQuantity(correction.getCountedQty())
            .productionDate(correction.getProductionDate())
            .expiryDate(correction.getExpiryDate())
            .entryDate(LocalDateTime.now())
            .active(true)
            .remark("Created by emergency stock correction")
            .build();
        return inventoryBatchRepository.save(batch);
    }

    private String nextCorrectionNo() {
        String prefix = "ESC" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i <= 9999; i++) {
            String no = prefix + String.format("%04d", i);
            if (!correctionRepository.existsByCorrectionNo(no)) {
                return no;
            }
        }
        throw new BusinessException(ErrorKeys.INTERNAL_SERVER_ERROR, Map.of("message", "Unable to allocate correction number"));
    }

    private EmergencyStockCorrectionResponse toResponse(EmergencyStockCorrection c) {
        return EmergencyStockCorrectionResponse.builder()
            .id(c.getId())
            .correctionNo(c.getCorrectionNo())
            .productSkuId(c.getProductSkuId())
            .locationId(c.getLocationId())
            .inventoryBatchId(c.getInventoryBatchId())
            .batchCode(c.getBatchCode())
            .productionDate(c.getProductionDate())
            .expiryDate(c.getExpiryDate())
            .systemQty(c.getSystemQty())
            .countedQty(c.getCountedQty())
            .adjustmentQty(c.getAdjustmentQty())
            .reasonCode(c.getReasonCode())
            .reasonDetail(c.getReasonDetail())
            .evidenceUrl(c.getEvidenceUrl())
            .relatedSalesOrderId(c.getRelatedSalesOrderId())
            .status(c.getStatus())
            .submittedBy(c.getSubmittedBy())
            .submittedAt(c.getSubmittedAt())
            .reviewedBy(c.getReviewedBy())
            .reviewedAt(c.getReviewedAt())
            .approvedBy(c.getApprovedBy())
            .approvedAt(c.getApprovedAt())
            .appliedBy(c.getAppliedBy())
            .appliedAt(c.getAppliedAt())
            .reviewComment(c.getReviewComment())
            .approvalComment(c.getApprovalComment())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .build();
    }
}
