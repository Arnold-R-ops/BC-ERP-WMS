package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GlobalExceptionHandler integration test.
 * Uses InventoryController as the endpoint to trigger various exceptions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InventoryService inventoryService;

    // ========== BusinessException mappings ==========

    @Test
    @WithMockUser
    @DisplayName("BusinessException PRODUCT_NOT_FOUND -> 404")
    void testBusinessException_NotFound_Returns404() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("productId", 1L))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.PRODUCT_NOT_FOUND));
    }

    @Test
    @WithMockUser
    @DisplayName("BusinessException STOCK_INSUFFICIENT -> 400")
    void testBusinessException_BadRequest_Returns400() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(ErrorKeys.STOCK_INSUFFICIENT, Map.of("currentStock", 5))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.STOCK_INSUFFICIENT));
    }

    @Test
    @WithMockUser
    @DisplayName("BusinessException STOCK_CONCURRENCY_CONFLICT -> 409")
    void testBusinessException_Conflict_Returns409() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(ErrorKeys.STOCK_CONCURRENCY_CONFLICT, Map.of("productId", 1L))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isConflict());
    }

    // ========== Validation exceptions ==========

    @Test
    @WithMockUser
    @DisplayName("MethodArgumentNotValidException -> 400 with VALIDATION_FAILED")
    void testValidationException_Returns400() throws Exception {
        // Missing required fields (productId, quantity, etc.)
        String emptyJson = "{}";

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.VALIDATION_FAILED));
    }

    @Test
    @WithMockUser
    @DisplayName("MethodArgumentTypeMismatchException -> 400 (non-numeric path variable)")
    void testTypeMismatch_Returns400() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/inventory/total-stock/NOT_A_NUMBER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("HttpMediaTypeNotSupportedException -> 415")
    void testUnsupportedMediaType_Returns415() throws Exception {
        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("some text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ========== Helper ==========

    private String validAdjustRequestJson() {
        return """
                {
                  "productId": 1,
                  "locationId": 1,
                  "transactionType": "IN",
                  "sourceType": "PURCHASE_IN",
                  "quantity": 10,
                  "sourceOrderId": "PO001"
                }
                """;
    }
}
