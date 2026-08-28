package com.wms.system.service;

import com.wms.system.dto.inbound.OtherInboundApplicationCreateRequest;
import com.wms.system.dto.inbound.OtherInboundApplicationItemRequest;
import com.wms.system.dto.inbound.OtherInboundApplicationItemResponse;
import com.wms.system.dto.inbound.OtherInboundApplicationResponse;
import com.wms.system.dto.inbound.OtherInboundApplicationUpdateRequest;
import com.wms.system.entity.OtherInboundApplication;
import com.wms.system.entity.OtherInboundApplicationItem;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.ReceivingTaskItem;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.OtherInboundStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.OtherInboundApplicationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtherInboundApplicationService {

    private final OtherInboundApplicationRepository applicationRepository;
    private final ReceivingTaskRepository receivingTaskRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductSkuRepository productSkuRepository;
    private final OtherInboundFeaturePolicyService featurePolicyService;
    private final ProductSkuOperationalPolicy productSkuOperationalPolicy;
    private final ProductSkuExpiryPolicy expiryPolicy;

    @Transactional(readOnly = true)
    public List<OtherInboundApplicationResponse> list(
        OtherInboundStatus status,
        Pageable pageable
    ) {
        return (status == null
            ? applicationRepository.findAllByOrderByIdDesc(pageable)
            : applicationRepository.findByStatus(status, pageable))
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OtherInboundApplicationResponse get(Long id) {
        return toResponse(requireApplication(id));
    }

    @Transactional(readOnly = true)
    public Long getWarehouseId(Long id) {
        OtherInboundApplication application = requireApplication(id);
        return application.getWarehouse() == null ? null : application.getWarehouse().getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherInboundApplicationResponse create(
        OtherInboundApplicationCreateRequest request,
        Long actorId,
        String actorName
    ) {
        featurePolicyService.requireEnabled(request.getBusinessType());
        Warehouse warehouse = requireActiveWarehouse(request.getWarehouseId());
        OtherInboundApplication application = OtherInboundApplication.builder()
            .applicationNumber(uniqueNumber("OIN"))
            .businessType(request.getBusinessType())
            .sourceReference(normalizeRequired(request.getSourceReference(), "sourceReference"))
            .businessReason(normalizeRequired(request.getBusinessReason(), "businessReason"))
            .warehouse(warehouse)
            .status(OtherInboundStatus.DRAFT)
            .createdBy(actorId)
            .createdByName(normalizeActor(actorName))
            .remark(trim(request.getRemark()))
            .build();
        replaceItems(application, request.getItems());
        return toResponse(applicationRepository.save(application));
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherInboundApplicationResponse update(
        Long id,
        OtherInboundApplicationUpdateRequest request
    ) {
        OtherInboundApplication application = requireForUpdate(id, request.getVersion());
        requireStatus(application, OtherInboundStatus.DRAFT, "UPDATE");
        featurePolicyService.requireEnabled(request.getBusinessType());
        application.setBusinessType(request.getBusinessType());
        application.setSourceReference(normalizeRequired(
            request.getSourceReference(), "sourceReference"));
        application.setBusinessReason(normalizeRequired(
            request.getBusinessReason(), "businessReason"));
        application.setWarehouse(requireActiveWarehouse(request.getWarehouseId()));
        application.setRemark(trim(request.getRemark()));
        replaceItems(application, request.getItems());
        return toResponse(applicationRepository.saveAndFlush(application));
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherInboundApplicationResponse confirm(
        Long id,
        Long version,
        Long actorId,
        String actorName
    ) {
        OtherInboundApplication application = requireForUpdate(id, version);
        requireStatus(application, OtherInboundStatus.DRAFT, "CONFIRM");
        featurePolicyService.requireEnabled(application.getBusinessType());
        requireActiveWarehouse(application.getWarehouse().getId());
        if (application.getItems().isEmpty()) {
            throw validation("items", "MUST_NOT_BE_EMPTY");
        }
        for (OtherInboundApplicationItem item : application.getItems()) {
            productSkuOperationalPolicy.requireEnabled(
                item.getProductSku(), ProductSkuOperationalPolicy.OTHER_INBOUND);
            expiryPolicy.validateForInventoryReceipt(
                item.getProductSku(), item.getProductionDate(), item.getExpiryDate());
        }

        ReceivingTask task = ReceivingTask.builder()
            .taskNumber(uniqueNumber("RCV"))
            .sourceType(ReceivingTaskSourceType.OTHER_INBOUND)
            .otherInboundApplication(application)
            .warehouse(application.getWarehouse())
            .status(ReceivingTaskStatus.PENDING)
            .createdBy(actorId)
            .createdByName(normalizeActor(actorName))
            .build();
        application.getItems().forEach(item -> task.addItem(ReceivingTaskItem.builder()
            .otherInboundApplicationItem(item)
            .productSku(item.getProductSku())
            .expectedQuantity(item.getPlannedQuantity())
            .productionDate(item.getProductionDate())
            .expiryDate(item.getExpiryDate())
            .externalBatchCode(item.getExternalBatchCode())
            .remark(item.getRemark())
            .build()));
        receivingTaskRepository.save(task);

        application.setStatus(OtherInboundStatus.CONFIRMED);
        application.setConfirmedBy(actorId);
        application.setConfirmedByName(normalizeActor(actorName));
        application.setConfirmedAt(LocalDateTime.now());
        return toResponse(applicationRepository.save(application));
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherInboundApplicationResponse cancel(
        Long id,
        Long version,
        String reason,
        Long actorId,
        String actorName
    ) {
        OtherInboundApplication application = requireForUpdate(id, version);
        requireStatus(application, OtherInboundStatus.CONFIRMED, "CANCEL");
        ReceivingTask task = requireTask(application.getId());
        if (task.getItems().stream().anyMatch(item -> item.getReceivedQuantity() > 0)) {
            throw operationNotAllowed("CANCEL_OTHER_INBOUND", "APPLICATION_HAS_RECEIPTS");
        }
        task.setStatus(ReceivingTaskStatus.CANCELLED);
        terminate(application, OtherInboundStatus.CANCELLED, reason, actorId, actorName);
        return toResponse(applicationRepository.save(application));
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherInboundApplicationResponse voidApplication(
        Long id,
        Long version,
        String reason,
        Long actorId,
        String actorName
    ) {
        OtherInboundApplication application = requireForUpdate(id, version);
        requireStatus(application, OtherInboundStatus.DRAFT, "VOID");
        if (receivingTaskRepository.findByOtherInboundApplicationId(id).isPresent()) {
            throw operationNotAllowed("VOID_OTHER_INBOUND", "APPLICATION_HAS_DOWNSTREAM");
        }
        terminate(application, OtherInboundStatus.VOIDED, reason, actorId, actorName);
        return toResponse(applicationRepository.save(application));
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherInboundApplicationResponse closeRemainder(
        Long id,
        Long version,
        String reason,
        Long actorId,
        String actorName
    ) {
        OtherInboundApplication application = requireForUpdate(id, version);
        requireStatus(application, OtherInboundStatus.PARTIALLY_RECEIVED, "CLOSE_REMAINDER");
        ReceivingTask task = requireTask(application.getId());
        Map<Long, ReceivingTaskItem> taskItems = task.getItems().stream()
            .collect(java.util.stream.Collectors.toMap(
                item -> item.getOtherInboundApplicationItem().getId(), item -> item));
        for (OtherInboundApplicationItem item : application.getItems()) {
            int remaining = item.remainingQuantity();
            if (remaining <= 0) {
                continue;
            }
            item.setClosedQuantity(item.getClosedQuantity() + remaining);
            ReceivingTaskItem taskItem = taskItems.get(item.getId());
            if (taskItem == null) {
                throw validation("receivingTaskItem", item.getId());
            }
            taskItem.setClosedQuantity(taskItem.getClosedQuantity() + remaining);
        }
        task.setStatus(ReceivingTaskStatus.CLOSED);
        task.setClosedBy(actorId);
        task.setClosedByName(normalizeActor(actorName));
        task.setClosedAt(LocalDateTime.now());
        task.setCloseReason(normalizeRequired(reason, "reason"));
        terminate(application, OtherInboundStatus.CLOSED, reason, actorId, actorName);
        return toResponse(applicationRepository.save(application));
    }

    private void replaceItems(
        OtherInboundApplication application,
        List<OtherInboundApplicationItemRequest> requestedItems
    ) {
        Set<Long> productIds = new HashSet<>();
        List<OtherInboundApplicationItem> replacements = requestedItems.stream().map(request -> {
            if (!productIds.add(request.getProductSkuId())) {
                throw validation("items.productSkuId", "DUPLICATE_SKU");
            }
            ProductSku productSku = productSkuRepository.findById(request.getProductSkuId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                    Map.of("productSkuId", request.getProductSkuId())
                ));
            productSkuOperationalPolicy.requireEnabled(
                productSku, ProductSkuOperationalPolicy.OTHER_INBOUND);
            return OtherInboundApplicationItem.builder()
                .productSku(productSku)
                .plannedQuantity(request.getPlannedQuantity())
                .productionDate(request.getProductionDate())
                .expiryDate(request.getExpiryDate())
                .externalBatchCode(trim(request.getExternalBatchCode()))
                .remark(trim(request.getRemark()))
                .build();
        }).toList();
        application.getItems().clear();
        replacements.forEach(application::addItem);
    }

    private OtherInboundApplication requireForUpdate(Long id, Long version) {
        OtherInboundApplication application = applicationRepository.findByIdForUpdate(id)
            .orElseThrow(() -> resourceNotFound(id));
        if (!Objects.equals(application.getVersion(), version)) {
            throw new BusinessException(
                ErrorKeys.PO_EDIT_CONFLICT,
                Map.of("resourceType", "OtherInboundApplication", "resourceId", id,
                    "expectedVersion", version, "currentVersion", application.getVersion())
            );
        }
        return application;
    }

    private OtherInboundApplication requireApplication(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> resourceNotFound(id));
    }

    private ReceivingTask requireTask(Long applicationId) {
        return receivingTaskRepository.findByOtherInboundApplicationId(applicationId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "ReceivingTask", "otherInboundApplicationId", applicationId)
            ));
    }

    private Warehouse requireActiveWarehouse(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
            .filter(warehouse -> Boolean.TRUE.equals(warehouse.getIsActive()))
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.WAREHOUSE_NOT_FOUND, Map.of("warehouseId", warehouseId)
            ));
    }

    private void requireStatus(
        OtherInboundApplication application,
        OtherInboundStatus expected,
        String operation
    ) {
        if (application.getStatus() != expected) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", operation, "resourceId", application.getId(),
                    "currentStatus", application.getStatus().name(),
                    "expectedStatus", expected.name())
            );
        }
    }

    private void terminate(
        OtherInboundApplication application,
        OtherInboundStatus status,
        String reason,
        Long actorId,
        String actorName
    ) {
        application.setStatus(status);
        application.setTerminatedBy(actorId);
        application.setTerminatedByName(normalizeActor(actorName));
        application.setTerminatedAt(LocalDateTime.now());
        application.setTerminationReason(normalizeRequired(reason, "reason"));
    }

    private OtherInboundApplicationResponse toResponse(OtherInboundApplication application) {
        Warehouse warehouse = application.getWarehouse();
        return OtherInboundApplicationResponse.builder()
            .id(application.getId())
            .version(application.getVersion())
            .applicationNumber(application.getApplicationNumber())
            .businessType(application.getBusinessType())
            .sourceReference(application.getSourceReference())
            .businessReason(application.getBusinessReason())
            .warehouseId(warehouse == null ? null : warehouse.getId())
            .warehouseName(warehouse == null ? null : warehouse.getName())
            .legacyInboundOrderId(application.getLegacyInboundOrderId())
            .status(application.getStatus())
            .plannedQuantity(application.plannedQuantity())
            .receivedQuantity(application.receivedQuantity())
            .createdByName(application.getCreatedByName())
            .confirmedAt(application.getConfirmedAt())
            .terminationReason(application.getTerminationReason())
            .remark(application.getRemark())
            .items(application.getItems().stream().map(item ->
                OtherInboundApplicationItemResponse.builder()
                    .id(item.getId())
                    .productSkuId(item.getProductSku().getId())
                    .productName(item.getProductSku().getName())
                    .productBarcode(item.getProductSku().getBarcode())
                    .plannedQuantity(item.getPlannedQuantity())
                    .receivedQuantity(item.getReceivedQuantity())
                    .closedQuantity(item.getClosedQuantity())
                    .productionDate(item.getProductionDate())
                    .expiryDate(item.getExpiryDate())
                    .externalBatchCode(item.getExternalBatchCode())
                    .remark(item.getRemark())
                    .build()).toList())
            .createdAt(application.getCreatedAt())
            .updatedAt(application.getUpdatedAt())
            .build();
    }

    private String uniqueNumber(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString()
            .replace("-", "").substring(0, 16).toUpperCase();
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw validation(field, "MUST_NOT_BE_BLANK");
        }
        return value.trim();
    }

    private String normalizeActor(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException resourceNotFound(Long id) {
        return new BusinessException(
            ErrorKeys.RESOURCE_NOT_FOUND,
            Map.of("resourceType", "OtherInboundApplication", "resourceId", id)
        );
    }

    private BusinessException validation(String field, Object reason) {
        return new BusinessException(
            ErrorKeys.VALIDATION_FAILED, Map.of("field", field, "reason", reason)
        );
    }

    private BusinessException operationNotAllowed(String operation, String reason) {
        return new BusinessException(
            ErrorKeys.OPERATION_NOT_ALLOWED, Map.of("operation", operation, "reason", reason)
        );
    }
}
