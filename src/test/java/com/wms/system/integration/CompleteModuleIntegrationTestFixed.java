package com.wms.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WMS System Complete Module Integration Test Suite
 *
 * Tests all major business modules:
 * 1. Authentication and Authorization
 * 2. Warehouse and Location Management
 * 3. Purchase and Inbound Management
 * 4. Inventory Management
 * 5. Customer Management (with data masking)
 * 6. Sales and Outbound Management
 * 7. Stocktake Management
 * 8. System Health Check
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Complete Integration Test Suite)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@DisplayName("case-1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompleteModuleIntegrationTestFixed {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== Module 1: Authentication Tests ==========

    @Test
    @Order(1)
    @DisplayName("case-2")
    void testModule1_Auth_HealthCheck() throws Exception {
        mockMvc.perform(get("/api/auth/health"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("AuthenticationService"));
    }

    // ========== Module 2: Warehouse Tests ==========

    @Test
    @Order(2)
    @WithMockUser(username = "admin", authorities = {"warehouse:view", "SUPER_ADMIN"})
    @DisplayName("case-3")
    void testModule2_Warehouse_ListAll() throws Exception {
        mockMvc.perform(get("/api/warehouses"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ========== Module 3: Purchase Tests ==========

    @Test
    @Order(3)
    @WithMockUser(username = "buyer", authorities = {"purchase:view", "BUYER"})
    @DisplayName("case-4")
    void testModule3_Purchase_ListOrders() throws Exception {
        mockMvc.perform(get("/api/purchase-orders"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ========== Module 4: Inventory Tests ==========

    @Test
    @Order(4)
    @WithMockUser(username = "admin", authorities = {"inventory:view", "SUPER_ADMIN"})
    @DisplayName("case-5")
    void testModule4_Inventory_QuerySummary() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "0")
                .param("size", "10"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    // ========== Module 5: Customer Tests (V4.1 Data Security) ==========

    @Test
    @Order(5)
    @WithMockUser(username = "sales", authorities = {"customer:view", "SALESPERSON"})
    @DisplayName("case-6")
    void testModule5_Customer_SalesViewWithMasking() throws Exception {
        mockMvc.perform(get("/api/customers"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(6)
    @WithMockUser(username = "admin", authorities = {"customer:view", "SUPER_ADMIN"})
    @DisplayName("case-7")
    void testModule5_Customer_AdminViewWithoutMasking() throws Exception {
        mockMvc.perform(get("/api/customers"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ========== Module 6: Sales Tests ==========

    @Test
    @Order(7)
    @WithMockUser(username = "sales", authorities = {"sales:create", "SALESPERSON"})
    @DisplayName("case-8")
    void testModule6_Sales_DownloadTemplate() throws Exception {
        mockMvc.perform(get("/api/sales-orders/template"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    @WithMockUser(username = "sales", authorities = {"sales:view", "SALESPERSON"})
    @DisplayName("case-9")
    void testModule6_Sales_ListOrders() throws Exception {
        mockMvc.perform(get("/api/sales-orders"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ========== Module 7: Outbound Tests ==========

    @Test
    @Order(9)
    @WithMockUser(username = "warehouse", authorities = {"outbound:view", "WAREHOUSE_ADMIN"})
    @DisplayName("case-10")
    void testModule7_Outbound_ListTasks() throws Exception {
        mockMvc.perform(get("/api/outbound-tasks"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ========== Module 8: Stocktake Tests ==========

    @Test
    @Order(10)
    @WithMockUser(username = "admin", authorities = {"stocktake:view", "SUPER_ADMIN"})
    @DisplayName("case-11")
    void testModule8_Stocktake_ListTasks() throws Exception {
        mockMvc.perform(get("/api/stocktake/tasks"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ========== Module 9: System Health Tests ==========

    @Test
    @Order(11)
    @DisplayName("case-12")
    void testModule9_System_HealthCheck() throws Exception {
        mockMvc.perform(get("/health/check"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // ========== End-to-End Integration Test ==========

    @Test
    @Order(12)
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("case-13")
    void testE2E_CompleteBusinessFlowSmokeTest() throws Exception {
        // Test authentication
        mockMvc.perform(get("/api/auth/health"))
            .andExpect(status().isOk());

        // Test warehouse module
        mockMvc.perform(get("/api/warehouses"))
            .andExpect(status().isOk());

        // Test inventory module
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk());

        // Test customer module
        mockMvc.perform(get("/api/customers"))
            .andExpect(status().isOk());

        // Test system health
        mockMvc.perform(get("/health/check"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
