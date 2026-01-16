package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified Error Response DTO
 *
 * CLAUDE.md Compliance:
 * - Returns error key (not hardcoded message)
 * - Includes dynamic parameters for frontend i18n translation
 * - Standardized error format across all APIs
 *
 * Usage Example:
 * <pre>
 * // Backend returns:
 * {
 *   "errorKey": "STOCK_INSUFFICIENT",
 *   "params": {
 *     "productId": 123,
 *     "currentStock": 50,
 *     "requestedQuantity": 100,
 *     "shortage": 50
 *   },
 *   "timestamp": "2025-01-11T10:30:00+00:00",
 *   "path": "/api/inventory/adjust"
 * }
 *
 * // Frontend translates:
 * EN: "Insufficient stock: Current 50, Requested 100, Shortage 50"
 * ZH: "库存不足：当前 50，请求 100，缺少 50"
 * </pre>
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Error Key System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * Error key for frontend i18n translation
     * Examples: "STOCK_INSUFFICIENT", "PRODUCT_NOT_FOUND"
     */
    private String errorKey;

    /**
     * Dynamic parameters for error message interpolation
     * Examples: {"productId": 123, "currentStock": 50}
     */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * Timestamp when error occurred (UTC, JVM timezone is UTC)
     * Format: ISO 8601 Local DateTime (e.g., "2025-01-11T10:30:00")
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * API request path where error occurred
     * Example: "/api/inventory/adjust"
     */
    private String path;

    /**
     * HTTP status code
     * Examples: 400 (Bad Request), 404 (Not Found), 409 (Conflict)
     */
    private Integer status;

    /**
     * Factory method: Create error response from error key only
     *
     * @param errorKey Error key
     * @return ErrorResponse
     */
    public static ErrorResponse of(String errorKey) {
        return ErrorResponse.builder()
            .errorKey(errorKey)
            .build();
    }

    /**
     * Factory method: Create error response from error key and params
     *
     * @param errorKey Error key
     * @param params Dynamic parameters
     * @return ErrorResponse
     */
    public static ErrorResponse of(String errorKey, Map<String, Object> params) {
        return ErrorResponse.builder()
            .errorKey(errorKey)
            .params(params)
            .build();
    }

    /**
     * Factory method: Create error response with full details
     *
     * @param errorKey Error key
     * @param params Dynamic parameters
     * @param path Request path
     * @param status HTTP status code
     * @return ErrorResponse
     */
    public static ErrorResponse of(String errorKey, Map<String, Object> params, String path, Integer status) {
        return ErrorResponse.builder()
            .errorKey(errorKey)
            .params(params)
            .path(path)
            .status(status)
            .build();
    }
}
