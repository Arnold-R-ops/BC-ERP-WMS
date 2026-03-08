package com.wms.system.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@DisplayName("API Endpoint Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiEndpointTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SysRoleRepository sysRoleRepository;

    @Autowired
    private SysUserRoleRepository sysUserRoleRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    private OutboundTaskRepository outboundTaskRepository;

    @Autowired
    private StocktakeTaskRepository stocktakeTaskRepository;

    @Autowired
    private StocktakeItemRepository stocktakeItemRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void ensureAdminHasRole() {
        SysRole superAdmin = ensureSuperAdminRole();

        User admin = userRepository.findByUsername("admin")
            .orElseGet(() -> userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("password123"))
                .displayName("System Admin")
                .enabled(true)
                .remark("Created by test bootstrap")
                .build()));

        if (!sysUserRoleRepository.existsByUserIdAndRoleId(admin.getId(), superAdmin.getId())) {
            sysUserRoleRepository.save(SysUserRole.builder()
                .userId(admin.getId())
                .roleId(superAdmin.getId())
                .assignedBy(admin.getId())
                .build());
        }

        if (admin.getDefaultRoleId() == null || !admin.getDefaultRoleId().equals(superAdmin.getId())) {
            admin.setDefaultRoleId(superAdmin.getId());
            userRepository.save(admin);
        }

        ensureSalesApprovalConfig();
    }

    private void ensureSalesApprovalConfig() {
        systemConfigRepository.findByConfigKey("sales.approval.amount_threshold")
            .orElseGet(() -> systemConfigRepository.save(SystemConfig.builder()
                .configKey("sales.approval.amount_threshold")
                .configValue("50000.00")
                .configType(SystemConfig.ConfigType.DECIMAL.name())
                .description("Sales order approval amount threshold")
                .build()));
    }

    @Nested
    @DisplayName("Auth APIs")
    class AuthApiTests {

        @Test
        @Order(1)
        void testLogin() throws Exception {
            String loginRequest = json(Map.of(
                "username", "admin",
                "password", "password123"
            ));

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("admin"));
        }

        @Test
        @Order(2)
        @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
        void testSwitchRole() throws Exception {
            String switchRequest = json(Map.of("targetRoleCode", "SUPER_ADMIN"));

            mockMvc.perform(post("/api/auth/switch-role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(switchRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @Order(3)
        void testHealthCheck() throws Exception {
            mockMvc.perform(get("/api/auth/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("AuthenticationService"));
        }
    }

    @Nested
    @DisplayName("User APIs")
    class UserApiTests {

        @Test
        @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
        void testGetAllUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
        void testCreateUser() throws Exception {
            Long roleId = superAdminRoleId();
            String username = "newuser" + nextId();
            String createRequest = json(Map.of(
                "username", username,
                "password", "password123",
                "displayName", "New User",
                "roleIds", List.of(roleId),
                "enabled", true
            ));

            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roleCodes", hasItem("SUPER_ADMIN")));
        }

        @Test
        @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
        void testGetUserById() throws Exception {
            mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("admin")));
        }

        @Test
        @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
        void testUpdateUser() throws Exception {
            User user = userRepository.save(User.builder()
                .username("upd" + nextId())
                .password(passwordEncoder.encode("password123"))
                .displayName("Before Update")
                .enabled(true)
                .build());

            String updateRequest = json(Map.of(
                "displayName", "updateduser",
                "enabled", true,
                "remark", "updated by test"
            ));

            mockMvc.perform(put("/api/users/" + user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("updateduser"));
        }

        @Test
        @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
        void testDeleteUser() throws Exception {
            User user = userRepository.save(User.builder()
                .username("del" + nextId())
                .password(passwordEncoder.encode("password123"))
                .displayName("Delete Me")
                .enabled(true)
                .build());

            mockMvc.perform(delete("/api/users/" + user.getId()))
                .andDo(print())
                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
        void testAssignRoles() throws Exception {
            Long roleId = superAdminRoleId();
            User user = userRepository.save(User.builder()
                .username("assign" + nextId())
                .password(passwordEncoder.encode("password123"))
                .enabled(true)
                .build());

            String rolesRequest = json(Map.of("roleIds", List.of(roleId)));

            mockMvc.perform(post("/api/users/" + user.getId() + "/roles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(rolesRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes", hasItem("SUPER_ADMIN")));
        }
    }

    @Nested
    @DisplayName("Warehouse APIs")
    class WarehouseApiTests {

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:view"})
        void testGetAllWarehouses() throws Exception {
            mockMvc.perform(get("/api/warehouses"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:view"})
        void testGetActiveWarehouses() throws Exception {
            mockMvc.perform(get("/api/warehouses/active"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:create"})
        void testCreateWarehouse() throws Exception {
            String code = nextWarehouseCode();
            String createRequest = json(Map.of(
                "code", code,
                "name", "Test Warehouse " + nextId(),
                "address", "Beijing Test Road 100",
                "contact", "test-contact"
            ));

            mockMvc.perform(post("/api/warehouses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:view"})
        void testGetWarehouseById() throws Exception {
            Warehouse warehouse = createWarehouse();

            mockMvc.perform(get("/api/warehouses/" + warehouse.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(warehouse.getId()));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:edit"})
        void testUpdateWarehouse() throws Exception {
            Warehouse warehouse = createWarehouse();
            String updateRequest = json(Map.of(
                "name", "Updated Warehouse",
                "address", "Shanghai Example Road 200",
                "contact", "updated-contact"
            ));

            mockMvc.perform(put("/api/warehouses/" + warehouse.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Warehouse"));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:edit"})
        void testActivateWarehouse() throws Exception {
            Warehouse warehouse = createWarehouse();
            warehouse.setIsActive(false);
            warehouseRepository.save(warehouse);

            mockMvc.perform(put("/api/warehouses/" + warehouse.getId() + "/activate"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:edit"})
        void testDeactivateWarehouse() throws Exception {
            Warehouse warehouse = createWarehouse();

            mockMvc.perform(put("/api/warehouses/" + warehouse.getId() + "/deactivate"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        }
    }

    @Nested
    @DisplayName("Location APIs")
    class LocationApiTests {

        @Test
        @WithMockUser(username = "admin", authorities = {"location:view"})
        void testGetAllLocations() throws Exception {
            Warehouse warehouse = createWarehouse();
            mockMvc.perform(get("/api/locations/warehouse/" + warehouse.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"location:create"})
        void testCreateLocation() throws Exception {
            Warehouse warehouse = createWarehouse();
            String createRequest = json(Map.of(
                "warehouseId", warehouse.getId(),
                "zone", "ZONE_A",
                "shelfNumber", "A" + nextId(),
                "positionNumber", "001",
                "remark", "test location"
            ));

            mockMvc.perform(post("/api/locations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationCode").exists());
        }
    }

    @Nested
    @DisplayName("Customer APIs")
    class CustomerApiTests {

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:view", "SALESPERSON"})
        void testGetCustomers_RowLevelSecurity() throws Exception {
            User sales = ensureUser("sales");
            createCustomer(sales);

            mockMvc.perform(get("/api/customers"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:view", "SALESPERSON"})
        void testGetCustomer_DataMasking() throws Exception {
            User sales = ensureUser("sales");
            Customer customer = createCustomer(sales);

            mockMvc.perform(get("/api/customers/" + customer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(containsString("****")))
                .andExpect(jsonPath("$.email").value(containsString("***")));
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:create", "SALESPERSON"})
        void testCreateCustomer_AutoSetOwner() throws Exception {
            User sales = ensureUser("sales");
            String code = "CUST-" + nextId();
            String createRequest = json(Map.of(
                "code", code,
                "name", "Test Customer",
                "contact", "Zhang San",
                "phone", "13912345678",
                "email", "test@example.com",
                "address", "Shenzhen Nanshan Example Ave 88",
                "creditLimit", 100000.00,
                "isActive", true
            ));

            mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code));

            Customer saved = customerRepository.findByCode(code).orElseThrow();
            assertEquals(sales.getId(), saved.getOwnerId());
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:edit"})
        void testUpdateCustomer_EditProtection() throws Exception {
            User sales = ensureUser("sales");
            Customer customer = createCustomer(sales);

            String updateRequest = json(Map.of(
                "code", customer.getCode(),
                "name", "Updated Customer",
                "contact", "Li Si",
                "phone", "13888886666",
                "email", "updated@example.com",
                "address", "Updated address",
                "creditLimit", 200000.00,
                "isActive", true
            ));

            mockMvc.perform(put("/api/customers/" + customer.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Customer"));
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:delete"})
        void testDeleteCustomer() throws Exception {
            Customer customer = createCustomer(ensureUser("sales"));

            mockMvc.perform(delete("/api/customers/" + customer.getId()))
                .andDo(print())
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Purchase Order APIs")
    class PurchaseOrderApiTests {

        @Test
        @WithMockUser(username = "buyer", authorities = {"purchase:view"})
        void testGetPurchaseOrders() throws Exception {
            mockMvc.perform(get("/api/purchase-orders"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "buyer", authorities = {"purchase:create"})
        void testCreatePurchaseOrder() throws Exception {
            Product product = createProduct(BigDecimal.ZERO);
            JsonNode poJson = createPurchaseOrderApi(product.getId());
            assertFalse(poJson.path("id").isMissingNode());
        }

        @Test
        @WithMockUser(username = "buyer", authorities = {"purchase:edit"})
        void testConfirmAsn() throws Exception {
            Product product = createProduct(BigDecimal.ZERO);
            JsonNode createJson = createPurchaseOrderApi(product.getId());
            long orderId = createJson.path("id").asLong();
            long itemId = createJson.path("items").get(0).path("id").asLong();

            String confirmRequest = json(Map.of(
                "items", List.of(Map.of(
                    "itemId", itemId,
                    "expiryDate", LocalDate.now().plusDays(180).toString()
                ))
            ));

            mockMvc.perform(put("/api/purchase-orders/" + orderId + "/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"purchase:receive"})
        void testReceiveGoods() throws Exception {
            Product product = createProduct(BigDecimal.ZERO);
            Location location = createLocation(createWarehouse());

            JsonNode createJson = createPurchaseOrderApi(product.getId());
            long orderId = createJson.path("id").asLong();
            long itemId = createJson.path("items").get(0).path("id").asLong();

            String confirmRequest = json(Map.of(
                "items", List.of(Map.of(
                    "itemId", itemId,
                    "expiryDate", LocalDate.now().plusDays(180).toString()
                ))
            ));

            MvcResult confirmResult = mockMvc.perform(put("/api/purchase-orders/" + orderId + "/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

            String batchCode = inventoryBatchRepository.findByPurchaseOrderItemId(itemId).stream()
                .findFirst()
                .map(InventoryBatch::getBatchCode)
                .orElse("");
            assertFalse(batchCode.isBlank());

            String receiveRequest = json(Map.of(
                "receiveItems", List.of(Map.of(
                    "itemId", itemId,
                    "batches", List.of(Map.of(
                        "batchCode", batchCode,
                        "locationId", location.getId()
                    ))
                )),
                "operatorId", 1,
                "operatorName", "warehouse"
            ));

            mockMvc.perform(put("/api/purchase-orders/" + orderId + "/receive")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receiveRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", anyOf(is("PARTIALLY_RECEIVED"), is("COMPLETED"))));
        }
    }

    @Nested
    @DisplayName("Inventory APIs")
    class InventoryApiTests {

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:view"})
        void testGetInventorySummary() throws Exception {
            mockMvc.perform(get("/api/inventory/summary"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:view"})
        void testGetInventoryDetails() throws Exception {
            Product product = createProduct(BigDecimal.ZERO);
            Location location = createLocation(createWarehouse());
            createInventoryBatch(product, location, 30, 365);

            mockMvc.perform(get("/api/inventory/details/" + product.getId()))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:view"})
        void testGetInventoryByLocation() throws Exception {
            Location location = createLocation(createWarehouse());

            mockMvc.perform(get("/api/inventory/location/" + location.getLocationCode()))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:adjust"})
        void testAdjustInventory() throws Exception {
            Product product = createProduct(BigDecimal.ZERO);
            Location location = createLocation(createWarehouse());

            String adjustRequest = json(Map.of(
                "productId", product.getId(),
                "locationId", location.getId(),
                "quantity", 10,
                "transactionType", "ADJUST",
                "sourceType", "MANUAL_ADJUST",
                "sourceOrderId", "ADJ-" + nextId(),
                "remark", "Inventory adjustment"
            ));

            mockMvc.perform(post("/api/inventory/adjust")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(adjustRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product.getId()));
        }
    }

    @Nested
    @DisplayName("Sales Order APIs")
    class SalesOrderApiTests {

        @Test
        @WithMockUser(username = "sales", authorities = {"sales:view"})
        void testGetSalesOrders() throws Exception {
            mockMvc.perform(get("/api/sales-orders"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"sales:create"})
        void testCreateSalesOrder() throws Exception {
            Customer customer = createCustomer(ensureUser("sales"));
            Product product = createProduct(new BigDecimal("100.00"));

            String createRequest = json(Map.of(
                "customerId", customer.getId(),
                "items", List.of(Map.of(
                    "productId", product.getId(),
                    "quantity", 10,
                    "unitPrice", 6000.00
                ))
            ));

            mockMvc.perform(post("/api/sales-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customer.getId()));
        }

        @Test
        @WithMockUser(username = "manager", authorities = {"sales:create", "sales:approve"})
        void testApproveSalesOrder() throws Exception {
            Customer customer = createCustomer(ensureUser("sales"));
            Product product = createProduct(new BigDecimal("200.00"));
            Location location = createLocation(createWarehouse());
            createInventoryBatch(product, location, 200, 365);

            JsonNode orderJson = createSalesOrderApi(customer.getId(), product.getId(), new BigDecimal("20000.00"), 5);
            long orderId = orderJson.path("id").asLong();
            assertEquals("PENDING_APPROVAL", orderJson.path("status").asText());

            String approveRequest = json(Map.of("comment", "approved by test"));
            mockMvc.perform(post("/api/sales-orders/" + orderId + "/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(approveRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED_AWAITING_SHIPMENT"));
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"sales:create"})
        void testDownloadTemplate() throws Exception {
            mockMvc.perform(get("/api/sales-orders/template"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheet")));
        }
    }

    @Nested
    @DisplayName("Outbound Task APIs")
    class OutboundTaskApiTests {

        @Test
        @WithMockUser(username = "warehouse", authorities = {"outbound:view"})
        void testGetOutboundTasks() throws Exception {
            mockMvc.perform(get("/api/outbound-tasks"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"outbound:pick"})
        void testConfirmPicking() throws Exception {
            OutboundTask task = createPendingOutboundTask(5);
            String req = json(Map.of("actualQty", 5));

            mockMvc.perform(post("/api/outbound-tasks/" + task.getId() + "/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(req))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"outbound:pick"})
        void testBatchConfirmPicking() throws Exception {
            OutboundTask task1 = createPendingOutboundTask(3);
            OutboundTask task2 = createPendingOutboundTask(4);

            String batchRequest = json(List.of(task1.getId(), task2.getId()));

            mockMvc.perform(post("/api/outbound-tasks/batch-confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        }
    }

    @Nested
    @DisplayName("Stocktake APIs")
    class StocktakeApiTests {

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:view"})
        void testGetStocktakeTasks() throws Exception {
            mockMvc.perform(get("/api/stocktake/tasks"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:create"})
        void testCreateStocktakeTask() throws Exception {
            Warehouse warehouse = createWarehouse();

            String createRequest = json(Map.of(
                "warehouseId", warehouse.getId(),
                "cycleType", "MONTHLY"
            ));

            mockMvc.perform(post("/api/stocktake/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskNo").exists());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:create", "stocktake:count"})
        void testStartStocktake() throws Exception {
            Warehouse warehouse = createWarehouse();
            JsonNode task = createStocktakeTaskApi(warehouse.getId(), "ADHOC");
            long taskId = task.path("id").asLong();

            mockMvc.perform(post("/api/stocktake/tasks/" + taskId + "/start"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COUNTING"));
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:create", "stocktake:count"})
        void testSubmitCount() throws Exception {
            Warehouse warehouse = createWarehouse();
            Location location = createLocation(warehouse);
            Product product = createProduct(BigDecimal.ZERO);
            InventoryBatch batch = createInventoryBatch(product, location, 50, 365);

            JsonNode task = createStocktakeTaskApi(warehouse.getId(), "QUARTERLY");
            long taskId = task.path("id").asLong();

            mockMvc.perform(post("/api/stocktake/tasks/" + taskId + "/start"))
                .andExpect(status().isOk());

            MvcResult itemsResult = mockMvc.perform(get("/api/stocktake/tasks/" + taskId + "/items"))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode items = readJson(itemsResult);
            long itemId = items.get(0).path("id").asLong();

            String countRequest = json(Map.of("countedQty", batch.getQuantity()));
            mockMvc.perform(post("/api/stocktake/tasks/" + taskId + "/items/" + itemId + "/count")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(countRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCounted").value(true));
        }

        @Test
        @WithMockUser(username = "manager", authorities = {"stocktake:create", "stocktake:count", "stocktake:review"})
        void testReviewStocktake() throws Exception {
            Warehouse warehouse = createWarehouse();
            Location location = createLocation(warehouse);
            Product product = createProduct(BigDecimal.ZERO);
            createInventoryBatch(product, location, 20, 365);

            JsonNode task = createStocktakeTaskApi(warehouse.getId(), "QUARTERLY");
            long taskId = task.path("id").asLong();

            mockMvc.perform(post("/api/stocktake/tasks/" + taskId + "/start"))
                .andExpect(status().isOk());

            MvcResult itemsResult = mockMvc.perform(get("/api/stocktake/tasks/" + taskId + "/items"))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode items = readJson(itemsResult);
            long itemId = items.get(0).path("id").asLong();

            String countRequest = json(Map.of("countedQty", 20));
            mockMvc.perform(post("/api/stocktake/tasks/" + taskId + "/items/" + itemId + "/count")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(countRequest))
                .andExpect(status().isOk());

            String reviewRequest = json(Map.of(
                "approved", true,
                "comment", "Stocktake reviewed"
            ));

            mockMvc.perform(post("/api/stocktake/tasks/" + taskId + "/review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reviewRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }

    @Nested
    @DisplayName("Health APIs")
    class HealthCheckApiTests {

        @Test
        void testSystemHealth() throws Exception {
            mockMvc.perform(get("/health/check"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        void testAuthHealth() throws Exception {
            mockMvc.perform(get("/api/auth/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("AuthenticationService"));
        }

        @Test
        void testInventoryHealth() throws Exception {
            mockMvc.perform(get("/api/inventory/health"))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }

    private JsonNode createPurchaseOrderApi(Long productId) throws Exception {
        User buyer = ensureUser("buyer");
        String request = json(Map.of(
            "supplier", "Supplier-" + nextId(),
            "items", List.of(Map.of(
                "productId", productId,
                "orderedQuantity", 10,
                "unitCost", new BigDecimal("10.00")
            )),
            "expectedDate", LocalDate.now().plusDays(3),
            "operatorId", buyer.getId(),
            "operatorName", buyer.getUsername(),
            "remark", "Created by ApiEndpointTestSuite"
        ));

        MvcResult result = mockMvc.perform(post("/api/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andReturn();
        return readJson(result);
    }

    private JsonNode createSalesOrderApi(Long customerId, Long productId, BigDecimal unitPrice, int quantity) throws Exception {
        String request = json(Map.of(
            "customerId", customerId,
            "items", List.of(Map.of(
                "productId", productId,
                "quantity", quantity,
                "unitPrice", unitPrice
            ))
        ));

        MvcResult result = mockMvc.perform(post("/api/sales-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andReturn();
        return readJson(result);
    }

    private JsonNode createStocktakeTaskApi(Long warehouseId, String cycleType) throws Exception {
        String request = json(Map.of(
            "warehouseId", warehouseId,
            "cycleType", cycleType
        ));

        MvcResult result = mockMvc.perform(post("/api/stocktake/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andReturn();
        return readJson(result);
    }

    private OutboundTask createPendingOutboundTask(int planQty) {
        User salesUser = ensureUser("sales");
        Customer customer = createCustomer(salesUser);
        Product product = createProduct(new BigDecimal("12.50"));
        Warehouse warehouse = createWarehouse();
        Location location = createLocation(warehouse);
        InventoryBatch batch = createInventoryBatch(product, location, Math.max(planQty, 1) + 20, 365);

        SalesOrder salesOrder = salesOrderRepository.save(SalesOrder.builder()
            .orderNo(nextOrderNo())
            .customerId(customer.getId())
            .totalAmount(new BigDecimal(planQty).multiply(new BigDecimal("12.50")))
            .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
            .applicantId(salesUser.getId())
            .applicantName(salesUser.getUsername())
            .channel("MANUAL")
            .build());

        SalesOrderItem item = salesOrderItemRepository.save(SalesOrderItem.builder()
            .salesOrderId(salesOrder.getId())
            .productId(product.getId())
            .quantity(planQty)
            .unitPrice(new BigDecimal("12.50"))
            .subtotal(new BigDecimal(planQty).multiply(new BigDecimal("12.50")))
            .rejectNearExpiry(false)
            .build());

        return outboundTaskRepository.save(OutboundTask.builder()
            .salesOrderId(salesOrder.getId())
            .salesOrderItemId(item.getId())
            .assignedBatchId(batch.getId())
            .locationId(location.getId())
            .planQty(planQty)
            .actualQty(0)
            .status(OutboundTaskStatus.PENDING)
            .build());
    }

    private User ensureUser(String username) {
        User user = userRepository.findByUsername(username)
            .orElseGet(() -> userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("password123"))
                .displayName(username)
                .enabled(true)
                .remark("Created by ApiEndpointTestSuite")
                .build()));

        SysRole superAdmin = ensureSuperAdminRole();
        if (!sysUserRoleRepository.existsByUserIdAndRoleId(user.getId(), superAdmin.getId())) {
            sysUserRoleRepository.save(SysUserRole.builder()
                .userId(user.getId())
                .roleId(superAdmin.getId())
                .assignedBy(user.getId())
                .build());
        }

        if (user.getDefaultRoleId() == null || !user.getDefaultRoleId().equals(superAdmin.getId())) {
            user.setDefaultRoleId(superAdmin.getId());
            user = userRepository.save(user);
        }
        return user;
    }

    private SysRole ensureSuperAdminRole() {
        return sysRoleRepository.findByRoleCode("SUPER_ADMIN")
            .orElseGet(() -> sysRoleRepository.save(SysRole.builder()
                .roleCode("SUPER_ADMIN")
                .roleName("Super Admin")
                .description("Created by ApiEndpointTestSuite")
                .roleType("SYSTEM")
                .status("ACTIVE")
                .sortOrder(0)
                .build()));
    }

    private Long superAdminRoleId() {
        return ensureSuperAdminRole().getId();
    }

    private Warehouse createWarehouse() {
        return warehouseRepository.save(Warehouse.builder()
            .code(nextWarehouseCode())
            .name("Warehouse-" + nextId())
            .address("Test Address")
            .contact("13800000000")
            .isActive(true)
            .build());
    }

    private Location createLocation(Warehouse warehouse) {
        return locationRepository.save(Location.builder()
            .warehouse(warehouse)
            .warehouseCode(warehouse.getCode())
            .zone(Zone.ZONE_A)
            .shelfNumber("S" + nextId())
            .positionNumber("P" + nextId())
            .enabled(true)
            .remark("Created by ApiEndpointTestSuite")
            .build());
    }

    private Customer createCustomer(User owner) {
        return customerRepository.save(Customer.builder()
            .code("CUST-" + nextId())
            .name("Customer-" + nextId())
            .contact("Tester")
            .phone("13800000000")
            .email("test" + nextId() + "@example.com")
            .address("Test Address")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .ownerId(owner.getId())
            .build());
    }

    private Product createProduct(BigDecimal unitPrice) {
        ProductSpu spu = productSpuRepository.save(ProductSpu.builder()
            .spuCode("SPU-" + nextId())
            .spuName("SPU-" + nextId())
            .category("TEST")
            .description("Created by ApiEndpointTestSuite")
            .enabled(true)
            .brand("WMS")
            .build());

        return productRepository.save(Product.builder()
            .spu(spu)
            .skuName("SKU-" + nextId())
            .specs("spec")
            .barcode("BC-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-" + nextId())
            .name("Product-" + nextId())
            .specification("unit")
            .unitPrice(unitPrice)
            .minStock(0)
            .leadTime(7)
            .safetyStock(0)
            .description("Created by ApiEndpointTestSuite")
            .enabled(true)
            .category("TEST")
            .supplier("TEST-SUPPLIER")
            .packUnit("Box")
            .conversionRate(1)
            .build());
    }

    private InventoryBatch createInventoryBatch(Product product, Location location, int quantity, int expiryDays) {
        return inventoryBatchRepository.save(InventoryBatch.builder()
            .batchCode("BATCH-" + nextId())
            .locationCode(location.getLocationCode() != null ? location.getLocationCode() : location.getWarehouseCode() + "-TEMP-" + nextId())
            .product(product)
            .location(location)
            .quantity(quantity)
            .initialQuantity(quantity)
            .expiryDate(LocalDate.now().plusDays(expiryDays))
            .entryDate(LocalDateTime.now())
            .active(true)
            .remark("Created by ApiEndpointTestSuite")
            .build());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String nextOrderNo() {
        return "SO" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%05d", nextId());
    }

    private String nextWarehouseCode() {
        return "WH-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + nextId();
    }

    private long nextId() {
        return SEQ.getAndIncrement();
    }
}
