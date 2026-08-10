package com.wms.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.HistoricalTestDataArchivePreview;
import com.wms.system.dto.sales.HistoricalTestDataArchiveRequest;
import com.wms.system.dto.sales.HistoricalTestDataArchiveResult;
import com.wms.system.entity.BackorderLine;
import com.wms.system.entity.Customer;
import com.wms.system.entity.HistoricalTestDataArchiveAudit;
import com.wms.system.entity.HistoricalTestDataRegistry;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.OutboundTask;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesOrderItem;
import com.wms.system.entity.SalesOrderShipment;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.FulfillmentStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.ShipmentStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.event.SalesOrderFactsChangedEvent;
import com.wms.system.repository.BackorderLineRepository;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.HistoricalTestDataArchiveAuditRepository;
import com.wms.system.repository.HistoricalTestDataRegistryRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.OutboundTaskRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.SalesOrderItemRepository;
import com.wms.system.repository.SalesOrderRepository;
import com.wms.system.repository.SalesOrderShipmentRepository;
import com.wms.system.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guarded archive for historical test orders. It never releases reservations,
 * changes batches, or creates stock transactions.
 */
@Service
@RequiredArgsConstructor
public class HistoricalTestDataArchiveService {

    private static final List<String> TEST_MARKERS = List.of(
        "V4.4测试", "V4.4 测试", "V4.4 TEST", "V4_4_TEST", "V4-4-TEST",
        "IDEMPOTENCY TEST", "幂等性测试", "幂等测试"
    );

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final SalesOrderShipmentRepository salesOrderShipmentRepository;
    private final BackorderLineRepository backorderLineRepository;
    private final CustomerRepository customerRepository;
    private final ProductSkuRepository productSkuRepository;
    private final HistoricalTestDataRegistryRepository registryRepository;
    private final HistoricalTestDataArchiveAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public HistoricalTestDataArchivePreview preview(Long salesOrderId) {
        SalesOrder order = requireOrder(salesOrderId, false);
        Evaluation evaluation = evaluate(order, false);
        return toPreview(evaluation);
    }

    @Transactional(rollbackFor = Exception.class)
    public HistoricalTestDataArchiveResult archive(
        Long salesOrderId,
        HistoricalTestDataArchiveRequest request,
        Long operatorId,
        String operatorUsername
    ) {
        SalesOrder order = requireOrder(salesOrderId, true);
        Evaluation evaluation = evaluate(order, true);

        if (!Objects.equals(order.getOrderNo(), request.getConfirmationOrderNo())) {
            throw new BusinessException(
                ErrorKeys.HISTORICAL_ARCHIVE_CONFIRMATION_MISMATCH,
                Map.of("salesOrderId", salesOrderId, "orderNo", order.getOrderNo())
            );
        }
        if (!evaluation.blockers().isEmpty()) {
            throw new BusinessException(
                ErrorKeys.HISTORICAL_ARCHIVE_NOT_ELIGIBLE,
                Map.of("salesOrderId", salesOrderId, "blockers", evaluation.blockers())
            );
        }
        if (!evaluation.fingerprint().equalsIgnoreCase(request.getExpectedFingerprint())) {
            throw new BusinessException(
                ErrorKeys.HISTORICAL_ARCHIVE_SNAPSHOT_STALE,
                Map.of(
                    "salesOrderId", salesOrderId,
                    "expectedFingerprint", request.getExpectedFingerprint(),
                    "actualFingerprint", evaluation.fingerprint()
                )
            );
        }

        LocalDateTime archivedAt = LocalDateTime.now();
        order.setStatus(SalesOrderStatus.VOIDED);
        order.setCommercialStatus(CommercialStatus.VOIDED);
        order.setFulfillmentStatus(FulfillmentStatus.VOIDED);
        appendOrderAudit(order, operatorUsername, request.getReason(), archivedAt);

        evaluation.items().forEach(item -> item.setFulfillmentStatus(FulfillmentStatus.VOIDED));
        evaluation.tasks().forEach(task -> {
            task.setStatus(OutboundTaskStatus.VOIDED);
            task.setArchivedAt(archivedAt);
            task.setArchivedBy(operatorId);
            task.setArchiveReason(request.getReason());
        });

        salesOrderRepository.saveAndFlush(order);
        salesOrderItemRepository.saveAllAndFlush(evaluation.items());
        outboundTaskRepository.saveAllAndFlush(evaluation.tasks());

        String afterSnapshot = serializeSnapshot(snapshot(
            order,
            evaluation.items(),
            evaluation.tasks(),
            evaluation.reservations(),
            evaluation.transactions(),
            evaluation.shipments(),
            evaluation.backorders(),
            evaluation.registration()
        ));

        HistoricalTestDataArchiveAudit audit = auditRepository.saveAndFlush(
            HistoricalTestDataArchiveAudit.builder()
                .companyId(order.getCompanyId())
                .salesOrderId(order.getId())
                .orderNo(order.getOrderNo())
                .operatorId(operatorId)
                .operatorUsername(operatorUsername)
                .reason(request.getReason())
                .snapshotFingerprint(evaluation.fingerprint())
                .beforeSnapshot(evaluation.snapshotJson())
                .afterSnapshot(afterSnapshot)
                .createdAt(archivedAt)
                .build()
        );

        eventPublisher.publishEvent(new SalesOrderFactsChangedEvent(
            order.getCompanyId(),
            order.getId(),
            order.getCreatedAt().toLocalDate(),
            "ARCHIVE_HISTORICAL_TEST_DATA"
        ));

        return HistoricalTestDataArchiveResult.builder()
            .salesOrderId(order.getId())
            .orderNo(order.getOrderNo())
            .orderStatus(order.getStatus().name())
            .archivedTaskIds(evaluation.tasks().stream().map(OutboundTask::getId).toList())
            .auditId(audit.getId())
            .archivedAt(archivedAt)
            .inventoryChanged(false)
            .reservationsChanged(false)
            .stockTransactionsCreated(false)
            .build();
    }

