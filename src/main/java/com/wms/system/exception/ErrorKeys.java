package com.wms.system.exception;

/**
 * Error Keys for Business Exceptions
 *
 * CLAUDE.md Compliance:
 * - All error keys are constants (no hardcoded strings in Service layer)
 * - Frontend uses these keys for i18n translation
 * - Format: DOMAIN_ERROR_TYPE (e.g., STOCK_INSUFFICIENT, PRODUCT_NOT_FOUND)
 *
 * Naming Convention:
 * - DOMAIN: Business domain (STOCK, PRODUCT, LOCATION, etc.)
 * - ERROR_TYPE: Error type (NOT_FOUND, INSUFFICIENT, CONFLICT, etc.)
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Error Key System)
 */
public final class ErrorKeys {

    // Prevent instantiation
    private ErrorKeys() {
        throw new UnsupportedOperationException("ErrorKeys is a utility class and cannot be instantiated");
    }

    // ========== Product Related Errors ==========

    /**
     * Product not found by ID or barcode
     *
     * Parameters:
     * - productId (Long): Product ID
     * - barcode (String): Product barcode (optional)
     */
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";

    /**
     * Product already exists (duplicate barcode)
     *
     * Parameters:
     * - barcode (String): Duplicate barcode
     */
    public static final String PRODUCT_ALREADY_EXISTS = "PRODUCT_ALREADY_EXISTS";

    // ========== Location Related Errors ==========

    /**
     * Location not found by ID or location code
     *
     * Parameters:
     * - locationId (Long): Location ID
     * - locationCode (String): Location code (optional)
     */
    public static final String LOCATION_NOT_FOUND = "LOCATION_NOT_FOUND";

    /**
     * Location already exists (duplicate location code)
     *
     * Parameters:
     * - locationCode (String): Duplicate location code
     */
    public static final String LOCATION_ALREADY_EXISTS = "LOCATION_ALREADY_EXISTS";

    // ========== Stock/Inventory Related Errors ==========

    /**
     * Insufficient stock for outbound operation
     *
     * Parameters:
     * - productId (Long): Product ID
     * - productName (String): Product name
     * - locationId (Long): Location ID
     * - locationCode (String): Location code
     * - currentStock (Integer): Current stock quantity
     * - requestedQuantity (Integer): Requested outbound quantity
     * - shortage (Integer): Shortage amount (requestedQuantity - currentStock)
     */
    public static final String STOCK_INSUFFICIENT = "STOCK_INSUFFICIENT";

    /**
     * Stock concurrency conflict (optimistic lock failure after retries)
     *
     * Parameters:
     * - productId (Long): Product ID
     * - locationId (Long): Location ID
     * - operationType (String): Operation type (IN/OUT/ADJUST)
     * - retryAttempts (Integer): Number of retry attempts (default: 3)
     */
    public static final String STOCK_CONCURRENCY_CONFLICT = "STOCK_CONCURRENCY_CONFLICT";

    /**
     * Invalid stock quantity (negative or zero)
     *
     * Parameters:
     * - quantity (Integer): Invalid quantity value
     * - operationType (String): Operation type
     */
    public static final String STOCK_INVALID_QUANTITY = "STOCK_INVALID_QUANTITY";

    /**
     * Inventory record not found
     *
     * Parameters:
     * - productId (Long): Product ID
     * - locationId (Long): Location ID
     */
    public static final String INVENTORY_NOT_FOUND = "INVENTORY_NOT_FOUND";

    // ========== Transaction Related Errors ==========

    /**
     * Stock transaction not found by ID
     *
     * Parameters:
     * - transactionId (Long): Transaction ID
     */
    public static final String TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";

    /**
     * Invalid transaction type
     *
     * Parameters:
     * - transactionType (String): Invalid transaction type
     */
    public static final String TRANSACTION_INVALID_TYPE = "TRANSACTION_INVALID_TYPE";

    /**
     * Source order not found
     *
     * Parameters:
     * - sourceOrderId (String): Source order ID
     * - sourceType (String): Source type (PURCHASE_IN, SALE_OUT, etc.)
     */
    public static final String SOURCE_ORDER_NOT_FOUND = "SOURCE_ORDER_NOT_FOUND";

    // ========== User/Authentication Related Errors ==========

    /**
     * User not found by ID or username
     *
     * Parameters:
     * - userId (Long): User ID (optional)
     * - username (String): Username (optional)
     */
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    /**
     * User already exists (duplicate username)
     *
     * Parameters:
     * - username (String): Duplicate username
     */
    public static final String USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";

    /**
     * User account is disabled
     *
     * Parameters:
     * - username (String): Username
     * - userId (Long): User ID (optional)
     */
    public static final String USER_ACCOUNT_DISABLED = "USER_ACCOUNT_DISABLED";

