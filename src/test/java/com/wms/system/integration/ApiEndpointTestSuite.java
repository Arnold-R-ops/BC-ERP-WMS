package com.wms.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WMS 缂備緡鍨靛畷鐢靛垝?API 闂佽浜介崕杈亹濞戞◤鍦偓锝庡幘濡茬鈹戦崒娑栦沪婵?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偛绠ラ柍褜鍓熷鍨緞鐎ｅ棔绶氬畷绋课旈崘鈺冩殸 RESTful API 闂佽浜介崕杈亹濞戙垺鏅?
 * 1. 闁荤姳闄嶉崐娑㈡儊婢舵劕绠抽柕澶堝劚缂?(/api/auth/*)
 * 2. 闂佹椿娼块崝宥夊春濞戞氨涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟?(/api/users/*)
 * 3. 婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟?(/api/warehouses/*)
 * 4. 闁圭厧鐡ㄩ幐椋庣礊閸涱垳涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟?(/api/locations/*)
 * 5. 闂備焦褰冨ú鈺呭窗濡吋濯奸柕蹇曞Т缁€瀣煙閹帒鍔氱憸?(/api/purchase-orders/*)
 * 6. 闂佺绻堥崕杈╄姳闁秴纭€闁哄洨鍠庢径宥夋煕?(/api/inbound-orders/*)
 * 7. 闁圭厧鐡ㄩ幐鎼佹偤閵娧呬笉闁挎稑瀚崐鐐烘煙閹帒鍔氱憸?(/api/inventory/*)
 * 8. 闁圭厧鐡ㄩ幐鎼佹偤閵娾晛钃熼柕澶樼厛閸ゅ嫰鏌熼幁鎺戝姎鐟?(/api/inventory/*)
 * 9. 闁诲骸绠嶉崹娲春濞戞氨涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟?(/api/customers/*)
 * 10. 闂備礁绨遍崑鎾绘煕閻戝棗鏋︽い鎾崇秺瀹曪繝寮撮悢宄邦槻闂?(/api/sales-orders/*)
 * 11. 闂佸憡鍨甸幖顐よ姳鏉堛劎顩烽悹鍥ㄥ絻椤倝鏌熼幁鎺戝姎鐟?(/api/outbound-tasks/*)
 * 12. 闂佺儵鏅滈…鍥磻閿濆洨涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟?(/api/stocktake/*)
 * 13. 闂佺顑冮崕閬嶅箖瀹ュ憘娑㈠焵椤掑嫬钃熼柕澶涚畱婢跺秹鏌?(/api/health)
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Complete API Test Suite)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@DisplayName("case-1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiEndpointTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 1. 闁荤姳闄嶉崐娑㈡儊婢舵劕绠抽柕澶堝劚缂嶆挻绻涢弶鎴創闁?==========

    @Nested
    @DisplayName("case-2")
    class AuthApiTests {

        @Test
        @Order(1)
        @DisplayName("case-3")
        void testLogin() throws Exception {
            String loginRequest = """
                {
                    "username": "admin",
                    "password": "password"
                }
                """;

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
        @WithMockUser(username = "testuser", authorities = {"SALESPERSON"})
        @DisplayName("case-4")
        void testSwitchRole() throws Exception {
            String switchRequest = """
                {
                    "roleCode": "BUYER"
                }
                """;

            mockMvc.perform(post("/api/auth/switch-role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(switchRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @Order(3)
        @DisplayName("case-5")
        void testHealthCheck() throws Exception {
            mockMvc.perform(get("/api/auth/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
        }
    }

    // ========== 2. 闂佹椿娼块崝宥夊春濞戞氨涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟滅増绋戦湁閻庯綆鍘惧Σ?==========

    @Nested
    @DisplayName("case-6")
    class UserApiTests {

        @Test
        @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
        @DisplayName("case-7")
        void testGetAllUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
        @DisplayName("case-8")
        void testCreateUser() throws Exception {
            String createRequest = """
                {
                    "username": "newuser",
                    "password": "password123",
                    "enabled": true
                }
                """;

            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
        @DisplayName("case-9")
        void testGetUserById() throws Exception {
            mockMvc.perform(get("/api/users/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
        @DisplayName("case-10")
        void testUpdateUser() throws Exception {
            String updateRequest = """
                {
                    "username": "updateduser",
                    "enabled": true
                }
                """;

            mockMvc.perform(put("/api/users/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
        @DisplayName("case-11")
        void testDeleteUser() throws Exception {
            mockMvc.perform(delete("/api/users/1"))
                .andDo(print())
                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
        @DisplayName("case-12")
        void testAssignRoles() throws Exception {
            String rolesRequest = """
                {
                    "roleIds": [1, 2]
                }
                """;

            mockMvc.perform(post("/api/users/1/roles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(rolesRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }

    // ========== 3. 婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟滅増绋戦湁閻庯綆鍘惧Σ?==========

    @Nested
    @DisplayName("case-13")
    class WarehouseApiTests {

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:view"})
        @DisplayName("case-14")
        void testGetAllWarehouses() throws Exception {
            mockMvc.perform(get("/api/warehouses"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:view"})
        @DisplayName("case-15")
        void testGetActiveWarehouses() throws Exception {
            mockMvc.perform(get("/api/warehouses/active"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:create"})
        @DisplayName("case-16")
        void testCreateWarehouse() throws Exception {
            String createRequest = """
                {
                    "code": "WH01",
                    "name": "婵炴垶鎹侀濠勫垝閵婏附鍎?,
                    "address": "闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻?,
                    "isActive": true
                }
                """;

            mockMvc.perform(post("/api/warehouses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WH01"));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:view"})
        @DisplayName("case-17")
        void testGetWarehouseById() throws Exception {
            mockMvc.perform(get("/api/warehouses/1"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:edit"})
        @DisplayName("case-18")
        void testUpdateWarehouse() throws Exception {
            String updateRequest = """
                {
                    "name": "闂佸搫娲ら悺銊╁蓟婵犲洤瑙﹂幖杈剧稻閻ｅ崬霉閻樿櫕灏紒?,
                    "address": "婵炴垶鎸搁敃銉╁箲閿濆洦鏆滈柛灞剧洴閸忓繐鈽夐幘瀵哥煁闁哄苯锕畷?
                }
                """;

            mockMvc.perform(put("/api/warehouses/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:edit"})
        @DisplayName("case-19")
        void testActivateWarehouse() throws Exception {
            mockMvc.perform(put("/api/warehouses/1/activate"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"warehouse:edit"})
        @DisplayName("case-20")
        void testDeactivateWarehouse() throws Exception {
            mockMvc.perform(put("/api/warehouses/1/deactivate"))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }

    // ========== 4. 闁圭厧鐡ㄩ幐椋庣礊閸涱垳涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟滅増绋戦湁閻庯綆鍘惧Σ?==========

    @Nested
    @DisplayName("case-21")
    class LocationApiTests {

        @Test
        @WithMockUser(username = "admin", authorities = {"location:view"})
        @DisplayName("case-22")
        void testGetAllLocations() throws Exception {
            mockMvc.perform(get("/api/locations"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"location:create"})
        @DisplayName("case-23")
        void testCreateLocation() throws Exception {
            String createRequest = """
                {
                    "code": "WH01-A-01-01-001",
                    "warehouseId": 1,
                    "zone": "A",
                    "rack": "01",
                    "level": "01",
                    "position": "001",
                    "isActive": true
                }
                """;

            mockMvc.perform(post("/api/locations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated());
        }
    }

    // ========== 5. 闁诲骸绠嶉崹娲春濞戞氨涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟滅増绋戦湁閻庯綆鍘惧Σ鎼佹煥濞戞澧曢柟顖氱墦瀵偊鎮ч崼婵堛偊闂佽偐顥愭ご鎼佸疾闁秵鏅?=========

    @Nested
    @DisplayName("case-24")
    class CustomerApiTests {

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:view", "SALESPERSON"})
        @DisplayName("case-25")
        void testGetCustomers_RowLevelSecurity() throws Exception {
            mockMvc.perform(get("/api/customers"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:view", "SALESPERSON"})
        @DisplayName("case-26")
        void testGetCustomer_DataMasking() throws Exception {
            mockMvc.perform(get("/api/customers/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(containsString("****")))
                .andExpect(jsonPath("$.email").value(containsString("***")));
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:create"})
        @DisplayName("case-27")
        void testCreateCustomer_AutoSetOwner() throws Exception {
            String createRequest = """
                {
                    "code": "CUST001",
                    "name": "濠电偞娼欓鍫ユ儊椤栨壕鍋撻獮鍨仾闁?,
                    "contact": "閻庢鍠氭慨宕囩箔?,
                    "phone": "13912345678",
                    "email": "test@example.com",
                    "address": "闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻?,
                    "creditLimit": 100000.00,
                    "isActive": true
                }
                """;

            mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CUST001"));
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:edit"})
        @DisplayName("case-28")
        void testUpdateCustomer_EditProtection() throws Exception {
            String updateRequest = """
                {
                    "code": "CUST001",
                    "name": "闂佸搫娲ら悺銊╁蓟婵犲洤瑙﹂幖杈剧稻閻ｉ亶鎮楅獮鍨仾闁?,
                    "phone": "139****5678",
                    "email": "test@example.com"
                }
                """;

            mockMvc.perform(put("/api/customers/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"customer:delete"})
        @DisplayName("case-29")
        void testDeleteCustomer() throws Exception {
            mockMvc.perform(delete("/api/customers/1"))
                .andDo(print())
                .andExpect(status().isNoContent());
        }
    }

    // ========== 6. 闂備焦褰冨ú鈺呭窗濡吋濯奸柕蹇曞Т缁€瀣煙閹帒鍔氱憸鐗堢☉闇夐悗锝庡幘濡?==========

    @Nested
    @DisplayName("case-30")
    class PurchaseOrderApiTests {

        @Test
        @WithMockUser(username = "buyer", authorities = {"purchase:view"})
        @DisplayName("case-31")
        void testGetPurchaseOrders() throws Exception {
            mockMvc.perform(get("/api/purchase-orders"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "buyer", authorities = {"purchase:create"})
        @DisplayName("case-32")
        void testCreatePurchaseOrder() throws Exception {
            String createRequest = """
                {
                    "orderNumber": "PO001",
                    "supplierId": 1,
                    "items": [
                        {
                            "productId": 1,
                            "quantity": 100,
                            "unitPrice": 50.00
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/purchase-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(username = "buyer", authorities = {"purchase:edit"})
        @DisplayName("case-33")
        void testConfirmAsn() throws Exception {
            mockMvc.perform(put("/api/purchase-orders/1/confirm"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"purchase:receive"})
        @DisplayName("case-34")
        void testReceiveGoods() throws Exception {
            mockMvc.perform(put("/api/purchase-orders/1/receive"))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }

    // ========== 7. 闁圭厧鐡ㄩ幐鎼佹偤閵娧呬笉闁挎稑瀚崐鐐烘煙閹帒鍔氱憸鐗堢☉闇夐悗锝庡幘濡?==========

    @Nested
    @DisplayName("case-35")
    class InventoryApiTests {

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:view"})
        @DisplayName("case-36")
        void testGetInventorySummary() throws Exception {
            mockMvc.perform(get("/api/inventory/summary"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:view"})
        @DisplayName("case-37")
        void testGetInventoryDetails() throws Exception {
            mockMvc.perform(get("/api/inventory/details/SKU001"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:view"})
        @DisplayName("case-38")
        void testGetInventoryByLocation() throws Exception {
            mockMvc.perform(get("/api/inventory/location/WH01-A-01-01-001"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"inventory:adjust"})
        @DisplayName("case-39")
        void testAdjustInventory() throws Exception {
            String adjustRequest = """
                {
                    "productId": 1,
                    "locationCode": "WH01-A-01-01-001",
                    "quantity": 10,
                    "transactionType": "IN",
                    "reason": "闁圭厧鐡ㄩ幐鎼佹偤閵娧勫闁告劏鏅滃▓?
                }
                """;

            mockMvc.perform(post("/api/inventory/adjust")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(adjustRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }

    // ========== 8. 闂備礁绨遍崑鎾绘煕閻戝棗鏋︽い鎾崇秺瀹曪繝寮撮悢宄邦槻闂佸憡鐟辩徊鍓х矈鐎靛憡瀚?==========

    @Nested
    @DisplayName("case-40")
    class SalesOrderApiTests {

        @Test
        @WithMockUser(username = "sales", authorities = {"sales:view"})
        @DisplayName("case-41")
        void testGetSalesOrders() throws Exception {
            mockMvc.perform(get("/api/sales-orders"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"sales:create"})
        @DisplayName("case-42")
        void testCreateSalesOrder() throws Exception {
            String createRequest = """
                {
                    "orderNumber": "SO001",
                    "customerId": 1,
                    "items": [
                        {
                            "productId": 1,
                            "quantity": 10,
                            "unitPrice": 100.00
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/sales-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(username = "manager", authorities = {"sales:approve"})
        @DisplayName("case-43")
        void testApproveSalesOrder() throws Exception {
            mockMvc.perform(post("/api/sales-orders/1/approve"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "sales", authorities = {"sales:create"})
        @DisplayName("case-44")
        void testDownloadTemplate() throws Exception {
            mockMvc.perform(get("/api/sales-orders/template"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheet")));
        }
    }

    // ========== 9. 闂佸憡鍨甸幖顐よ姳鏉堛劎顩烽悹鍥ㄥ絻椤倝鏌熼幁鎺戝姎鐟滅増绋戦湁閻庯綆鍘惧Σ?==========

    @Nested
    @DisplayName("case-45")
    class OutboundTaskApiTests {

        @Test
        @WithMockUser(username = "warehouse", authorities = {"outbound:view"})
        @DisplayName("case-46")
        void testGetOutboundTasks() throws Exception {
            mockMvc.perform(get("/api/outbound-tasks"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"outbound:pick"})
        @DisplayName("case-47")
        void testConfirmPicking() throws Exception {
            mockMvc.perform(post("/api/outbound-tasks/1/confirm"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"outbound:pick"})
        @DisplayName("case-48")
        void testBatchConfirmPicking() throws Exception {
            String batchRequest = """
                {
                    "taskIds": [1, 2, 3]
                }
                """;

            mockMvc.perform(post("/api/outbound-tasks/batch-confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }

    // ========== 10. 闂佺儵鏅滈…鍥磻閿濆洨涓嶉柨娑樺閸婄偤鏌熼幁鎺戝姎鐟滅増绋戦湁閻庯綆鍘惧Σ?==========

    @Nested
    @DisplayName("case-49")
    class StocktakeApiTests {

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:view"})
        @DisplayName("case-50")
        void testGetStocktakeTasks() throws Exception {
            mockMvc.perform(get("/api/stocktake/tasks"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:create"})
        @DisplayName("case-51")
        void testCreateStocktakeTask() throws Exception {
            String createRequest = """
                {
                    "taskName": "闂佸搫鐗嗛悧鍡欌偓瑙勫▕閹嫬螣閻撳簼鍖?,
                    "cycleType": "MONTHLY",
                    "batchIds": [1, 2, 3]
                }
                """;

            mockMvc.perform(post("/api/stocktake/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest))
                .andDo(print())
                .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:count"})
        @DisplayName("case-52")
        void testStartStocktake() throws Exception {
            mockMvc.perform(post("/api/stocktake/tasks/1/start"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "warehouse", authorities = {"stocktake:count"})
        @DisplayName("case-53")
        void testSubmitCount() throws Exception {
            String countRequest = """
                {
                    "actualQuantity": 95
                }
                """;

            mockMvc.perform(post("/api/stocktake/tasks/1/items/1/count")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(countRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "manager", authorities = {"stocktake:review"})
        @DisplayName("case-54")
        void testReviewStocktake() throws Exception {
            String reviewRequest = """
                {
                    "approved": true,
                    "comments": "闁诲骸鍘滈崜婵嬫偋閹惰姤鐒绘慨妯虹－缁?
                }
                """;

            mockMvc.perform(post("/api/stocktake/tasks/1/review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reviewRequest))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }

    // ========== 11. 缂備緡鍨靛畷鐢靛垝濞差亜纾婚柕澶堝劜閸婏絾淇婇鐔蜂壕闂佸搫琚崕鑼暜閹绢喖鐭楅柨婵嗘閵堟挳鎮?==========

    @Nested
    @DisplayName("case-55")
    class HealthCheckApiTests {

        @Test
        @DisplayName("case-56")
        void testSystemHealth() throws Exception {
            mockMvc.perform(get("/api/health"))
                .andDo(print())
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("case-57")
        void testAuthHealth() throws Exception {
            mockMvc.perform(get("/api/auth/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
        }

        @Test
        @DisplayName("case-58")
        void testInventoryHealth() throws Exception {
            mockMvc.perform(get("/api/inventory/health"))
                .andDo(print())
                .andExpect(status().isOk());
        }
    }
}
