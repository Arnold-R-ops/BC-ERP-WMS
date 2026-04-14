package com.wms.system.service;

import com.wms.system.dto.inbound.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.InboundOrderStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 入库单服务
 *
 * 核心业务流程：
 * 1. createInboundOrder() - 用户创建入库单
 * 2. approvePlan() - 总经理审批计划
 * 3. confirmOrder() - 采购员确认订单（生成批次码）
 * 4. receiveGoods() - 仓库收货（更新库存）
 * 5. rejectOrder() - 拒绝入库单
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundOrderService {

    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final SpuSkuDateBatchCodeGenerator batchCodeGenerator;
    private final UserRepository userRepository;

    private static final String ORDER_NO_PREFIX = "IB";
    private static final DateTimeFormatter ORDER_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 创建入库单
     *
     * @param request 创建请求
     * @param applicantId 申请人ID
     * @param applicantName 申请人姓名
     * @return 入库单响应
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderResponse createInboundOrder(
        CreateInboundOrderRequest request,
        Long applicantId,
        String applicantName
    ) {
        log.info("Creating inbound order for supplier: {}, applicant: {}", request.getSupplierId(), applicantName);

        // 1. 验证供应商
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
            .orElseThrow(() -> new BusinessException(
                "SUPPLIER_NOT_FOUND",
                Map.of("supplierId", request.getSupplierId())
            ));

        if (!supplier.getIsActive()) {
            throw new BusinessException(
                "SUPPLIER_NOT_ACTIVE",
                Map.of("supplierId", request.getSupplierId())
            );
        }

        // 2. 生成入库单号
        String orderNo = generateOrderNo();

        // 3. 创建入库单
        InboundOrder order = InboundOrder.builder()
            .orderNo(orderNo)
            .supplier(supplier)
            .status(InboundOrderStatus.PENDING_APPROVAL)
            .totalPlanQty(0)
            .totalConfirmedQty(null)
            .totalActualQty(0)
            .expectedDate(request.getExpectedDate())
            .remark(request.getRemark())
            .applicantId(applicantId)
            .applicantName(applicantName)
            .auditLog(createAuditLog("创建入库单", applicantName))
            .build();

        // 4. 创建入库单明细
        for (CreateInboundOrderRequest.InboundOrderItemRequest itemReq : request.getItems()) {
            // 验证产品
            Product product = productRepository.findById(itemReq.getProductId())
                .orElseThrow(() -> new BusinessException(
                    "PRODUCT_NOT_FOUND",
                    Map.of("productId", itemReq.getProductId())
                ));

            // 验证仓库和库位（如果提供）
            Warehouse targetWarehouse = null;
            Location targetLocation = null;

            if (itemReq.getTargetWarehouseId() != null) {
                targetWarehouse = warehouseRepository.findById(itemReq.getTargetWarehouseId())
                    .orElseThrow(() -> new BusinessException(
                        "WAREHOUSE_NOT_FOUND",
                        Map.of("warehouseId", itemReq.getTargetWarehouseId())
                    ));
            }

            if (itemReq.getTargetLocationId() != null) {
                targetLocation = locationRepository.findById(itemReq.getTargetLocationId())
                    .orElseThrow(() -> new BusinessException(
                        "LOCATION_NOT_FOUND",
                        Map.of("locationId", itemReq.getTargetLocationId())
                    ));
            }

            // 创建明细项
            InboundOrderItem item = InboundOrderItem.builder()
                .product(product)
                .planQty(itemReq.getPlanQty())
                .confirmedQty(null)
                .actualQty(0)
                .batchCode(null)  // 批次码在确认阶段生成
                .targetWarehouse(targetWarehouse)
                .targetLocation(targetLocation)
                .unitCost(itemReq.getUnitCost())
                .remark(itemReq.getRemark())
                .build();

            order.addItem(item);
        }

        // 5. 重新计算计划总数量
        order.recalculateTotalPlanQty();

        // 6. 保存入库单
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        log.info("Inbound order created successfully: {}", savedOrder.getOrderNo());

        return convertToResponse(savedOrder);
    }

    /**
     * 总经理审批计划
     *
     * @param orderId 入库单ID
     * @param gmUserId 总经理用户ID
     * @param gmUserName 总经理用户名
     * @param comment 审批意见
     * @return 入库单响应
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderResponse approvePlan(
        Long orderId,
        Long gmUserId,
        String gmUserName,
        String comment
    ) {
        log.info("GM approving inbound order: {}, GM: {}", orderId, gmUserName);

        // 1. 查询入库单
        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        // 2. 验证状态
        if (!order.getStatus().canApproveByGM()) {
            throw new BusinessException(
                "INVALID_STATUS_FOR_APPROVAL",
                Map.of("currentStatus", order.getStatus(), "orderId", orderId)
            );
        }

        // 3. 更新状态为 APPROVED_PLAN
        order.setStatus(InboundOrderStatus.APPROVED_PLAN);
        order.setGmApprovedBy(gmUserId);
        order.setGmApprovedAt(LocalDateTime.now());
        order.setGmApprovalComment(comment);

        // 4. 追加审计日志
        String auditEntry = createAuditLog("总经理审批通过", gmUserName);
        order.setAuditLog(appendAuditLog(order.getAuditLog(), auditEntry));

        // 5. 保存
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        log.info("Inbound order approved by GM: {}", savedOrder.getOrderNo());

        return convertToResponse(savedOrder);
    }

    /**
     * 采购员确认订单
     *
     * 核心逻辑：
     * 1. 更新 confirmed_qty（可与 plan_qty 不同）
     * 2. 删除未确认的 item（供应商无货）
     * 3. 生成 SPU-SKU-DATE 批次码
     * 4. 更新状态为 AWAITING_RECEIVAL
     *
     * @param orderId 入库单ID
     * @param request 确认请求
     * @param confirmedBy 确认人ID
     * @param confirmedByName 确认人姓名
     * @return 入库单响应
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderResponse confirmOrder(
        Long orderId,
        ConfirmOrderRequest request,
        Long confirmedBy,
        String confirmedByName
    ) {
        log.info("Confirming inbound order: {}, confirmer: {}", orderId, confirmedByName);

        // 1. 查询入库单
        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        // 2. 验证状态
        if (!order.getStatus().canConfirmByPurchaser()) {
            throw new BusinessException(
                "INVALID_STATUS_FOR_CONFIRMATION",
                Map.of("currentStatus", order.getStatus(), "orderId", orderId)
            );
        }

        // 3. 获取所有明细项
        List<InboundOrderItem> items = order.getItems();

        // 4. 创建确认项的 ID 集合
        Set<Long> confirmedItemIds = request.getConfirmations().stream()
            .map(ConfirmOrderRequest.ItemConfirmation::getItemId)
            .collect(Collectors.toSet());

        // 5. 删除未确认的明细项（供应商无货）
        items.removeIf(item -> !confirmedItemIds.contains(item.getId()));

        // 6. 更新确认的明细项
        LocalDate batchDate = LocalDate.now();  // 使用当前日期作为批次日期

        for (ConfirmOrderRequest.ItemConfirmation confirmation : request.getConfirmations()) {
            InboundOrderItem item = items.stream()
                .filter(i -> i.getId().equals(confirmation.getItemId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                    "ITEM_NOT_FOUND",
                    Map.of("itemId", confirmation.getItemId())
                ));

            // 更新确认数量
            item.setConfirmedQty(confirmation.getConfirmedQty());

            // 生成批次码（SPU-SKU-DATE 格式）
            String batchCode = batchCodeGenerator.generateUnique(item.getProduct(), batchDate);
            item.setBatchCode(batchCode);

            // 更新其他信息
            item.setExpiryDate(confirmation.getExpiryDate());
            item.setProductionDate(confirmation.getProductionDate());
            item.setExternalBatchCode(confirmation.getExternalBatchCode());

            // 更新目标仓库和库位（如果提供）
            if (confirmation.getTargetWarehouseId() != null) {
                Warehouse warehouse = warehouseRepository.findById(confirmation.getTargetWarehouseId())
                    .orElseThrow(() -> new BusinessException(
                        "WAREHOUSE_NOT_FOUND",
                        Map.of("warehouseId", confirmation.getTargetWarehouseId())
                    ));
                item.setTargetWarehouse(warehouse);
            }

            if (confirmation.getTargetLocationId() != null) {
                Location location = locationRepository.findById(confirmation.getTargetLocationId())
                    .orElseThrow(() -> new BusinessException(
                        "LOCATION_NOT_FOUND",
                        Map.of("locationId", confirmation.getTargetLocationId())
                    ));
                item.setTargetLocation(location);
            }

            log.info("Generated batch code for item {}: {}", item.getId(), batchCode);
        }

        // 7. 更新状态为 AWAITING_RECEIVAL
        order.setStatus(InboundOrderStatus.AWAITING_RECEIVAL);
        order.setConfirmedBy(confirmedBy);
        order.setConfirmedAt(LocalDateTime.now());
        order.setConfirmationComment(request.getComment());

        // 8. 重新计算确认总数量
        order.recalculateTotalConfirmedQty();

        // 9. 追加审计日志
        String auditEntry = createAuditLog("采购员确认订单", confirmedByName);
        order.setAuditLog(appendAuditLog(order.getAuditLog(), auditEntry));

        // 10. 保存
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        log.info("Inbound order confirmed: {}, total confirmed qty: {}",
            savedOrder.getOrderNo(), savedOrder.getTotalConfirmedQty());

        return convertToResponse(savedOrder);
    }

    /**
     * 仓库收货
     *
     * 核心逻辑：
     * 1. 更新 actual_qty
     * 2. 创建或更新 InventoryBatch 记录
     * 3. 生成库存流水记录
     * 4. 更新状态为 COMPLETED
     *
     * @param orderId 入库单ID
     * @param request 收货请求
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @return 入库单响应
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderResponse receiveGoods(
        Long orderId,
        ReceiveGoodsRequest request,
        Long operatorId,
        String operatorName
    ) {
        log.info("Receiving goods for inbound order: {}, operator: {}", orderId, operatorName);

        // 1. 查询入库单
        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        // 2. 验证状态
        if (!order.getStatus().canReceive()) {
            throw new BusinessException(
                "INVALID_STATUS_FOR_RECEIVING",
                Map.of("currentStatus", order.getStatus(), "orderId", orderId)
            );
        }

        // 3. 更新明细项并创建库存记录
        for (ReceiveGoodsRequest.ItemReceipt receipt : request.getReceipts()) {
            InboundOrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(receipt.getItemId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                    "ITEM_NOT_FOUND",
                    Map.of("itemId", receipt.getItemId())
                ));

            // 验证实收数量不超过确认数量
            if (item.getConfirmedQty() != null && receipt.getActualQty() > item.getConfirmedQty()) {
                throw new BusinessException(
                    "ACTUAL_QTY_EXCEEDS_CONFIRMED_QTY",
                    Map.of("itemId", receipt.getItemId(),
                           "actualQty", receipt.getActualQty(),
                           "confirmedQty", item.getConfirmedQty())
                );
            }

            // 更新实收数量
            item.setActualQty(receipt.getActualQty());

            // 获取库位
            Location location = locationRepository.findById(receipt.getLocationId())
                .orElseThrow(() -> new BusinessException(
                    "LOCATION_NOT_FOUND",
                    Map.of("locationId", receipt.getLocationId())
                ));

            // 更新目标库位
            item.setTargetLocation(location);

            // 创建或更新库存批次记录
            if (receipt.getActualQty() > 0) {
                // 获取变动前的库存数量
                Integer quantityBefore = inventoryBatchRepository
                    .findByBatchCodeAndLocation(item.getBatchCode(), location)
                    .stream()
                    .findFirst()
                    .map(InventoryBatch::getQuantity)
                    .orElse(0);

                // 计算变动后的库存数量
                Integer quantityAfter = quantityBefore + receipt.getActualQty();

                // 更新库存批次
                createOrUpdateInventoryBatch(item, location, receipt.getActualQty());

                // 生成库存流水记录
                createStockTransaction(item, location, receipt.getActualQty(), order.getOrderNo(),
                    quantityBefore, quantityAfter);
            }

            log.info("Received goods for item {}: {} units to location {}",
                item.getId(), receipt.getActualQty(), location.getLocationCode());
        }

        // 4. 更新状态为 COMPLETED
        order.setStatus(InboundOrderStatus.COMPLETED);
        order.setReceivedBy(operatorId);
        order.setReceivedAt(LocalDateTime.now());

        // 5. 重新计算实收总数量
        order.recalculateTotalActualQty();

        // 6. 追加审计日志
        String auditEntry = createAuditLog("仓库收货完成", operatorName);
        order.setAuditLog(appendAuditLog(order.getAuditLog(), auditEntry));

        // 7. 保存
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        log.info("Inbound order completed: {}, total actual qty: {}",
            savedOrder.getOrderNo(), savedOrder.getTotalActualQty());

        return convertToResponse(savedOrder);
    }

    /**
     * 拒绝入库单
     *
     * @param orderId 入库单ID
     * @param reason 拒绝原因
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @return 入库单响应
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderResponse rejectOrder(
        Long orderId,
        String reason,
        Long operatorId,
        String operatorName
    ) {
        log.info("Rejecting inbound order: {}, operator: {}", orderId, operatorName);

        // 1. 查询入库单
        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        // 2. 验证状态
        if (!order.getStatus().canReject()) {
            throw new BusinessException(
                "INVALID_STATUS_FOR_REJECTION",
                Map.of("currentStatus", order.getStatus(), "orderId", orderId)
            );
        }

        // 3. 更新状态为 REJECTED
        order.setStatus(InboundOrderStatus.REJECTED);
        order.setGmApprovalComment(reason);  // 使用审批意见字段存储拒绝原因

        // 4. 追加审计日志
        String auditEntry = createAuditLog("拒绝入库单: " + reason, operatorName);
        order.setAuditLog(appendAuditLog(order.getAuditLog(), auditEntry));

        // 5. 保存
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        log.info("Inbound order rejected: {}", savedOrder.getOrderNo());

        return convertToResponse(savedOrder);
    }

    // ========== Delete/Cancel/Void Operations ==========

    /**
     * Delete inbound order (physical delete, PENDING_APPROVAL only)
     *
     * Business Rule:
     * - PENDING_APPROVAL status: physical DELETE from database
     * - Any other status: throws exception, use cancelInboundOrder or voidInboundOrder instead
     *
     * @param orderId 入库单ID
     * @param operatorId 操作人ID
     * @throws BusinessException if order not found or not in PENDING_APPROVAL status
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteInboundOrder(Long orderId, Long operatorId) {
        log.info("🗑️ Deleting inbound order (physical): orderId={}, operatorId={}", orderId, operatorId);

        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        if (!order.getStatus().canPhysicallyDelete()) {
            throw new BusinessException(
                "INVALID_STATUS_FOR_DELETION",
                Map.of(
                    "orderId", orderId,
                    "currentStatus", order.getStatus().name(),
                    "reason", "只有待审批状态的入库单可以物理删除，已生效的入库单请使用取消或作废"
                )
            );
        }

        inboundOrderRepository.deleteById(orderId);
        log.info("✅ Inbound order physically deleted: orderId={}, orderNo={}", orderId, order.getOrderNo());
    }

    /**
     * Cancel inbound order (business failure, kept for AI learning)
     *
     * Business Rule:
     * - PENDING_APPROVAL status → physical delete
     * - APPROVED_PLAN / AWAITING_RECEIVAL → set status = CANCELLED
     * - reason is required
     * - Data preserved for AI learning
     *
     * @param orderId 入库单ID
     * @param reason 取消原因 (required)
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @return 入库单响应
     * @throws BusinessException if order not found or cannot be cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderResponse cancelInboundOrder(
        Long orderId,
        String reason,
        Long operatorId,
        String operatorName
    ) {
        log.info("🚫 Cancelling inbound order: orderId={}, operator={}, reason={}", orderId, operatorName, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                "INVALID_CANCELLATION_REASON",
                Map.of("orderId", orderId, "reason", "业务取消必须填写取消原因")
            );
        }

        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        // PENDING_APPROVAL → physical delete
        if (order.getStatus() == InboundOrderStatus.PENDING_APPROVAL) {
            inboundOrderRepository.deleteById(orderId);
            log.info("✅ PENDING_APPROVAL inbound order physically deleted during cancel: orderId={}", orderId);
            return null;
        }

        if (!order.getStatus().canCancel()) {
            throw new BusinessException(
                "INVALID_STATUS_FOR_CANCELLATION",
                Map.of(
                    "orderId", orderId,
                    "currentStatus", order.getStatus().name(),
                    "reason", "当前状态不允许取消"
                )
            );
        }

        order.setStatus(InboundOrderStatus.CANCELLED);
        String auditEntry = createAuditLog("业务取消: " + reason, operatorName);
        order.setAuditLog(appendAuditLog(order.getAuditLog(), auditEntry));
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        log.info("✅ Inbound order cancelled: orderId={}, orderNo={}", orderId, savedOrder.getOrderNo());
        return convertToResponse(savedOrder);
    }

    /**
     * Void inbound order (data noise, filtered from AI and statistics)
     *
     * Business Rule:
     * - PENDING_APPROVAL status → physical delete
     * - APPROVED_PLAN / AWAITING_RECEIVAL → set status = VOIDED
     * - VOIDED orders are excluded from AI training and business statistics
     * - Financial audit trail is preserved (order number retained)
     *
     * @param orderId 入库单ID
     * @param reason 作废原因 (required)
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @return 入库单响应
     * @throws BusinessException if order not found or cannot be voided
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderResponse voidInboundOrder(
        Long orderId,
        String reason,
        Long operatorId,
        String operatorName
    ) {
        log.info("🚫 Voiding inbound order: orderId={}, operator={}, reason={}", orderId, operatorName, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                "INVALID_VOID_REASON",
                Map.of("orderId", orderId, "reason", "系统作废必须填写作废原因")
            );
        }

        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        // PENDING_APPROVAL → physical delete
        if (order.getStatus() == InboundOrderStatus.PENDING_APPROVAL) {
            inboundOrderRepository.deleteById(orderId);
            log.info("✅ PENDING_APPROVAL inbound order physically deleted during void: orderId={}", orderId);
            return null;
        }

        if (!order.getStatus().canVoid()) {
            throw new BusinessException(
                "INVALID_STATUS_FOR_VOID",
                Map.of(
                    "orderId", orderId,
                    "currentStatus", order.getStatus().name(),
                    "reason", "当前状态不允许作废"
                )
            );
        }

        order.setStatus(InboundOrderStatus.VOIDED);
        String auditEntry = createAuditLog("系统作废（数据噪音）: " + reason, operatorName);
        order.setAuditLog(appendAuditLog(order.getAuditLog(), auditEntry));
        InboundOrder savedOrder = inboundOrderRepository.save(order);

        log.info("✅ Inbound order voided: orderId={}, orderNo={}", orderId, savedOrder.getOrderNo());
        return convertToResponse(savedOrder);
    }

    /**
     * 根据ID查询入库单
     */
    public InboundOrderResponse getInboundOrderById(Long orderId) {
        InboundOrder order = inboundOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderId", orderId)
            ));

        return convertToResponse(order);
    }

    /**
     * 根据入库单号查询
     */
    public InboundOrderResponse getInboundOrderByOrderNo(String orderNo) {
        InboundOrder order = inboundOrderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new BusinessException(
                "INBOUND_ORDER_NOT_FOUND",
                Map.of("orderNo", orderNo)
            ));

        return convertToResponse(order);
    }

    /**
     * 查询指定状态的入库单列表
     */
    public List<InboundOrderResponse> getInboundOrdersByStatus(InboundOrderStatus status) {
        List<InboundOrder> orders = inboundOrderRepository.findByStatusOrderByCreatedAtDesc(status);
        return orders.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * 查询指定申请人的入库单列表
     */
    public List<InboundOrderResponse> getInboundOrdersByApplicant(Long applicantId) {
        List<InboundOrder> orders = inboundOrderRepository.findByApplicantId(applicantId);
        return orders.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成入库单号
     * 格式：IB + YYYYMMDD + 4位序号
     * 示例：IB20260125-0001
     */
    private String generateOrderNo() {
        String dateStr = LocalDate.now().format(ORDER_NO_DATE_FORMAT);
        String prefix = ORDER_NO_PREFIX + dateStr;

        // 查询当天最大序号
        List<InboundOrder> todayOrders = inboundOrderRepository.findAll().stream()
            .filter(order -> order.getOrderNo().startsWith(prefix))
            .collect(Collectors.toList());

        int maxSeq = todayOrders.stream()
            .map(order -> {
                String seqStr = order.getOrderNo().substring(prefix.length() + 1);
                try {
                    return Integer.parseInt(seqStr);
                } catch (NumberFormatException e) {
                    return 0;
                }
            })
            .max(Integer::compareTo)
            .orElse(0);

        int nextSeq = maxSeq + 1;
        return String.format("%s-%04d", prefix, nextSeq);
    }

    /**
     * 创建或更新库存批次记录
     */
    private void createOrUpdateInventoryBatch(
        InboundOrderItem item,
        Location location,
        Integer quantity
    ) {
        // 查找是否已存在相同批次码和库位的记录
        List<InventoryBatch> existingBatches = inventoryBatchRepository
            .findByBatchCodeAndLocation(item.getBatchCode(), location);

        if (!existingBatches.isEmpty()) {
            // 更新现有批次
            InventoryBatch batch = existingBatches.get(0);
            batch.setQuantity(batch.getQuantity() + quantity);
            inventoryBatchRepository.save(batch);

            log.info("Updated inventory batch: {}, new quantity: {}",
                batch.getBatchCode(), batch.getQuantity());
        } else {
            // 验证必填字段
            if (item.getExpiryDate() == null) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("field", "expiryDate", "message", "Expiry date is required for creating inventory batch")
                );
            }

            // 创建新批次
            InventoryBatch newBatch = InventoryBatch.builder()
                .product(item.getProduct())
                .location(location)
                .locationCode(location.getLocationCode())  // V3.3: 必填字段
                .batchCode(item.getBatchCode())
                .quantity(quantity)
                .initialQuantity(quantity)  // 初始数量等于入库数量
                .expiryDate(item.getExpiryDate())
                .productionDate(item.getProductionDate())
                .entryDate(LocalDateTime.now())
                .active(true)  // 激活状态
                .build();

            inventoryBatchRepository.save(newBatch);

            log.info("Created new inventory batch: {}, quantity: {}",
                newBatch.getBatchCode(), newBatch.getQuantity());
        }
    }

    /**
     * 创建库存流水记录
     */
    private void createStockTransaction(
        InboundOrderItem item,
        Location location,
        Integer quantity,
        String orderNo,
        Integer quantityBefore,
        Integer quantityAfter
    ) {
        StockTransaction transaction = StockTransaction.builder()
            .product(item.getProduct())
            .location(location)
            .transactionType(TransactionType.IN)
            .sourceType(SourceType.INBOUND_IN)
            .quantity(quantity)
            .quantityBefore(quantityBefore)
            .quantityAfter(quantityAfter)
            .sourceOrderId(orderNo)
            .build();

        stockTransactionRepository.save(transaction);

        log.info("Created stock transaction: {} units of product {} to location {}, before: {}, after: {}",
            quantity, item.getProduct().getId(), location.getLocationCode(), quantityBefore, quantityAfter);
    }

    /**
     * 创建审计日志条目
     */
    private String createAuditLog(String action, String operator) {
        return String.format("[%s] %s - %s",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            action,
            operator);
    }

    /**
     * 追加审计日志
     */
    private String appendAuditLog(String existingLog, String newEntry) {
        if (existingLog == null || existingLog.trim().isEmpty()) {
            return newEntry;
        }
        return existingLog + "\n" + newEntry;
    }

    /**
     * 转换为响应 DTO
     */
    private InboundOrderResponse convertToResponse(InboundOrder order) {
        // 构建明细列表
        List<InboundOrderResponse.InboundOrderItemResponse> itemResponses = order.getItems().stream()
            .map(this::convertItemToResponse)
            .collect(Collectors.toList());

        // 构建主响应
        return InboundOrderResponse.builder()
            .id(order.getId())
            .orderNo(order.getOrderNo())
            .status(order.getStatus())
            .statusDescription(order.getStatus().getDescription())
            .supplierId(order.getSupplier().getId())
            .supplierCode(order.getSupplier().getCode())
            .supplierName(order.getSupplier().getName())
            .totalPlanQty(order.getTotalPlanQty())
            .totalConfirmedQty(order.getTotalConfirmedQty())
            .totalActualQty(order.getTotalActualQty())
            .expectedDate(order.getExpectedDate())
            .remark(order.getRemark())
            .gmApprovedBy(order.getGmApprovedBy())
            .gmApprovedAt(order.getGmApprovedAt())
            .gmApprovalComment(order.getGmApprovalComment())
            .confirmedBy(order.getConfirmedBy())
            .confirmedAt(order.getConfirmedAt())
            .confirmationComment(order.getConfirmationComment())
            .receivedBy(order.getReceivedBy())
            .receivedAt(order.getReceivedAt())
            .applicantId(order.getApplicantId())
            .applicantName(order.getApplicantName())
            .auditLog(order.getAuditLog())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .items(itemResponses)
            .build();
    }

    /**
     * 转换明细项为响应 DTO
     */
    private InboundOrderResponse.InboundOrderItemResponse convertItemToResponse(InboundOrderItem item) {
        return InboundOrderResponse.InboundOrderItemResponse.builder()
            .id(item.getId())
            .productId(item.getProduct().getId())
            .productName(item.getProduct().getName())
            .productBarcode(item.getProduct().getBarcode())
            .productSpu(item.getProduct().getSpu() != null ? item.getProduct().getSpu().getSpuCode() : null)
            .productSku(item.getProduct().getSkuName())
            .planQty(item.getPlanQty())
            .confirmedQty(item.getConfirmedQty())
            .actualQty(item.getActualQty())
            .batchCode(item.getBatchCode())
            .expiryDate(item.getExpiryDate())
            .productionDate(item.getProductionDate())
            .externalBatchCode(item.getExternalBatchCode())
            .targetWarehouseId(item.getTargetWarehouse() != null ? item.getTargetWarehouse().getId() : null)
            .targetWarehouseName(item.getTargetWarehouse() != null ? item.getTargetWarehouse().getName() : null)
            .targetLocationId(item.getTargetLocation() != null ? item.getTargetLocation().getId() : null)
            .targetLocationCode(item.getTargetLocation() != null ? item.getTargetLocation().getLocationCode() : null)
            .unitCost(item.getUnitCost())
            .remark(item.getRemark())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }
}
