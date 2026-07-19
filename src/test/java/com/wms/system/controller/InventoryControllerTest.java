package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.StockAdjustmentRequest;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
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
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InventoryService inventoryService;

    // ========== POST /api/inventory/adjust ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("adjustStock - returns 200 when successful")
    void testAdjustStock_Success() throws Exception {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(1L)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(50)
                .sourceOrderId("PO20260101001")
                .build();

        ProductSku product = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode()).id(1L).name("Test ProductSku").barcode("BAR001").build();
        Location location = Location.builder().id(1L).locationCode("WH01-A-01-001").build();
        StockTransaction tx = StockTransaction.builder()
                .id(1L).productSku(product).location(location)
                .transactionType(TransactionType.IN).sourceType(SourceType.PURCHASE_IN)
                .quantity(50).quantityBefore(0).quantityAfter(50)
                .sourceOrderId("PO20260101001")
                .build();

        when(inventoryService.adjustStock(any(StockAdjustmentRequest.class))).thenReturn(tx);

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("adjustStock - returns 400 when request is invalid (missing productSkuId)")
    void testAdjustStock_InvalidRequest() throws Exception {
        String invalidJson = """
                {
                  "locationId": 1,
                  "transactionType": "IN",
                  "sourceType": "PURCHASE_IN",
                  "quantity": 50,
                  "sourceOrderId": "PO001"
                }
                """;

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("adjustStock - returns error when product not found")
    void testAdjustStock_ProductNotFound() throws Exception {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(99L)
                .locationId(1L)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(10)
                .sourceOrderId("PO001")
                .build();

        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND, Map.of("productSkuId", 99L))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("adjustStock - returns error when stock insufficient")
    void testAdjustStock_InsufficientStock() throws Exception {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(1L)
                .transactionType(TransactionType.OUT)
                .sourceType(SourceType.SALE_OUT)
                .quantity(999)
                .sourceOrderId("SO001")
                .build();

        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(ErrorKeys.STOCK_INSUFFICIENT, Map.of("currentStock", 10))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========== GET /api/inventory/total-stock/{productSkuId} ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getTotalStock - returns total stock for product")
    void testGetTotalStock_Success() throws Exception {
        when(inventoryService.getTotalStock(1L)).thenReturn(350);

        mockMvc.perform(get("/api/inventory/total-stock/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productSkuId").value(1))
                .andExpect(jsonPath("$.totalStock").value(350));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getTotalStock - returns 0 when no inventory")
    void testGetTotalStock_Zero() throws Exception {
        when(inventoryService.getTotalStock(1L)).thenReturn(0);

        mockMvc.perform(get("/api/inventory/total-stock/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(0));
    }
}