    /**
     * User has no roles assigned (Multi-Role System v3.3+)
     *
     * Parameters:
     * - userId (Long): User ID
     * - username (String): Username
     */
    public static final String USER_NO_ROLES = "USER_NO_ROLES";

    /**
     * User has no active roles (all roles are disabled) (Multi-Role System v3.3+)
     *
     * Parameters:
     * - userId (Long): User ID
     * - username (String): Username
     * - totalRoles (Integer): Total number of assigned roles
     */
    public static final String USER_NO_ACTIVE_ROLES = "USER_NO_ACTIVE_ROLES";

    /**
     * Unauthorized operation (insufficient permissions)
     *
     * Parameters:
     * - operation (String): Operation name
     * - requiredRole (String): Required role
     * - currentRole (String): Current user's role
     */
    public static final String USER_UNAUTHORIZED = "USER_UNAUTHORIZED";

    // ========== Role Related Errors (Multi-Role System v3.3+) ==========

    /**
     * Role not found by ID or role code
     *
     * Parameters:
     * - roleId (Long): Role ID (optional)
     * - roleCode (String): Role code (optional)
     */
    public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";

    /**
     * Role is not assigned to the user
     *
     * Parameters:
     * - roleId (Long): Role ID
     * - roleCode (String): Role code
     * - userId (Long): User ID
     * - username (String): Username
     */
    public static final String ROLE_NOT_ASSIGNED = "ROLE_NOT_ASSIGNED";

    /**
     * Role is disabled (inactive)
     *
     * Parameters:
     * - roleId (Long): Role ID
     * - roleCode (String): Role code
     * - roleName (String): Role display name
     */
    public static final String ROLE_DISABLED = "ROLE_DISABLED";

    /**
     * Role switch operation failed
     *
     * Parameters:
     * - targetRoleCode (String): Target role code
     * - username (String): Username
     * - reason (String): Failure reason
     */
    public static final String ROLE_SWITCH_FAILED = "ROLE_SWITCH_FAILED";

    // ========== Authentication/JWT Related Errors ==========

    /**
     * Invalid credentials (wrong username or password)
     *
     * Parameters:
     * - username (String): Username (optional, for security reasons may be omitted)
     */
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";

    /**
     * JWT Token is missing from request header
     *
     * Parameters:
     * - header (String): Expected header name (e.g., "Authorization")
     */
    public static final String AUTH_TOKEN_MISSING = "AUTH_TOKEN_MISSING";

    /**
     * JWT Token is invalid (malformed, signature mismatch, etc.)
     *
     * Parameters:
     * - reason (String): Reason for invalidity (e.g., "Signature mismatch", "Malformed token")
     */
    public static final String AUTH_TOKEN_INVALID = "AUTH_TOKEN_INVALID";

    /**
     * JWT Token has expired
     *
     * Parameters:
     * - expiredAt (String): Token expiration timestamp (ISO 8601)
     * - currentTime (String): Current timestamp (ISO 8601)
     */
    public static final String AUTH_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";

    /**
     * Authentication failed (generic)
     *
     * Parameters:
     * - reason (String): Failure reason
     */
    public static final String AUTH_FAILED = "AUTH_FAILED";

    /**
     * Access denied (authenticated but insufficient permissions)
     *
     * Parameters:
     * - resource (String): Resource being accessed
     * - requiredPermission (String): Required permission/role
     */
    public static final String AUTH_ACCESS_DENIED = "AUTH_ACCESS_DENIED";

    // ========== Purchase Order Related Errors ==========

    /**
     * Purchase order not found by ID or PO number
     *
     * Parameters:
     * - purchaseOrderId (Long): Purchase order ID (optional)
     * - poNumber (String): Purchase order number (optional)
     */
    public static final String PURCHASE_ORDER_NOT_FOUND = "PURCHASE_ORDER_NOT_FOUND";

    /**
     * Purchase order status is invalid for the requested operation
     *
     * Parameters:
     * - purchaseOrderId (Long): Purchase order ID
     * - currentStatus (String): Current order status
     * - requiredStatus (String): Required status for operation (optional)
     */
    public static final String PO_INVALID_STATUS = "PO_INVALID_STATUS";

    /**
     * Purchase order is already completed, cannot be modified
     *
     * Parameters:
     * - purchaseOrderId (Long): Purchase order ID
     * - poNumber (String): Purchase order number
     */
    public static final String PO_ALREADY_COMPLETED = "PO_ALREADY_COMPLETED";

