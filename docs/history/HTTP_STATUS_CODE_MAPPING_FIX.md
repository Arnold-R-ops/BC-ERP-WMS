# 测试失败修复 - HTTP 状态码映射问题

**修复日期**: 2026-01-20
**测试结果**: 9个失败 → 预期全部通过
**问题**: GlobalExceptionHandler 缺少 v3.3 错误键映射

---

## 🔴 问题分析

### 失败的测试 (9个)

#### AuthControllerMultiRoleIntegrationTest (4个):
1. ✗ switchRole_RoleNotFound_NotFound - 期望 404，实际 500
2. ✗ switchRole_RoleNotAssigned_Forbidden - 期望 403，实际 500
3. ✗ login_NoRoles_Forbidden - 期望 403，实际 500
4. ✗ switchRole_RoleDisabled_Forbidden - 期望 403，实际 500

#### UserControllerIntegrationTest (5个):
5. ✗ assignRoles_RoleNotFound_NotFound - 期望 404，实际 500
6. ✗ createUser_UsernameExists_Conflict - 期望 409，实际 500
7. ✗ removeRole_RoleNotAssigned_Forbidden - 期望 403，实际 500
8. ✗ assignRoles_AsSuperAdmin_Success - 期望 200，实际 500
9. ✗ createUser_RoleNotFound_NotFound - 期望 404，实际 500

### 错误症状

**测试响应示例**:
```json
{
  "errorKey": "ROLE_NOT_FOUND",
  "params": {"roleCode": "NONEXISTENT_ROLE"},
  "timestamp": "2026-01-20T08:03:19.7328703",
  "path": "/api/auth/switch-role",
  "status": 500  // ❌ 错误！应该是 404
}
```

**现象**:
- ✅ BusinessException 被正确抛出
- ✅ ErrorKey 和 params 正确返回
- ❌ HTTP status 返回 500 而不是预期的业务错误代码

### 根本原因

**文件**: `GlobalExceptionHandler.java:246-288`

**问题**: `mapErrorKeyToHttpStatus()` 方法缺少 v3.3 多角色系统新增的错误键映射

**缺少的错误键**:
```java
// 404 Not Found - 缺少
ErrorKeys.ROLE_NOT_FOUND
ErrorKeys.PURCHASE_ORDER_NOT_FOUND
ErrorKeys.PO_ITEM_NOT_FOUND
ErrorKeys.BATCH_NOT_FOUND

// 403 Forbidden - 缺少
ErrorKeys.USER_NO_ROLES
ErrorKeys.USER_NO_ACTIVE_ROLES
ErrorKeys.ROLE_NOT_ASSIGNED
ErrorKeys.ROLE_DISABLED
ErrorKeys.PO_ALREADY_COMPLETED

// 409 Conflict - 缺少
ErrorKeys.USER_ALREADY_EXISTS
ErrorKeys.BATCH_CODE_GENERATION_FAILED

// 500 Internal Server Error - 缺少
ErrorKeys.ROLE_SWITCH_FAILED

// 400 Bad Request - 缺少 (采购订单/批次相关)
ErrorKeys.PO_INVALID_STATUS
ErrorKeys.PO_ROLLBACK_NOT_ALLOWED
ErrorKeys.PO_EXPIRY_DATE_REQUIRED
ErrorKeys.BATCH_INACTIVE
ErrorKeys.BATCH_STOCK_INSUFFICIENT
ErrorKeys.BATCH_EXPIRED
ErrorKeys.INVALID_FILE_FORMAT
ErrorKeys.INVALID_EXCEL_DATA
ErrorKeys.FILE_READ_ERROR
```

**执行流程（修复前）**:
```
1. AuthController.switchRole()
   ↓
2. throw new BusinessException(ErrorKeys.ROLE_NOT_FOUND, ...)
   ↓
3. GlobalExceptionHandler.handleBusinessException()
   ↓
4. mapErrorKeyToHttpStatus("ROLE_NOT_FOUND")
   ↓
5. switch 语句:
   - case PRODUCT_NOT_FOUND: ❌ 不匹配
   - case LOCATION_NOT_FOUND: ❌ 不匹配
   - ...
   - default: ✅ 匹配 → 返回 500 ❌
   ↓
6. 返回 HTTP 500 (错误)
```

---

## ✅ 修复方案

### 修改文件

**`src/main/java/com/wms/system/controller/GlobalExceptionHandler.java`**

### 修复内容

在 `mapErrorKeyToHttpStatus()` 方法的 switch 语句中添加所有缺失的错误键映射：

