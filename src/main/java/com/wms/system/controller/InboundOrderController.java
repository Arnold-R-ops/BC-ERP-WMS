package com.wms.system.controller;

import com.wms.system.dto.inbound.*;
import com.wms.system.entity.enums.InboundOrderStatus;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.InboundOrderService;
import com.wms.system.service.WarehouseScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 入库单管理控制器
 *
 * 提供入库单的 RESTful API：
 * - 创建入库单（BUYER, SELLER, WAREHOUSE_ADMIN）
 * - 总经理审批（CHAIRMAN, SUPER_ADMIN）
 * - 采购员确认（BUYER, 原申请人）
 * - 仓库收货（WAREHOUSE_ADMIN）
 * - 拒绝入库单（CHAIRMAN, SUPER_ADMIN）
 * - 查询操作（所有角色）
 *
 * 业务流程：
 * User (Apply) → GM (Approve Plan) → User (Confirm Order) → System (Generate Batch Code) → Warehouse (Receive)
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Slf4j
@RestController
@RequestMapping("/api/inbound-orders")
@RequiredArgsConstructor
public class InboundOrderController {

    private final InboundOrderService inboundOrderService;
    private final WarehouseScopeService warehouseScopeService;

    /**
     * 创建入库单
     *
     * POST /api/inbound-orders
     *
     * 权限：BUYER, SELLER, WAREHOUSE_ADMIN
     *
     * Request Body:
     * {
     *   "supplierId": 1,
     *   "expectedDate": "2026-02-15",
     *   "items": [
     *     {
     *       "productSkuId": 101,
     *       "planQty": 100,
     *       "unitCost": 10.50,
     *       "targetWarehouseId": 1,
     *       "targetLocationId": 10
     *     }
     *   ],
     *   "remark": "春节备货"
     * }
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('inbound:create', 'SUPER_ADMIN')")
    public ResponseEntity<InboundOrderResponse> createInboundOrder(
        @Valid @RequestBody CreateInboundOrderRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("Creating inbound order, user: {}", user.getUsername());

        InboundOrderResponse response = inboundOrderService.createInboundOrder(
            request,
            user.getId(),
            user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 总经理审批计划
     *
     * POST /api/inbound-orders/{id}/approve-plan
     *
     * 权限：CHAIRMAN, SUPER_ADMIN
     *
     * Request Body:
     * {
     *   "comment": "批准，按计划采购"
     * }
     */
    @PostMapping("/{id}/approve-plan")
    @PreAuthorize("hasAnyAuthority('inbound:approve_plan', 'SUPER_ADMIN')")
    public ResponseEntity<InboundOrderResponse> approvePlan(
        @PathVariable Long id,
        @Valid @RequestBody ApprovalRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("GM approving inbound order: {}, user: {}", id, user.getUsername());

        InboundOrderResponse response = inboundOrderService.approvePlan(
            id,
            user.getId(),
            user.getUsername(),
            request.getComment()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 采购员确认订单
     *
     * POST /api/inbound-orders/{id}/confirm-order
     *
     * 权限：BUYER, 原申请人
     *
     * Request Body:
     * {
     *   "confirmations": [
     *     {
     *       "itemId": 1,
     *       "confirmedQty": 90,
     *       "expiryDate": "2026-12-31",
     *       "productionDate": "2026-01-20",
     *       "targetWarehouseId": 1,
     *       "targetLocationId": 10
     *     }
     *   ],
     *   "comment": "供应商确认可提供 90 件"
     * }
     */
    @PostMapping("/{id}/confirm-order")
    @PreAuthorize("hasAnyAuthority('inbound:confirm_order', 'SUPER_ADMIN')")
    public ResponseEntity<InboundOrderResponse> confirmOrder(
        @PathVariable Long id,
        @Valid @RequestBody ConfirmOrderRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("Confirming inbound order: {}, user: {}", id, user.getUsername());

        InboundOrderResponse response = inboundOrderService.confirmOrder(
            id,
            request,
            user.getId(),
            user.getUsername()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 仓库收货
     *
     * POST /api/inbound-orders/{id}/receive-goods
     *
     * 权限：WAREHOUSE_ADMIN
     *
     * Request Body:
     * {
     *   "receipts": [
     *     {
     *       "itemId": 1,
     *       "actualQty": 88,
     *       "locationId": 10
     *     }
     *   ]
     * }
     */
    @PostMapping("/{id}/receive-goods")
    @PreAuthorize("hasAnyAuthority('inbound:receive_goods', 'SUPER_ADMIN')")
    public ResponseEntity<InboundOrderResponse> receiveGoods(
        @PathVariable Long id,
        @Valid @RequestBody ReceiveGoodsRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("Receiving goods for inbound order: {}, user: {}", id, user.getUsername());

        if (warehouseScopeService.isWarehouseStaff(authentication)) {
            requireOrderAccess(authentication, inboundOrderService.getInboundOrderById(id));
        }

        InboundOrderResponse response = inboundOrderService.receiveGoods(
            id,
            request,
            user.getId(),
            user.getUsername()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 拒绝入库单
     *
     * POST /api/inbound-orders/{id}/reject
     *
     * 权限：CHAIRMAN, SUPER_ADMIN
     *
     * Request Body:
     * {
     *   "reason": "预算不足，暂缓采购"
     * }
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('inbound:reject', 'SUPER_ADMIN')")
    public ResponseEntity<InboundOrderResponse> rejectOrder(
        @PathVariable Long id,
        @Valid @RequestBody RejectRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("Rejecting inbound order: {}, user: {}", id, user.getUsername());

        InboundOrderResponse response = inboundOrderService.rejectOrder(
            id,
            request.getReason(),
            user.getId(),
            user.getUsername()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 根据ID查询入库单
     *
     * GET /api/inbound-orders/{id}
     *
     * 权限：所有角色
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('inbound:view', 'SUPER_ADMIN')")
    public ResponseEntity<InboundOrderResponse> getInboundOrderById(
        @PathVariable Long id,
        Authentication authentication
    ) {
        log.info("Getting inbound order by ID: {}", id);

        InboundOrderResponse response = inboundOrderService.getInboundOrderById(id);
        requireOrderAccess(authentication, response);

        return ResponseEntity.ok(response);
    }

    /**
     * 根据入库单号查询
     *
     * GET /api/inbound-orders/by-order-no/{orderNo}
     *
     * 权限：所有角色
     */
    @GetMapping("/by-order-no/{orderNo}")
    @PreAuthorize("hasAnyAuthority('inbound:view', 'SUPER_ADMIN')")
    public ResponseEntity<InboundOrderResponse> getInboundOrderByOrderNo(
        @PathVariable String orderNo,
        Authentication authentication
    ) {
        log.info("Getting inbound order by order no: {}", orderNo);

        InboundOrderResponse response = inboundOrderService.getInboundOrderByOrderNo(orderNo);
        requireOrderAccess(authentication, response);

        return ResponseEntity.ok(response);
    }

    /**
     * 查询指定状态的入库单列表
     *
     * GET /api/inbound-orders/by-status/{status}
     *
     * 权限：所有角色
     */
    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasAnyAuthority('inbound:list', 'SUPER_ADMIN')")
    public ResponseEntity<List<InboundOrderResponse>> getInboundOrdersByStatus(
        @PathVariable InboundOrderStatus status,
        Authentication authentication
    ) {
        log.info("Getting inbound orders by status: {}", status);

        List<InboundOrderResponse> responses = inboundOrderService.getInboundOrdersByStatus(status);

        return ResponseEntity.ok(filterOrders(authentication, responses));
    }

    /**
     * 查询当前用户的入库单列表
     *
     * GET /api/inbound-orders/my-orders
     *
     * 权限：所有角色
     */
    @GetMapping("/my-orders")
    @PreAuthorize("hasAnyAuthority('inbound:list', 'SUPER_ADMIN')")
    public ResponseEntity<List<InboundOrderResponse>> getMyInboundOrders(Authentication authentication) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("Getting inbound orders for user: {}", user.getUsername());

        List<InboundOrderResponse> responses = inboundOrderService.getInboundOrdersByApplicant(user.getId());

        return ResponseEntity.ok(filterOrders(authentication, responses));
    }

    /**
     * 查询待审批的入库单列表（总经理）
     *
     * GET /api/inbound-orders/pending-approval
     *
     * 权限：CHAIRMAN, SUPER_ADMIN
     */
    @GetMapping("/pending-approval")
    @PreAuthorize("hasAnyAuthority('inbound:approve_plan', 'SUPER_ADMIN')")
    public ResponseEntity<List<InboundOrderResponse>> getPendingApprovalOrders() {
        log.info("Getting pending approval inbound orders");

        List<InboundOrderResponse> responses = inboundOrderService
            .getInboundOrdersByStatus(InboundOrderStatus.PENDING_APPROVAL);

        return ResponseEntity.ok(responses);
    }

    /**
     * 查询待确认的入库单列表（采购员）
     *
     * GET /api/inbound-orders/pending-confirmation
     *
     * 权限：BUYER
     */
    @GetMapping("/pending-confirmation")
    @PreAuthorize("hasAnyAuthority('inbound:confirm_order', 'SUPER_ADMIN')")
    public ResponseEntity<List<InboundOrderResponse>> getPendingConfirmationOrders() {
        log.info("Getting pending confirmation inbound orders");

        List<InboundOrderResponse> responses = inboundOrderService
            .getInboundOrdersByStatus(InboundOrderStatus.APPROVED_PLAN);

        return ResponseEntity.ok(responses);
    }

    /**
     * 查询待收货的入库单列表（仓库管理员）
     *
     * GET /api/inbound-orders/pending-receival
     *
     * 权限：WAREHOUSE_ADMIN
     */
    @GetMapping("/pending-receival")
    @PreAuthorize("hasAnyAuthority('inbound:receive_goods', 'SUPER_ADMIN')")
    public ResponseEntity<List<InboundOrderResponse>> getPendingReceivalOrders(
        Authentication authentication
    ) {
        log.info("Getting pending receival inbound orders");

        List<InboundOrderResponse> responses = inboundOrderService
            .getInboundOrdersByStatus(InboundOrderStatus.AWAITING_RECEIVAL);

        return ResponseEntity.ok(filterOrders(authentication, responses));
    }

    private List<InboundOrderResponse> filterOrders(
        Authentication authentication,
        List<InboundOrderResponse> orders
    ) {
        return warehouseScopeService.filterAccessible(
            authentication,
            orders,
            this::warehouseIds
        );
    }

    private void requireOrderAccess(Authentication authentication, InboundOrderResponse order) {
        warehouseScopeService.requireAccess(
            authentication,
            warehouseIds(order),
            "INBOUND_ORDER",
            order.getId()
        );
    }

    private List<Long> warehouseIds(InboundOrderResponse order) {
        if (order.getItems() == null) {
            return List.of();
        }
        return order.getItems().stream()
            .map(InboundOrderResponse.InboundOrderItemResponse::getTargetWarehouseId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }
}
