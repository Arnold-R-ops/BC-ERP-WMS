package com.wms.system.controller;

import com.wms.system.dto.ErrorResponse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;

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
 *     "productSkuId": 123,
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
     * - PRODUCT_SKU_NOT_FOUND, LOCATION_NOT_FOUND, etc. → 404 Not Found
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
     * Handle Access Denied (Spring Security)
     *
     * Converts AccessDeniedException to 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.AUTH_ACCESS_DENIED)
            .params(Map.of(
                "message", ex.getMessage() != null ? ex.getMessage() : "Access denied",
                "exceptionType", ex.getClass().getSimpleName()
            ))
            .path(request.getRequestURI())
            .status(HttpStatus.FORBIDDEN.value())
            .build();

        log.warn("Access denied: path={}, message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle Request Parameter Type Mismatch
     *
     * Example: /api/warehouses/{id} with non-numeric id.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("parameter", ex.getName());
        params.put("value", ex.getValue() != null ? ex.getValue().toString() : "null");
        if (ex.getRequiredType() != null) {
            params.put("expectedType", ex.getRequiredType().getSimpleName());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.VALIDATION_FAILED)
            .params(params)
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        log.warn("Type mismatch: param={}, value={}, expected={}, path={}",
            ex.getName(), ex.getValue(), ex.getRequiredType(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle Missing Request Parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
        MissingServletRequestParameterException ex,
        HttpServletRequest request
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.PARAMETER_REQUIRED)
            .params(Map.of(
                "parameter", ex.getParameterName(),
                "expectedType", ex.getParameterType()
            ))
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        log.warn("Missing parameter: param={}, path={}", ex.getParameterName(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle Validation Errors for @RequestParam/@PathVariable
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
        ConstraintViolationException ex,
        HttpServletRequest request
    ) {
        Map<String, Object> validationErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            validationErrors.put(fieldName, violation.getMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.VALIDATION_FAILED)
            .params(validationErrors)
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        log.warn("Constraint violation: errors={}, path={}", validationErrors, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
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
     *     "productSkuId": "ProductSku ID is required",
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
     * Handle DataIntegrityViolationException (V4.4 幂等性防御)
     *
     * 拦截数据库唯一约束违反异常，用于实现物理级接口幂等性。
     *
     * 触发场景：
     * - Shopify 网络抖动导致订单重发
     * - 前端业务员疯狂连击提交按钮
     * - 并发请求导致的订单号重复
     *
     * 处理策略：
     * - 检测异常消息中是否包含唯一约束关键字（unique, duplicate）
     * - 提取违反的字段名（order_no, po_number 等）
     * - 返回 HTTP 409 Conflict 和友好提示
     *
     * @param ex DataIntegrityViolationException
     * @param request HTTP request
     * @return ResponseEntity with ErrorResponse
     * @since V4.4 (Idempotency Defense)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.VALIDATION_FAILED)
            .params(Map.of(
                "message", ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : "Request body is not readable",
                "exceptionType", ex.getClass().getSimpleName()
            ))
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        log.warn("Request body is not readable: path={}, message={}",
            request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(
        MultipartException ex,
        HttpServletRequest request
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.VALIDATION_FAILED)
            .params(Map.of(
                "message", ex.getMessage() != null ? ex.getMessage() : "Invalid multipart request",
                "exceptionType", ex.getClass().getSimpleName()
            ))
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        log.warn("Multipart request error: path={}, message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
        DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String rootCauseMessage = ex.getRootCause() != null ? ex.getRootCause().getMessage().toLowerCase() : "";
        String fullMessage = message + " " + rootCauseMessage;

        if (fullMessage.contains("uq_sales_orders_company_channel_external_order")) {
            log.warn("External order duplicate detected: {}", ex.getMessage());

            ErrorResponse errorResponse = ErrorResponse.builder()
                .errorKey(ErrorKeys.SHOPIFY_ORDER_ALREADY_SYNCED)
                .params(Map.of("message", "该渠道订单已同步，请勿重复提交"))
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .build();

            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }

        // 检测是否为唯一约束违反
        if (fullMessage.contains("unique")
            || fullMessage.contains("duplicate")
            || fullMessage.contains("already exists")) {
            log.warn("Unique constraint violation detected: {}", ex.getMessage());

            // 提取订单类型和订单号
            String orderType = "UNKNOWN";
            String fieldName = "order_number";

            if (fullMessage.contains("sales_orders") || fullMessage.contains("idx_sales_order_no")) {
                orderType = "SALES";
                fieldName = "order_no";
            } else if (fullMessage.contains("purchase_order") || fullMessage.contains("idx_po_number")) {
                orderType = "PURCHASE";
                fieldName = "po_number";
            } else if (fullMessage.contains("inbound_orders") || fullMessage.contains("idx_inbound_order_no")) {
                orderType = "INBOUND";
                fieldName = "order_no";
            }

            Map<String, Object> params = new HashMap<>();
            params.put("orderType", orderType);
            params.put("fieldName", fieldName);
            params.put("message", "该订单号已存在，请勿重复提交");

            ErrorResponse errorResponse = ErrorResponse.builder()
                .errorKey(ErrorKeys.ORDER_NUMBER_DUPLICATE)
                .params(params)
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .build();

            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }

        // 其他数据完整性异常，返回 400 Bad Request
        log.error("Data integrity violation: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.VALIDATION_FAILED)
            .params(Map.of("message", "数据完整性约束违反"))
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
        OptimisticLockingFailureException ex,
        HttpServletRequest request
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorKey(ErrorKeys.STOCK_CONCURRENCY_CONFLICT)
            .params(Map.of(
                "message", "Concurrent stock update conflict, please retry",
                "exceptionType", ex.getClass().getSimpleName()
            ))
            .path(request.getRequestURI())
            .status(HttpStatus.CONFLICT.value())
            .build();

        log.warn("Optimistic locking conflict: path={}, type={}, message={}",
            request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
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
            case ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                 ErrorKeys.PRODUCT_NOT_FOUND,
                 ErrorKeys.CATEGORY_NOT_FOUND,
                 ErrorKeys.LOCATION_NOT_FOUND,
                 ErrorKeys.INVENTORY_NOT_FOUND,
                 ErrorKeys.INVENTORY_RESERVATION_NOT_FOUND,
                 ErrorKeys.TRANSACTION_NOT_FOUND,
                 ErrorKeys.USER_NOT_FOUND,
                 ErrorKeys.SOURCE_ORDER_NOT_FOUND,
                 ErrorKeys.RESOURCE_NOT_FOUND,
                 ErrorKeys.ROLE_NOT_FOUND,  // v3.3 Multi-Role System
                 ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                 ErrorKeys.PO_ITEM_NOT_FOUND,
                 ErrorKeys.BATCH_NOT_FOUND,
                 ErrorKeys.WAREHOUSE_NOT_FOUND,  // Phase 3.4
                 ErrorKeys.SUPPLIER_NOT_FOUND,
                 ErrorKeys.INTEGRATION_CONFIG_NOT_FOUND,  // V3.9 Shopify Integration
                 ErrorKeys.SHOPIFY_SKU_NOT_FOUND,  // V3.9 Shopify Integration
                 "INBOUND_ORDER_NOT_FOUND",  // Phase 3.5
                 "ITEM_NOT_FOUND" -> HttpStatus.NOT_FOUND;  // Phase 3.5

            // 400 Bad Request
            case ErrorKeys.STOCK_INSUFFICIENT,
                 ErrorKeys.CATEGORY_DEPTH_EXCEEDED,
                 ErrorKeys.CATEGORY_PARENT_DISABLED,
                 ErrorKeys.CATEGORY_CYCLE_DETECTED,
                 ErrorKeys.PRODUCT_CATEGORY_INVALID,
                 ErrorKeys.PRODUCT_DISABLED,
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
                 ErrorKeys.INSUFFICIENT_STOCK,
                 ErrorKeys.EXPIRED_BATCH_FOUND,
                 ErrorKeys.INVALID_FILE_FORMAT,
                 ErrorKeys.INVALID_EXCEL_DATA,
                 ErrorKeys.FILE_READ_ERROR,
                 ErrorKeys.SHOPIFY_API_ERROR,  // V3.9 Shopify Integration
                 ErrorKeys.SHOPIFY_RATE_LIMIT,  // V3.9 Shopify Integration
                 ErrorKeys.PASSWORD_INCORRECT,  // P0.5 Password Management
                 ErrorKeys.PASSWORD_TOO_WEAK,  // P0.5 Password Management
                 ErrorKeys.PASSWORD_SAME_AS_OLD,  // P0.5 Password Management
                 "INVALID_STATUS_FOR_APPROVAL",  // Phase 3.5
                 "INVALID_STATUS_FOR_CONFIRMATION",  // Phase 3.5
                 "INVALID_STATUS_FOR_RECEIVING",  // Phase 3.5
                 "ACTUAL_QTY_EXCEEDS_CONFIRMED_QTY" -> HttpStatus.BAD_REQUEST;  // Phase 3.5

            // 401 Unauthorized
            case ErrorKeys.AUTH_INVALID_CREDENTIALS,
                 ErrorKeys.AUTH_FAILED,
                 ErrorKeys.SHOPIFY_AUTH_FAILED -> HttpStatus.UNAUTHORIZED;  // V3.9 Shopify Integration

            // 403 Forbidden
            case ErrorKeys.USER_UNAUTHORIZED,
                 ErrorKeys.USER_ACCOUNT_DISABLED,
                 ErrorKeys.OPERATION_NOT_ALLOWED,
                 ErrorKeys.USER_NO_ROLES,  // v3.3 Multi-Role System
                 ErrorKeys.USER_NO_ACTIVE_ROLES,  // v3.3 Multi-Role System
                 ErrorKeys.ROLE_NOT_ASSIGNED,  // v3.3 Multi-Role System
                 ErrorKeys.ROLE_DISABLED,  // v3.3 Multi-Role System
                 ErrorKeys.AUTH_TOKEN_MISSING,
                 ErrorKeys.AUTH_TOKEN_INVALID,
                 ErrorKeys.AUTH_TOKEN_EXPIRED,
                 ErrorKeys.AUTH_ACCESS_DENIED,
                 ErrorKeys.PO_ALREADY_COMPLETED -> HttpStatus.FORBIDDEN;

            // 409 Conflict
            case ErrorKeys.STOCK_CONCURRENCY_CONFLICT,
                 ErrorKeys.CATEGORY_ALREADY_EXISTS,
                 ErrorKeys.CATEGORY_HAS_CHILDREN,
                 ErrorKeys.CATEGORY_IN_USE,
                 ErrorKeys.PRODUCT_SKU_ALREADY_EXISTS,
                 ErrorKeys.PRODUCT_ALREADY_EXISTS,
                 ErrorKeys.LOCATION_ALREADY_EXISTS,
                 ErrorKeys.LOCATION_BATCH_MIXING_FORBIDDEN,
                 ErrorKeys.LOCATION_VISUAL_BATCH_AMBIGUOUS,
                 ErrorKeys.USER_ALREADY_EXISTS,  // v3.3 Multi-Role System
                 ErrorKeys.BATCH_CODE_GENERATION_FAILED,
                 ErrorKeys.WAREHOUSE_ALREADY_EXISTS,  // Phase 3.4
                 ErrorKeys.SUPPLIER_ALREADY_EXISTS,
                 ErrorKeys.SUPPLIER_NOT_ACTIVE,
                 ErrorKeys.SUPPLIER_IN_USE,
                 ErrorKeys.CUSTOMER_CONSUMER_READ_ONLY,
                 ErrorKeys.MANUAL_ORDER_REQUIRES_CLIENT,
                 ErrorKeys.SHOPIFY_ORDER_ALREADY_SYNCED,  // V3.9 Shopify Integration
                 ErrorKeys.SKU_MAPPING_PENDING,  // P1-B2 Channel SKU Mapping
                 ErrorKeys.SALES_ORDER_INVALID_STATUS,
                 ErrorKeys.OUTBOUND_TASK_ALREADY_COMPLETED,
                 ErrorKeys.INVENTORY_RESERVATION_INVALID_STATUS,
                 ErrorKeys.STOCKTAKE_TASK_INVALID_STATUS,
                 ErrorKeys.STOCKTAKE_TASK_CANNOT_START,
                 ErrorKeys.STOCKTAKE_TASK_CANNOT_COUNT,
                 ErrorKeys.STOCKTAKE_TASK_CANNOT_REVIEW,
                 ErrorKeys.ORDER_NUMBER_DUPLICATE -> HttpStatus.CONFLICT;  // V4.4 Idempotency Defense

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