#### 1. 404 Not Found - 新增 4 个
```java
case ErrorKeys.PRODUCT_NOT_FOUND,
     ErrorKeys.LOCATION_NOT_FOUND,
     // ... 原有的
     ErrorKeys.ROLE_NOT_FOUND,  // ⭐ 新增
     ErrorKeys.PURCHASE_ORDER_NOT_FOUND,  // ⭐ 新增
     ErrorKeys.PO_ITEM_NOT_FOUND,  // ⭐ 新增
     ErrorKeys.BATCH_NOT_FOUND -> HttpStatus.NOT_FOUND;  // ⭐ 新增
```

#### 2. 400 Bad Request - 新增 9 个
```java
case ErrorKeys.STOCK_INSUFFICIENT,
     // ... 原有的
     ErrorKeys.PO_INVALID_STATUS,  // ⭐ 新增
     ErrorKeys.PO_ROLLBACK_NOT_ALLOWED,  // ⭐ 新增
     ErrorKeys.PO_EXPIRY_DATE_REQUIRED,  // ⭐ 新增
     ErrorKeys.BATCH_INACTIVE,  // ⭐ 新增
     ErrorKeys.BATCH_STOCK_INSUFFICIENT,  // ⭐ 新增
     ErrorKeys.BATCH_EXPIRED,  // ⭐ 新增
     ErrorKeys.INVALID_FILE_FORMAT,  // ⭐ 新增
     ErrorKeys.INVALID_EXCEL_DATA,  // ⭐ 新增
     ErrorKeys.FILE_READ_ERROR -> HttpStatus.BAD_REQUEST;  // ⭐ 新增
```

#### 3. 403 Forbidden - 新增 5 个
```java
case ErrorKeys.USER_UNAUTHORIZED,
     ErrorKeys.USER_ACCOUNT_DISABLED,
     ErrorKeys.OPERATION_NOT_ALLOWED,
     ErrorKeys.USER_NO_ROLES,  // ⭐ 新增 (v3.3)
     ErrorKeys.USER_NO_ACTIVE_ROLES,  // ⭐ 新增 (v3.3)
     ErrorKeys.ROLE_NOT_ASSIGNED,  // ⭐ 新增 (v3.3)
     ErrorKeys.ROLE_DISABLED,  // ⭐ 新增 (v3.3)
     ErrorKeys.PO_ALREADY_COMPLETED -> HttpStatus.FORBIDDEN;  // ⭐ 新增
```

#### 4. 409 Conflict - 新增 2 个
```java
case ErrorKeys.STOCK_CONCURRENCY_CONFLICT,
     ErrorKeys.PRODUCT_ALREADY_EXISTS,
     ErrorKeys.LOCATION_ALREADY_EXISTS,
     ErrorKeys.USER_ALREADY_EXISTS,  // ⭐ 新增 (v3.3)
     ErrorKeys.BATCH_CODE_GENERATION_FAILED -> HttpStatus.CONFLICT;  // ⭐ 新增
```

#### 5. 500 Internal Server Error - 新增专用分支
```java
// 500 Internal Server Error
case ErrorKeys.ROLE_SWITCH_FAILED,  // ⭐ 新增 (v3.3)
     ErrorKeys.INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
```

### 修复后的执行流程

```
1. AuthController.switchRole()
   ↓
2. throw new BusinessException(ErrorKeys.ROLE_NOT_FOUND, ...)
   ↓
3. GlobalExceptionHandler.handleBusinessException()
   ↓
4. mapErrorKeyToHttpStatus("ROLE_NOT_FOUND")
   ↓
5. switch 语句:
   - case ROLE_NOT_FOUND: ✅ 匹配 → 返回 404 ✅
   ↓
6. 返回 HTTP 404 (正确)
```

---

## 📊 修复统计

### 代码变更

| 文件 | 修改内容 | 代码行数 |
|------|---------|---------|
| **GlobalExceptionHandler.java** | 添加 v3.3 和采购订单/批次错误键映射 | +20 行 |
| **总计** | 1 个文件 | **+20 行** |

### 新增映射 (20个错误键)

