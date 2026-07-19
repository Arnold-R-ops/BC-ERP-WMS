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
import org.springframework.dao.DataIntegrityViolationException;
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
    @DisplayName("BusinessException PRODUCT_SKU_NOT_FOUND -> 404")
    void testBusinessException_NotFound_Returns404() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND, Map.of("productSkuId", 1L))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.PRODUCT_SKU_NOT_FOUND));
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
                new BusinessException(ErrorKeys.STOCK_CONCURRENCY_CONFLICT, Map.of("productSkuId", 1L))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    @DisplayName("BusinessException OUTBOUND_TASK_ALREADY_COMPLETED -> 409")
    void testBusinessException_OutboundTaskAlreadyCompleted_Returns409() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(ErrorKeys.OUTBOUND_TASK_ALREADY_COMPLETED, Map.of("taskId", 7L))
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.OUTBOUND_TASK_ALREADY_COMPLETED));
    }

    @Test
    @WithMockUser
    @DisplayName("BusinessException SALES_ORDER_INVALID_STATUS -> 409")
    void testBusinessException_SalesOrderInvalidStatus_Returns409() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new BusinessException(
                        ErrorKeys.SALES_ORDER_INVALID_STATUS,
                        Map.of(
                                "salesOrderId", 26L,
                                "currentStatus", "APPROVED_AWAITING_SHIPMENT",
                                "requiredStatus", "PENDING_APPROVAL"
                        )
                )
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.SALES_ORDER_INVALID_STATUS))
                .andExpect(jsonPath("$.status").value(409));
    }

    // ========== Validation exceptions ==========

    @Test
    @WithMockUser
    @DisplayName("MethodArgumentNotValidException -> 400 with VALIDATION_FAILED")
    void testValidationException_Returns400() throws Exception {
        // Missing required fields (productSkuId, quantity, etc.)
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
                  "productSkuId": 1,
                  "locationId": 1,
                  "transactionType": "IN",
                  "sourceType": "PURCHASE_IN",
                  "quantity": 10,
                  "sourceOrderId": "PO001"
                }
                """;
    }

    // ========== V4.4 Idempotency: DataIntegrityViolationException ==========

    @Test
    @WithMockUser
    @DisplayName("DataIntegrityViolationException (unique sales_orders) -> 409 ORDER_NUMBER_DUPLICATE")
    void testDataIntegrityViolation_SalesOrderDuplicate_Returns409() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new DataIntegrityViolationException(
                        "could not execute statement; SQL [n/a]; constraint [idx_sales_order_no]; " +
                        "detail: Key (order_no)=(SO20260315001) already exists in sales_orders")
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.ORDER_NUMBER_DUPLICATE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.params.message").value("该订单号已存在，请勿重复提交"))
                .andExpect(jsonPath("$.params.orderType").value("SALES"))
                .andExpect(jsonPath("$.params.fieldName").value("order_no"));
    }

    @Test
    @WithMockUser
    @DisplayName("DataIntegrityViolationException (unique purchase_order) -> 409 ORDER_NUMBER_DUPLICATE")
    void testDataIntegrityViolation_PurchaseOrderDuplicate_Returns409() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new DataIntegrityViolationException(
                        "constraint violation: unique constraint [idx_po_number] on table purchase_order; " +
                        "detail: duplicate key value violates unique constraint")
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.ORDER_NUMBER_DUPLICATE))
                .andExpect(jsonPath("$.params.orderType").value("PURCHASE"))
                .andExpect(jsonPath("$.params.fieldName").value("po_number"));
    }

    @Test
    @WithMockUser
    @DisplayName("DataIntegrityViolationException (unique inbound_orders) -> 409 ORDER_NUMBER_DUPLICATE")
    void testDataIntegrityViolation_InboundOrderDuplicate_Returns409() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new DataIntegrityViolationException(
                        "constraint [idx_inbound_order_no] on inbound_orders; duplicate key value")
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.ORDER_NUMBER_DUPLICATE))
                .andExpect(jsonPath("$.params.orderType").value("INBOUND"))
                .andExpect(jsonPath("$.params.fieldName").value("order_no"));
    }

    @Test
    @WithMockUser
    @DisplayName("DataIntegrityViolationException (non-unique violation) -> 400 VALIDATION_FAILED")
    void testDataIntegrityViolation_NonUnique_Returns400() throws Exception {
        when(inventoryService.adjustStock(any())).thenThrow(
                new DataIntegrityViolationException("foreign key constraint violated")
        );

        mockMvc.perform(post("/api/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdjustRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.VALIDATION_FAILED));
    }
}
