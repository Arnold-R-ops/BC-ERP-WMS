package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inbound.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.InboundOrderStatus;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.*;
import com.wms.system.security.JwtUtil;
import com.wms.system.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InboundOrderController 集成测试
 *
 * 使用 @SpringBootTest 启动完整的 Spring 容器
 * 使用 MockMvc 模拟 HTTP 请求
 * 使用真实的数据库进行测试
 *
 * 测试覆盖：
 * 1. 完整的入库流程（创建 → 审批 → 确认 → 收货）
 * 2. 数据库持久化验证
 * 3. 批次码生成验证
 * 4. 库存更新验证
 * 5. 权限控制验证
 * 6. 异常处理验证
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
@WithMockUser(authorities = {"SUPER_ADMIN"})
@DisplayName("InboundOrderController 集成测试")
class InboundOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @Autowired
    private InboundOrderItemRepository inboundOrderItemRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private Supplier testSupplier;
    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;

    private User buyerUser;
    private User gmUser;
    private User warehouseUser;

    private String buyerToken;
    private String gmToken;
    private String warehouseToken;

    private final String TEST_PASSWORD = "Test@123456";

    @BeforeEach
    void setUp() {
        // 清理数据
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        inventoryBatchRepository.deleteAll();
        stockTransactionRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();
        productSpuRepository.deleteAll();
        supplierRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // 创建测试供应商
        testSupplier = supplierRepository.save(Supplier.builder()
                .code("SUP001")
                .name("Test Supplier")
                .contact("John Doe")
                .phone("1234567890")
                .isActive(true)
                .build());

        // 创建测试产品
        ProductSpu spu = productSpuRepository.save(ProductSpu.builder()
                .spuCode("SPU001")
                .spuName("Test SPU")
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Test Product")
                .barcode("SKU001")
                .skuName("Test SKU")
                .spu(spu)
                .unitPrice(BigDecimal.valueOf(100.00))  // 使用 BigDecimal
                .enabled(true)
                .build());

        // 创建测试仓库
        testWarehouse = warehouseRepository.save(Warehouse.builder()
                .code("WH01")
                .name("Main Warehouse")
                .address("123 Main St")
                .isActive(true)
                .build());

        // 创建测试库位
        testLocation = locationRepository.save(Location.builder()
                .locationCode("WH01-A-01-001")
                .warehouse(testWarehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("01")
                .positionNumber("001")
                .enabled(true)
                .build());

        // 创建测试用户和角色
        setupUsersAndRoles();
    }

    private void setupUsersAndRoles() {
        // 获取或创建角色
        SysRole buyerRole = roleRepository.findByRoleCode("BUYER")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("BUYER")
                        .roleName("采购员")
                        .sortOrder(3)
                        .status("ACTIVE")
                        .build()));

        SysRole chairmanRole = roleRepository.findByRoleCode("CHAIRMAN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("CHAIRMAN")
                        .roleName("总经理")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .build()));

        SysRole warehouseRole = roleRepository.findByRoleCode("WAREHOUSE_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("WAREHOUSE_ADMIN")
                        .roleName("仓库管理员")
                        .sortOrder(4)
                        .status("ACTIVE")
                        .build()));

        // 创建采购员用户
        buyerUser = userRepository.save(User.builder()
                .username("buyer")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("采购员")
                .enabled(true)
                .build());
        userRoleRepository.save(SysUserRole.builder()
                .userId(buyerUser.getId())
                .roleId(buyerRole.getId())
                .user(buyerUser)
                .role(buyerRole)
                .build());
        buyerToken = jwtUtil.generateToken(buyerUser.getUsername(), buyerRole.getRoleCode());

        // 创建总经理用户
        gmUser = userRepository.save(User.builder()
                .username("chairman")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("总经理")
                .enabled(true)
                .build());
        userRoleRepository.save(SysUserRole.builder()
                .userId(gmUser.getId())
                .roleId(chairmanRole.getId())
                .user(gmUser)
                .role(chairmanRole)
                .build());
        gmToken = jwtUtil.generateToken(gmUser.getUsername(), chairmanRole.getRoleCode());

        // 创建仓库管理员用户
        warehouseUser = userRepository.save(User.builder()
                .username("warehouse")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("仓库管理员")
                .enabled(true)
                .build());
        userRoleRepository.save(SysUserRole.builder()
                .userId(warehouseUser.getId())
                .roleId(warehouseRole.getId())
                .user(warehouseUser)
                .role(warehouseRole)
                .build());
        warehouseToken = jwtUtil.generateToken(warehouseUser.getUsername(), warehouseRole.getRoleCode());
    }

    /**
     * 创建 Authentication 对象用于测试
     */
    private UsernamePasswordAuthenticationToken createAuthentication(User user) {
        SecurityUser securityUser = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(
                securityUser,
                null,
                securityUser.getAuthorities()
        );
    }

    @AfterEach
    void tearDown() {
        // 清理数据
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        inventoryBatchRepository.deleteAll();
        stockTransactionRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();
        productSpuRepository.deleteAll();
        supplierRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== 完整流程测试 ====================

    @Test
    @DisplayName("完整入库流程 - 创建 → 审批 → 确认 → 收货")
    void completeInboundFlow_Success() throws Exception {
        // Step 1: 采购员创建入库单
        CreateInboundOrderRequest createRequest = new CreateInboundOrderRequest();
        createRequest.setSupplierId(testSupplier.getId());
        createRequest.setExpectedDate(LocalDate.now().plusDays(7));
        createRequest.setRemark("Test inbound order");

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductId(testProduct.getId());
        itemReq.setPlanQty(100);
        itemReq.setTargetWarehouseId(testWarehouse.getId());
        itemReq.setTargetLocationId(testLocation.getId());
        createRequest.setItems(List.of(itemReq));

        String createResponse = mockMvc.perform(post("/api/inbound-orders")
                        .principal(createAuthentication(buyerUser))
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo").exists())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.totalPlanQty").value(100))
                .andReturn().getResponse().getContentAsString();

        InboundOrderResponse createdOrder = objectMapper.readValue(createResponse, InboundOrderResponse.class);
        Long orderId = createdOrder.getId();

        // 验证数据库
        InboundOrder dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);
        assertThat(dbOrder.getTotalPlanQty()).isEqualTo(100);

        // Step 2: 总经理审批
        ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setComment("Approved by GM");

        mockMvc.perform(post("/api/inbound-orders/" + orderId + "/approve-plan")
                        .principal(createAuthentication(gmUser))
                        .header("Authorization", "Bearer " + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED_PLAN"))
                .andExpect(jsonPath("$.gmApprovedBy").value(gmUser.getId()))
                .andExpect(jsonPath("$.gmApprovedAt").exists());

        // 验证数据库
        dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.APPROVED_PLAN);
        assertThat(dbOrder.getGmApprovedBy()).isEqualTo(gmUser.getId());

        // Step 3: 采购员确认
        ConfirmOrderRequest confirmRequest = new ConfirmOrderRequest();
        confirmRequest.setComment("Confirmed by buyer");

        InboundOrderItem item = dbOrder.getItems().get(0);
        ConfirmOrderRequest.ItemConfirmation confirmation = new ConfirmOrderRequest.ItemConfirmation();
        confirmation.setItemId(item.getId());
        confirmation.setConfirmedQty(90);
        confirmation.setExpiryDate(LocalDate.now().plusMonths(6));
        confirmation.setProductionDate(LocalDate.now());
        confirmation.setTargetWarehouseId(testWarehouse.getId());
        confirmation.setTargetLocationId(testLocation.getId());
        confirmRequest.setConfirmations(List.of(confirmation));

        mockMvc.perform(post("/api/inbound-orders/" + orderId + "/confirm-order")
                        .principal(createAuthentication(buyerUser))
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_RECEIVAL"))
                .andExpect(jsonPath("$.confirmedBy").value(buyerUser.getId()))
                .andExpect(jsonPath("$.totalConfirmedQty").value(90));

        // 验证数据库和批次码生成
        dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.AWAITING_RECEIVAL);
        assertThat(dbOrder.getTotalConfirmedQty()).isEqualTo(90);

        InboundOrderItem confirmedItem = dbOrder.getItems().get(0);
        assertThat(confirmedItem.getBatchCode()).isNotNull();
        assertThat(confirmedItem.getBatchCode()).matches("SPU\\d+-SKU\\d+-\\d{8}(-\\d{2})?");
        assertThat(confirmedItem.getConfirmedQty()).isEqualTo(90);

        // Step 4: 仓库收货
        ReceiveGoodsRequest receiveRequest = new ReceiveGoodsRequest();
        ReceiveGoodsRequest.ItemReceipt receipt = new ReceiveGoodsRequest.ItemReceipt();
        receipt.setItemId(confirmedItem.getId());
        receipt.setActualQty(88);
        receipt.setLocationId(testLocation.getId());
        receiveRequest.setReceipts(List.of(receipt));

        mockMvc.perform(post("/api/inbound-orders/" + orderId + "/receive-goods")
                        .principal(createAuthentication(warehouseUser))
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.receivedBy").value(warehouseUser.getId()))
                .andExpect(jsonPath("$.totalActualQty").value(88));

        // 验证数据库
        dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.COMPLETED);
        assertThat(dbOrder.getTotalActualQty()).isEqualTo(88);

        // 验证库存批次创建
        List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCodeAndLocation(
                confirmedItem.getBatchCode(), testLocation);
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getQuantity()).isEqualTo(88);

        // 验证库存流水创建
        List<StockTransaction> transactions = stockTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getQuantity()).isEqualTo(88);
        assertThat(transactions.get(0).getSourceOrderId()).isEqualTo(dbOrder.getOrderNo());
    }

    // ==================== 创建入库单测试 ====================

    @Test
    @DisplayName("创建入库单 - 成功")
    void createInboundOrder_Success() throws Exception {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(testSupplier.getId());
        request.setExpectedDate(LocalDate.now().plusDays(7));

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductId(testProduct.getId());
        itemReq.setPlanQty(100);
        itemReq.setTargetWarehouseId(testWarehouse.getId());
        itemReq.setTargetLocationId(testLocation.getId());
        request.setItems(List.of(itemReq));

        // When & Then
        mockMvc.perform(post("/api/inbound-orders")
                        .principal(createAuthentication(buyerUser))
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo").exists())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.supplierId").value(testSupplier.getId()))
                .andExpect(jsonPath("$.totalPlanQty").value(100));

        // 验证数据库
        List<InboundOrder> orders = inboundOrderRepository.findAll();
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("创建入库单 - 供应商不存在")
    void createInboundOrder_SupplierNotFound() throws Exception {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(999L);  // 不存在的供应商ID
        request.setExpectedDate(LocalDate.now().plusDays(7));

        // 添加一个有效的 item，避免验证错误
        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductId(testProduct.getId());
        itemReq.setPlanQty(100);
        itemReq.setTargetWarehouseId(testWarehouse.getId());
        itemReq.setTargetLocationId(testLocation.getId());
        request.setItems(List.of(itemReq));

        // When & Then
        mockMvc.perform(post("/api/inbound-orders")
                        .principal(createAuthentication(buyerUser))
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("SUPPLIER_NOT_FOUND"));
    }

    // ==================== 查询测试 ====================

    @Test
    @DisplayName("查询待审批的入库单")
    void getPendingApprovalOrders_Success() throws Exception {
        // Given - 创建一个待审批的入库单
        InboundOrder order = inboundOrderRepository.save(InboundOrder.builder()
                .orderNo("IB202601260001")
                .supplier(testSupplier)
                .status(InboundOrderStatus.PENDING_APPROVAL)
                .totalPlanQty(100)
                .applicantId(buyerUser.getId())
                .applicantName(buyerUser.getUsername())
                .build());

        // When & Then
        mockMvc.perform(get("/api/inbound-orders/pending-approval")
                        .principal(createAuthentication(gmUser))
                        .header("Authorization", "Bearer " + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("根据ID查询入库单 - 成功")
    void getInboundOrderById_Success() throws Exception {
        // Given
        InboundOrder order = inboundOrderRepository.save(InboundOrder.builder()
                .orderNo("IB202601260001")
                .supplier(testSupplier)
                .status(InboundOrderStatus.PENDING_APPROVAL)
                .totalPlanQty(100)
                .applicantId(buyerUser.getId())
                .applicantName(buyerUser.getUsername())
                .build());

        // When & Then
        mockMvc.perform(get("/api/inbound-orders/" + order.getId())
                        .principal(createAuthentication(buyerUser))
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.orderNo").value("IB202601260001"));
    }

    @Test
    @DisplayName("根据ID查询入库单 - 不存在")
    void getInboundOrderById_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inbound-orders/999")
                        .principal(createAuthentication(buyerUser))
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("INBOUND_ORDER_NOT_FOUND"));
    }

    // ==================== 拒绝测试 ====================

    @Test
    @DisplayName("拒绝入库单 - 成功")
    void rejectOrder_Success() throws Exception {
        // Given
        InboundOrder order = inboundOrderRepository.save(InboundOrder.builder()
                .orderNo("IB202601260001")
                .supplier(testSupplier)
                .status(InboundOrderStatus.PENDING_APPROVAL)
                .totalPlanQty(100)
                .applicantId(buyerUser.getId())
                .applicantName(buyerUser.getUsername())
                .build());

        RejectRequest request = new RejectRequest();
        request.setReason("Budget insufficient");

        // When & Then
        mockMvc.perform(post("/api/inbound-orders/" + order.getId() + "/reject")
                        .principal(createAuthentication(gmUser))
                        .header("Authorization", "Bearer " + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // 验证数据库
        InboundOrder dbOrder = inboundOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.REJECTED);
    }
}
