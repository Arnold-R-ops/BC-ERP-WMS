package com.wms.system.controller;

import com.wms.system.dto.ErrorResponse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler
 *
 * Handles all exceptions thrown by Controllers and converts them to unified JSON format.
 *
 * Key Features:
 * 1. Catches BusinessException and returns error key + params
 * 2. Handles validation errors (MethodArgumentNotValidException)
 * 3. Maps error keys to appropriate HTTP status codes
 * 4. Logs all exceptions for monitoring and debugging
 *
 * Response Format:
 * <pre>
 * {
 *   "errorKey": "STOCK_INSUFFICIENT",
 *   "params": {
 *     "productId": 123,
 *     "currentStock": 50,
 *     "requestedQuantity": 100,
 *     "shortage": 50
 *   },
 *   "timestamp": "2025-01-11T10:30:00+00:00",
 *   "path": "/api/inventory/adjust",
 *   "status": 400
 * }
 * </pre>
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Error Key System)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle BusinessException (Error Key System)
     *
     * Maps error keys to HTTP status codes:
     * - PRODUCT_NOT_FOUND, LOCATION_NOT_FOUND, etc. → 404 Not Found
     * - STOCK_INSUFFICIENT, VALIDATION_FAILED, etc. → 400 Bad Request
     * - STOCK_CONCURRENCY_CONFLICT → 409 Conflict
     * - Others → 500 Internal Server Error
     *
     * @param ex BusinessException
     * @param request HTTP request
     * @return ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request
    ) {
        // Map error key to HTTP status
        HttpStatus status = mapErrorKeyToHttpStatus(ex.getErrorKey());

        // Build error response
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ex.getErrorKey())
            .params(ex.getParams())
            .path(request.getRequestURI())
            .status(status.value())
            .build();

        // Log error for monitoring
        log.error("Business exception: errorKey={}, params={}, path={}",
            ex.getErrorKey(), ex.getParams(), request.getRequestURI(), ex);

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle Validation Errors (MethodArgumentNotValidException)
     *
     * Thrown when @Valid annotation fails (e.g., @NotNull, @Min constraints).
     *
     * Response example:
     * <pre>
     * {
     *   "errorKey": "VALIDATION_FAILED",
     *   "params": {
     *     "productId": "Product ID is required",
     *     "quantity": "Quantity must be greater than 0"
     *   },
     *   "timestamp": "...",
     *   "path": "/api/inventory/adjust",
     *   "status": 400
     * }
     * </pre>
     *
     * @param ex MethodArgumentNotValidException
     * @param request HTTP request
     * @return ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        // Extract field validation errors
        Map<String, Object> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        // Build error response
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.VALIDATION_FAILED)
            .params(validationErrors)
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        // Log validation errors
        log.warn("Validation failed: errors={}, path={}",
            validationErrors, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle Unsupported Media Type (HttpMediaTypeNotSupportedException)
     *
     * Thrown when request Content-Type is not supported by the endpoint.
     * For example, sending text/plain to an endpoint that requires application/json.
     *
     * Response example:
     * <pre>
     * {
     *   "errorKey": "VALIDATION_FAILED",
     *   "params": {
     *     "contentType": "text/plain;charset=UTF-8",
     *     "supportedTypes": ["application/json", "application/*+json"]
     *   },
     *   "timestamp": "...",
     *   "path": "/api/auth/login",
     *   "status": 415
     * }
     * </pre>
     *
     * @param ex HttpMediaTypeNotSupportedException
     * @param request HTTP request
     * @return ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
        HttpMediaTypeNotSupportedException ex,
        HttpServletRequest request
    ) {
        // Extract supported media types
        String supportedTypes = ex.getSupportedMediaTypes().stream()
            .map(Object::toString)
            .reduce((a, b) -> a + ", " + b)
            .orElse("application/json");

        // Build error response
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.VALIDATION_FAILED)
            .params(Map.of(
                "contentType", ex.getContentType() != null ? ex.getContentType().toString() : "unknown",
                "supportedTypes", supportedTypes
            ))
            .path(request.getRequestURI())
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
            .build();

        // Log error
        log.warn("Unsupported media type: contentType={}, supportedTypes={}, path={}",
            ex.getContentType(), supportedTypes, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse);
    }

    /**
     * Handle Generic Exceptions (Unexpected Errors)
     *
     * Catches all other exceptions not handled by specific handlers.
     *
     * Response example:
     * <pre>
     * {
     *   "errorKey": "INTERNAL_SERVER_ERROR",
     *   "params": {
     *     "message": "Unexpected error occurred",
     *     "exceptionType": "NullPointerException"
     *   },
     *   "timestamp": "...",
     *   "path": "/api/inventory/adjust",
     *   "status": 500
     * }
     * </pre>
     *
     * @param ex Exception
     * @param request HTTP request
     * @return ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        // Build error response
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.INTERNAL_SERVER_ERROR)
            .params(Map.of(
                "message", ex.getMessage() != null ? ex.getMessage() : "Unexpected error occurred",
                "exceptionType", ex.getClass().getSimpleName()
            ))
            .path(request.getRequestURI())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .build();

        // Log full exception stack trace
        log.error("Unexpected exception: type={}, message={}, path={}",
            ex.getClass().getSimpleName(), ex.getMessage(), request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Map error key to HTTP status code
     *
     * @param errorKey Error key
     * @return HttpStatus
     */
    private HttpStatus mapErrorKeyToHttpStatus(String errorKey) {
        return switch (errorKey) {
            // 404 Not Found
            case ErrorKeys.PRODUCT_NOT_FOUND,
                 ErrorKeys.LOCATION_NOT_FOUND,
                 ErrorKeys.INVENTORY_NOT_FOUND,
                 ErrorKeys.TRANSACTION_NOT_FOUND,
                 ErrorKeys.USER_NOT_FOUND,
                 ErrorKeys.SOURCE_ORDER_NOT_FOUND,
                 ErrorKeys.RESOURCE_NOT_FOUND,
                 ErrorKeys.ROLE_NOT_FOUND,  // v3.3 Multi-Role System
                 ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                 ErrorKeys.PO_ITEM_NOT_FOUND,
                 ErrorKeys.BATCH_NOT_FOUND,
                 ErrorKeys.WAREHOUSE_NOT_FOUND,  // Phase 3.4
                 "INBOUND_ORDER_NOT_FOUND",  // Phase 3.5
                 "ITEM_NOT_FOUND" -> HttpStatus.NOT_FOUND;  // Phase 3.5

            // 400 Bad Request
            case ErrorKeys.STOCK_INSUFFICIENT,
                 ErrorKeys.STOCK_INVALID_QUANTITY,
                 ErrorKeys.VALIDATION_FAILED,
                 ErrorKeys.PARAMETER_REQUIRED,
                 ErrorKeys.TRANSACTION_INVALID_TYPE,
                 ErrorKeys.PO_INVALID_STATUS,
                 ErrorKeys.PO_ROLLBACK_NOT_ALLOWED,
                 ErrorKeys.PO_EXPIRY_DATE_REQUIRED,
                 ErrorKeys.BATCH_INACTIVE,
                 ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                 ErrorKeys.BATCH_EXPIRED,
                 ErrorKeys.INVALID_FILE_FORMAT,
                 ErrorKeys.INVALID_EXCEL_DATA,
                 ErrorKeys.FILE_READ_ERROR,
                 "SUPPLIER_NOT_FOUND",  // Phase 3.5
                 "INVALID_STATUS_FOR_APPROVAL",  // Phase 3.5
                 "INVALID_STATUS_FOR_CONFIRMATION",  // Phase 3.5
                 "INVALID_STATUS_FOR_RECEIVING",  // Phase 3.5
                 "ACTUAL_QTY_EXCEEDS_CONFIRMED_QTY" -> HttpStatus.BAD_REQUEST;  // Phase 3.5

            // 401 Unauthorized
            case ErrorKeys.AUTH_INVALID_CREDENTIALS,
                 ErrorKeys.AUTH_TOKEN_MISSING,
                 ErrorKeys.AUTH_TOKEN_INVALID,
                 ErrorKeys.AUTH_TOKEN_EXPIRED,
                 ErrorKeys.AUTH_FAILED,
                 ErrorKeys.AUTH_ACCESS_DENIED -> HttpStatus.UNAUTHORIZED;

            // 403 Forbidden
            case ErrorKeys.USER_UNAUTHORIZED,
                 ErrorKeys.USER_ACCOUNT_DISABLED,
                 ErrorKeys.OPERATION_NOT_ALLOWED,
                 ErrorKeys.USER_NO_ROLES,  // v3.3 Multi-Role System
                 ErrorKeys.USER_NO_ACTIVE_ROLES,  // v3.3 Multi-Role System
                 ErrorKeys.ROLE_NOT_ASSIGNED,  // v3.3 Multi-Role System
                 ErrorKeys.ROLE_DISABLED,  // v3.3 Multi-Role System
                 ErrorKeys.PO_ALREADY_COMPLETED -> HttpStatus.FORBIDDEN;

            // 409 Conflict
            case ErrorKeys.STOCK_CONCURRENCY_CONFLICT,
                 ErrorKeys.PRODUCT_ALREADY_EXISTS,
                 ErrorKeys.LOCATION_ALREADY_EXISTS,
                 ErrorKeys.USER_ALREADY_EXISTS,  // v3.3 Multi-Role System
                 ErrorKeys.BATCH_CODE_GENERATION_FAILED,
                 ErrorKeys.WAREHOUSE_ALREADY_EXISTS -> HttpStatus.CONFLICT;  // Phase 3.4

            // 500 Internal Server Error
            case ErrorKeys.ROLE_SWITCH_FAILED,  // v3.3 Multi-Role System
                 ErrorKeys.INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;

            // Default (fallback)
            default -> {
                log.warn("Unknown error key: {}, defaulting to 500 Internal Server Error", errorKey);
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
        };
    }
}