| HTTP 状态码 | 新增错误键数量 | 错误键列表 |
|------------|--------------|-----------|
| **404 Not Found** | 4 个 | ROLE_NOT_FOUND, PURCHASE_ORDER_NOT_FOUND, PO_ITEM_NOT_FOUND, BATCH_NOT_FOUND |
| **400 Bad Request** | 9 个 | PO_INVALID_STATUS, PO_ROLLBACK_NOT_ALLOWED, PO_EXPIRY_DATE_REQUIRED, BATCH_INACTIVE, BATCH_STOCK_INSUFFICIENT, BATCH_EXPIRED, INVALID_FILE_FORMAT, INVALID_EXCEL_DATA, FILE_READ_ERROR |
| **403 Forbidden** | 5 个 | USER_NO_ROLES, USER_NO_ACTIVE_ROLES, ROLE_NOT_ASSIGNED, ROLE_DISABLED, PO_ALREADY_COMPLETED |
| **409 Conflict** | 2 个 | USER_ALREADY_EXISTS, BATCH_CODE_GENERATION_FAILED |
| **500 Internal Server Error** | 2 个 | ROLE_SWITCH_FAILED, INTERNAL_SERVER_ERROR (显式) |

---

## 🎯 预期效果

### 修复的测试 (9个)

| 测试 | 修复前 | 修复后 | 错误键 |
|------|--------|--------|--------|
| switchRole_RoleNotFound_NotFound | ❌ 返回 500 | ✅ 返回 404 | ROLE_NOT_FOUND |
| switchRole_RoleNotAssigned_Forbidden | ❌ 返回 500 | ✅ 返回 403 | ROLE_NOT_ASSIGNED |
| login_NoRoles_Forbidden | ❌ 返回 500 | ✅ 返回 403 | USER_NO_ROLES |
| switchRole_RoleDisabled_Forbidden | ❌ 返回 500 | ✅ 返回 403 | ROLE_DISABLED |
| assignRoles_RoleNotFound_NotFound | ❌ 返回 500 | ✅ 返回 404 | ROLE_NOT_FOUND |
| createUser_UsernameExists_Conflict | ❌ 返回 500 | ✅ 返回 409 | USER_ALREADY_EXISTS |
| removeRole_RoleNotAssigned_Forbidden | ❌ 返回 500 | ✅ 返回 403 | ROLE_NOT_ASSIGNED |
| assignRoles_AsSuperAdmin_Success | ❌ 返回 500 | ✅ 返回 200 | (成功场景) |
| createUser_RoleNotFound_NotFound | ❌ 返回 500 | ✅ 返回 404 | ROLE_NOT_FOUND |

---

## 🔍 HTTP 状态码语义

### 修复后的完整映射表

| 状态码 | 名称 | 语义 | 错误键示例 |
|--------|------|------|-----------|
| **200 OK** | 成功 | 请求成功处理 | (无错误) |
| **400 Bad Request** | 请求错误 | 请求参数无效、库存不足等 | STOCK_INSUFFICIENT, VALIDATION_FAILED |
| **401 Unauthorized** | 未认证 | 需要有效的认证凭证 | AUTH_TOKEN_MISSING, AUTH_INVALID_CREDENTIALS |
| **403 Forbidden** | 禁止访问 | 已认证但无权限 | USER_UNAUTHORIZED, ROLE_NOT_ASSIGNED |
| **404 Not Found** | 资源不存在 | 请求的资源不存在 | ROLE_NOT_FOUND, USER_NOT_FOUND |
| **409 Conflict** | 冲突 | 资源状态冲突、并发冲突等 | USER_ALREADY_EXISTS, STOCK_CONCURRENCY_CONFLICT |
| **500 Internal Server Error** | 服务器错误 | 未预期的服务器错误 | ROLE_SWITCH_FAILED, INTERNAL_SERVER_ERROR |

---

## 📝 测试验证

### 1. 运行测试

```bash
mvn test
```

**预期结果**: 所有 35 个测试通过 (包括修复的 9 个)

### 2. 运行特定测试类

```bash
# 测试 AuthController 集成测试
mvn test -Dtest=AuthControllerMultiRoleIntegrationTest

# 测试 UserController 集成测试
mvn test -Dtest=UserControllerIntegrationTest
```

**预期结果**: 所有测试通过

### 3. 手动测试 API

**测试 1: switchRole 角色不存在 (404)**
```bash
POST /api/auth/switch-role
Authorization: Bearer {valid_token}

{
  "targetRoleCode": "NONEXISTENT_ROLE"
}

预期响应: 404 Not Found
{
  "errorKey": "ROLE_NOT_FOUND",
  "params": {"roleCode": "NONEXISTENT_ROLE"},
  "status": 404
}
```

**测试 2: createUser 用户名已存在 (409)**
```bash
POST /api/users
Authorization: Bearer {super_admin_token}

{
  "username": "admin",  // 已存在
  "password": "test123",
  "roleIds": [1]
}

预期响应: 409 Conflict
{
  "errorKey": "USER_ALREADY_EXISTS",
  "params": {"username": "admin"},
  "status": 409
}
```

