package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.stocktake.*;
import com.wms.system.service.StocktakeService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StocktakeController Integration Test
 *
 * V3.8 Architecture: Smart Stocktake System Controller Tests
 *
 * Test Coverage:
 * 1. Create stocktake task
 * 2. List stocktake tasks
 * 3. Get stocktake task details
 * 4. Start counting
 * 5. Get stocktake items (blind count)
 * 6. Submit count result
 * 7. Finish counting
 * 8. Get review items
 * 9. Review stocktake (approve/reject)
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class StocktakeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StocktakeService stocktakeService;

    @Test
    @WithMockUser(username = "testuser", authorities = {"stocktake:create"})
    @DisplayName("case-2")
    void testCreateStocktakeTask() throws Exception {
        // Given
        CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
            .warehouseId(1L)
            .cycleType("MONTHLY")
            .build();

        StocktakeTaskResponse response = StocktakeTaskResponse.builder()
            .id(1L)
            .taskNo("ST202601290001")
            .warehouseId(1L)
            .warehouseName("Main Warehouse")
            .cycleType("MONTHLY")
            .cycleTypeDescription("鏈堝害鐩樼偣")
            .status("CREATED")
            .statusDescription("宸插垱寤?")
            .snapshotTime(LocalDateTime.now())
            .totalItems(100)
            .countedItems(0)
            .differenceItems(0)
            .progress(0)
            .createdBy(1L)
            .createdByName("testuser")
            .createdAt(LocalDateTime.now())
            .build();

        when(stocktakeService.createCycleTask(any(), anyLong(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.taskNo").value("ST202601290001"))
            .andExpect(jsonPath("$.warehouseId").value(1))
            .andExpect(jsonPath("$.cycleType").value("MONTHLY"))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.totalItems").value(100))
            .andExpect(jsonPath("$.progress").value(0));

        verify(stocktakeService).createCycleTask(any(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"stocktake:view"})
    @DisplayName("case-3")
    void testListStocktakeTasks() throws Exception {
        // Given
        List<StocktakeTaskResponse> responses = Arrays.asList(
            StocktakeTaskResponse.builder()
                .id(1L)
                .taskNo("ST202601290001")
                .warehouseId(1L)
                .warehouseName("Main Warehouse")
                .cycleType("MONTHLY")
                .status("CREATED")
                .statusDescription("宸插垱寤?")
                .totalItems(100)
                .progress(0)
                .build(),
            StocktakeTaskResponse.builder()
                .id(2L)
                .taskNo("ST202601290002")
                .warehouseId(1L)
                .warehouseName("Main Warehouse")
                .cycleType("QUARTERLY")
                .status("COUNTING")
                .statusDescription("鐩樼偣涓?")
                .totalItems(150)
                .progress(50)
                .build()
        );

        when(stocktakeService.listStocktakeTasks(null, null)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/stocktake/tasks")
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].taskNo").value("ST202601290001"))
            .andExpect(jsonPath("$[0].status").value("CREATED"))
            .andExpect(jsonPath("$[1].taskNo").value("ST202601290002"))
            .andExpect(jsonPath("$[1].status").value("COUNTING"))
            .andExpect(jsonPath("$[1].progress").value(50));

        verify(stocktakeService).listStocktakeTasks(null, null);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"stocktake:view"})
    @DisplayName("case-4")
    void testListStocktakeTasksWithFilters() throws Exception {
        // Given
        List<StocktakeTaskResponse> responses = Arrays.asList(
            StocktakeTaskResponse.builder()
                .id(1L)
                .taskNo("ST202601290001")
                .warehouseId(1L)
                .status("COUNTING")
                .build()
        );

        when(stocktakeService.listStocktakeTasks(1L, "COUNTING")).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/stocktake/tasks")
                .param("warehouseId", "1")
                .param("status", "COUNTING"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].warehouseId").value(1))
            .andExpect(jsonPath("$[0].status").value("COUNTING"));

        verify(stocktakeService).listStocktakeTasks(1L, "COUNTING");
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"stocktake:view"})
    @DisplayName("case-5")
    void testGetStocktakeTask() throws Exception {
        // Given
        StocktakeTaskResponse response = StocktakeTaskResponse.builder()
            .id(1L)
            .taskNo("ST202601290001")
            .warehouseId(1L)
            .warehouseName("Main Warehouse")
            .cycleType("MONTHLY")
            .cycleTypeDescription("鏈堝害鐩樼偣")
            .status("COUNTING")
            .statusDescription("鐩樼偣涓?")
            .snapshotTime(LocalDateTime.now())
            .totalItems(100)
            .countedItems(50)
            .differenceItems(5)
            .progress(50)
            .createdBy(1L)
            .createdByName("testuser")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(stocktakeService.getStocktakeTask(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/stocktake/tasks/{id}", 1L)
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.taskNo").value("ST202601290001"))
            .andExpect(jsonPath("$.status").value("COUNTING"))
            .andExpect(jsonPath("$.totalItems").value(100))
            .andExpect(jsonPath("$.countedItems").value(50))
            .andExpect(jsonPath("$.progress").value(50));

        verify(stocktakeService).getStocktakeTask(1L);
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"stocktake:count"})
    @DisplayName("case-6")
    void testStartCounting() throws Exception {
        // Given
        StocktakeTaskResponse response = StocktakeTaskResponse.builder()
            .id(1L)
            .taskNo("ST202601290001")
            .warehouseId(1L)
            .warehouseName("Main Warehouse")
            .status("COUNTING")
            .statusDescription("鐩樼偣涓?")
            .totalItems(100)
            .countedItems(0)
            .progress(0)
            .build();

        when(stocktakeService.startCounting(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks/{id}/start", 1L))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("COUNTING"))
            .andExpect(jsonPath("$.countedItems").value(0));

        verify(stocktakeService).startCounting(1L);
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"stocktake:count"})
    @DisplayName("case-7")
    void testGetStocktakeItems() throws Exception {
        // Given
        List<StocktakeItemResponse> responses = Arrays.asList(
            StocktakeItemResponse.builder()
                .id(1L)
                .taskId(1L)
                .batchId(1L)
                .batchCode("BATCH001")
                .productId(1L)
                .productName("Product A")
                .productBarcode("1234567890")
                .locationId(1L)
                .locationCode("A-01-01")
                .countedQty(null)
                .isCounted(false)
                .build(),
            StocktakeItemResponse.builder()
                .id(2L)
                .taskId(1L)
                .batchId(2L)
                .batchCode("BATCH002")
                .productId(2L)
                .productName("Product B")
                .productBarcode("0987654321")
                .locationId(2L)
                .locationCode("A-01-02")
                .countedQty(null)
                .isCounted(false)
                .build()
        );

        when(stocktakeService.getStocktakeItems(1L)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/stocktake/tasks/{id}/items", 1L)
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].batchCode").value("BATCH001"))
            .andExpect(jsonPath("$[0].snapshotQty").doesNotExist())  // Blind count verification
            .andExpect(jsonPath("$[0].isCounted").value(false))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].snapshotQty").doesNotExist());  // Blind count verification

        verify(stocktakeService).getStocktakeItems(1L);
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"stocktake:count"})
    @DisplayName("case-8")
    void testSubmitCount() throws Exception {
        // Given
        SubmitCountRequest request = SubmitCountRequest.builder()
            .countedQty(95)
            .remark("Counted by warehouse staff")
            .build();

        StocktakeItemResponse response = StocktakeItemResponse.builder()
            .id(1L)
            .taskId(1L)
            .batchId(1L)
            .batchCode("BATCH001")
            .productId(1L)
            .productName("Product A")
            .countedQty(95)
            .isCounted(true)
            .countedBy(1L)
            .countedByName("warehouse_staff")
            .countedAt(LocalDateTime.now())
            .remark("Counted by warehouse staff")
            .build();

        when(stocktakeService.submitCount(eq(1L), eq(1L), any(), anyLong(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks/{taskId}/items/{itemId}/count", 1L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.countedQty").value(95))
            .andExpect(jsonPath("$.isCounted").value(true))
            .andExpect(jsonPath("$.countedByName").value("warehouse_staff"))
            .andExpect(jsonPath("$.remark").value("Counted by warehouse staff"));

        verify(stocktakeService).submitCount(eq(1L), eq(1L), any(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"stocktake:count"})
    @DisplayName("case-9")
    void testFinishCounting() throws Exception {
        // Given
        StocktakeTaskResponse response = StocktakeTaskResponse.builder()
            .id(1L)
            .taskNo("ST202601290001")
            .status("REVIEWING")
            .statusDescription("寰呭鏍?")
            .totalItems(100)
            .countedItems(100)
            .differenceItems(10)
            .progress(100)
            .build();

        when(stocktakeService.finishCounting(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks/{id}/finish", 1L))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("REVIEWING"))
            .andExpect(jsonPath("$.countedItems").value(100))
            .andExpect(jsonPath("$.differenceItems").value(10))
            .andExpect(jsonPath("$.progress").value(100));

        verify(stocktakeService).finishCounting(1L);
    }

    @Test
    @WithMockUser(username = "manager", authorities = {"stocktake:review"})
    @DisplayName("case-10")
    void testGetReviewItems() throws Exception {
        // Given
        List<StocktakeItemDetailResponse> responses = Arrays.asList(
            StocktakeItemDetailResponse.detailBuilder()
                .id(1L)
                .taskId(1L)
                .batchId(1L)
                .batchCode("BATCH001")
                .productId(1L)
                .productName("Product A")
                .locationCode("A-01-01")
                .snapshotQty(100)  // Now visible for review
                .countedQty(95)
                .differenceQty(-5)  // Loss
                .isCounted(true)
                .countedByName("warehouse_staff")
                .remark("Counted")
                .build(),
            StocktakeItemDetailResponse.detailBuilder()
                .id(2L)
                .taskId(1L)
                .batchId(2L)
                .batchCode("BATCH002")
                .productId(2L)
                .productName("Product B")
                .locationCode("A-01-02")
                .snapshotQty(50)  // Now visible for review
                .countedQty(55)
                .differenceQty(5)  // Gain
                .isCounted(true)
                .countedByName("warehouse_staff")
                .build()
        );

        when(stocktakeService.getStocktakeItemsForReview(1L)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/stocktake/tasks/{id}/review-items", 1L)
                .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].snapshotQty").value(100))  // Now visible
            .andExpect(jsonPath("$[0].countedQty").value(95))
            .andExpect(jsonPath("$[0].differenceQty").value(-5))
            .andExpect(jsonPath("$[1].snapshotQty").value(50))
            .andExpect(jsonPath("$[1].countedQty").value(55))
            .andExpect(jsonPath("$[1].differenceQty").value(5));

        verify(stocktakeService).getStocktakeItemsForReview(1L);
    }

    @Test
    @WithMockUser(username = "manager", authorities = {"stocktake:review"})
    @DisplayName("case-11")
    void testReviewStocktakeApprove() throws Exception {
        // Given
        ReviewStocktakeRequest request = ReviewStocktakeRequest.builder()
            .approved(true)
            .comment("Differences are acceptable")
            .build();

        StocktakeTaskResponse response = StocktakeTaskResponse.builder()
            .id(1L)
            .taskNo("ST202601290001")
            .status("COMPLETED")
            .statusDescription("宸插畬鎴?")
            .reviewedBy(2L)
            .reviewedByName("manager")
            .reviewedAt(LocalDateTime.now())
            .reviewComment("Differences are acceptable")
            .build();

        when(stocktakeService.reviewStocktake(eq(1L), any(), anyLong(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks/{id}/review", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.reviewedByName").value("manager"))
            .andExpect(jsonPath("$.reviewComment").value("Differences are acceptable"));

        verify(stocktakeService).reviewStocktake(eq(1L), any(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "manager", authorities = {"stocktake:review"})
    @DisplayName("case-12")
    void testReviewStocktakeReject() throws Exception {
        // Given
        ReviewStocktakeRequest request = ReviewStocktakeRequest.builder()
            .approved(false)
            .comment("Too many differences, recount required")
            .build();

        StocktakeTaskResponse response = StocktakeTaskResponse.builder()
            .id(1L)
            .taskNo("ST202601290001")
            .status("REJECTED")
            .statusDescription("宸叉嫆缁?")
            .reviewedBy(2L)
            .reviewedByName("manager")
            .reviewedAt(LocalDateTime.now())
            .reviewComment("Too many differences, recount required")
            .build();

        when(stocktakeService.reviewStocktake(eq(1L), any(), anyLong(), anyString()))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks/{id}/review", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewComment").value("Too many differences, recount required"));

        verify(stocktakeService).reviewStocktake(eq(1L), any(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"stocktake:view"})
    @DisplayName("case-13")
    void testCreateStocktakeTaskWithoutPermission() throws Exception {
        // Given
        CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
            .warehouseId(1L)
            .cycleType("MONTHLY")
            .build();

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(stocktakeService, never()).createCycleTask(any(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "warehouse_staff", authorities = {"stocktake:count"})
    @DisplayName("case-14")
    void testSubmitCountInvalidQuantity() throws Exception {
        // Given
        SubmitCountRequest request = SubmitCountRequest.builder()
            .countedQty(-10)  // Invalid negative quantity
            .build();

        // When & Then
        mockMvc.perform(post("/api/stocktake/tasks/{taskId}/items/{itemId}/count", 1L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verify(stocktakeService, never()).submitCount(anyLong(), anyLong(), any(), anyLong(), anyString());
    }
}
