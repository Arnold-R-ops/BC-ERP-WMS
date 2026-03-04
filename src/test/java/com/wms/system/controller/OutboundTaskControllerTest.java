package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.outbound.ConfirmPickingRequest;
import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.service.OutboundService;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OutboundTaskController Integration Test
 *
 * V3.7 Architecture: Outbound Task Management Controller Tests
 *
 * Test Coverage:
 * 1. List outbound tasks
 * 2. List outbound tasks with filters
 * 3. Get outbound task details
 * 4. Confirm picking
 * 5. Batch confirm picking
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
class OutboundTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OutboundService outboundService;

    @Test
    @WithMockUser(username = "testuser", authorities = {"outbound:view"})
    @DisplayName("case-2")
    void testListOutboundTasks() throws Exception {
        // Given
        List<OutboundTaskResponse> responses = Arrays.asList(
            OutboundTaskResponse.builder()
                .id(1L)
                .salesOrderId(1L)
                .salesOrderNo("SO202601290001")
                .salesOrderItemId(1L)
                .assignedBatchId(1L)
                .batchCode("BATCH001")
                .locationId(1L)
                .locationCode("A-01-01")
                .productId(1L)
                .productName("Product A")
                .productBarcode("1234567890")
                .planQty(100)
                .status("PENDING")
                .statusDescription("寰呮嫞璐?")
                .createdAt(LocalDateTime.now())
                .build(),
            OutboundTaskResponse.builder()
                .id(2L)
                .salesOrderId(1L)
                .salesOrderNo("SO202601290001")
                .salesOrderItemId(2L)
                .assignedBatchId(2L)
                .batchCode("BATCH002")
                .locationId(2L)
                .locationCode("A-01-02")
                .productId(2L)
                .productName("Product B")
                .productBarcode("0987654321")
                .planQty(50)
                .status("PENDING")
                .statusDescription("寰呮嫞璐?")
                .createdAt(LocalDateTime.now())
                .build()
        );

        when(outboundService.listOutboundTasks(null, null)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/outbound-tasks")
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].salesOrderNo").value("SO202601290001"))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].planQty").value(100))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].productName").value("Product B"));

        verify(outboundService).listOutboundTasks(null, null);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"outbound:view"})
    @DisplayName("case-3")
    void testListOutboundTasksWithFilters() throws Exception {
        // Given
        List<OutboundTaskResponse> responses = Arrays.asList(
            OutboundTaskResponse.builder()
                .id(1L)
                .salesOrderId(1L)
                .salesOrderNo("SO202601290001")
                .status("PENDING")
                .statusDescription("寰呮嫞璐?")
                .build()
        );

        when(outboundService.listOutboundTasks(1L, "PENDING")).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/outbound-tasks")
                .param("salesOrderId", "1")
                .param("status", "PENDING"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].salesOrderId").value(1))
            .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(outboundService).listOutboundTasks(1L, "PENDING");
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"outbound:view"})
    @DisplayName("case-4")
    void testGetOutboundTask() throws Exception {
        // Given
        OutboundTaskResponse response = OutboundTaskResponse.builder()
            .id(1L)
            .salesOrderId(1L)
            .salesOrderNo("SO202601290001")
            .salesOrderItemId(1L)
            .assignedBatchId(1L)
            .batchCode("BATCH001")
            .locationId(1L)
            .locationCode("A-01-01")
            .productId(1L)
            .productName("Product A")
            .productBarcode("1234567890")
            .planQty(100)
            .actualQty(null)
            .status("PENDING")
            .statusDescription("寰呮嫞璐?")
            .remark("Test remark")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(outboundService.getOutboundTask(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/outbound-tasks/{id}", 1L)
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.salesOrderNo").value("SO202601290001"))
            .andExpect(jsonPath("$.batchCode").value("BATCH001"))
            .andExpect(jsonPath("$.locationCode").value("A-01-01"))
            .andExpect(jsonPath("$.productName").value("Product A"))
            .andExpect(jsonPath("$.planQty").value(100))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.remark").value("Test remark"));

        verify(outboundService).getOutboundTask(1L);
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"outbound:pick"})
    @DisplayName("case-5")
    void testConfirmPicking() throws Exception {
        // Given
        ConfirmPickingRequest request = ConfirmPickingRequest.builder()
            .actualQty(100)
            .remark("Picked successfully")
            .build();

        OutboundTaskResponse response = OutboundTaskResponse.builder()
            .id(1L)
            .salesOrderId(1L)
            .salesOrderNo("SO202601290001")
            .salesOrderItemId(1L)
            .assignedBatchId(1L)
            .batchCode("BATCH001")
            .locationId(1L)
            .locationCode("A-01-01")
            .productId(1L)
            .productName("Product A")
            .planQty(100)
            .actualQty(100)
            .status("COMPLETED")
            .statusDescription("宸插畬鎴?")
            .pickedBy(1L)
            .pickedByName("warehouse_staff")
            .pickedAt(LocalDateTime.now())
            .remark("Picked successfully")
            .build();

        when(outboundService.confirmPicking(eq(1L), eq(100), anyLong(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/outbound-tasks/{id}/confirm", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.actualQty").value(100))
            .andExpect(jsonPath("$.pickedByName").value("warehouse_staff"))
            .andExpect(jsonPath("$.pickedAt").exists())
            .andExpect(jsonPath("$.remark").value("Picked successfully"));

        verify(outboundService).confirmPicking(eq(1L), eq(100), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"outbound:pick"})
    @DisplayName("case-6")
    void testConfirmPickingPartialQuantity() throws Exception {
        // Given
        ConfirmPickingRequest request = ConfirmPickingRequest.builder()
            .actualQty(80)
            .remark("Only 80 available")
            .build();

        OutboundTaskResponse response = OutboundTaskResponse.builder()
            .id(1L)
            .salesOrderId(1L)
            .salesOrderNo("SO202601290001")
            .planQty(100)
            .actualQty(80)
            .status("COMPLETED")
            .statusDescription("宸插畬鎴?")
            .pickedBy(1L)
            .pickedByName("warehouse_staff")
            .pickedAt(LocalDateTime.now())
            .remark("Only 80 available")
            .build();

        when(outboundService.confirmPicking(eq(1L), eq(80), anyLong(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/outbound-tasks/{id}/confirm", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actualQty").value(80))
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(outboundService).confirmPicking(eq(1L), eq(80), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"outbound:pick"})
    @DisplayName("case-7")
    void testBatchConfirmPicking() throws Exception {
        // Given
        List<Long> taskIds = Arrays.asList(1L, 2L, 3L);

        List<OutboundTaskResponse> responses = Arrays.asList(
            OutboundTaskResponse.builder()
                .id(1L)
                .salesOrderNo("SO202601290001")
                .planQty(100)
                .actualQty(100)
                .status("COMPLETED")
                .statusDescription("宸插畬鎴?")
                .pickedBy(1L)
                .pickedByName("warehouse_staff")
                .pickedAt(LocalDateTime.now())
                .build(),
            OutboundTaskResponse.builder()
                .id(2L)
                .salesOrderNo("SO202601290001")
                .planQty(50)
                .actualQty(50)
                .status("COMPLETED")
                .statusDescription("宸插畬鎴?")
                .pickedBy(1L)
                .pickedByName("warehouse_staff")
                .pickedAt(LocalDateTime.now())
                .build(),
            OutboundTaskResponse.builder()
                .id(3L)
                .salesOrderNo("SO202601290001")
                .planQty(30)
                .actualQty(30)
                .status("COMPLETED")
                .statusDescription("宸插畬鎴?")
                .pickedBy(1L)
                .pickedByName("warehouse_staff")
                .pickedAt(LocalDateTime.now())
                .build()
        );

        when(outboundService.batchConfirmPicking(eq(taskIds), anyLong(), anyString()))
            .thenReturn(responses);

        // When & Then
        mockMvc.perform(post("/api/outbound-tasks/batch-confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(taskIds)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].status").value("COMPLETED"))
            .andExpect(jsonPath("$[2].id").value(3))
            .andExpect(jsonPath("$[2].status").value("COMPLETED"));

        verify(outboundService).batchConfirmPicking(eq(taskIds), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"outbound:view"})
    @DisplayName("case-8")
    void testConfirmPickingWithoutPermission() throws Exception {
        // Given
        ConfirmPickingRequest request = ConfirmPickingRequest.builder()
            .actualQty(100)
            .build();

        // When & Then
        mockMvc.perform(post("/api/outbound-tasks/{id}/confirm", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(outboundService, never()).confirmPicking(anyLong(), anyInt(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"outbound:pick"})
    @DisplayName("case-9")
    void testConfirmPickingInvalidRequest() throws Exception {
        // Given
        ConfirmPickingRequest request = ConfirmPickingRequest.builder()
            .actualQty(-10)
            .build();

        // When & Then
        mockMvc.perform(post("/api/outbound-tasks/{id}/confirm", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verify(outboundService, never()).confirmPicking(anyLong(), anyInt(), anyLong(), anyString());
    }
}
