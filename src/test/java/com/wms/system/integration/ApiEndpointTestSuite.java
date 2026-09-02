package com.wms.system.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.*;
import com.wms.system.security.SecurityUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductSkuRepository productSkuRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SupplierRepository supplierRepository;

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

        admin.setPassword(passwordEncoder.encode("password123"));
        admin.setEnabled(true);
        admin.setMustChangePassword(false);

        if (!sysUserRoleRepository.existsByUserIdAndRoleId(admin.getId(), superAdmin.getId())) {
            sysUserRoleRepository.save(SysUserRole.builder()
                .userId(admin.getId())
                .roleId(superAdmin.getId())
                .assignedBy(admin.getId())
                .build());
        }

        if (admin.getDefaultRoleId() == null || !admin.getDefaultRoleId().equals(superAdmin.getId())) {
            admin.setDefaultRoleId(superAdmin.getId());
        }
        userRepository.saveAndFlush(admin);

        ensureSalesApprovalConfig();
    }

    private void ensureSalesApprovalConfig() {
        systemConfigRepository.findByConfigKey("sales.order.approval.mode")
            .orElseGet(() -> systemConfigRepository.save(SystemConfig.builder()
                .configKey("sales.order.approval.mode")
                .configValue("RISK_BASED")
                .configType(SystemConfig.ConfigType.STRING.name())
                .description("Use the review workflow exercised by this integration suite")
                .build()));
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
        void testSwitchRole() throws Exception {
            String loginRequest = json(Map.of(
                "username", "admin",
                "password", "password123"
            ));
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn();
            String token = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).path("token").asText();
            String switchRequest = json(Map.of("targetRoleCode", "TENANT_ADMIN"));

            mockMvc.perform(post("/api/auth/switch-role")
                    .header("Authorization", "Bearer " + token)
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
        @WithMockUser(username = "admin", roles = {"TENANT_ADMIN"})
        void testGetAllUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", roles = {"TENANT_ADMIN"})
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
                .andExpect(jsonPath("$.roleCodes", hasItem("TENANT_ADMIN")));
        }

        @Test
        @WithMockUser(username = "admin", roles = {"TENANT_ADMIN"})
        void testGetUserById() throws Exception {
            mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("admin")));
        }

        @Test
        @WithMockUser(username = "admin", roles = {"TENANT_ADMIN"})
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
        @WithMockUser(username = "admin", roles = {"TENANT_ADMIN"})
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
        @WithMockUser(username = "admin", roles = {"TENANT_ADMIN"})
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
                .andExpect(jsonPath("$.roleCodes", hasItem("TENANT_ADMIN")));
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
            String createRequest = json(Map.of(
                "name", "Test Customer",
                "contact", "Zhang San",
                "phone", "13912345678",
                "email", "test@example.com",
                "address", "Shenzhen Nanshan Example Ave 88",
                "creditLimit", 100000.00,
                "isActive", true
            ));

            MvcResult result = mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("\\d+")))
                .andReturn();

            String generatedCode = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("code").asText();
            Customer saved = customerRepository.findByCode(generatedCode).orElseThrow();
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
            ProductSku product = createProduct(BigDecimal.ZERO);
            JsonNode poJson = createPurchaseOrderApi(product.getId());
            assertFalse(poJson.path("id").isMissingNode());
        }

        @Test
        @WithMockUser(username = "buyer", authorities = {"TENANT_ADMIN"})
        void testQuickConfirmCreatesAsnAndUnifiedReceivingTask() throws Exception {
            ProductSku product = createProduct(BigDecimal.ZERO);
            Warehouse warehouse = createWarehouse();
            JsonNode createJson = createPurchaseOrderApi(product.getId());
            long orderId = createJson.path("id").asLong();
            long itemId = createJson.path("items").get(0).path("id").asLong();

            JsonNode submitted = allocateAndSubmit(createJson, warehouse.getId());
            assertEquals("AWAITING_SHIPMENT", submitted.path("status").asText());

            String confirmRequest = json(Map.of(
                "warehouseId", warehouse.getId(),
                "items", List.of(Map.of(
                    "purchaseOrderItemId", itemId,
                    "shippedQuantity", 10
                ))
            ));
            mockMvc.perform(post("/api/purchase-orders/" + orderId + "/asns/quick-confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

            mockMvc.perform(get("/api/receiving-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").value("PURCHASE_ASN"))
                .andExpect(jsonPath("$[0].purchaseOrderId").value(orderId))
                .andExpect(jsonPath("$[0].supplier").doesNotExist())
                .andExpect(jsonPath("$[0].carrier").doesNotExist())
                .andExpect(jsonPath("$[0].trackingNo").doesNotExist());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"TENANT_ADMIN"})
        void testUnifiedReceiptPostsInventoryAndSupportsIdempotentReplay() throws Exception {
            ProductSku product = createProduct(BigDecimal.ZERO);
            Warehouse warehouse = createWarehouse();
            Location location = createLocation(warehouse);

            JsonNode createJson = createPurchaseOrderApi(product.getId());
            long orderId = createJson.path("id").asLong();
            long itemId = createJson.path("items").get(0).path("id").asLong();
            allocateAndSubmit(createJson, warehouse.getId());

            String confirmRequest = json(Map.of(
                "warehouseId", warehouse.getId(),
                "items", List.of(Map.of(
                    "purchaseOrderItemId", itemId,
                    "shippedQuantity", 10
                ))
            ));
            mockMvc.perform(post("/api/purchase-orders/" + orderId + "/asns/quick-confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmRequest))
                .andExpect(status().isCreated());

            JsonNode tasks = readJson(mockMvc.perform(get("/api/receiving-tasks"))
                .andExpect(status().isOk())
                .andReturn());
            JsonNode task = null;
            for (JsonNode candidate : tasks) {
                if (candidate.path("purchaseOrderId").asLong() == orderId) {
                    task = candidate;
                    break;
                }
            }
            assertTrue(task != null);
            long taskId = task.path("id").asLong();
            long taskItemId = task.path("items").get(0).path("id").asLong();
            String receiveRequest = json(Map.of(
                "lines", List.of(Map.of(
                    "receivingTaskItemId", taskItemId,
                    "locationId", location.getId(),
                    "quantity", 10
                ))
            ));
            String idempotencyKey = "api-suite-receipt-" + nextId();
            User receiver = userRepository.saveAndFlush(User.builder()
                .username("warehouse-receiver-" + nextId())
                .password(passwordEncoder.encode("password123"))
                .displayName("Warehouse Receiver")
                .enabled(true)
                .build());
            var receiverAuthentication = new UsernamePasswordAuthenticationToken(
                new SecurityUser(receiver), null,
                List.of(new SimpleGrantedAuthority("TENANT_ADMIN")));

            MvcResult first = mockMvc.perform(post("/api/receiving-tasks/" + taskId + "/receipts")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(receiverAuthentication))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receiveRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedByName").value(receiver.getUsername()))
                .andReturn();
            long eventId = readJson(first).path("id").asLong();

            mockMvc.perform(post("/api/receiving-tasks/" + taskId + "/receipts")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(receiverAuthentication))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receiveRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId));

            mockMvc.perform(get("/api/receiving-tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
            mockMvc.perform(get("/api/purchase-orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

            List<InventoryBatch> batches = inventoryBatchRepository
                .findByPurchaseOrderItemId(itemId);
            assertEquals(1, batches.size());
            assertEquals(10, batches.get(0).getQuantity());
            assertEquals(location.getId(), batches.get(0).getLocation().getId());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
        void legacyDirectReceiptAndInboundRoutesAreNotMapped() throws Exception {
            for (String route : List.of("confirm", "receive", "rollback")) {
                mockMvc.perform(put("/api/purchase-orders/999999/" + route)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                    .andExpect(status().isNotFound());
            }
            mockMvc.perform(post("/api/inbound-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound());
        }

        private JsonNode allocateAndSubmit(JsonNode purchaseOrder, long warehouseId) throws Exception {
            long orderId = purchaseOrder.path("id").asLong();
            long itemId = purchaseOrder.path("items").get(0).path("id").asLong();
            mockMvc.perform(put("/api/purchase-orders/" + orderId + "/allocations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                        "purchaseOrderVersion", purchaseOrder.path("version").asLong(),
                        "allocations", List.of(Map.of(
                            "purchaseOrderItemId", itemId,
                            "warehouseId", warehouseId,
                            "plannedQuantity", 10
                        ))
                    ))))
                .andExpect(status().isOk());

            JsonNode refreshed = readJson(mockMvc.perform(get("/api/purchase-orders/" + orderId))
                .andExpect(status().isOk())
                .andReturn());
            MvcResult submitted = mockMvc.perform(post("/api/purchase-orders/" + orderId + "/submit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("version", refreshed.path("version").asLong()))))
                .andExpect(status().isOk())
                .andReturn();
            return readJson(submitted);
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
            ProductSku product = createProduct(BigDecimal.ZERO);
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
        @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
        void testDirectInventoryWriteRoutesAreNotMapped() throws Exception {
            mockMvc.perform(post("/api/inventory/adjust"))
                .andDo(print())
                .andExpect(result -> assertRetiredInventoryRoute(result.getResponse().getStatus()));

            mockMvc.perform(put("/api/inventory/batches/BATCH001/adjust"))
                .andDo(print())
                .andExpect(result -> assertRetiredInventoryRoute(result.getResponse().getStatus()));

            mockMvc.perform(post("/api/inventory/batches/outbound"))
                .andDo(print())
                .andExpect(result -> assertRetiredInventoryRoute(result.getResponse().getStatus()));
        }

        private void assertRetiredInventoryRoute(int responseStatus) {
            assertTrue(responseStatus == 404 || responseStatus == 405,
                "retired inventory route must return 404 or 405, but was " + responseStatus);
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
            ProductSku product = createProduct(new BigDecimal("100.00"));

            String createRequest = json(Map.of(
                "customerId", customer.getId(),
                "items", List.of(Map.of(
                    "productSkuId", product.getId(),
                    "quantity", 10,
                    "unitPrice", 6000.00
                ))
            ));

            mockMvc.perform(post("/api/sales-orders")
                    .header("Idempotency-Key", "api-sales-create-" + nextId())
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
            ProductSku product = createProduct(new BigDecimal("200.00"));
            Location location = createLocation(createWarehouse());
            createInventoryBatch(product, location, 200, 365);

            JsonNode orderJson = createSalesOrderApi(customer.getId(), product.getId(), new BigDecimal("20000.00"), 5);
            long orderId = orderJson.path("id").asLong();
            assertEquals("PENDING_APPROVAL", orderJson.path("commercialStatus").asText());

            String approveRequest = json(Map.of("comment", "approved by test"));
            mockMvc.perform(post("/api/sales-orders/" + orderId + "/approve")
                    .header("Idempotency-Key", "api-sales-approve-" + nextId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(approveRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialStatus").value("APPROVED"));
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
        void testConfirmDispatch() throws Exception {
            OutboundTask task = createPendingOutboundTask(5);
            String req = json(Map.of("actualQty", 5));
            var operator = createActorAuthentication("dispatch-operator", "outbound:pick");

            mockMvc.perform(post("/api/outbound-tasks/" + task.getId() + "/dispatch")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(operator))
                    .header("Idempotency-Key", "api-dispatch-" + task.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(req))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"outbound:pick"})
        void testBatchConfirmDispatch() throws Exception {
            OutboundTask task1 = createPendingOutboundTask(3);
            OutboundTask task2 = createPendingOutboundTask(4);

            String batchRequest = json(List.of(task1.getId(), task2.getId()));
            var operator = createActorAuthentication("batch-dispatch-operator", "outbound:pick");

            mockMvc.perform(post("/api/outbound-tasks/batch-dispatch")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(operator))
                    .header("Idempotency-Key", "api-batch-dispatch-" + task1.getId())
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
            ProductSku product = createProduct(BigDecimal.ZERO);
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
            ProductSku product = createProduct(BigDecimal.ZERO);
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
            var reviewer = createActorAuthentication("stocktake-reviewer", "stocktake:review");

            mockMvc.perform(post("/api/stocktake/tasks/" + taskId + "/review")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(reviewer))
                    .header("Idempotency-Key", "api-stocktake-review-" + taskId)
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

    private JsonNode createPurchaseOrderApi(Long productSkuId) throws Exception {
        ensureUser("buyer");
        long supplierSequence = nextId();
        Supplier supplier = supplierRepository.save(Supplier.builder()
            .code("SUP-API-" + supplierSequence)
            .name("Supplier-" + supplierSequence)
            .isActive(true)
            .isDeleted(false)
            .build());
        String request = json(Map.of(
            "supplierId", supplier.getId(),
            "items", List.of(Map.of(
                "productSkuId", productSkuId,
                "orderedQuantity", 10,
                "unitCost", new BigDecimal("10.00")
            )),
            "expectedDate", LocalDate.now().plusDays(3),
            "remark", "Created by ApiEndpointTestSuite"
        ));

        MvcResult result = mockMvc.perform(post("/api/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andReturn();
        return readJson(result);
    }

    private JsonNode createSalesOrderApi(Long customerId, Long productSkuId, BigDecimal unitPrice, int quantity) throws Exception {
        String request = json(Map.of(
            "customerId", customerId,
            "items", List.of(Map.of(
                "productSkuId", productSkuId,
                "quantity", quantity,
                "unitPrice", unitPrice
            ))
        ));

        MvcResult result = mockMvc.perform(post("/api/sales-orders")
                .header("Idempotency-Key", "api-sales-helper-create-" + nextId())
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

    private UsernamePasswordAuthenticationToken createActorAuthentication(
        String usernamePrefix,
        String... authorities
    ) {
        User actor = userRepository.saveAndFlush(User.builder()
            .username(usernamePrefix + "-" + nextId())
            .password(passwordEncoder.encode("password123"))
            .displayName("API Suite Actor")
            .enabled(true)
            .build());
        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toList();
        return new UsernamePasswordAuthenticationToken(
            new SecurityUser(actor), null, grantedAuthorities);
    }

    private OutboundTask createPendingOutboundTask(int planQty) {
        User salesUser = ensureUser("sales");
        Customer customer = createCustomer(salesUser);
        ProductSku product = createProduct(new BigDecimal("12.50"));
        Warehouse warehouse = createWarehouse();
        Location location = createLocation(warehouse);
        InventoryBatch batch = createInventoryBatch(product, location, Math.max(planQty, 1) + 20, 365);

        SalesOrder salesOrder = salesOrderRepository.save(SalesOrder.builder()
            .orderNo(nextOrderNo())
            .customerId(customer.getId())
            .totalAmount(new BigDecimal(planQty).multiply(new BigDecimal("12.50")))
            .commercialStatus(CommercialStatus.APPROVED)
            .applicantId(salesUser.getId())
            .applicantName(salesUser.getUsername())
            .channel("MANUAL")
            .build());

        SalesOrderItem item = salesOrderItemRepository.save(SalesOrderItem.builder()
            .salesOrderId(salesOrder.getId())
            .productSkuId(product.getId())
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
        return sysRoleRepository.findByRoleCode("TENANT_ADMIN")
            .orElseGet(() -> sysRoleRepository.save(SysRole.builder()
                .roleCode("TENANT_ADMIN")
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

    private ProductSku createProduct(BigDecimal unitPrice) {
        Product spu = productRepository.save(Product.builder()
            .productCode("SPU-" + nextId())
            .productName("SPU-" + nextId())
            .category(com.wms.system.support.TestCatalogFactory.saveLeafCategory(categoryRepository))
            .description("Created by ApiEndpointTestSuite")
            .enabled(true)
            .brand("WMS")
            .build());

        return productSkuRepository.save(ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
            .product(spu)
            .skuName("SKU-" + nextId())
            .specs("spec")
            .barcode("BC-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-" + nextId())
            .name("ProductSku-" + nextId())
            .specification("unit")
            .unitPrice(unitPrice)
            .minStock(0)
            .leadTime(7)
            .safetyStock(0)
            .description("Created by ApiEndpointTestSuite")
            .enabled(true)
            .supplier("TEST-SUPPLIER")
            .packUnit("Box")
            .conversionRate(1)
            .build());
    }

    private InventoryBatch createInventoryBatch(ProductSku product, Location location, int quantity, int expiryDays) {
        return inventoryBatchRepository.save(InventoryBatch.builder()
            .batchCode("BATCH-" + nextId())
            .locationCode(location.getLocationCode() != null ? location.getLocationCode() : location.getWarehouseCode() + "-TEMP-" + nextId())
            .productSku(product)
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
