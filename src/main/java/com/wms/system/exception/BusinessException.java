package com.wms.system.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Business Exception with Error Key System
 *
 * IMPORTANT (CLAUDE.md Compliance):
 * - Exception constructor ONLY accepts Error Key (e.g., "STOCK_INSUFFICIENT")
 * - NO hardcoded Chinese or English descriptions in Service layer
 * - Frontend translates error keys using i18n framework
 *
 * Architecture Design:
 * - Backend: Throws error key + parameters
 * - Frontend: Receives error key and displays localized message
 *
 * Example Usage:
 * <pre>
 * // Service layer (backend)
 * throw new BusinessException(
 *     ErrorKeys.STOCK_INSUFFICIENT,
 *     Map.of(
 *         "productSkuId", 123L,
 *         "currentStock", 50,
 *         "requestedQuantity", 100
 *     )
 * );
 *
 * // Frontend (i18n translation)
 * EN: "Insufficient stock: Current 50, Requested 100"
 * ZH: "库存不足：当前 50，请求 100"
 * </pre>
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Error Key System)
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * Error key for frontend i18n translation
     * Examples: "STOCK_INSUFFICIENT", "PRODUCT_SKU_NOT_FOUND", "LOCATION_NOT_FOUND"
     */
    private final String errorKey;

    /**
     * Dynamic parameters for error message interpolation
     * Examples: {"productSkuId": 123, "currentStock": 50, "requestedQuantity": 100}
     */
    private final Map<String, Object> params;

    /**
     * Constructor with error key only (no parameters)
     *
     * @param errorKey Error key for i18n translation (e.g., "PRODUCT_SKU_NOT_FOUND")
     */
    public BusinessException(String errorKey) {
        super(errorKey);  // Store error key in exception message for logging
        this.errorKey = errorKey;
        this.params = new HashMap<>();
    }

    /**
     * Constructor with error key and parameters
     *
     * @param errorKey Error key for i18n translation
     * @param params Dynamic parameters for message interpolation
     */
    public BusinessException(String errorKey, Map<String, Object> params) {
        super(errorKey);  // Store error key in exception message for logging
        this.errorKey = errorKey;
        this.params = params != null ? params : new HashMap<>();
    }

    /**
     * Get parameter value by key
     *
     * @param key Parameter key
     * @return Parameter value (or null if not found)
     */
    public Object getParam(String key) {
        return params.get(key);
    }

    /**
     * Add parameter dynamically
     *
     * @param key Parameter key
     * @param value Parameter value
     * @return this (for method chaining)
     */
    public BusinessException withParam(String key, Object value) {
        this.params.put(key, value);
        return this;
    }

    /**
     * Convert exception to JSON-friendly map for API response
     *
     * @return Map containing error key and parameters
     */
    public Map<String, Object> toResponseMap() {
        Map<String, Object> response = new HashMap<>();
        response.put("errorKey", errorKey);
        response.put("params", params);
        return response;
    }

    @Override
    public String toString() {
        return "BusinessException{" +
                "errorKey='" + errorKey + '\'' +
                ", params=" + params +
                '}';
    }
}
