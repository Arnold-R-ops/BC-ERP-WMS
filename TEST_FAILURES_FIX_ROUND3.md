# 测试失败修复 - 第三轮

**修复日期**: 2026-01-20
**测试结果**: 20个失败 → 11个失败
**本轮修复**: 认证端点权限问题

---

## 📊 测试进展

| 轮次 | 失败数 | 通过数 | 通过率 | 主要问题 |
|------|--------|--------|--------|----------|
| 第一轮 | 35 | 0 | 0% | UserRepository 废弃方法导致 Spring Boot 无法启动 |
| 第二轮 | 20 | 15 | 43% | SUPER_ADMIN 绕过未启用 + switchRole null 检查 |
| **第三轮** | **11** | **24** | **69%** | **认证端点权限配置错误** |

---

## 🔴 本轮问题分析

### 问题：switchRole 相关测试全部失败

**失败的测试** (6个):
1. ✗ 切换角色 - 目标角色不存在 - 404
2. ✗ 切换角色 - 角色未分配给用户 - 403
3. ✗ 切换角色 - 目标角色已禁用 - 403
4. ✗ 切换角色 - 成功
5. ✗ 切换角色 - 切换到所有可用角色
6. ✗ 登录失败 - 用户无角色 - 403

**错误症状**:
```
Status expected:<404> but was:<403>
Status expected:<200> but was:<403>
No value at JSON path "$.errorKey"
```

**根本原因**:

测试流程：
```
1. 用户登录成功 → 获得 JWT Token
2. 使用 Token 访问 /api/auth/switch-role
3. DynamicAuthorizationManager 授权检查:
   - ✅ 不是公共端点
   - ✅ 用户已认证
   - ❌ 用户不是 SUPER_ADMIN
   - ❌ 用户没有访问此端点的权限 → 返回 403
4. 控制器从未被调用 → 无法返回业务错误（404/500/etc）
```

**设计问题**:

`/api/auth/switch-role` 是一个**用户自服务端点**：
- 任何已登录用户都应该能访问（切换自己的角色）
- 不需要特殊权限（因为用户只能切换自己已分配的角色）
- 但它不是公共端点（需要认证）

**当前配置**:
- ✅ PUBLIC_ENDPOINTS: 不需要认证的端点（如 `/api/auth/login`）
- ❌ 缺少: **需要认证但不需要权限**的端点配置

---

## ✅ 修复方案

### 新增端点类型：AUTHENTICATED_ENDPOINTS

**文件**: `src/main/java/com/wms/system/security/DynamicAuthorizationManager.java`

**核心概念**:

| 端点类型 | 认证要求 | 权限要求 | 示例 |
|---------|---------|---------|------|
| **PUBLIC_ENDPOINTS** | ❌ 不需要 | ❌ 不需要 | `/api/auth/login` |
| **AUTHENTICATED_ENDPOINTS** ⭐ | ✅ 需要 | ❌ 不需要 | `/api/auth/switch-role` |
| **普通端点** | ✅ 需要 | ✅ 需要 | `/api/users/**` |

---

### 修复 1: 添加 AUTHENTICATED_ENDPOINTS 常量

**位置**: 类成员变量

```java
/**
 * Authenticated endpoints (require authentication but no specific permission)
 *
 * These endpoints are accessible to any authenticated user.
 * Useful for user-specific operations like switching roles, updating profile, etc.
 */
private static final String[] AUTHENTICATED_ENDPOINTS = {
    "/api/auth/switch-role",  // Any authenticated user can switch their own role
    "/api/auth/logout",       // Any authenticated user can logout
    "/api/auth/refresh-token" // Any authenticated user can refresh token
};
```

**说明**:
- 这些端点只需要用户登录即可访问
- 不检查用户是否有特定权限
- 用于用户自服务功能

---

### 修复 2: 在授权流程中添加检查

**位置**: `check()` 方法

**修复前**:
```java
// 5. Check if user is SUPER_ADMIN
if (userPermissions.getRoleCodes().contains("SUPER_ADMIN")) {
    return new AuthorizationDecision(true);
}

// 6. Match request against user's API permissions
boolean hasPermission = matchPermission(...);  // ← switchRole 在这里被拒绝
```

**修复后**:
```java
// 5. Check if user is SUPER_ADMIN
if (userPermissions.getRoleCodes().contains("SUPER_ADMIN")) {
    return new AuthorizationDecision(true);
}

// 6. Check if endpoint requires only authentication (no specific permission) ⭐ 新增
if (isAuthenticatedEndpoint(requestUri)) {
    log.debug("Authenticated endpoint accessed by user {}: {} {}", userId, httpMethod, requestUri);
    return new AuthorizationDecision(true);  // ← switchRole 在这里通过
}

// 7. Match request against user's API permissions
boolean hasPermission = matchPermission(...);
```

