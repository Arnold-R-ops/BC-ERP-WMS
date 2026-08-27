package com.wms.system.controller;

import com.wms.system.config.TestSecurityConfig;
import com.wms.system.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("InventoryController Tests")
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private InventoryService inventoryService;

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("direct inventory adjustment route is not mapped")
    void directInventoryAdjustmentRouteIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/inventory/adjust"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(inventoryService);
    }

    // ========== GET /api/inventory/total-stock/{productSkuId} ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("getTotalStock - returns total stock for product")
    void testGetTotalStock_Success() throws Exception {
        when(inventoryService.getTotalStock(1L)).thenReturn(350);

        mockMvc.perform(get("/api/inventory/total-stock/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productSkuId").value(1))
                .andExpect(jsonPath("$.totalStock").value(350));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("getTotalStock - returns 0 when no inventory")
    void testGetTotalStock_Zero() throws Exception {
        when(inventoryService.getTotalStock(1L)).thenReturn(0);

        mockMvc.perform(get("/api/inventory/total-stock/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(0));
    }
}
