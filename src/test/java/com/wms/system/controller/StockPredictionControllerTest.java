package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.ReorderSuggestion;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.StockPredictionService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("StockPredictionController Tests")
class StockPredictionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private StockPredictionService predictionService;

    private ReorderSuggestion buildSuggestion(Long productId, String urgency) {
        ReorderSuggestion s = ReorderSuggestion.builder()
                .productId(productId)
                .productName("Product " + productId)
                .currentStock(10)
                .minStock(50)
                .leadTime(7)
                .dailyAverageOutbound(5.0)
                .calculationPeriod(30)
                .suggestedReorderQuantity(85)
                .estimatedDaysUntilStockout(2.0)
                .estimatedCost(new BigDecimal("850.00"))
                .build();
        // Set urgency via reflection simulation: just return the suggestion built
        return s;
    }

    // ========== GET /api/predictions/reorder/{productId} ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getReorderSuggestion - returns suggestion for product")
    void testGetReorderSuggestion_Success() throws Exception {
        ReorderSuggestion suggestion = buildSuggestion(1L, "HIGH");
        suggestion.calculateUrgencyLevel();

        when(predictionService.getReorderSuggestion(1L, 30)).thenReturn(suggestion);

        mockMvc.perform(get("/api/predictions/reorder/1").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.currentStock").value(10))
                .andExpect(jsonPath("$.suggestedReorderQuantity").value(85));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getReorderSuggestion - uses default 30 days when no param")
    void testGetReorderSuggestion_DefaultDays() throws Exception {
        ReorderSuggestion suggestion = buildSuggestion(1L, "MEDIUM");
        suggestion.calculateUrgencyLevel();

        when(predictionService.getReorderSuggestion(1L, 30)).thenReturn(suggestion);

        mockMvc.perform(get("/api/predictions/reorder/1"))
                .andExpect(status().isOk());

        verify(predictionService).getReorderSuggestion(1L, 30);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getReorderSuggestion - returns 404 when product not found")
    void testGetReorderSuggestion_NotFound() throws Exception {
        when(predictionService.getReorderSuggestion(99L, 30)).thenThrow(
                new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("productId", 99L))
        );

        mockMvc.perform(get("/api/predictions/reorder/99").param("days", "30"))
                .andExpect(status().isNotFound());
    }

    // ========== GET /api/predictions/reorder ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getAllReorderSuggestions - returns suggestions with totalCount and totalCost")
    void testGetAllReorderSuggestions_Success() throws Exception {
        ReorderSuggestion s = buildSuggestion(1L, "LOW");
        s.calculateUrgencyLevel();

        when(predictionService.getAllReorderSuggestions(30)).thenReturn(List.of(s));
        when(predictionService.calculateTotalReorderCost(any())).thenReturn(new BigDecimal("850.00"));

        mockMvc.perform(get("/api/predictions/reorder").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.totalCost").value(850.00))
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getAllReorderSuggestions - returns empty list when no low stock")
    void testGetAllReorderSuggestions_Empty() throws Exception {
        when(predictionService.getAllReorderSuggestions(30)).thenReturn(List.of());
        when(predictionService.calculateTotalReorderCost(any())).thenReturn(BigDecimal.ZERO);

        mockMvc.perform(get("/api/predictions/reorder").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    // ========== GET /api/predictions/reorder/urgent ==========

    @Test
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("getUrgentReorderSuggestions - returns only high priority suggestions")
    void testGetUrgentReorderSuggestions_Success() throws Exception {
        when(predictionService.getHighPriorityReorderSuggestions(30)).thenReturn(List.of());
        when(predictionService.calculateTotalReorderCost(any())).thenReturn(BigDecimal.ZERO);

        mockMvc.perform(get("/api/predictions/reorder/urgent").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    // ========== GET /api/predictions/health ==========

    @Test
    @WithMockUser
    @DisplayName("predictions health endpoint returns UP status")
    void testPredictionsHealth() throws Exception {
        mockMvc.perform(get("/api/predictions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"));
    }
}
