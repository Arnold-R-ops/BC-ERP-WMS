package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.ExcelImportService;
import com.wms.system.service.PurchaseOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
@DisplayName("PurchaseOrderController Tests")
class PurchaseOrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PurchaseOrderService purchaseOrderService;
    @MockBean private ExcelImportService excelImportService;

    private PurchaseOrder buildPurchaseOrder() {
        return PurchaseOrder.builder()
                .id(1L)
                .poNumber("PO-20260101-001")
                .supplier("Test Supplier")
                .status(PurchaseOrderStatus.ORDERING)
                .totalQuantity(100)
                .totalCost(new BigDecimal("1000.00"))
                .items(new ArrayList<>())
                .build();
    }

    // ========== POST /api/purchase-orders ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("createPurchaseOrder - returns 201 when successful")
    void testCreatePurchaseOrder_Success() throws Exception {
        PurchaseOrder order = buildPurchaseOrder();
        when(purchaseOrderService.createPurchaseOrder(any(), any(), any(), any(), any(), any()))
                .thenReturn(order);

        String requestJson = """
                {
                  "supplier": "Test Supplier",
                  "items": [{"productId": 1, "orderedQuantity": 100}],
                  "expectedDate": "2026-06-01",
                  "operatorId": 1,
                  "operatorName": "admin"
                }
                """;

        mockMvc.perform(post("/api/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());
    }

    // ========== POST /api/purchase-orders/upload ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("uploadExcel - returns 201 when valid xlsx uploaded")
    void testUploadExcel_Success() throws Exception {
        PurchaseOrderService.PurchaseOrderItemData item =
                PurchaseOrderService.PurchaseOrderItemData.builder()
                        .productId(1L)
                        .orderedQuantity(100)
                        .build();
        PurchaseOrder order = buildPurchaseOrder();

        when(excelImportService.importPurchaseOrderFromExcel(any())).thenReturn(List.of(item));
        when(purchaseOrderService.createPurchaseOrder(any(), any(), any(), any(), any(), any()))
                .thenReturn(order);

        MockMultipartFile file = new MockMultipartFile(
                "file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/purchase-orders/upload")
                        .file(file)
                        .param("supplier", "Test Supplier")
                        .param("operatorId", "1")
                        .param("operatorName", "admin"))
                .andExpect(status().isCreated());
    }

    // ========== GET /api/purchase-orders/{id} ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getById - returns 200 with purchase order details")
    void testGetById_Success() throws Exception {
        PurchaseOrder order = buildPurchaseOrder();
        when(purchaseOrderService.findById(1L)).thenReturn(order);

        mockMvc.perform(get("/api/purchase-orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poNumber").value("PO-20260101-001"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getById - returns 404 when not found")
    void testGetById_NotFound() throws Exception {
        when(purchaseOrderService.findById(99L)).thenThrow(
                new BusinessException(ErrorKeys.PURCHASE_ORDER_NOT_FOUND, Map.of("id", 99L))
        );

        mockMvc.perform(get("/api/purchase-orders/99"))
                .andExpect(status().isNotFound());
    }

    // ========== Staff privacy masking ==========

    @Test
    @WithMockUser(username = "staff", authorities = {"STAFF"})
    @DisplayName("getById - STAFF role sees masked supplier")
    void testGetById_StaffRoleSeesmaskedSupplier() throws Exception {
        PurchaseOrder order = buildPurchaseOrder();
        when(purchaseOrderService.findById(1L)).thenReturn(order);

        // STAFF role: supplier should be masked
        mockMvc.perform(get("/api/purchase-orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplier").value("***"));
    }

    // ========== PUT /api/purchase-orders/{id}/rollback ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("rollback - returns 200 when successful")
    void testRollback_Success() throws Exception {
        PurchaseOrder rolledBack = buildPurchaseOrder();
        when(purchaseOrderService.rollbackToOrdering(1L, "Wrong delivery")).thenReturn(rolledBack);

        mockMvc.perform(put("/api/purchase-orders/1/rollback")
                        .param("reason", "Wrong delivery"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("rollback - returns error when wrong status")
    void testRollback_WrongStatus() throws Exception {
        when(purchaseOrderService.rollbackToOrdering(any(), any())).thenThrow(
                new BusinessException(ErrorKeys.PO_ROLLBACK_NOT_ALLOWED, Map.of("status", "ORDERING"))
        );

        mockMvc.perform(put("/api/purchase-orders/1/rollback")
                        .param("reason", "Wrong"))
                .andExpect(status().isBadRequest());
    }

    // ========== V4.4 幂等性防御：重复采购单号 ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("createPurchaseOrder - 重复 po_number -> 409 ORDER_NUMBER_DUPLICATE")
    void testCreatePurchaseOrder_DuplicatePoNumber_Returns409() throws Exception {
        when(purchaseOrderService.createPurchaseOrder(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException(
                        "constraint violation: unique constraint [idx_po_number] on purchase_order; " +
                        "detail: duplicate key value violates unique constraint \"idx_po_number\""));

        String requestJson = """
                {
                  "supplier": "Test Supplier",
                  "items": [{"productId": 1, "orderedQuantity": 100}],
                  "expectedDate": "2026-06-01",
                  "operatorId": 1,
                  "operatorName": "admin"
                }
                """;

        mockMvc.perform(post("/api/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value(ErrorKeys.ORDER_NUMBER_DUPLICATE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.params.orderType").value("PURCHASE"))
                .andExpect(jsonPath("$.params.fieldName").value("po_number"))
                .andExpect(jsonPath("$.params.message").value("该订单号已存在，请勿重复提交"));
    }

    // ========== PUT /api/purchase-orders/{id}/confirm ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("confirmAndGenerateBatchCodes - returns 200 when successful")
    void testConfirmAndGenerateBatchCodes_Success() throws Exception {
        PurchaseOrder order = buildPurchaseOrder();
        order.setStatus(PurchaseOrderStatus.IN_TRANSIT);
        when(purchaseOrderService.confirmAndGenerateBatchCodes(eq(1L), any())).thenReturn(order);

        String requestJson = """
                {
                  "items": [
                    {
                      "itemId": 1,
                      "expiryDate": "2027-01-01",
                      "productionDate": "2026-01-01"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/purchase-orders/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("confirmAndGenerateBatchCodes - returns 400 when status invalid")
    void testConfirmAndGenerateBatchCodes_InvalidStatus() throws Exception {
        when(purchaseOrderService.confirmAndGenerateBatchCodes(eq(1L), any()))
                .thenThrow(new BusinessException(ErrorKeys.PO_INVALID_STATUS,
                        Map.of("currentStatus", "COMPLETED")));

        String requestJson = """
                {"items": [{"itemId": 1, "expiryDate": "2027-01-01"}]}
                """;

        mockMvc.perform(put("/api/purchase-orders/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    // ========== PUT /api/purchase-orders/{id}/receive ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("receiveGoods - returns 200 when successful (partial)")
    void testReceiveGoods_Success() throws Exception {
        PurchaseOrder order = buildPurchaseOrder();
        order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        when(purchaseOrderService.receiveGoods(eq(1L), any(), any(), any())).thenReturn(order);

        String requestJson = """
                {
                  "receiveItems": [
                    {
                      "itemId": 1,
                      "batches": [{"batchCode": "R7M4K9", "locationId": 101}]
                    }
                  ],
                  "operatorId": 1,
                  "operatorName": "warehouse_staff"
                }
                """;

        mockMvc.perform(put("/api/purchase-orders/1/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));
    }

    // ========== GET /api/purchase-orders ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getPurchaseOrders - returns 200 with list")
    void testGetPurchaseOrders_Success() throws Exception {
        PurchaseOrder order = buildPurchaseOrder();
        when(purchaseOrderService.findByStatus(isNull(), any())).thenReturn(List.of(order));

        mockMvc.perform(get("/api/purchase-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].poNumber").value("PO-20260101-001"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getPurchaseOrders - filter by status returns 200")
    void testGetPurchaseOrders_FilterByStatus() throws Exception {
        PurchaseOrder order = buildPurchaseOrder();
        when(purchaseOrderService.findByStatus(eq(PurchaseOrderStatus.ORDERING), any()))
                .thenReturn(List.of(order));

        mockMvc.perform(get("/api/purchase-orders").param("status", "ORDERING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ORDERING"));
    }
}