**测试 3: switchRole 角色未分配 (403)**
```bash
POST /api/auth/switch-role
Authorization: Bearer {valid_token}

{
  "targetRoleCode": "SUPER_ADMIN"  // 未分配给当前用户
}

预期响应: 403 Forbidden
{
  "errorKey": "ROLE_NOT_ASSIGNED",
  "params": {"roleCode": "SUPER_ADMIN", ...},
  "status": 403
}
```

---

## 💡 设计经验

### 1. 错误键与 HTTP 状态码的映射原则

| 错误类型 | HTTP 状态码 | 判断标准 |
|---------|------------|---------|
| **资源不存在** | 404 | 查询数据库时 findById/findByCode 返回 Optional.empty() |
| **参数错误** | 400 | 业务规则验证失败（库存不足、状态错误等） |
| **认证失败** | 401 | Token 缺失、无效、过期 |
| **权限不足** | 403 | 已认证但角色/权限不足 |
| **资源冲突** | 409 | 唯一约束冲突、并发冲突 |
| **服务器错误** | 500 | 未预期的异常、系统错误 |

### 2. 新增错误键时的检查清单

✅ **在 ErrorKeys.java 中定义常量**
```java
public static final String NEW_ERROR_KEY = "NEW_ERROR_KEY";
```

✅ **在 GlobalExceptionHandler 中添加映射**
```java
case ErrorKeys.NEW_ERROR_KEY -> HttpStatus.NOT_FOUND;
```

✅ **在代码中使用错误键**
```java
throw new BusinessException(
    ErrorKeys.NEW_ERROR_KEY,
    Map.of("param1", value1)
);
```

✅ **编写测试验证 HTTP 状态码**
```java
.andExpect(status().isNotFound())
.andExpect(jsonPath("$.errorKey").value("NEW_ERROR_KEY"))
```

### 3. 避免重复的错误

❌ **错误示例**: 在多个地方定义相同的错误键
```java
// ❌ 不要在代码中硬编码错误键
throw new BusinessException("ROLE_NOT_FOUND", ...);

// ✅ 使用 ErrorKeys 常量
throw new BusinessException(ErrorKeys.ROLE_NOT_FOUND, ...);
```

❌ **错误示例**: 忘记在 GlobalExceptionHandler 中添加映射
```java
// 结果：所有新错误键都返回 500
```

✅ **正确做法**: 同步更新 ErrorKeys 和 GlobalExceptionHandler
```java
// 1. 在 ErrorKeys.java 添加
public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";

// 2. 在 GlobalExceptionHandler.java 添加映射
case ErrorKeys.ROLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
```

---

## 🚀 后续优化建议

### 1. 使用注解自动映射

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ErrorKeyMapping {
    HttpStatus httpStatus();
}

public final class ErrorKeys {
    @ErrorKeyMapping(httpStatus = HttpStatus.NOT_FOUND)
    public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";

    @ErrorKeyMapping(httpStatus = HttpStatus.FORBIDDEN)
    public static final String ROLE_NOT_ASSIGNED = "ROLE_NOT_ASSIGNED";
}
```

### 2. 单元测试覆盖

```java
@Test
void mapErrorKeyToHttpStatus_AllErrorKeys_ShouldHaveMapping() {
    // 使用反射获取所有 ErrorKeys 常量
    Field[] fields = ErrorKeys.class.getDeclaredFields();

    for (Field field : fields) {
        if (Modifier.isStatic(field.getModifiers())) {
            String errorKey = (String) field.get(null);

            // 验证每个错误键都有映射（不返回 500 或有明确的 500 映射）
            HttpStatus status = globalExceptionHandler.mapErrorKeyToHttpStatus(errorKey);
            assertThat(status).isNotNull();
        }
    }
}
```

### 3. 文档自动生成

从 ErrorKeys 和 GlobalExceptionHandler 自动生成 API 错误码文档：

```markdown
# API 错误码参考

| 错误键 | HTTP 状态码 | 描述 | 参数 |
|--------|------------|------|------|
| ROLE_NOT_FOUND | 404 | 角色不存在 | roleId, roleCode |
| USER_ALREADY_EXISTS | 409 | 用户名已存在 | username |
| ... | ... | ... | ... |
```

---

**修复完成时间**: 2026-01-20
**修复执行者**: Claude Sonnet 4.5
**测试状态**: 等待验证
**预期结果**: 所有 35 个测试通过 (100%)
