package com.wms.system.exception;

/**
 * Error Keys for Business Exceptions
 *
 * CLAUDE.md Compliance:
 * - All error keys are constants (no hardcoded strings in Service layer)
 * - Frontend uses these keys for i18n translation
 * - Format: DOMAIN_ERROR_TYPE (e.g., STOCK_INSUFFICIENT, PRODUCT_SKU_NOT_FOUND)
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

    // ========== Public SaaS Signup ==========
    public static final String SIGNUP_INVALID_REQUEST = "SIGNUP_INVALID_REQUEST";
    public static final String SIGNUP_REQUEST_NOT_FOUND = "SIGNUP_REQUEST_NOT_FOUND";
    public static final String SIGNUP_REQUEST_EXPIRED = "SIGNUP_REQUEST_EXPIRED";
    public static final String SIGNUP_EMAIL_VERIFICATION_REQUIRED = "SIGNUP_EMAIL_VERIFICATION_REQUIRED";
    public static final String SIGNUP_VERIFICATION_CODE_INVALID = "SIGNUP_VERIFICATION_CODE_INVALID";
    public static final String SIGNUP_VERIFICATION_CODE_EXPIRED = "SIGNUP_VERIFICATION_CODE_EXPIRED";
    public static final String SIGNUP_VERIFICATION_ATTEMPTS_EXCEEDED = "SIGNUP_VERIFICATION_ATTEMPTS_EXCEEDED";
    public static final String SIGNUP_RESEND_TOO_SOON = "SIGNUP_RESEND_TOO_SOON";
    public static final String SIGNUP_SEND_LIMIT_EXCEEDED = "SIGNUP_SEND_LIMIT_EXCEEDED";
    public static final String SIGNUP_EMAIL_UNAVAILABLE = "SIGNUP_EMAIL_UNAVAILABLE";
    public static final String SIGNUP_SLUG_UNAVAILABLE = "SIGNUP_SLUG_UNAVAILABLE";
    public static final String SIGNUP_IDEMPOTENCY_CONFLICT = "SIGNUP_IDEMPOTENCY_CONFLICT";
    public static final String SIGNUP_PROVISIONING_FAILED = "SIGNUP_PROVISIONING_FAILED";
    public static final String SESSION_HANDOFF_INVALID = "SESSION_HANDOFF_INVALID";

    // ========== Platform Administration ==========
    public static final String PLATFORM_ADMIN_NOT_FOUND = "PLATFORM_ADMIN_NOT_FOUND";
    public static final String PLATFORM_ADMIN_LAST_SUPER_ADMIN = "PLATFORM_ADMIN_LAST_SUPER_ADMIN";
    public static final String PLATFORM_ADMIN_SUPER_ADMIN_LIMIT = "PLATFORM_ADMIN_SUPER_ADMIN_LIMIT";
    public static final String PLATFORM_ADMIN_STATUS_CONFLICT = "PLATFORM_ADMIN_STATUS_CONFLICT";
    public static final String PLATFORM_ADMIN_CONCURRENT_MODIFICATION = "PLATFORM_ADMIN_CONCURRENT_MODIFICATION";
    public static final String PLATFORM_ADMIN_IDEMPOTENCY_CONFLICT = "PLATFORM_ADMIN_IDEMPOTENCY_CONFLICT";
    public static final String PLATFORM_ADMIN_ENABLE_REVIEW_REQUIRED = "PLATFORM_ADMIN_ENABLE_REVIEW_REQUIRED";
    public static final String PLATFORM_ADMIN_DISABLED = "PLATFORM_ADMIN_DISABLED";
    public static final String PLATFORM_ADMIN_MFA_NOT_ENROLLED = "PLATFORM_ADMIN_MFA_NOT_ENROLLED";
    public static final String PLATFORM_ADMIN_EMAIL_EXISTS = "PLATFORM_ADMIN_EMAIL_EXISTS";
    public static final String PLATFORM_ADMIN_INVITATION_ACTIVE = "PLATFORM_ADMIN_INVITATION_ACTIVE";
    public static final String PLATFORM_ADMIN_INVITATION_NOT_FOUND = "PLATFORM_ADMIN_INVITATION_NOT_FOUND";
    public static final String PLATFORM_ADMIN_INVITATION_TERMINAL = "PLATFORM_ADMIN_INVITATION_TERMINAL";
    public static final String PLATFORM_ADMIN_ROLE_CHANGE_FORBIDDEN = "PLATFORM_ADMIN_ROLE_CHANGE_FORBIDDEN";
    public static final String PLATFORM_ADMIN_ACTIVE_GRANTS_EXIST = "PLATFORM_ADMIN_ACTIVE_GRANTS_EXIST";

    // ========== ProductSku Related Errors ==========

    /**
     * ProductSku not found by ID or barcode
     *
     * Parameters:
     * - productSkuId (Long): ProductSku ID
     * - barcode (String): ProductSku barcode (optional)
     */
    public static final String PRODUCT_SKU_NOT_FOUND = "PRODUCT_SKU_NOT_FOUND";

    /**
     * ProductSku already exists (duplicate barcode)
     *
     * Parameters:
     * - barcode (String): Duplicate barcode
     */
    public static final String PRODUCT_SKU_ALREADY_EXISTS = "PRODUCT_SKU_ALREADY_EXISTS";

    /**
     * SPU not found by ID
     *
     * Parameters:
     * - productId (Long): SPU ID
     */
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String PRODUCT_ALREADY_EXISTS = "PRODUCT_ALREADY_EXISTS";
    public static final String PRODUCT_CATEGORY_INVALID = "PRODUCT_CATEGORY_INVALID";
    public static final String PRODUCT_DISABLED = "PRODUCT_DISABLED";
    public static final String PRODUCT_SKU_DISABLED = "PRODUCT_SKU_DISABLED";

    // ========== Category Related Errors ==========

    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String CATEGORY_ALREADY_EXISTS = "CATEGORY_ALREADY_EXISTS";
    public static final String CATEGORY_DEPTH_EXCEEDED = "CATEGORY_DEPTH_EXCEEDED";
    public static final String CATEGORY_PARENT_DISABLED = "CATEGORY_PARENT_DISABLED";
    public static final String CATEGORY_CYCLE_DETECTED = "CATEGORY_CYCLE_DETECTED";
    public static final String CATEGORY_HAS_CHILDREN = "CATEGORY_HAS_CHILDREN";
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";

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

    /**
     * Location is already occupied by another product or batch.
     */
    public static final String LOCATION_BATCH_MIXING_FORBIDDEN = "LOCATION_BATCH_MIXING_FORBIDDEN";

    /**
     * Location-visual mode cannot resolve a unique batch from location + product.
     */
    public static final String LOCATION_VISUAL_BATCH_AMBIGUOUS = "LOCATION_VISUAL_BATCH_AMBIGUOUS";

    // ========== Warehouse Related Errors (Phase 3.4) ==========

    /**
     * Warehouse not found by ID or code
     *
     * Parameters:
     * - warehouseId (Long): Warehouse ID (optional)
     * - code (String): Warehouse code (optional)
     */
    public static final String WAREHOUSE_NOT_FOUND = "WAREHOUSE_NOT_FOUND";

    /**
     * Warehouse already exists (duplicate code)
     *
     * Parameters:
     * - code (String): Duplicate warehouse code
     */
    public static final String WAREHOUSE_ALREADY_EXISTS = "WAREHOUSE_ALREADY_EXISTS";

    /**
     * Warehouse is inactive and cannot accept new locations or operations.
     */
    public static final String WAREHOUSE_INACTIVE = "WAREHOUSE_INACTIVE";

    /** WAREHOUSE_STAFF must have at least one active warehouse assignment. */
    public static final String WAREHOUSE_SCOPE_REQUIRED = "WAREHOUSE_SCOPE_REQUIRED";

    /** Activated WAREHOUSE_STAFF attempted to access a task outside its warehouses. */
    public static final String WAREHOUSE_SCOPE_DENIED = "WAREHOUSE_SCOPE_DENIED";

    // ========== Supplier Related Errors ==========

    public static final String SUPPLIER_NOT_FOUND = "SUPPLIER_NOT_FOUND";
    public static final String SUPPLIER_ALREADY_EXISTS = "SUPPLIER_ALREADY_EXISTS";
    public static final String SUPPLIER_NOT_ACTIVE = "SUPPLIER_NOT_ACTIVE";
    public static final String SUPPLIER_IN_USE = "SUPPLIER_IN_USE";

    // ========== Stock/Inventory Related Errors ==========

    /**
     * Insufficient stock for outbound operation
     *
     * Parameters:
     * - productSkuId (Long): ProductSku ID
     * - productName (String): ProductSku name
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
     * - productSkuId (Long): ProductSku ID
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
     * - productSkuId (Long): ProductSku ID
     * - locationId (Long): Location ID
     */
    public static final String INVENTORY_NOT_FOUND = "INVENTORY_NOT_FOUND";

    /**
     * Inventory reservation not found by ID.
     */
    public static final String INVENTORY_RESERVATION_NOT_FOUND = "INVENTORY_RESERVATION_NOT_FOUND";

    /**
     * Reservation is not in a status that allows the requested operation.
     */
    public static final String INVENTORY_RESERVATION_INVALID_STATUS = "INVENTORY_RESERVATION_INVALID_STATUS";

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

    /**
     * Old password verification failed during self password change (P0.5)
     *
     * Parameters:
     * - userId (Long): User ID
     */
    public static final String PASSWORD_INCORRECT = "PASSWORD_INCORRECT";

    /**
     * New password does not satisfy the strength policy (P0.5)
     *
     * Parameters:
     * - policy (String): Human-readable policy description
     */
    public static final String PASSWORD_TOO_WEAK = "PASSWORD_TOO_WEAK";

    /**
     * New password is identical to the current password (P0.5)
     *
     * Parameters:
     * - userId (Long): User ID
     */
    public static final String PASSWORD_SAME_AS_OLD = "PASSWORD_SAME_AS_OLD";

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

    public static final String ROLE_COPY_SOURCE_DISABLED = "ROLE_COPY_SOURCE_DISABLED";
    public static final String ROLE_COPY_SOURCE_NOT_ALLOWED = "ROLE_COPY_SOURCE_NOT_ALLOWED";
    public static final String ROLE_COPY_CRITICAL_PERMISSION = "ROLE_COPY_CRITICAL_PERMISSION";
    public static final String ROLE_COPY_RISK_CONFIRMATION_REQUIRED = "ROLE_COPY_RISK_CONFIRMATION_REQUIRED";
    public static final String ROLE_COPY_SNAPSHOT_STALE = "ROLE_COPY_SNAPSHOT_STALE";
    public static final String ROLE_COPY_TARGET_EXISTS = "ROLE_COPY_TARGET_EXISTS";
    public static final String ROLE_PACKAGE_NOT_CUSTOM = "ROLE_PACKAGE_NOT_CUSTOM";
    public static final String ROLE_PACKAGE_NOT_DRAFT = "ROLE_PACKAGE_NOT_DRAFT";
    public static final String ROLE_PACKAGE_NOT_PENDING_REVIEW = "ROLE_PACKAGE_NOT_PENDING_REVIEW";
    public static final String ROLE_PACKAGE_NOT_APPROVED = "ROLE_PACKAGE_NOT_APPROVED";
    public static final String ROLE_PACKAGE_ACTIVE_IMMUTABLE = "ROLE_PACKAGE_ACTIVE_IMMUTABLE";
    public static final String ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE = "ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE";
    public static final String ROLE_PACKAGE_INHERITANCE_EDIT_UNSUPPORTED = "ROLE_PACKAGE_INHERITANCE_EDIT_UNSUPPORTED";
    public static final String ROLE_PACKAGE_HIGH_RISK_REASON_REQUIRED = "ROLE_PACKAGE_HIGH_RISK_REASON_REQUIRED";
    public static final String ROLE_PACKAGE_SECOND_REVIEWER_REQUIRED = "ROLE_PACKAGE_SECOND_REVIEWER_REQUIRED";
    public static final String ROLE_PACKAGE_REJECTION_COMMENT_REQUIRED = "ROLE_PACKAGE_REJECTION_COMMENT_REQUIRED";
    public static final String ROLE_PACKAGE_CONFIRMATION_REQUIRED = "ROLE_PACKAGE_CONFIRMATION_REQUIRED";
    public static final String ROLE_PACKAGE_NOT_ASSIGNABLE_TO_USER = "ROLE_PACKAGE_NOT_ASSIGNABLE_TO_USER";

    public static final String PERMISSION_REQUEST_NOT_FOUND = "PERMISSION_REQUEST_NOT_FOUND";
    public static final String PERMISSION_REQUEST_ALREADY_PENDING = "PERMISSION_REQUEST_ALREADY_PENDING";
    public static final String PERMISSION_REQUEST_INVALID_STATUS = "PERMISSION_REQUEST_INVALID_STATUS";
    public static final String PERMISSION_REQUEST_ROLE_NOT_ALLOWED = "PERMISSION_REQUEST_ROLE_NOT_ALLOWED";
    public static final String PERMISSION_REQUEST_ROLE_ALREADY_ASSIGNED = "PERMISSION_REQUEST_ROLE_ALREADY_ASSIGNED";
    public static final String PERMISSION_REQUEST_TARGET_INACTIVE = "PERMISSION_REQUEST_TARGET_INACTIVE";
    public static final String PERMISSION_REQUEST_PROTECTED_TARGET = "PERMISSION_REQUEST_PROTECTED_TARGET";
    public static final String PERMISSION_REQUEST_SELF_GRANT_FORBIDDEN = "PERMISSION_REQUEST_SELF_GRANT_FORBIDDEN";
    public static final String PERMISSION_REQUEST_SECOND_REVIEWER_REQUIRED = "PERMISSION_REQUEST_SECOND_REVIEWER_REQUIRED";
    public static final String PERMISSION_REQUEST_REJECTION_COMMENT_REQUIRED = "PERMISSION_REQUEST_REJECTION_COMMENT_REQUIRED";
    public static final String PERMISSION_REQUEST_REVOCATION_COMMENT_REQUIRED = "PERMISSION_REQUEST_REVOCATION_COMMENT_REQUIRED";
    public static final String PERMISSION_REQUEST_WAREHOUSE_NOT_ALLOWED = "PERMISSION_REQUEST_WAREHOUSE_NOT_ALLOWED";
    public static final String PERMISSION_REQUEST_SNAPSHOT_STALE = "PERMISSION_REQUEST_SNAPSHOT_STALE";
    public static final String APPROVAL_TEMPLATE_NOT_FOUND = "APPROVAL_TEMPLATE_NOT_FOUND";

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

    public static final String AUTH_SESSION_INVALIDATED = "AUTH_SESSION_INVALIDATED";

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
     * Backward-compatible alias for legacy service references.
     */
    public static final String PURCHASE_ORDER_INVALID_STATUS = PO_INVALID_STATUS;

    /** The submitted ORDERING edit was based on a stale purchase-order version. */
    public static final String PO_EDIT_CONFLICT = "PO_EDIT_CONFLICT";

    /** A rolled-back line with batch history cannot be replaced or removed. */
    public static final String PO_ITEM_HISTORY_LOCKED = "PO_ITEM_HISTORY_LOCKED";

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
     * - productSkuId (Long): ProductSku ID
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
     * - productSkuId (Long): ProductSku ID
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
     * - productSkuId (Long): ProductSku ID
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

    /**
     * Insufficient stock for operation (alias for STOCK_INSUFFICIENT)
     *
     * Parameters:
     * - productSkuId (Long): ProductSku ID
     * - requestedQuantity (Integer): Requested quantity
     * - availableQuantity (Integer): Available quantity
     */
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

    /**
     * Expired batch found during FIFO outbound operation
     *
     * Parameters:
     * - batchCode (String): Expired batch code
     * - expiryDate (String): Expiry date
     * - productSkuId (Long): ProductSku ID
     */
    public static final String EXPIRED_BATCH_FOUND = "EXPIRED_BATCH_FOUND";

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
     * - resourceType (String): Resource type (e.g., "ProductSku", "Location")
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

    /**
     * Excel template generation failed
     *
     * Parameters:
     * - reason (String): Failure reason
     */
    public static final String EXCEL_TEMPLATE_GENERATION_FAILED = "EXCEL_TEMPLATE_GENERATION_FAILED";

    // ========== Customer Related Errors (V3.7) ==========

    /**
     * Customer not found by ID or code
     *
     * Parameters:
     * - customerId (Long): Customer ID (optional)
     * - customerCode (String): Customer code (optional)
     */
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";

    /**
     * Customer already exists (duplicate code)
     *
     * Parameters:
     * - customerCode (String): Duplicate customer code
     */
    public static final String CUSTOMER_ALREADY_EXISTS = "CUSTOMER_ALREADY_EXISTS";

    /**
     * Customer is inactive
     *
     * Parameters:
     * - customerId (Long): Customer ID
     * - customerCode (String): Customer code
     */
    public static final String CUSTOMER_INACTIVE = "CUSTOMER_INACTIVE";

    /** Channel-managed consumer profiles cannot be changed through client master-data APIs. */
    public static final String CUSTOMER_CONSUMER_READ_ONLY = "CUSTOMER_CONSUMER_READ_ONLY";

    /** Manual sales orders may only reference managed CLIENT customers. */
    public static final String MANUAL_ORDER_REQUIRES_CLIENT = "MANUAL_ORDER_REQUIRES_CLIENT";

    // ========== Sales Order Related Errors (V3.7) ==========

    /**
     * Sales order not found by ID or order number
     *
     * Parameters:
     * - salesOrderId (Long): Sales order ID (optional)
     * - orderNo (String): Order number (optional)
     */
    public static final String SALES_ORDER_NOT_FOUND = "SALES_ORDER_NOT_FOUND";

    /**
     * Sales order cannot be modified (invalid status)
     *
     * Parameters:
     * - salesOrderId (Long): Sales order ID
     * - currentStatus (String): Current order status
     * - allowedStatuses (String): Allowed statuses for modification (e.g., "DRAFT, PENDING_APPROVAL")
     */
    public static final String SALES_ORDER_CANNOT_MODIFY = "SALES_ORDER_CANNOT_MODIFY";

    /**
     * Sales order cannot be cancelled (invalid status)
     *
     * Parameters:
     * - salesOrderId (Long): Sales order ID
     * - currentStatus (String): Current order status
     * - reason (String): Reason for denial
     */
    public static final String SALES_ORDER_CANNOT_CANCEL = "SALES_ORDER_CANNOT_CANCEL";

    /**
     * Sales order already exists (duplicate order number)
     *
     * Parameters:
     * - orderNo (String): Duplicate order number
     */
    public static final String SALES_ORDER_ALREADY_EXISTS = "SALES_ORDER_ALREADY_EXISTS";

    /**
     * Sales order status is invalid for the requested operation
     *
     * Parameters:
     * - salesOrderId (Long): Sales order ID
     * - currentStatus (String): Current order status
     * - requiredStatus (String): Required status for operation (optional)
     */
    public static final String SALES_ORDER_INVALID_STATUS = "SALES_ORDER_INVALID_STATUS";

    /** Historical test archive preconditions failed; no data was changed. */
    public static final String HISTORICAL_ARCHIVE_NOT_ELIGIBLE = "HISTORICAL_ARCHIVE_NOT_ELIGIBLE";

    /** Preview fingerprint no longer matches the locked execution snapshot. */
    public static final String HISTORICAL_ARCHIVE_SNAPSHOT_STALE = "HISTORICAL_ARCHIVE_SNAPSHOT_STALE";

    /** Typed order number does not match the target order. */
    public static final String HISTORICAL_ARCHIVE_CONFIRMATION_MISMATCH = "HISTORICAL_ARCHIVE_CONFIRMATION_MISMATCH";

    /**
     * Sales order item not found by ID
     *
     * Parameters:
     * - itemId (Long): Sales order item ID
     * - salesOrderId (Long): Parent sales order ID (optional)
     */
    public static final String SALES_ORDER_ITEM_NOT_FOUND = "SALES_ORDER_ITEM_NOT_FOUND";

    /**
     * Unit price is below minimum sales price
     *
     * Parameters:
     * - productSkuId (Long): ProductSku ID
     * - productName (String): ProductSku name
     * - unitPrice (BigDecimal): Requested unit price
     * - minSalesPrice (BigDecimal): Minimum sales price
     */
    public static final String SALES_PRICE_BELOW_MINIMUM = "SALES_PRICE_BELOW_MINIMUM";

    /**
     * Total amount exceeds approval threshold
     *
     * Parameters:
     * - totalAmount (BigDecimal): Total order amount
     * - approvalThreshold (BigDecimal): Approval threshold
     */
    public static final String SALES_AMOUNT_EXCEEDS_THRESHOLD = "SALES_AMOUNT_EXCEEDS_THRESHOLD";

    // ========== Outbound Task Related Errors (V3.7) ==========

    /**
     * Outbound task not found by ID
     *
     * Parameters:
     * - taskId (Long): Outbound task ID
     */
    public static final String OUTBOUND_TASK_NOT_FOUND = "OUTBOUND_TASK_NOT_FOUND";

    /**
     * Outbound task status is invalid for the requested operation
     *
     * Parameters:
     * - taskId (Long): Outbound task ID
     * - currentStatus (String): Current task status
     * - requiredStatus (String): Required status for operation (optional)
     */
    public static final String OUTBOUND_TASK_INVALID_STATUS = "OUTBOUND_TASK_INVALID_STATUS";

    /**
     * Outbound task already completed
     *
     * Parameters:
     * - taskId (Long): Outbound task ID
     */
    public static final String OUTBOUND_TASK_ALREADY_COMPLETED = "OUTBOUND_TASK_ALREADY_COMPLETED";

    /**
     * Actual quantity exceeds planned quantity
     *
     * Parameters:
     * - taskId (Long): Outbound task ID
     * - planQty (Integer): Planned quantity
     * - actualQty (Integer): Actual quantity
     */
    public static final String OUTBOUND_ACTUAL_QTY_EXCEEDS_PLAN = "OUTBOUND_ACTUAL_QTY_EXCEEDS_PLAN";

    // ========== System Config Related Errors (V3.7) ==========

    /**
     * System configuration not found by key
     *
     * Parameters:
     * - configKey (String): Configuration key
     */
    public static final String SYSTEM_CONFIG_NOT_FOUND = "SYSTEM_CONFIG_NOT_FOUND";

    /**
     * Invalid configuration value type
     *
     * Parameters:
     * - configKey (String): Configuration key
     * - expectedType (String): Expected type (DECIMAL, INTEGER, STRING, BOOLEAN)
     * - actualValue (String): Actual value
     */
    public static final String SYSTEM_CONFIG_INVALID_TYPE = "SYSTEM_CONFIG_INVALID_TYPE";

    // ========== Stocktake Related Errors (V3.8) ==========

    /**
     * Stocktake task not found by ID or task number
     *
     * Parameters:
     * - taskId (Long): Stocktake task ID (optional)
     * - taskNo (String): Task number (optional)
     */
    public static final String STOCKTAKE_TASK_NOT_FOUND = "STOCKTAKE_TASK_NOT_FOUND";

    /**
     * Stocktake item not found by ID
     *
     * Parameters:
     * - itemId (Long): Stocktake item ID
     * - taskId (Long): Parent task ID (optional)
     */
    public static final String STOCKTAKE_ITEM_NOT_FOUND = "STOCKTAKE_ITEM_NOT_FOUND";

    /**
     * Stocktake task status is invalid for the requested operation
     *
     * Parameters:
     * - taskId (Long): Stocktake task ID
     * - currentStatus (String): Current task status
     * - requiredStatus (String): Required status for operation (optional)
     */
    public static final String STOCKTAKE_TASK_INVALID_STATUS = "STOCKTAKE_TASK_INVALID_STATUS";

    /**
     * Backward-compatible alias for legacy service references.
     */
    public static final String STOCKTAKE_INVALID_STATUS = STOCKTAKE_TASK_INVALID_STATUS;

    /**
     * Stocktake task cannot start (invalid status)
     *
     * Parameters:
     * - taskId (Long): Stocktake task ID
     * - currentStatus (String): Current task status
     */
    public static final String STOCKTAKE_TASK_CANNOT_START = "STOCKTAKE_TASK_CANNOT_START";

    /**
     * Stocktake task cannot count (invalid status)
     *
     * Parameters:
     * - taskId (Long): Stocktake task ID
     * - currentStatus (String): Current task status
     */
    public static final String STOCKTAKE_TASK_CANNOT_COUNT = "STOCKTAKE_TASK_CANNOT_COUNT";

    /**
     * Stocktake task cannot review (invalid status)
     *
     * Parameters:
     * - taskId (Long): Stocktake task ID
     * - currentStatus (String): Current task status
     */
    public static final String STOCKTAKE_TASK_CANNOT_REVIEW = "STOCKTAKE_TASK_CANNOT_REVIEW";

    // ========== Shopify Integration Related Errors (V3.9) ==========

    /**
     * Shopify API error (generic API call failure)
     *
     * Parameters:
     * - storeUrl (String): Shopify store URL
     * - endpoint (String): API endpoint
     * - statusCode (Integer): HTTP status code (optional)
     * - error (String): Error message
     */
    public static final String SHOPIFY_API_ERROR = "SHOPIFY_API_ERROR";

    /**
     * Shopify authentication failed (401/403)
     *
     * Parameters:
     * - storeUrl (String): Shopify store URL
     * - statusCode (Integer): HTTP status code
     */
    public static final String SHOPIFY_AUTH_FAILED = "SHOPIFY_AUTH_FAILED";

    /**
     * Shopify rate limit exceeded (429)
     *
     * Parameters:
     * - storeUrl (String): Shopify store URL
     * - retryAfter (Integer): Retry after seconds (optional)
     */
    public static final String SHOPIFY_RATE_LIMIT = "SHOPIFY_RATE_LIMIT";

    /**
     * SKU not found in WMS during Shopify order sync
     *
     * Parameters:
     * - sku (String): SKU from Shopify
     * - externalOrderNo (String): Shopify order number
     * - externalOrderId (String): Shopify order ID
     */
    public static final String SHOPIFY_SKU_NOT_FOUND = "SHOPIFY_SKU_NOT_FOUND";

    /**
     * Shopify order already synced (duplicate)
     *
     * Parameters:
     * - externalOrderId (String): Shopify order ID
     * - externalOrderNo (String): Shopify order number
     * - salesOrderId (Long): Existing WMS sales order ID
     */
    public static final String SHOPIFY_ORDER_ALREADY_SYNCED = "SHOPIFY_ORDER_ALREADY_SYNCED";

    /**
     * Channel order blocked: unknown SKU(s) queued for human mapping (P1-B2)
     *
     * The order is NOT lost - it stays as a FAILED raw event and will be
     * retried automatically once the pending SKUs are resolved.
     *
     * Parameters:
     * - externalOrderNo (String): Channel order number
     * - pendingSkus (String): Comma-joined unknown SKUs
     */
    public static final String SKU_MAPPING_PENDING = "SKU_MAPPING_PENDING";

    /**
     * Integration configuration not found
     *
     * Parameters:
     * - platform (String): Platform type (e.g., SHOPIFY)
     * - configId (Long): Configuration ID (optional)
     */
    public static final String INTEGRATION_CONFIG_NOT_FOUND = "INTEGRATION_CONFIG_NOT_FOUND";

    // ========== V4.4 Idempotency Related Errors ==========

    /**
     * Duplicate order number (idempotency violation)
     *
     * V4.4 架构加固：物理级接口幂等性
     * - 触发场景：Shopify 网络抖动重发、前端连击
     * - 数据库层面：唯一索引约束违反
     * - HTTP 状态码：409 Conflict
     *
     * Parameters:
     * - orderNumber (String): 重复的订单号
     * - orderType (String): 订单类型（SALES/PURCHASE/INBOUND）
     * - existingOrderId (Long): 已存在的订单ID（可选）
     */
    public static final String ORDER_NUMBER_DUPLICATE = "ORDER_NUMBER_DUPLICATE";
}
