# WMS System - International Architecture Upgrade v2.0

## 📋 Executive Summary

This document describes the international architecture upgrade applied to the WMS system to ensure compliance with cross-region deployment standards and CLAUDE.md requirements.

**Upgrade Date**: 2025-01-11
**Version**: 2.0 (International Standard)
**Status**: ✅ COMPLETED

---

## 🎯 Core Architecture Decisions

### 1. Backend Timezone: **UTC (Coordinated Universal Time)**

**Rationale**: International systems must store absolute time without timezone ambiguity.

**Implementation**:
- ✅ JVM default timezone forced to UTC on application startup
- ✅ Database stores all timestamps in UTC
- ✅ Frontend converts UTC to user's local timezone for display

**Benefits**:
- Multi-region deployments (China, UK, US, etc.) work seamlessly
- No daylight saving time (DST) ambiguity
- Audit logs show consistent absolute time across all locations

---

### 2. Time Data Type: **OffsetDateTime** (ISO 8601 Compliant)

**CLAUDE.md Requirement**:
```
❌ Do NOT use: java.util.Date (deprecated, timezone issues)
❌ Do NOT use: java.time.LocalDateTime (no timezone information)
✅ MUST use: java.time.OffsetDateTime or java.time.ZonedDateTime
```

**Implementation**:
All entity timestamp fields use `OffsetDateTime`:

```java
// BaseEntity.java
@CreatedDate
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;  // Stores: 2025-01-11T10:30:00+00:00

@LastModifiedDate
@Column(name = "updated_at", nullable = false)
private OffsetDateTime updatedAt;  // Stores: 2025-01-11T15:45:30+00:00
```

**Database Mapping**:
PostgreSQL stores as `TIMESTAMP WITH TIME ZONE`:
```sql
CREATE TABLE inventory (
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,  -- Stores UTC + offset
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

**API Response Example**:
```json
{
  "productId": 123,
  "currentStock": 100,
  "createdAt": "2025-01-11T10:30:00+00:00",  // ISO 8601 format with timezone
  "updatedAt": "2025-01-11T15:45:30+00:00"
}
```

---

### 3. Code Comments: **English Only**

**CLAUDE.md Requirement**:
```
Code comments: English only. Ensure UK technical teams can understand the logic.
```

**Implementation**:
All Java code comments have been translated to English:

**Before (Chinese)**:
```java
/**
 * 库存管理服务
 * 负责处理入库、出库、库存调整等核心业务逻辑
 */
public class InventoryService {
    // ...
}
```

**After (English)**:
```java
/**
 * Inventory Management Service
 *
 * Handles core business logic for inbound, outbound, and stock adjustments.
 */
public class InventoryService {
    // ...
}
```

---

## ✅ Completed Modifications

### File: `WmsSystemApplication.java`
**Changes**:
1. ✅ Timezone set to UTC (not Europe/London)
2. ✅ Added `@EnableRetry` for optimistic lock handling
3. ✅ Fixed `System.out.println` location (moved to `@PostConstruct`)
4. ✅ All comments translated to English

**Code**:
```java
@PostConstruct
public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    System.out.println("=== WMS System Started Successfully ===");
    System.out.println("=== Backend Timezone: UTC (International Standard) ===");
}
```

---

### File: `BaseEntity.java`
**Changes**:
1. ✅ Changed `LocalDateTime` → `OffsetDateTime`
2. ✅ All comments translated to English
3. ✅ Added architecture decision notes

**Code**:
```java
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;  // UTC with offset: 2025-01-11T10:30:00+00:00

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;  // UTC with offset: 2025-01-11T15:45:30+00:00
}
```

---

### File: `StockTransactionRepository.java`
**Changes**:
1. ✅ All method parameters: `LocalDateTime` → `OffsetDateTime`
2. ✅ Updated import statement

**Affected Methods** (7 methods):
```java
List<StockTransaction> findMovementHistory(
    @Param("productId") Long productId,
    @Param("startDate") OffsetDateTime startDate,  // Changed from LocalDateTime
    @Param("endDate") OffsetDateTime endDate        // Changed from LocalDateTime
);