    /**
     * Purchase order rollback not allowed (only IN_TRANSIT → ORDERING allowed)
     *
     * Parameters:
     * - purchaseOrderId (Long): Purchase order ID
     * - currentStatus (String): Current order status
     * - requestedStatus (String): Requested status to rollback to
     */
    public static final String PO_ROLLBACK_NOT_ALLOWED = "PO_ROLLBACK_NOT_ALLOWED";

    /**
     * Purchase order item not found by ID
     *
     * Parameters:
     * - itemId (Long): Purchase order item ID
     * - purchaseOrderId (Long): Parent purchase order ID (optional)
     */
    public static final String PO_ITEM_NOT_FOUND = "PO_ITEM_NOT_FOUND";

    /**
     * Expiry date is required for batch generation (Stage 2)
     *
     * Parameters:
     * - itemId (Long): Purchase order item ID
     * - productId (Long): Product ID
     */
    public static final String PO_EXPIRY_DATE_REQUIRED = "PO_EXPIRY_DATE_REQUIRED";

    // ========== Batch Related Errors ==========

    /**
     * Batch not found by ID or batch code
     *
     * Parameters:
     * - batchId (Long): Batch ID (optional)
     * - batchCode (String): Batch code (optional)
     */
    public static final String BATCH_NOT_FOUND = "BATCH_NOT_FOUND";

    /**
     * Batch code generation failed after retry attempts
     *
     * Parameters:
     * - productId (Long): Product ID
     * - itemId (Long): Purchase order item ID
     * - attempts (Integer): Number of retry attempts (default: 3)
     */
    public static final String BATCH_CODE_GENERATION_FAILED = "BATCH_CODE_GENERATION_FAILED";

    /**
     * Batch is inactive (marked as invalid)
     *
     * Parameters:
     * - batchId (Long): Batch ID
     * - batchCode (String): Batch code
     * - reason (String): Reason for inactivity (optional)
     */
    public static final String BATCH_INACTIVE = "BATCH_INACTIVE";

    /**
     * Batch stock insufficient for FIFO outbound operation
     *
     * Parameters:
     * - productId (Long): Product ID
     * - requestedQuantity (Integer): Requested outbound quantity
     * - availableQuantity (Integer): Available quantity across all active batches
     * - shortage (Integer): Shortage amount
     */
    public static final String BATCH_STOCK_INSUFFICIENT = "BATCH_STOCK_INSUFFICIENT";

    /**
     * Batch has expired (past expiry date)
     *
     * Parameters:
     * - batchId (Long): Batch ID
     * - batchCode (String): Batch code
     * - expiryDate (String): Expiry date (ISO 8601 format)
     */
    public static final String BATCH_EXPIRED = "BATCH_EXPIRED";

    // ========== Validation Related Errors ==========

    /**
     * Invalid input parameters
     *
     * Parameters:
     * - field (String): Field name
     * - value (Object): Invalid value
     * - constraint (String): Validation constraint
     */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /**
     * Required parameter missing
     *
     * Parameters:
     * - parameter (String): Parameter name
     */
    public static final String PARAMETER_REQUIRED = "PARAMETER_REQUIRED";

    // ========== General Errors ==========

    /**
     * Resource not found (generic)
     *
     * Parameters:
     * - resourceType (String): Resource type (e.g., "Product", "Location")
     * - resourceId (String): Resource identifier
     */
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

    /**
     * Internal server error (unexpected exception)
     *
     * Parameters:
     * - message (String): Error message
     * - exceptionType (String): Exception class name
     */
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    /**
     * Operation not allowed (business rule violation)
     *
     * Parameters:
     * - operation (String): Operation name
     * - reason (String): Reason for denial
     */
    public static final String OPERATION_NOT_ALLOWED = "OPERATION_NOT_ALLOWED";

    // ========== File Upload/Import Related Errors ==========

    /**
     * Invalid file format (e.g., expected .xlsx but got .xls)
     *
     * Parameters:
     * - filename (String): Uploaded filename
     * - expectedFormat (String): Expected file format (e.g., ".xlsx")
     * - actualFormat (String): Actual file format (optional)
     */
    public static final String INVALID_FILE_FORMAT = "INVALID_FILE_FORMAT";

    /**
     * Invalid Excel data (parsing error or validation failure)
     *
     * Parameters:
     * - rowIndex (Integer): Row number (1-indexed for user-facing)
     * - columnIndex (Integer): Column index (optional)
     * - error (String): Error message
     */
    public static final String INVALID_EXCEL_DATA = "INVALID_EXCEL_DATA";

    /**
     * File read error (I/O exception)
     *
     * Parameters:
     * - filename (String): Filename
     * - error (String): Error message
     */
    public static final String FILE_READ_ERROR = "FILE_READ_ERROR";
}