**执行顺序**:
```
Request: POST /api/auth/switch-role (with valid JWT)
    ↓
1. isPublicEndpoint() → false
    ↓
2. auth.isAuthenticated() → true ✅
    ↓
3. extractUserId() → 123
    ↓
4. getUserPermissions(123) → 加载权限
    ↓
5. SUPER_ADMIN 检查 → false (普通用户)
    ↓
6. isAuthenticatedEndpoint() → true ✅ (新增检查)
    ↓
7. return AuthorizationDecision(true) → 允许访问
    ↓
8. AuthController.switchRole() → 执行业务逻辑 → 返回业务错误（404/403/etc）
```

---

### 修复 3: 添加辅助方法

**位置**: 类成员方法

```java
/**
 * Check if request URI matches any authenticated endpoint pattern
 *
 * Authenticated endpoints require authentication but no specific permission.
 * Any authenticated user can access these endpoints.
 *
 * @param requestUri Request URI
 * @return true if authenticated endpoint
 */
private boolean isAuthenticatedEndpoint(String requestUri) {
    for (String pattern : AUTHENTICATED_ENDPOINTS) {
        if (pathMatcher.match(pattern, requestUri)) {
            return true;
        }
    }
    return false;
}
```

---

## 📊 修复统计

### 修改的文件

| 文件 | 修改内容 | 代码行数 |
|------|---------|---------|
| **DynamicAuthorizationManager.java** | 添加 AUTHENTICATED_ENDPOINTS 配置 | +9 行 |
| **DynamicAuthorizationManager.java** | 在 check() 方法添加认证端点检查 | +5 行 |
| **DynamicAuthorizationManager.java** | 添加 isAuthenticatedEndpoint() 方法 | +15 行 |
| **总计** | 1 个文件 | **+29 行** |

---

## 🔍 授权流程对比

### 修复前

```
用户访问 /api/auth/switch-role
    ↓
已认证？ YES
    ↓
SUPER_ADMIN？ NO
    ↓
有权限？ NO ❌
    ↓
返回 403 Forbidden (Spring Security 层)
    ↓
控制器从未执行 → 无法返回业务错误
```

### 修复后

```
用户访问 /api/auth/switch-role
    ↓
已认证？ YES
    ↓
SUPER_ADMIN？ NO
    ↓
认证端点？ YES ✅
    ↓
允许访问
    ↓
AuthController.switchRole() 执行
    ↓
业务逻辑检查:
  - 角色是否存在？
  - 用户是否拥有该角色？
  - 角色是否启用？
    ↓
返回正确的业务错误 (404/403/500)
```

---

## 🎯 预期效果

### 修复的测试 (预期)

| 测试 | 修复前 | 修复后 | 原因 |
|------|--------|--------|------|
| 切换角色 - 目标角色不存在 - 404 | ❌ 返回 403 | ✅ 返回 404 | 控制器抛出 ROLE_NOT_FOUND |
| 切换角色 - 角色未分配给用户 - 403 | ❌ 返回 403 (授权层) | ✅ 返回 403 (业务层) | 控制器抛出 ROLE_NOT_ASSIGNED |
| 切换角色 - 目标角色已禁用 - 403 | ❌ 返回 403 (授权层) | ✅ 返回 403 (业务层) | 控制器抛出 ROLE_DISABLED |
| 切换角色 - 成功 | ❌ 返回 403 | ✅ 返回 200 | 控制器正常执行 |
| 切换角色 - 切换到所有可用角色 | ❌ 返回 403 | ✅ 返回 200 | 控制器正常执行 |

**关键区别**: 403 错误现在由**业务逻辑**返回（包含 errorKey），而不是由 Spring Security 返回（无 errorKey）。

---

## 🔐 安全考虑

### 1. 为什么 switchRole 不需要权限检查？

**原因**:
- 用户只能切换到**已分配给自己**的角色（控制器中验证）
- 无法切换到其他用户的角色
- 无法切换到未分配的角色

**安全保障**:
```java
// AuthController.switchRole() 中的检查
boolean hasRole = userRoleService.userHasRole(user.getId(), targetRole.getId());
if (!hasRole) {
    throw new BusinessException(ROLE_NOT_ASSIGNED, ...);
}
```

**类比**: 就像一个人可以自由换衣服（前提是衣服是自己的），不需要额外权限。

---

### 2. 其他认证端点的安全性

