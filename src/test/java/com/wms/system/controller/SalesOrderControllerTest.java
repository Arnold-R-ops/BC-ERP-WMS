package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.sales.*;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.SalesEntryService;
import com.wms.system.service.SalesSubmissionService;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SalesOrderController Integration Test
 *
 * V3.7 Architecture: Sales Order Management Controller Tests
 *
 * Test Coverage:
 * 1. Download Excel template
 * 2. Upload Excel file
 * 3. Get batch options
 * 4. Create sales order
 * 5. List sales orders
 * 6. Get sales order details
 * 7. Update sales order
 * 8. Approve sales order
 * 9. Reject sales order
 * 10. Cancel sales order
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class SalesOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SalesEntryService salesEntryService;

    @MockBean
    private SalesSubmissionService salesSubmissionService;

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:create"})
    @DisplayName("case-2")
    void testDownloadExcelTemplate() throws Exception {
        // Given
        byte[] excelBytes = "Excel Template Content".getBytes();
        when(salesEntryService.downloadExcelTemplate()).thenReturn(excelBytes);

        // When & Then
        mockMvc.perform(get("/api/sales-orders/template")
                .accept(MediaType.APPLICATION_OCTET_STREAM))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
            .andExpect(header().string("Content-Disposition",
                containsString("sales_order_template.xlsx")))
            .andExpect(content().bytes(excelBytes));

        verify(salesEntryService).downloadExcelTemplate();
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:create"})
    @DisplayName("case-3")
    void testUploadExcel() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sales_order.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "Excel Content".getBytes()
        );

        List<CreateSalesOrderRequest.SalesOrderItemData> items = Arrays.asList(
            CreateSalesOrderRequest.SalesOrderItemData.builder()
                .productId(1L)
                .quantity(100)
                .unitPrice(new BigDecimal("10.50"))
                .rejectNearExpiry(false)
                .build(),
            CreateSalesOrderRequest.SalesOrderItemData.builder()
                .productId(2L)
                .quantity(50)
                .unitPrice(new BigDecimal("20.00"))
                .rejectNearExpiry(true)
                .build()
        );

        when(salesEntryService.importSalesOrderFromExcel(any())).thenReturn(items);

        // When & Then
        mockMvc.perform(multipart("/api/sales-orders/upload")
                .file(file))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].productId").value(1))
            .andExpect(jsonPath("$[0].quantity").value(100))
            .andExpect(jsonPath("$[0].unitPrice").value(10.50))
            .andExpect(jsonPath("$[1].productId").value(2))
            .andExpect(jsonPath("$[1].quantity").value(50))
            .andExpect(jsonPath("$[1].rejectNearExpiry").value(true));

        verify(salesEntryService).importSalesOrderFromExcel(any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:create"})
    @DisplayName("case-4")
    void testGetBatchOptions() throws Exception {
        // Given
        List<BatchOptionDto> options = Arrays.asList(
            BatchOptionDto.builder()
                .batchId(1L)
                .batchCode("BATCH001")
                .quantity(100)
                .freshnessStatus("FRESH")
                .build(),
            BatchOptionDto.builder()
                .batchId(2L)
                .batchCode("BATCH002")
                .quantity(50)
                .freshnessStatus("FRESH")
                .build()
        );

        when(salesEntryService.getBatchOptions(1L, 80, false)).thenReturn(options);

        // When & Then
        mockMvc.perform(post("/api/sales-orders/batch-options")
                .param("productId", "1")
                .param("quantity", "80")
                .param("rejectNearExpiry", "false"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].batchId").value(1))
            .andExpect(jsonPath("$[0].batchCode").value("BATCH001"))
            .andExpect(jsonPath("$[0].quantity").value(100))
            .andExpect(jsonPath("$[1].batchId").value(2));

        verify(salesEntryService).getBatchOptions(1L, 80, false);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:create"})
    @DisplayName("case-5")
    void testCreateSalesOrder() throws Exception {
        // Given
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(1L)
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(1L)
                    .quantity(100)
                    .unitPrice(new BigDecimal("10.00"))
                    .rejectNearExpiry(false)
                    .build()
            ))
            .build();

        SalesOrderResponse response = SalesOrderResponse.builder()
            .id(1L)
            .orderNo("SO202601290001")
            .customerId(1L)
            .customerName("Test Customer")
            .totalAmount(new BigDecimal("1000.00"))
            .status("APPROVED_AWAITING_SHIPMENT")
            .statusDescription("宸插鎵?寰呭嚭搴?")
            .applicantId(1L)
            .applicantName("testuser")
            .createdAt(LocalDateTime.now())
            .build();

        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/sales-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.orderNo").value("SO202601290001"))
            .andExpect(jsonPath("$.status").value("APPROVED_AWAITING_SHIPMENT"))
            .andExpect(jsonPath("$.totalAmount").value(1000.00))
            .andExpect(jsonPath("$.customerName").value("Test Customer"));

        verify(salesSubmissionService).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:view"})
    @DisplayName("case-6")
    void testListSalesOrders() throws Exception {
        // Given
        List<SalesOrderResponse> responses = Arrays.asList(
            SalesOrderResponse.builder()
                .id(1L)
                .orderNo("SO202601290001")
                .customerId(1L)
                .customerName("Customer A")
                .totalAmount(new BigDecimal("1000.00"))
                .status("APPROVED_AWAITING_SHIPMENT")
                .statusDescription("宸插鎵?寰呭嚭搴?")
                .build(),
            SalesOrderResponse.builder()
                .id(2L)
                .orderNo("SO202601290002")
                .customerId(2L)
                .customerName("Customer B")
                .totalAmount(new BigDecimal("2000.00"))
                .status("PENDING_APPROVAL")
                .statusDescription("寰呭鎵?")
                .build()
        );

        when(salesSubmissionService.listSalesOrders(null, null)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/sales-orders")
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].orderNo").value("SO202601290001"))
            .andExpect(jsonPath("$[0].status").value("APPROVED_AWAITING_SHIPMENT"))
            .andExpect(jsonPath("$[1].orderNo").value("SO202601290002"))
            .andExpect(jsonPath("$[1].status").value("PENDING_APPROVAL"));

        verify(salesSubmissionService).listSalesOrders(null, null);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:view"})
    @DisplayName("case-7")
    void testListSalesOrdersWithFilters() throws Exception {
        // Given
        List<SalesOrderResponse> responses = Arrays.asList(
            SalesOrderResponse.builder()
                .id(1L)
                .orderNo("SO202601290001")
                .customerId(1L)
                .customerName("Customer A")
                .status("PENDING_APPROVAL")
                .build()
        );

        when(salesSubmissionService.listSalesOrders("PENDING_APPROVAL", 1L))
            .thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/sales-orders")
                .param("status", "PENDING_APPROVAL")
                .param("customerId", "1"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$[0].customerId").value(1));

        verify(salesSubmissionService).listSalesOrders("PENDING_APPROVAL", 1L);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:view"})
    @DisplayName("case-8")
    void testGetSalesOrder() throws Exception {
        // Given
        SalesOrderResponse response = SalesOrderResponse.builder()
            .id(1L)
            .orderNo("SO202601290001")
            .customerId(1L)
            .customerName("Test Customer")
            .totalAmount(new BigDecimal("1000.00"))
            .status("APPROVED_AWAITING_SHIPMENT")
            .statusDescription("宸插鎵?寰呭嚭搴?")
            .items(Arrays.asList(
                SalesOrderResponse.SalesOrderItemResponse.builder()
                    .id(1L)
                    .productId(1L)
                    .productName("Product A")
                    .quantity(100)
                    .unitPrice(new BigDecimal("10.00"))
                    .subtotal(new BigDecimal("1000.00"))
                    .build()
            ))
            .build();

        when(salesSubmissionService.getSalesOrder(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/sales-orders/{id}", 1L)
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.orderNo").value("SO202601290001"))
            .andExpect(jsonPath("$.status").value("APPROVED_AWAITING_SHIPMENT"))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].productName").value("Product A"))
            .andExpect(jsonPath("$.items[0].quantity").value(100));

        verify(salesSubmissionService).getSalesOrder(1L);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:edit"})
    @DisplayName("case-9")
    void testUpdateSalesOrder() throws Exception {
        // Given
        UpdateSalesOrderRequest request = UpdateSalesOrderRequest.builder()
            .items(Arrays.asList(
                UpdateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(1L)
                    .quantity(150)
                    .unitPrice(new BigDecimal("10.00"))
                    .build()
            ))
            .build();

        SalesOrderResponse response = SalesOrderResponse.builder()
            .id(1L)
            .orderNo("SO202601290001")
            .customerId(1L)
            .customerName("Test Customer")
            .totalAmount(new BigDecimal("1500.00"))
            .status("PENDING_APPROVAL")
            .statusDescription("寰呭鎵?")
            .build();

        when(salesSubmissionService.updateSalesOrder(eq(1L), any(), anyLong()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/sales-orders/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.totalAmount").value(1500.00))
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        verify(salesSubmissionService).updateSalesOrder(eq(1L), any(), anyLong());
    }

    @Test
    @WithMockUser(username = "manager", authorities = {"sales:approve"})
    @DisplayName("case-10")
    void testApproveSalesOrder() throws Exception {
        // Given
        ApprovalRequest request = ApprovalRequest.builder()
            .comment("Approved by manager")
            .build();

        SalesOrderResponse response = SalesOrderResponse.builder()
            .id(1L)
            .orderNo("SO202601290001")
            .status("APPROVED_AWAITING_SHIPMENT")
            .statusDescription("宸插鎵?寰呭嚭搴?")
            .reviewedBy(2L)
            .reviewedByName("manager")
            .reviewedAt(LocalDateTime.now())
            .reviewComment("Approved by manager")
            .build();

        when(salesSubmissionService.approveSalesOrder(eq(1L), anyLong(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/sales-orders/{id}/approve", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("APPROVED_AWAITING_SHIPMENT"))
            .andExpect(jsonPath("$.reviewedByName").value("manager"))
            .andExpect(jsonPath("$.reviewComment").value("Approved by manager"));

        verify(salesSubmissionService).approveSalesOrder(eq(1L), anyLong(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "manager", authorities = {"sales:approve"})
    @DisplayName("case-11")
    void testRejectSalesOrder() throws Exception {
        // Given
        ApprovalRequest request = ApprovalRequest.builder()
            .reason("Price too low")
            .build();

        SalesOrderResponse response = SalesOrderResponse.builder()
            .id(1L)
            .orderNo("SO202601290001")
            .status("REJECTED")
            .statusDescription("宸叉嫆缁?")
            .reviewedBy(2L)
            .reviewedByName("manager")
            .reviewedAt(LocalDateTime.now())
            .reviewReason("Price too low")
            .build();

        when(salesSubmissionService.rejectSalesOrder(eq(1L), anyLong(), anyString(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/sales-orders/{id}/reject", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewedByName").value("manager"))
            .andExpect(jsonPath("$.reviewReason").value("Price too low"));

        verify(salesSubmissionService).rejectSalesOrder(eq(1L), anyLong(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:cancel"})
    @DisplayName("case-12")
    void testCancelSalesOrder() throws Exception {
        // Given
        CancelOrderRequest request = CancelOrderRequest.builder()
            .reason("Customer requested cancellation")
            .build();

        SalesOrderResponse response = SalesOrderResponse.builder()
            .id(1L)
            .orderNo("SO202601290001")
            .status("CANCELLED")
            .statusDescription("宸插彇娑?")
            .build();

        when(salesSubmissionService.cancelSalesOrder(eq(1L), anyString(), anyLong()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/sales-orders/{id}/cancel", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(salesSubmissionService).cancelSalesOrder(eq(1L), anyString(), anyLong());
    }

    // ========== V4.4 幂等性防御：重复订单号 ==========

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:create"})
    @DisplayName("case-14 createSalesOrder - 重复 order_no -> 409 ORDER_NUMBER_DUPLICATE")
    void testCreateSalesOrder_DuplicateOrderNo_Returns409() throws Exception {
        // Given: 数据库层唯一约束抛出
        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
            .thenThrow(new DataIntegrityViolationException(
                "could not execute statement; constraint [idx_sales_order_no]; " +
                "detail: Key (order_no)=(SO20260315001) already exists in sales_orders"));

        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(1L)
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(1L).quantity(10).unitPrice(new BigDecimal("10.00"))
                    .rejectNearExpiry(false).build()
            ))
            .build();

        // When & Then
        mockMvc.perform(post("/api/sales-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorKey").value(ErrorKeys.ORDER_NUMBER_DUPLICATE))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.params.message").value("该订单号已存在，请勿重复提交"));
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"sales:view"})
    @DisplayName("case-13")
    void testCreateSalesOrderWithoutPermission() throws Exception {
        // Given
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(1L)
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(1L)
                    .quantity(100)
                    .unitPrice(new BigDecimal("10.00"))
                    .build()
            ))
            .build();

        // When & Then
        mockMvc.perform(post("/api/sales-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }
}
