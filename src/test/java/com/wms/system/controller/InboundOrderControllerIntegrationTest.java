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
 * InboundOrderController 闂傚倸妫楀Λ娆撳垂濮橆優鍦偓锝庡幘濡?
 *
 * 婵炶揪缍€濞夋洟寮?@SpringBootTest 闂佸憡鍑归崹鐗堟叏閳哄啠鍋撻悷鐗堟拱闁哄棴缍侀幆?Spring 闁诲骸婀遍幊鎾斥枍?
 * 婵炶揪缍€濞夋洟寮?MockMvc 濠碘槅鍨崜婵堚偓?HTTP 闁荤姴娲弨閬嶆儑?
 * 婵炶揪缍€濞夋洟寮妶澶嬪剳闁绘棃顥撻弶浠嬫煟閵娿儱顏柡鍡欏枛楠炴垿顢欓懖鈺傜殤闁哄鏅滅粙鏍€侀幋婢濆湱鈧綆鍘惧Σ?
 *
 * 濠电偞娼欓鍫ユ儊椤栨粍鍟洪柛鈩冪懄绾句即鏌?
 * 1. 闁诲海鎳撻張顒勫汲閿濆鍎嶉柛鏇ㄥ亜瀵娊骞栫€涙ɑ灏扮紒渚婂缁瑧鈧絽澧庣粈鍕煕閹烘挾鈽夌紓?闂?闁诲骸鍘滈崜婵囩珶?闂?缂佺虎鍙庨崰娑㈩敇?闂?闂佽　鍋撻柟顖滃瑜版盯鏌?
 * 2. 闂佽桨鑳舵晶妤€鐣垫担瑙勫劅闁圭偓娼欓惁顔尖槈閺傛寧鍣归悗闈涘级椤ㄣ儱鐣濋崘顏咁潔
 * 3. 闂侀€涚祷椤顢曟總鍛婂剺濞达絿鍎ら弲鎼佹煙鐎涙ê濮嶉柣娑欑懅閹?
 * 4. 闁圭厧鐡ㄩ幐鎼佹偤閵娾晛鍗抽悗娑櫳戦悡鈧俊銈囧Т閻線鎯?
 * 5. 闂佸搫顦崯鏉戭瀶濞差亜绠崇憸宥夊春濡や緡娈界€光偓閸愵亝顫?
 * 6. 閻庢鍠栭崐鎼佹偉閼搁潧绶為柛鏇ㄥ幗閸婄偛螖閻樿尙鐒烽柣?
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
@WithMockUser(authorities = {"SUPER_ADMIN"})
@DisplayName("case-1")
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
    private ProductSkuRepository productSkuRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
    private ProductSku testProduct;
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
        // 濠电偞鎸搁幊鎰板箖婵犲洤鏋侀柣妤€鐗嗙粊?
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        inventoryBatchRepository.deleteAll();
        stockTransactionRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productSkuRepository.deleteAll();
        productRepository.deleteAll();
        supplierRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡茬銆掑顓犫槈缂併劍鐓″畷?
        testSupplier = supplierRepository.save(Supplier.builder()
                .code("SUP001")
                .name("Test Supplier")
                .contact("John Doe")
                .phone("1234567890")
                .isActive(true)
                .build());

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡茬霉濠х姴鍟幆?
        Product spu = productRepository.save(Product.builder()
                .category(com.wms.system.support.TestCatalogFactory.saveLeafCategory(categoryRepository))
                .productCode("SPU001")
                .productName("Test SPU")
                .build());

        testProduct = productSkuRepository.save(ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .name("Test ProductSku")
                .barcode("SKU001")
                .skuName("Test SKU")
                .product(spu)
                .unitPrice(BigDecimal.valueOf(100.00))  // 婵炶揪缍€濞夋洟寮?BigDecimal
                .enabled(true)
                .build());

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡茬霉閻樿櫕灏紒?
        testWarehouse = warehouseRepository.save(Warehouse.builder()
                .code("WH01")
                .name("Main Warehouse")
                .address("123 Main St")
                .isActive(true)
                .build());

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂骞栫€涙ɑ灏紓?
        testLocation = locationRepository.save(Location.builder()
                .locationCode("WH01-A-01-001")
                .warehouse(testWarehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("01")
                .positionNumber("001")
                .enabled(true)
                .build());

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡介柛鈺傜洴瀹曨亜鐣濋崘鐐仴闂?
        setupUsersAndRoles();
    }

    private void setupUsersAndRoles() {
        // 闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ｉ柡宓啰浠悗鐐瑰€濈紓姘讹綖濡ゅ懏鍤?
        SysRole buyerRole = roleRepository.findByRoleCode("BUYER")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("BUYER")
                        .roleName("闂備焦褰冨ú鈺呭窗濮椻偓瀹?")
                        .sortOrder(3)
                        .status("ACTIVE")
                        .build()));

        SysRole chairmanRole = roleRepository.findByRoleCode("CHAIRMAN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("CHAIRMAN")
                        .roleName("闂佽鍓濆畷鐢靛垝閿熺姵鍋?")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .build()));

        SysRole warehouseRole = roleRepository.findByRoleCode("WAREHOUSE_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("WAREHOUSE_ADMIN")
                        .roleName("婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌?")
                        .sortOrder(4)
                        .status("ACTIVE")
                        .build()));

        // 闂佸憡甯楃粙鎴犵磽閹剧粯鐓傞柛銉簻閺嬬娀鏌涘☉娆樼劷闁轰降鍊濋獮?
        buyerUser = userRepository.save(User.builder()
                .username("buyer")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("闂備焦褰冨ú鈺呭窗濮椻偓瀹?")
                .enabled(true)
                .build());
        userRoleRepository.save(SysUserRole.builder()
                .userId(buyerUser.getId())
                .roleId(buyerRole.getId())
                .user(buyerUser)
                .role(buyerRole)
                .build());
        buyerToken = jwtUtil.generateToken(buyerUser.getUsername(), buyerRole.getRoleCode());

        // 闂佸憡甯楃粙鎴犵磽閹捐绠戦柤濮愬€楅惀鍛存煟閻愬弶顥犻柡浣靛€濋獮?
        gmUser = userRepository.save(User.builder()
                .username("chairman")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("闂佽鍓濆畷鐢靛垝閿熺姵鍋?")
                .enabled(true)
                .build());
        userRoleRepository.save(SysUserRole.builder()
                .userId(gmUser.getId())
                .roleId(chairmanRole.getId())
                .user(gmUser)
                .role(chairmanRole)
                .build());
        gmToken = jwtUtil.generateToken(gmUser.getUsername(), chairmanRole.getRoleCode());

        // 闂佸憡甯楃粙鎴犵磽閹惧顩烽柟鎯х－濮樸劎绱掗悪鍛？闁诡喖锕畷銊ノ熼崗鍏兼闂?
        warehouseUser = userRepository.save(User.builder()
                .username("warehouse")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌?")
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
     * 闂佸憡甯楃粙鎴犵磽?Authentication 闁诲海鏁搁、濠囨寘閸曨垱鍋ㄩ柕濞垮€楅懝鐐箾閺夋埈鍎撻柣?
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
        // 濠电偞鎸搁幊鎰板箖婵犲洤鏋侀柣妤€鐗嗙粊?
        inboundOrderItemRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        inventoryBatchRepository.deleteAll();
        stockTransactionRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productSkuRepository.deleteAll();
        productRepository.deleteAll();
        supplierRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== 闁诲海鎳撻張顒勫汲閿濆悿瑙勬媴鐞涒剝鐓犲┑鐐存綑椤戝牓鎯?====================

    @Test
    @DisplayName("case-2")
    void completeInboundFlow_Success() throws Exception {
        // Step 1: 闂備焦褰冨ú鈺呭窗濮椻偓瀹曘劌螣閸濆嫮浠悗鐐瑰€曢幖顐﹀矗閸℃ɑ鍎熼柟鎯у暱缁€?
        CreateInboundOrderRequest createRequest = new CreateInboundOrderRequest();
        createRequest.setSupplierId(testSupplier.getId());
        createRequest.setExpectedDate(LocalDate.now().plusDays(7));
        createRequest.setRemark("Test inbound order");

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductSkuId(testProduct.getId());
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

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傚箹?
        InboundOrder dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);
        assertThat(dbOrder.getTotalPlanQty()).isEqualTo(100);

        // Step 2: 闂佽鍓濆畷鐢靛垝閿熺姵鍋犻柛鈩冾殢閸氣偓闂?
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

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傚箹?
        dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.APPROVED_PLAN);
        assertThat(dbOrder.getGmApprovedBy()).isEqualTo(gmUser.getId());

        // Step 3: 闂備焦褰冨ú鈺呭窗濮椻偓瀹曘劌螣鐏忔牑鍋撳Ο鍏煎?
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

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傚箹鐎涙ɑ灏柟铚傚嵆楠炲秶鎲撮崟闈涗还闂佹椿鍠曢懗鍫曞极閹捐绠?
        dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.AWAITING_RECEIVAL);
        assertThat(dbOrder.getTotalConfirmedQty()).isEqualTo(90);

        InboundOrderItem confirmedItem = dbOrder.getItems().get(0);
        assertThat(confirmedItem.getBatchCode()).isNotNull();
        assertThat(confirmedItem.getBatchCode()).matches("SPU\\d+-SKU\\d+-\\d{8}(-\\d{2})?");
        assertThat(confirmedItem.getConfirmedQty()).isEqualTo(90);

        // Step 4: 婵炲濮甸幐鍝ヨ姳闁秴缁╅柟顖滃瑜?
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

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傚箹?
        dbOrder = inboundOrderRepository.findById(orderId).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.COMPLETED);
        assertThat(dbOrder.getTotalActualQty()).isEqualTo(88);

        // 婵°倗濮撮惌渚€鎯佹径瀣劅闁规儳纾幗鐘绘煙娴ｅ喚娼愭い鎰偢瀹曟艾鈽夊Ο鑲╁
        List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCodeAndLocation(
                confirmedItem.getBatchCode(), testLocation);
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getQuantity()).isEqualTo(88);

        // 婵°倗濮撮惌渚€鎯佹径瀣劅闁规儳纾幗鐘崇箾缂堢姷鍔嶉柟绋款樀瀹曟艾鈽夊Ο鑲╁
        List<StockTransaction> transactions = stockTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getQuantity()).isEqualTo(88);
        assertThat(transactions.get(0).getSourceOrderId()).isEqualTo(dbOrder.getOrderNo());
    }

    // ==================== 闂佸憡甯楃粙鎴犵磽閹捐绀傞柕澶堝劤濮樸劑鏌涘Δ浣圭缂佷礁顕幏?====================

    @Test
    @DisplayName("case-3")
    void createInboundOrder_Success() throws Exception {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(testSupplier.getId());
        request.setExpectedDate(LocalDate.now().plusDays(7));

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductSkuId(testProduct.getId());
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

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傚箹?
        List<InboundOrder> orders = inboundOrderRepository.findAll();
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("case-4")
    void createInboundOrder_SupplierNotFound() throws Exception {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(999L);  // 婵炴垶鎸哥粔鎾偤閵娾晛鎹堕柕濞у嫮鏆犳繛鎾寸缁嬫垹鑺遍弻銉ョ柈闁告粎鐦?
        request.setExpectedDate(LocalDate.now().plusDays(7));

        // 濠电儑缍€椤曆勬叏閻愬鈻旈柍褜鍓氱粙澶愵敂閸涱喚鐣抽梺杞扮閻楀繐鈻?item闂佹寧绋戦惌鍌涘閳哄懎绀傜€广儱顦卞畷锝夋偣閸ワ妇绐旈柡浣革功閹?
        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductSkuId(testProduct.getId());
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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("SUPPLIER_NOT_FOUND"));
    }

    // ==================== 闂佸搫琚崕鎾敋濡も偓闇夐悗锝庡幘濡?====================

    @Test
    @DisplayName("case-5")
    void getPendingApprovalOrders_Success() throws Exception {
        // Given - 闂佸憡甯楃粙鎴犵磽閹惧鈻旈柍褜鍓氱粙澶愵敂閸曨厾顎€闁诲骸鍘滈崜婵囩珶閹烘鍎嶉柛鏇ㄥ亜瀵娊骞栫€涙ɑ灏€?
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
    @DisplayName("case-6")
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
    @DisplayName("case-7")
    void getInboundOrderById_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inbound-orders/999")
                        .principal(createAuthentication(buyerUser))
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("INBOUND_ORDER_NOT_FOUND"));
    }

    // ==================== 闂佸綊鏀辩敮鐐靛垝瀹勬噴鍦偓锝庡幘濡?====================

    @Test
    @DisplayName("case-8")
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

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傚箹?
        InboundOrder dbOrder = inboundOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(InboundOrderStatus.REJECTED);
    }
}
