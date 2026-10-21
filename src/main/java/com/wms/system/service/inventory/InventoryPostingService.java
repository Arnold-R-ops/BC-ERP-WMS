package com.wms.system.service.inventory;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.StockTransactionRepository;
import com.wms.system.service.DomainOutboxService;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative transaction boundary for posting a quantity delta to an
 * existing inventory batch.
 *
 * <p>This first slice deliberately does not create batches. Callers must pass
 * the expected SKU, location and warehouse so a stale or misrouted business
 * command cannot silently post stock to another inventory identity.</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryPostingService {

    static final String OUTBOX_EVENT_TYPE = "INVENTORY_POSTED";
    static final String OUTBOX_AGGREGATE_TYPE = "InventoryBatch";

    private final InventoryBatchRepository inventoryBatchRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final DomainOutboxService domainOutboxService;

    /**
     * Posts a delta against an existing batch while holding a database write
     * lock. Batch quantity, ledger row and Outbox event commit or roll back as
     * one transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public InventoryPostingResult post(InventoryPostingCommand command) {
        ValidatedCommand validated = validateCommand(command);

        InventoryBatch batch = inventoryBatchRepository
            .findByIdForPosting(validated.inventoryBatchId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", validated.inventoryBatchId())
            ));

        validateBatchIdentity(batch, validated);

        int quantityBefore = requireQuantity(batch.getQuantity(), "quantity");
        validateBatchPostingState(batch, validated, quantityBefore);
        int reservedQuantity = batch.getReservedQuantity() == null
            ? 0
            : requireQuantity(batch.getReservedQuantity(), "reservedQuantity");
        long candidateQuantity = (long) quantityBefore + validated.delta();

        if (candidateQuantity < 0 || candidateQuantity > Integer.MAX_VALUE) {
            throw new BusinessException(
                ErrorKeys.STOCK_INVALID_QUANTITY,
                Map.of(
                    "batchId", batch.getId(),
                    "quantityBefore", quantityBefore,
                    "delta", validated.delta()
                )
            );
        }

        if (candidateQuantity < reservedQuantity) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_RESERVED_QUANTITY_CONFLICT,
                Map.of(
                    "batchId", batch.getId(),
                    "quantityBefore", quantityBefore,
                    "reservedQuantity", reservedQuantity,
                    "quantityAfter", candidateQuantity
                )
            );
        }

        int quantityAfter = (int) candidateQuantity;
        if (batch.getStocktakeTaskId() != null) {
            throw new BusinessException("PACKAGING_STOCKTAKE_LOCKED");
        }
        if (command.packagingAfter() != null) {
            command.packagingAfter().requireTotal(quantityAfter);
            batch.setPackaging(command.packagingAfter());
        } else if (batch.getPackaging() != null && validated.delta() != 0) {
            if (quantityAfter == 0) {
                batch.setPackaging(com.wms.system.entity.StockPackaging.empty(batch.getPackaging().packSize()));
            } else if (validated.context().sourceType() == SourceType.SALE_OUT
                || validated.context().sourceType() == SourceType.TRANSFER_OUT) {
                throw new BusinessException("PACKAGING_SELECTION_REQUIRED");
            } else {
                // Existing exception workflows have quantities but no physical source selection.
                // Discard obsolete composition instead of inventing unopened/opened boxes.
                batch.setPackaging(null);
            }
        }
        if (validated.delta() == 0) {
            if (command.packagingAfter() != null) inventoryBatchRepository.save(batch);
            return result(batch, validated, quantityBefore, quantityAfter, false, null);
        }

        batch.setQuantity(quantityAfter);
        batch.setActive(quantityAfter > 0);
        inventoryBatchRepository.save(batch);

        StockTransaction transaction = createTransaction(
            batch,
            validated,
            quantityBefore,
            quantityAfter
        );
        StockTransaction savedTransaction = stockTransactionRepository.save(transaction);

        domainOutboxService.append(
            OUTBOX_EVENT_TYPE,
            OUTBOX_AGGREGATE_TYPE,
            batch.getId(),
            outboxPayload(batch, validated, quantityBefore, quantityAfter)
        );

        return result(
            batch,
            validated,
            quantityBefore,
            quantityAfter,
            true,
            savedTransaction.getId()
        );
    }

    private ValidatedCommand validateCommand(InventoryPostingCommand command) {
        if (command == null) {
            throw validationFailure("command");
        }

        Long batchId = requirePositive(command.inventoryBatchId(), "inventoryBatchId");
        Long productSkuId = requirePositive(command.productSkuId(), "productSkuId");
        Long locationId = requirePositive(command.locationId(), "locationId");
        if (command.delta() == null) {
            throw validationFailure("delta");
        }

        PostingContext context = command.context();
        if (context == null) {
            throw validationFailure("context");
        }

        Long companyId = requirePositive(context.companyId(), "context.companyId");
        Long warehouseId = requirePositive(context.warehouseId(), "context.warehouseId");
        if (!companyId.equals(CompanyScope.currentCompanyId())) {
            throw new BusinessException(
                ErrorKeys.AUTH_ACCESS_DENIED,
                Map.of("companyId", companyId)
            );
        }
        if (context.sourceType() == null) {
            throw validationFailure("context.sourceType");
        }

        String sourceOrderId = requireText(
            context.sourceOrderId(),
            "context.sourceOrderId",
            50
        );
        String reasonCode = requireText(context.reasonCode(), "context.reasonCode", 50);
        String operatorName = requireText(context.operatorName(), "context.operatorName", 100);
        Long operatorId = requirePositive(context.operatorId(), "context.operatorId");
        String postingId = requireText(context.postingId(), "context.postingId", 120);
        String idempotencyKey = requireText(
            context.idempotencyKey(),
            "context.idempotencyKey",
            120
        );
        String remarks = normalizeOptionalText(context.remarks(), "context.remarks", 500);

        return new ValidatedCommand(
            batchId,
            productSkuId,
            locationId,
            command.delta(),
            new PostingContext(
                companyId,
                warehouseId,
                context.sourceType(),
                sourceOrderId,
                reasonCode,
                remarks,
                operatorId,
                operatorName,
                postingId,
                idempotencyKey
            )
        );
    }

    private void validateBatchIdentity(InventoryBatch batch, ValidatedCommand command) {
        PostingContext context = command.context();
        if (!Objects.equals(batch.getCompanyId(), context.companyId())) {
            throw new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", command.inventoryBatchId())
            );
        }
        ProductSku productSku = batch.getProductSku();
        if (productSku == null) {
            throw new BusinessException(
                ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                Map.of("productSkuId", command.productSkuId())
            );
        }
        if (!Objects.equals(productSku.getId(), command.productSkuId())) {
            throw contextMismatch(
                "productSkuId",
                command.productSkuId(),
                productSku.getId()
            );
        }
        if (!Objects.equals(productSku.getCompanyId(), context.companyId())) {
            throw contextMismatch(
                "productSku.companyId",
                context.companyId(),
                productSku.getCompanyId()
            );
        }

        Location location = batch.getLocation();
        if (location == null) {
            throw new BusinessException(
                ErrorKeys.LOCATION_NOT_FOUND,
                Map.of("locationId", command.locationId())
            );
        }
        if (!Objects.equals(location.getId(), command.locationId())) {
            throw contextMismatch("locationId", command.locationId(), location.getId());
        }
        if (!Objects.equals(location.getCompanyId(), context.companyId())) {
            throw contextMismatch(
                "location.companyId",
                context.companyId(),
                location.getCompanyId()
            );
        }

        Warehouse warehouse = location.getWarehouse();
        if (warehouse == null) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_NOT_FOUND,
                Map.of("warehouseId", context.warehouseId())
            );
        }
        if (!Objects.equals(warehouse.getId(), context.warehouseId())) {
            throw contextMismatch("warehouseId", context.warehouseId(), warehouse.getId());
        }
        if (!Objects.equals(warehouse.getCompanyId(), context.companyId())) {
            throw contextMismatch(
                "warehouse.companyId",
                context.companyId(),
                warehouse.getCompanyId()
            );
        }

        requireText(batch.getBatchCode(), "batch.batchCode", 50);
        String locationCode = requireText(location.getLocationCode(), "batch.location.locationCode", 100);
        if (!Objects.equals(batch.getLocationCode(), locationCode)) {
            throw contextMismatch("locationCode", locationCode, batch.getLocationCode());
        }
    }

    private void validateBatchPostingState(
        InventoryBatch batch,
        ValidatedCommand command,
        int quantityBefore
    ) {
        if (Boolean.TRUE.equals(batch.getActive())) {
            return;
        }
        boolean governedReactivation = quantityBefore == 0
            && command.delta() > 0
            && command.context().sourceType() == SourceType.INVENTORY_GAIN;
        if (!governedReactivation) {
            throw new BusinessException(
                ErrorKeys.BATCH_INACTIVE,
                Map.of("batchId", batch.getId(), "batchCode", safeBatchCode(batch))
            );
        }
    }

    private StockTransaction createTransaction(
        InventoryBatch batch,
        ValidatedCommand command,
        int quantityBefore,
        int quantityAfter
    ) {
        PostingContext context = command.context();
        TransactionType transactionType = command.delta() > 0
            ? TransactionType.IN
            : TransactionType.OUT;
        int ledgerQuantity = Math.toIntExact(Math.abs((long) command.delta()));

        StockTransaction transaction = StockTransaction.builder()
            .productSku(batch.getProductSku())
            .location(batch.getLocation())
            .transactionType(transactionType)
            .sourceType(context.sourceType())
            .quantity(ledgerQuantity)
            .quantityBefore(quantityBefore)
            .quantityAfter(quantityAfter)
            .sourceOrderId(context.sourceOrderId())
            .operatorId(context.operatorId())
            .operatorName(context.operatorName())
            .reasonCode(context.reasonCode())
            .remarks(context.remarks())
            .remark(context.remarks())
            .warehouseId(context.warehouseId())
            .inventoryBatchId(batch.getId())
            .batchCode(batch.getBatchCode())
            .postingId(context.postingId())
            .idempotencyKey(context.idempotencyKey())
            .build();
        transaction.setCompanyId(context.companyId());
        return transaction;
    }

    private Map<String, Object> outboxPayload(
        InventoryBatch batch,
        ValidatedCommand command,
        int quantityBefore,
        int quantityAfter
    ) {
        PostingContext context = command.context();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("companyId", context.companyId());
        payload.put("warehouseId", context.warehouseId());
        payload.put("productSkuId", command.productSkuId());
        payload.put("inventoryBatchId", batch.getId());
        payload.put("batchCode", batch.getBatchCode());
        payload.put("delta", command.delta());
        payload.put("quantityBefore", quantityBefore);
        payload.put("quantityAfter", quantityAfter);
        payload.put("sourceType", context.sourceType().name());
        payload.put("sourceOrderId", context.sourceOrderId());
        payload.put("reasonCode", context.reasonCode());
        payload.put("postingId", context.postingId());
        payload.put("idempotencyKey", context.idempotencyKey());
        return payload;
    }

    private InventoryPostingResult result(
        InventoryBatch batch,
        ValidatedCommand command,
        int quantityBefore,
        int quantityAfter,
        boolean changed,
        Long stockTransactionId
    ) {
        PostingContext context = command.context();
        return new InventoryPostingResult(
            batch.getId(),
            batch.getBatchCode(),
            command.productSkuId(),
            command.locationId(),
            context.warehouseId(),
            command.delta(),
            quantityBefore,
            quantityAfter,
            changed,
            stockTransactionId,
            context.postingId(),
            context.idempotencyKey()
        );
    }

    private Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw validationFailure(field);
        }
        return value;
    }

    private int requireQuantity(Integer value, String field) {
        if (value == null || value < 0) {
            throw validationFailure(field);
        }
        return value;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw validationFailure(field);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw validationFailure(field);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw validationFailure(field);
        }
        return normalized;
    }

    private String safeBatchCode(InventoryBatch batch) {
        return batch.getBatchCode() == null ? "" : batch.getBatchCode();
    }

    private BusinessException validationFailure(String field) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
    }

    private BusinessException contextMismatch(String field, Object expected, Object actual) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("field", field);
        params.put("expected", expected);
        params.put("actual", actual);
        return new BusinessException(ErrorKeys.INVENTORY_POSTING_CONTEXT_MISMATCH, params);
    }

    private record ValidatedCommand(
        Long inventoryBatchId,
        Long productSkuId,
        Long locationId,
        Integer delta,
        PostingContext context
    ) {
    }
}
