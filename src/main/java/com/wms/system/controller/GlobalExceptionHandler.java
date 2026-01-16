package com.wms.system.controller;

import com.wms.system.dto.ErrorResponse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
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
                 ErrorKeys.RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;

            // 400 Bad Request
            case ErrorKeys.STOCK_INSUFFICIENT,
                 ErrorKeys.STOCK_INVALID_QUANTITY,
                 ErrorKeys.VALIDATION_FAILED,
                 ErrorKeys.PARAMETER_REQUIRED,
                 ErrorKeys.TRANSACTION_INVALID_TYPE -> HttpStatus.BAD_REQUEST;

            // 409 Conflict
            case ErrorKeys.STOCK_CONCURRENCY_CONFLICT,
                 ErrorKeys.PRODUCT_ALREADY_EXISTS,
                 ErrorKeys.LOCATION_ALREADY_EXISTS -> HttpStatus.CONFLICT;

            // 403 Forbidden
            case ErrorKeys.USER_UNAUTHORIZED,
                 ErrorKeys.OPERATION_NOT_ALLOWED -> HttpStatus.FORBIDDEN;

            // 500 Internal Server Error (default)
            default -> {
                log.warn("Unknown error key: {}, defaulting to 500 Internal Server Error", errorKey);
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
        };
    }
}