Integer sumOutboundQuantity(..., OffsetDateTime startDate, OffsetDateTime endDate);
Integer sumInboundQuantity(..., OffsetDateTime startDate, OffsetDateTime endDate);
List<StockTransaction> findByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);
long countByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);
Integer calculateTurnoverRate(..., OffsetDateTime startDate, OffsetDateTime endDate);
List<StockTransaction> findByTypeAndDateRange(..., OffsetDateTime startDate, OffsetDateTime endDate);
```

---

### File: `StockPredictionService.java`
**Changes**:
1. ✅ Changed `LocalDateTime.now()` → `OffsetDateTime.now()`
2. ✅ Updated method implementations

**Code**:
```java
// Before
LocalDateTime endDate = LocalDateTime.now();
LocalDateTime startDate = endDate.minusDays(calculationPeriod);

// After
OffsetDateTime endDate = OffsetDateTime.now();
OffsetDateTime startDate = endDate.minusDays(calculationPeriod);
```

---

### File: `HealthCheckController.java`
**Changes**:
1. ✅ Changed `LocalDateTime.now()` → `OffsetDateTime.now()`
2. ✅ All comments translated to English

**API Response**:
```json
{
  "status": "UP",
  "message": "WMS System is running successfully!",
  "timestamp": "2025-01-11T10:30:00+00:00",  // UTC with offset
  "java_version": "17.0.xx",
  "spring_boot_version": "3.2.11"
}
```

---

### File: `pom.xml`
**Changes**:
1. ✅ Added Spring Retry dependencies

**Code**:
```xml
<!-- Spring Retry (重试机制，用于处理乐观锁冲突) -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aspects</artifactId>
</dependency>
```

---

### File: `application.yml`
**Existing Configuration** (Already Correct):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db?serverTimezone=UTC  # ✅ UTC

  jackson:
    time-zone: UTC  # ✅ JSON serialization uses UTC
    date-format: yyyy-MM-dd HH:mm:ss
    serialization:
      write-dates-as-timestamps: false  # ✅ ISO 8601 format
```

---

## 🔍 Verification Checklist

### ✅ Timezone Configuration
- [x] JVM default timezone set to UTC (`WmsSystemApplication.java`)
- [x] Database connection uses UTC (`application.yml`)
- [x] Jackson JSON serialization uses UTC (`application.yml`)
- [x] All timestamp fields use `OffsetDateTime` (not `LocalDateTime`)

### ✅ Code Quality
- [x] All Java code comments in English
- [x] All entity classes inherit from `BaseEntity`
- [x] All Repository methods use `OffsetDateTime` parameters
- [x] All Service methods use `OffsetDateTime` for date calculations

### ✅ Retry Mechanism
- [x] `spring-retry` dependency added to `pom.xml`
- [x] `@EnableRetry` annotation added to main application class
- [x] `@Retryable` annotation present in `InventoryService`

---

## 🚀 Deployment Guide

### 1. **Database Migration** (If Existing Data)

If you have existing data with `TIMESTAMP` (without timezone), run this migration:

```sql
-- Backup existing data first!
ALTER TABLE inventory
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE;

ALTER TABLE inventory
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Repeat for all tables inheriting BaseEntity
-- (products, locations, stock_transactions, users)
```

### 2. **Application Startup**

```bash
# Clean rebuild
mvn clean package -DskipTests

# Start application
java -jar target/wms-system-0.0.1-SNAPSHOT.jar
```

**Expected Console Output**:
```
=== WMS System Started Successfully ===
=== Backend Timezone: UTC (International Standard) ===
```

### 3. **Verify Timezone**

**Health Check API**:
```bash
curl http://localhost:8080/api/health/check
```

**Expected Response**:
```json
{
  "status": "UP",
  "message": "WMS System is running successfully!",
  "timestamp": "2025-01-11T10:30:00+00:00",  // Should show +00:00 (UTC offset)
  "java_version": "17.0.xx",
  "spring_boot_version": "3.2.11"
}
```

---

## 📊 Impact Analysis

### Database Schema Changes

**Before** (LocalDateTime):
```sql
CREATE TABLE inventory (
    created_at TIMESTAMP NOT NULL,  -- No timezone information
    updated_at TIMESTAMP NOT NULL
);
```

**After** (OffsetDateTime):
```sql
CREATE TABLE inventory (
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,  -- Includes timezone offset
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### API Response Changes

**Before** (LocalDateTime):
```json
{
  "createdAt": "2025-01-11T10:30:00"  // Ambiguous - what timezone?
}
```

**After** (OffsetDateTime):
```json
{
  "createdAt": "2025-01-11T10:30:00+00:00"  // Clear - UTC time with offset
}
```

---

## ⚠️ Remaining CLAUDE.md Requirements (Future Work)

### 1. Exception Messages with Error Codes

**Current Implementation**:
```java
throw InsufficientStockException.of(productId, locationId, currentStock, requestedQuantity);
// Message: "库存不足！商品ID：123，库位ID：456，当前库存：50，请求出库：100，缺少：50"
```

**CLAUDE.md Requirement**:
```
Exception messages: Use Key-based codes (ERROR_001, ERROR_002, etc.)
Frontend translates error codes using i18n plugins.
```

**Recommended Future Implementation**:
```java
// Custom exception with error code
throw new BusinessException(
    "ERROR_STOCK_001",  // Error code
    Map.of(
        "productId", productId,
        "currentStock", currentStock,
        "requestedQuantity", requestedQuantity
    )
);