    private SalesOrder requireOrder(Long salesOrderId, boolean forUpdate) {
        return (forUpdate
            ? salesOrderRepository.findByIdForUpdate(salesOrderId)
            : salesOrderRepository.findById(salesOrderId))
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", salesOrderId)
            ));
    }

    private Evaluation evaluate(SalesOrder order, boolean forUpdate) {
        List<SalesOrderItem> items = sorted(salesOrderItemRepository.findBySalesOrderId(order.getId()));
        List<OutboundTask> tasks = sorted(forUpdate
            ? outboundTaskRepository.findBySalesOrderIdForUpdate(order.getId())
            : outboundTaskRepository.findBySalesOrderId(order.getId()));
        List<InventoryReservation> reservations = sorted(forUpdate
            ? inventoryReservationRepository.findBySalesOrderIdForUpdate(order.getId())
            : inventoryReservationRepository.findBySalesOrderId(order.getId()));
        List<StockTransaction> transactions = sorted(
            stockTransactionRepository.findByCompanyIdAndSourceOrderId(
                order.getCompanyId(), order.getOrderNo()
            )
        );
        List<SalesOrderShipment> shipments = sorted(
            salesOrderShipmentRepository.findBySalesOrderIdOrderByIdAsc(order.getId())
        );
        List<BackorderLine> backorders = sorted(backorderLineRepository.findBySalesOrderId(order.getId()));
        HistoricalTestDataRegistry registration = registryRepository
            .findByCompanyIdAndSalesOrderId(order.getCompanyId(), order.getId())
            .filter(row -> Objects.equals(row.getOrderNo(), order.getOrderNo()))
            .orElse(null);

        List<String> blockers = new ArrayList<>();
        if (order.getStatus() != SalesOrderStatus.APPROVED_AWAITING_SHIPMENT) {
            blockers.add("ORDER_STATUS_NOT_APPROVED_AWAITING_SHIPMENT");
        }
        if (registration == null) {
            blockers.add("ORDER_NOT_REGISTERED_AS_HISTORICAL_TEST_DATA");
        }

        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        if (customer == null || !hasTestMarker(customer.getCode(), customer.getName())) {
            blockers.add("CUSTOMER_TEST_MARKER_MISSING");
        }

        Map<Long, ProductSku> products = productSkuRepository.findAllById(
                items.stream().map(SalesOrderItem::getProductSkuId).distinct().toList()
            ).stream()
            .collect(Collectors.toMap(ProductSku::getId, Function.identity()));
        if (items.isEmpty()) {
            blockers.add("ORDER_ITEMS_REQUIRED");
        }
        for (SalesOrderItem item : items) {
            ProductSku product = products.get(item.getProductSkuId());
            if (product == null || !hasTestMarker(
                item.getRemark(), product.getSkuCode(), product.getSkuName(),
                product.getName(), product.getBarcode()
            )) {
                blockers.add("ITEM_TEST_MARKER_MISSING:" + item.getId());
            }
            if (value(item.getShippedQty()) != 0) {
                blockers.add("ITEM_SHIPPED_QTY_NONZERO:" + item.getId());
            }
        }

        if (tasks.isEmpty()) {
            blockers.add("OUTBOUND_TASKS_REQUIRED");
        }
        for (OutboundTask task : tasks) {
            if (task.getStatus() != OutboundTaskStatus.PENDING) {
                blockers.add("TASK_NOT_PENDING:" + task.getId());
            }
            if (value(task.getActualQty()) != 0) {
                blockers.add("TASK_ACTUAL_QTY_NONZERO:" + task.getId());
            }
            if (task.getReservationId() != null) {
                blockers.add("TASK_RESERVATION_LINK_PRESENT:" + task.getId());
            }
        }
        if (!reservations.isEmpty()) {
            blockers.add("RESERVATION_RECORDS_PRESENT:" + reservations.size());
        }
        if (!transactions.isEmpty()) {
            blockers.add("STOCK_TRANSACTIONS_PRESENT:" + transactions.size());
        }
        long activeShipmentCount = shipments.stream()
            .filter(shipment -> shipment.getStatus() != ShipmentStatus.VOIDED)
            .count();
        if (activeShipmentCount > 0) {
            blockers.add("ACTIVE_SHIPMENTS_PRESENT:" + activeShipmentCount);
        }
        if (!backorders.isEmpty()) {
            blockers.add("BACKORDERS_PRESENT:" + backorders.size());
        }

        String snapshotJson = serializeSnapshot(snapshot(
            order, items, tasks, reservations, transactions, shipments, backorders, registration
        ));
        return new Evaluation(
            order, items, tasks, reservations, transactions, shipments, backorders, registration,
            List.copyOf(blockers), snapshotJson, fingerprint(snapshotJson)
        );
    }

    private HistoricalTestDataArchivePreview toPreview(Evaluation evaluation) {
        SalesOrder order = evaluation.order();
        return HistoricalTestDataArchivePreview.builder()
            .salesOrderId(order.getId())
            .orderNo(order.getOrderNo())
            .orderStatus(order.getStatus().name())
            .eligible(evaluation.blockers().isEmpty())
            .blockers(evaluation.blockers())
            .taskIds(evaluation.tasks().stream().map(OutboundTask::getId).toList())
            .reservationCount(evaluation.reservations().size())
            .stockTransactionCount(evaluation.transactions().size())
            .shipmentCount(evaluation.shipments().size())
            .activeShipmentCount((int) evaluation.shipments().stream()
                .filter(shipment -> shipment.getStatus() != ShipmentStatus.VOIDED)
                .count())
            .voidedShipmentCount((int) evaluation.shipments().stream()
                .filter(shipment -> shipment.getStatus() == ShipmentStatus.VOIDED)
                .count())
            .snapshotFingerprint(evaluation.fingerprint())
            .build();
    }

    private Map<String, Object> snapshot(
        SalesOrder order,
        List<SalesOrderItem> items,
        List<OutboundTask> tasks,
        List<InventoryReservation> reservations,
        List<StockTransaction> transactions,
        List<SalesOrderShipment> shipments,
        List<BackorderLine> backorders,
        HistoricalTestDataRegistry registration
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("order", map(
            "id", order.getId(),
            "orderNo", order.getOrderNo(),
            "status", name(order.getStatus()),
            "commercialStatus", name(order.getCommercialStatus()),
            "fulfillmentStatus", name(order.getFulfillmentStatus()),
            "fulfillmentVersion", order.getFulfillmentVersion(),
            "customerId", order.getCustomerId()
        ));
        root.put("items", items.stream().map(item -> map(
            "id", item.getId(),
            "productSkuId", item.getProductSkuId(),
            "quantity", item.getQuantity(),
            "allocatedQty", item.getAllocatedQty(),
            "shippedQty", item.getShippedQty(),
            "fulfillmentStatus", name(item.getFulfillmentStatus()),
            "remark", item.getRemark()
        )).toList());
        root.put("tasks", tasks.stream().map(task -> map(
            "id", task.getId(),
            "status", name(task.getStatus()),
            "planQty", task.getPlanQty(),
            "actualQty", task.getActualQty(),
            "reservationId", task.getReservationId(),
            "batchId", task.getAssignedBatchId(),
            "version", task.getVersion(),
            "archivedAt", format(task.getArchivedAt()),
            "archivedBy", task.getArchivedBy(),
            "archiveReason", task.getArchiveReason()
        )).toList());
        root.put("reservations", reservations.stream().map(row -> map(
            "id", row.getId(),
            "reservedQty", row.getReservedQty(),
            "consumedQty", row.getConsumedQty(),
            "releasedQty", row.getReleasedQty(),
            "status", name(row.getStatus()),
            "version", row.getVersion()
        )).toList());
        root.put("stockTransactions", transactions.stream().map(row -> map(
            "id", row.getId(),
            "type", name(row.getTransactionType()),
            "sourceType", name(row.getSourceType()),
            "quantity", row.getQuantity(),
            "quantityBefore", row.getQuantityBefore(),
            "quantityAfter", row.getQuantityAfter()
        )).toList());
        root.put("shipments", shipments.stream().map(row -> map(
            "id", row.getId(), "status", name(row.getStatus()), "trackingNo", row.getTrackingNo()
        )).toList());
        root.put("backorders", backorders.stream().map(row -> map(
            "id", row.getId(), "status", name(row.getStatus()), "remainingQty", row.getRemainingQty()
        )).toList());
        root.put("historicalTestRegistration", registration == null ? null : map(
            "id", registration.getId(),
            "companyId", registration.getCompanyId(),
            "salesOrderId", registration.getSalesOrderId(),
            "orderNo", registration.getOrderNo(),
            "sourceVersion", registration.getSourceVersion(),
            "registrationReason", registration.getRegistrationReason(),
            "registeredBy", registration.getRegisteredBy(),
            "createdAt", format(registration.getCreatedAt())
        ));
        return root;
    }

    private void appendOrderAudit(
        SalesOrder order,
        String operatorUsername,
        String reason,
        LocalDateTime archivedAt
    ) {
        try {
            List<Map<String, Object>> entries = order.getAuditLog() == null || order.getAuditLog().isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(objectMapper.readValue(
                    order.getAuditLog(), new TypeReference<List<Map<String, Object>>>() { }
                ));
            entries.add(map(
                "timestamp", archivedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "operator", operatorUsername,
                "action", "ARCHIVE_HISTORICAL_TEST_DATA",
                "details", reason
            ));
            order.setAuditLog(objectMapper.writeValueAsString(entries));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to append historical archive audit", exception);
        }
    }

    private boolean hasTestMarker(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.toUpperCase(Locale.ROOT);
            if (TEST_MARKERS.stream().anyMatch(normalized::contains)) {
                return true;
            }
        }
        return false;
    }

    private String serializeSnapshot(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize historical archive snapshot", exception);
        }
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> sorted(List<T> values) {
        return values.stream()
            .sorted(Comparator.comparing(value -> entityId(value), Comparator.nullsLast(Long::compareTo)))
            .toList();
    }

    private Long entityId(Object value) {
        if (value instanceof SalesOrderItem row) return row.getId();
        if (value instanceof OutboundTask row) return row.getId();
        if (value instanceof InventoryReservation row) return row.getId();
        if (value instanceof StockTransaction row) return row.getId();
        if (value instanceof SalesOrderShipment row) return row.getId();
        if (value instanceof BackorderLine row) return row.getId();
        return null;
    }

    private record Evaluation(
        SalesOrder order,
        List<SalesOrderItem> items,
        List<OutboundTask> tasks,
        List<InventoryReservation> reservations,
        List<StockTransaction> transactions,
        List<SalesOrderShipment> shipments,
        List<BackorderLine> backorders,
        HistoricalTestDataRegistry registration,
        List<String> blockers,
        String snapshotJson,
        String fingerprint
    ) { }
}
