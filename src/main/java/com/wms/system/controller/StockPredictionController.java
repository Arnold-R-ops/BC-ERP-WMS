package com.wms.system.controller;

import com.wms.system.dto.ReorderSuggestion;
import com.wms.system.service.StockPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Stock Prediction Controller
 *
 * Provides RESTful APIs for stock prediction and reorder suggestions:
 * - Generate reorder suggestions based on historical data
 * - Calculate daily average outbound
 * - Predict stockout time
 * - Assess reorder urgency
 *
 * API Conventions:
 * - Base path: /api/predictions
 * - Timezone: All calculations use Europe/London timezone for date grouping
 * - Error handling: GlobalExceptionHandler converts exceptions to ErrorResponse
 * - Response format: JSON (DTO)
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Controller Layer + Timezone Conversion)
 */
@Slf4j
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class StockPredictionController {

    private final StockPredictionService predictionService;

    /**
     * Get reorder suggestion for a single product
     *
     * API Endpoint:
     * GET /api/predictions/reorder/{productSkuId}?days=30
     *
     * Query Parameters:
     * - days: Calculation period in days (default: 30)
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "productSkuId": 123,
     *   "barcode": "4901234567890",
     *   "productName": "Coca-Cola 500ml",
     *   "specification": "500ml",
     *   "unitPrice": 3.50,
     *   "supplier": "Coca-Cola Company",
     *   "currentStock": 50,
     *   "minStock": 100,
     *   "leadTime": 7,
     *   "dailyAverageOutbound": 30.5,
     *   "calculationPeriod": 30,
     *   "suggestedReorderQuantity": 314,
     *   "estimatedDaysUntilStockout": 1.64,
     *   "urgencyLevel": "CRITICAL",
     *   "estimatedCost": 1099.00,
     *   "needsReorder": true
     * }
     * </pre>
     *
     * Urgency Levels:
     * - CRITICAL: Current stock < Safety stock
     * - HIGH: Estimated stockout days < Lead time
     * - MEDIUM: Estimated stockout days ≈ Lead time (±3 days)
     * - LOW: Stock sufficient
     *
     * Timezone Conversion:
     * - Database stores UTC timestamps
     * - Calculations use Europe/London timezone for date grouping
     * - Ensures accurate daily statistics for UK business
     *
     * @param productSkuId ProductSku ID
     * @param days Calculation period in days (optional, default: 30)
     * @return ResponseEntity<ReorderSuggestion> Reorder suggestion
     * @throws com.wms.system.exception.BusinessException if product not found
     */
    @GetMapping("/reorder/{productSkuId}")
    public ResponseEntity<ReorderSuggestion> getReorderSuggestion(
        @PathVariable("productSkuId") Long productSkuId,
        @RequestParam(name = "days", defaultValue = "30") Integer days
    ) {
        log.info("API: Get reorder suggestion - productSkuId={}, calculationPeriod={} days",
            productSkuId, days);

        ReorderSuggestion suggestion = predictionService.getReorderSuggestion(productSkuId, days);

        log.info("API: Reorder suggestion generated - productSkuId={}, urgency={}, " +
                "suggestedReorder={}, estimatedStockout={} days",
            productSkuId,
            suggestion.getUrgencyLevel().getDescription(),
            suggestion.getSuggestedReorderQuantity(),
            String.format("%.1f", suggestion.getEstimatedDaysUntilStockout())
        );

        return ResponseEntity.ok(suggestion);
    }

    /**
     * Get all reorder suggestions (low stock products only)
     *
     * API Endpoint:
     * GET /api/predictions/reorder?days=30
     *
     * Query Parameters:
     * - days: Calculation period in days (optional, default: 30)
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "suggestions": [
     *     {
     *       "productSkuId": 123,
     *       "productName": "Coca-Cola 500ml",
     *       "urgencyLevel": "CRITICAL",
     *       "currentStock": 50,
     *       "suggestedReorderQuantity": 314,
     *       ...
     *     },
     *     {
     *       "productSkuId": 456,
     *       "productName": "Pepsi 500ml",
     *       "urgencyLevel": "HIGH",
     *       "currentStock": 80,
     *       "suggestedReorderQuantity": 250,
     *       ...
     *     }
     *   ],
     *   "totalCount": 2,
     *   "totalCost": 2349.00
     * }
     * </pre>
     *
     * Sorting:
     * - Primary: Urgency level (CRITICAL > HIGH > MEDIUM > LOW)
     * - Secondary: Estimated stockout days (ascending)
     *
     * @param days Calculation period in days (optional, default: 30)
     * @return ResponseEntity with suggestions and summary
     */
    @GetMapping("/reorder")
    public ResponseEntity<?> getAllReorderSuggestions(
        @RequestParam(name = "days", defaultValue = "30") Integer days
    ) {
        log.info("API: Get all reorder suggestions - calculationPeriod={} days", days);

        List<ReorderSuggestion> suggestions = predictionService.getAllReorderSuggestions(days);
        BigDecimal totalCost = predictionService.calculateTotalReorderCost(suggestions);

        log.info("API: Batch reorder suggestions generated - count={}, totalCost={}",
            suggestions.size(), totalCost);

        // Build response with suggestions and summary
        Map<String, Object> response = Map.of(
            "suggestions", suggestions,
            "totalCount", suggestions.size(),
            "totalCost", totalCost
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get high priority reorder suggestions (CRITICAL and HIGH only)
     *
     * API Endpoint:
     * GET /api/predictions/reorder/urgent?days=30
     *
     * Query Parameters:
     * - days: Calculation period in days (optional, default: 30)
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "suggestions": [
     *     {
     *       "productSkuId": 123,
     *       "urgencyLevel": "CRITICAL",
     *       ...
     *     }
     *   ],
     *   "totalCount": 1,
     *   "totalCost": 1099.00
     * }
     * </pre>
     *
     * @param days Calculation period in days (optional, default: 30)
     * @return ResponseEntity with urgent suggestions
     */
    @GetMapping("/reorder/urgent")
    public ResponseEntity<?> getUrgentReorderSuggestions(
        @RequestParam(name = "days", defaultValue = "30") Integer days
    ) {
        log.info("API: Get urgent reorder suggestions - calculationPeriod={} days", days);

        List<ReorderSuggestion> suggestions = predictionService.getHighPriorityReorderSuggestions(days);
        BigDecimal totalCost = predictionService.calculateTotalReorderCost(suggestions);

        log.info("API: Urgent reorder suggestions generated - count={}, totalCost={}",
            suggestions.size(), totalCost);

        Map<String, Object> response = Map.of(
            "suggestions", suggestions,
            "totalCount", suggestions.size(),
            "totalCost", totalCost
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Health check for prediction API
     *
     * API Endpoint:
     * GET /api/predictions/health
     *
     * @return ResponseEntity with status
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "StockPredictionService",
            "timezone", "Europe/London",
            "timestamp", java.time.LocalDateTime.now()
        ));
    }
}