// Frontend displays based on user's locale:
// EN: "Insufficient stock: Current 50, Requested 100, Shortage 50"
// ZH: "库存不足：当前50，请求100，缺少50"
```

**Status**: ⏳ **Deferred** (requires frontend i18n framework)

---

## 📚 Technical References

### ISO 8601 Date Format
```
Format: YYYY-MM-DDTHH:mm:ss±hh:mm
Example: 2025-01-11T10:30:00+00:00

Components:
- 2025-01-11: Date (YYYY-MM-DD)
- T: Time separator
- 10:30:00: Time (HH:mm:ss)
- +00:00: UTC offset (±hh:mm)
```

### OffsetDateTime vs ZonedDateTime

| Feature | OffsetDateTime | ZonedDateTime |
|---------|---------------|---------------|
| **Timezone Info** | Offset only (+00:00) | Full timezone (Europe/London) |
| **DST Handling** | No | Yes |
| **Use Case** | API responses, database storage | Business logic with DST |
| **Recommendation** | ✅ Use for backend | Use for complex timezone logic |

**Why OffsetDateTime for Backend**:
- Simpler than ZonedDateTime (no DST complexity)
- ISO 8601 compliant (international standard)
- PostgreSQL `TIMESTAMP WITH TIME ZONE` maps directly
- Frontend handles local timezone conversion

---

## 🎓 Best Practices

### 1. Always Store UTC in Backend
```java
// ✅ Good - Store absolute time
private OffsetDateTime createdAt;  // 2025-01-11T10:30:00+00:00

// ❌ Bad - Ambiguous time
private LocalDateTime createdAt;  // 2025-01-11T10:30:00 (what timezone?)
```

### 2. Convert to Local Time in Frontend
```javascript
// Backend sends: "2025-01-11T10:30:00+00:00"
const utcTime = "2025-01-11T10:30:00+00:00";

// Frontend converts to user's timezone
const localTime = new Date(utcTime).toLocaleString('zh-CN', {
  timeZone: 'Asia/Shanghai'
});
// Result: "2025-01-11 18:30:00" (UTC+8)
```

### 3. Use OffsetDateTime in Service Layer
```java
// ✅ Good - Explicit timezone
OffsetDateTime startDate = OffsetDateTime.now();
OffsetDateTime endDate = startDate.plusDays(30);

// ❌ Bad - No timezone context
LocalDateTime startDate = LocalDateTime.now();  // What timezone?
```

---

## 🔐 Security Considerations

### 1. Audit Logs with UTC
All audit logs (stock transactions, user operations) use UTC timestamps:
```java
StockTransaction transaction = StockTransaction.builder()
    .createdAt(OffsetDateTime.now())  // Automatically UTC
    .operatorId(userId)
    .build();
```

### 2. Compliance with International Standards
- ✅ ISO 8601 date format (internationally recognized)
- ✅ RFC 3339 compliant (used by REST APIs)
- ✅ GDPR audit requirements (absolute time for all operations)

---

## 📞 Support and Documentation

**Architecture Owner**: WMS Team
**Last Updated**: 2025-01-11
**Version**: 2.0 (International Architecture Upgrade)

**Related Documents**:
- `CLAUDE.md` - Project technical requirements
- `README.md` - Project overview and setup guide
- `ENTITY_MODELING.md` - Database design documentation
- `REPOSITORY_LAYER.md` - Data access layer documentation
- `SERVICE_LAYER.md` - Business logic layer documentation

---

## ✅ Upgrade Completion Status

| Component | Status | Notes |
|-----------|--------|-------|
| **Timezone (UTC)** | ✅ Complete | JVM, DB, JSON all use UTC |
| **OffsetDateTime** | ✅ Complete | All entities migrated |
| **English Comments** | ✅ Complete | All Java files updated |
| **Retry Mechanism** | ✅ Complete | spring-retry configured |
| **Error Code System** | ⏳ Deferred | Requires i18n framework |

---

**End of Document**