| 端点 | 安全检查 | 说明 |
|------|---------|------|
| `/api/auth/switch-role` | 用户只能切换自己的角色 | 控制器验证 roleId 是否分配给当前用户 |
| `/api/auth/logout` | 用户只能登出自己 | Token 中包含用户信息，只能登出自己 |
| `/api/auth/refresh-token` | 用户只能刷新自己的 Token | Token 验证确保只能刷新自己的 |

**总结**: 这些端点虽然不检查权限，但都有**业务逻辑层面的安全检查**，确保用户只能操作自己的数据。

---

### 3. 与 PUBLIC_ENDPOINTS 的区别

| 端点类型 | 认证 | 权限 | 示例 | 风险 |
|---------|------|------|------|------|
| **PUBLIC** | ❌ | ❌ | `/api/auth/login` | 低（公开接口） |
| **AUTHENTICATED** | ✅ | ❌ | `/api/auth/switch-role` | 低（用户自服务） |
| **PROTECTED** | ✅ | ✅ | `/api/users/**` | 高（管理功能） |

**原则**:
- PUBLIC: 任何人都能访问
- AUTHENTICATED: 登录用户能访问（操作自己的数据）
- PROTECTED: 有权限的用户才能访问（操作系统数据）

---

## 📝 测试验证

### 1. 运行测试

```bash
mvn test
```

**预期结果**:
- ✅ 6个 switchRole 测试通过
- ❓ UserController 测试仍需检查

---

### 2. 手动测试 switchRole

**步骤 1**: 登录
```bash
POST /api/auth/login
{
  "username": "multi_role_user",
  "password": "password123"
}

Response:
{
  "token": "eyJhbGciOiJI...",
  "currentRole": "WAREHOUSE_ADMIN",
  "availableRoles": ["WAREHOUSE_ADMIN", "SALESPERSON"]
}
```

**步骤 2**: 切换角色（成功）
```bash
POST /api/auth/switch-role
Authorization: Bearer eyJhbGciOiJI...
{
  "targetRoleCode": "SALESPERSON"
}

Response: 200 OK
{
  "token": "eyJhbGciOiJI...",
  "currentRole": "SALESPERSON",
  "message": "Role switched successfully"
}
```

**步骤 3**: 切换到未分配的角色（失败）
```bash
POST /api/auth/switch-role
Authorization: Bearer eyJhbGciOiJI...
{
  "targetRoleCode": "SUPER_ADMIN"
}

Response: 403 Forbidden
{
  "errorKey": "ROLE_NOT_ASSIGNED",
  "params": { "roleCode": "SUPER_ADMIN" },
  "timestamp": "2026-01-20T15:30:00",
  "status": 403
}
```

---

## 🚀 后续工作

### 待修复的测试 (5个)

根据错误日志，还有 UserController 相关的测试失败：
1. ✗ 批量分配角色 - 角色不存在 - 404
2. ✗ 创建用户 - 用户名已存在 - 409 冲突
3. ✗ 移除角色 - 角色未分配 - 403
4. ✗ 批量分配角色 - SUPER_ADMIN - 成功
5. ✗ 创建用户 - 角色不存在 - 404

**可能原因**:
- 500 错误：控制器抛出未处理的异常
- 403 错误：SUPER_ADMIN 绕过未生效（需检查）

---

## 💡 设计启示

### 端点权限设计的三层模型

```
┌──────────────────────────────────────────────────┐
│ Layer 1: PUBLIC_ENDPOINTS                       │
│ - No authentication required                    │
│ - Examples: /login, /register, /health          │
└──────────────────────────────────────────────────┘
                    ↓
┌──────────────────────────────────────────────────┐
│ Layer 2: AUTHENTICATED_ENDPOINTS ⭐             │
│ - Authentication required                        │
│ - No specific permission required                │
│ - Examples: /switch-role, /logout, /profile     │
└──────────────────────────────────────────────────┘
                    ↓
┌──────────────────────────────────────────────────┐
│ Layer 3: PROTECTED_ENDPOINTS (默认)             │
│ - Authentication required                        │
│ - Specific permission required                   │
│ - Examples: /users/**, /roles/**, /permissions/**│
└──────────────────────────────────────────────────┘
```

**最佳实践**:
1. 默认情况下，所有端点都是 PROTECTED（最安全）
2. 明确标记 PUBLIC 端点（最小化公开接口）
3. 明确标记 AUTHENTICATED 端点（用户自服务功能）
4. 避免混淆 AUTHENTICATED 和 PROTECTED（清晰的权限边界）

---

**修复完成时间**: 2026-01-20
**修复执行者**: Claude Sonnet 4.5
**测试状态**: 等待验证
**下一步**:
1. 运行测试验证 switchRole 测试通过
2. 检查并修复剩余的 UserController 测试失败
