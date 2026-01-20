# WMS 系统 - Postman API 测试指南

## 📌 系统信息

- **应用端口**: `8080`
- **Base URL**: `http://localhost:8080`
- **认证方式**: JWT (JSON Web Token)
- **Content-Type**: `application/json`

---

## 🔧 Postman 环境配置

### 1. 创建环境变量

在 Postman 中创建一个新环境，添加以下变量：

| 变量名 | 初始值 | 当前值 | 说明 |
|--------|--------|--------|------|
| `baseUrl` | `http://localhost:8080` | `http://localhost:8080` | API 基础地址 |
| `token` | 空 | (动态获取) | JWT Token |
| `username` | `admin` | `admin` | 测试用户名 |

### 2. 配置自动保存 Token

登录成功后自动保存 token 到环境变量，在登录接口的 **Tests** 标签页添加：

```javascript
// 解析响应
var jsonData = pm.response.json();

// 保存 token 到环境变量
if (jsonData.token) {
    pm.environment.set("token", jsonData.token);
    console.log("Token 已保存: " + jsonData.token);
}
```

---

## 🔐 API 接口测试步骤

### 接口 1: 用户登录 (Login)

#### 📍 基本信息
- **方法**: `POST`
- **URL**: `{{baseUrl}}/api/auth/login`
- **认证**: 无需认证（公开接口）
- **Content-Type**: `application/json`

#### 📤 请求参数

**Body (raw JSON):**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**字段说明:**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | ✅ | 用户名（3-50字符） |
| `password` | String | ✅ | 密码（明文，后端会验证） |

#### ✅ 成功响应 (200 OK)

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTY0MzY3NjAwMCwiZXhwIjoxNjQzNzYyNDAwfQ.xxx",
  "tokenType": "Bearer",
  "username": "admin",
  "role": "ADMIN",
  "expiresIn": 86400000
}
```

**返回字段说明:**
| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | String | JWT Token（用于后续请求认证） |
| `tokenType` | String | Token 类型（固定为 "Bearer"） |
| `username` | String | 登录用户名 |
| `role` | String | 用户角色（ADMIN/STAFF） |
| `expiresIn` | Long | Token 有效期（毫秒）86400000 = 24小时 |

#### ❌ 错误响应

**1. 用户名或密码错误 (401 Unauthorized)**
```json
{
  "errorKey": "AUTH_INVALID_CREDENTIALS",
  "params": {},
  "timestamp": "2025-01-19T10:30:00",
  "path": "/api/auth/login",
  "status": 401
}
```

**2. 账户被禁用 (403 Forbidden)**
```json
{
  "errorKey": "USER_ACCOUNT_DISABLED",
  "params": {
    "username": "admin"
  },
  "timestamp": "2025-01-19T10:30:00",
  "path": "/api/auth/login",
  "status": 403
}
```

**3. 请求参数验证失败 (400 Bad Request)**
```json
{
  "errorKey": "VALIDATION_FAILED",
  "params": {
    "username": "用户名不能为空"
  },
  "timestamp": "2025-01-19T10:30:00",
  "path": "/api/auth/login",
  "status": 400
}
```

#### 📝 Postman 配置步骤

1. **创建新请求**
   - 点击 `New` → `HTTP Request`
   - 命名为: `用户登录 - Login`

2. **配置请求**
   - 方法: `POST`
   - URL: `{{baseUrl}}/api/auth/login`

3. **配置 Body**
   - 选择 `Body` → `raw` → `JSON`
   - 输入上面的 JSON 请求体

4. **配置自动保存 Token（重要！）**
   - 点击 `Tests` 标签页
   - 添加以下脚本：
   ```javascript
   // 解析响应
   var jsonData = pm.response.json();

   // 如果登录成功，保存 token
   if (pm.response.code === 200 && jsonData.token) {
       pm.environment.set("token", jsonData.token);
       console.log("✅ Token 已保存到环境变量");
       console.log("Token: " + jsonData.token);
       console.log("用户: " + jsonData.username);
       console.log("角色: " + jsonData.role);
   }

   // 验证响应状态
   pm.test("登录成功", function () {
       pm.response.to.have.status(200);
   });

   // 验证返回数据
   pm.test("返回 token", function () {
       pm.expect(jsonData.token).to.not.be.undefined;
   });
   ```

5. **发送请求**
   - 点击 `Send` 按钮
   - 查看响应结果

---

### 接口 2: 健康检查 (Health Check)

#### 📍 基本信息
- **方法**: `GET`
- **URL**: `{{baseUrl}}/api/auth/health`
- **认证**: 无需认证
- **Content-Type**: 无

#### ✅ 成功响应 (200 OK)

```json
{
  "status": "UP",
  "service": "AuthenticationService"
}
```

#### 📝 Postman 配置步骤

1. 创建新请求: `健康检查 - Health`
2. 方法: `GET`
3. URL: `{{baseUrl}}/api/auth/health`
4. 直接点击 `Send` 即可

---

## 🔑 使用 JWT Token 访问受保护接口

### 配置方法（适用于所有需要认证的接口）

1. **在请求的 Authorization 标签页配置**
   - Type: 选择 `Bearer Token`
   - Token: 输入 `{{token}}`（使用环境变量）

2. **或者在 Headers 中手动添加**
   ```
   Key: Authorization
   Value: Bearer {{token}}
   ```

### 示例：访问受保护的 API

假设有一个获取用户信息的接口（如果存在）：

- **方法**: `GET`
- **URL**: `{{baseUrl}}/api/users/me`
- **Headers**:
  ```
  Authorization: Bearer {{token}}
  Content-Type: application/json
  ```

---

## 📋 完整测试流程

### 第一步：测试登录功能

#### 测试用例 1: 成功登录（使用正确凭据）
```json
POST {{baseUrl}}/api/auth/login
Body:
{
  "username": "admin",
  "password": "admin123"
}

预期结果: 200 OK + JWT Token
```

#### 测试用例 2: 用户名错误
```json
POST {{baseUrl}}/api/auth/login
Body:
{
  "username": "wronguser",
  "password": "admin123"
}

预期结果: 401 Unauthorized + AUTH_INVALID_CREDENTIALS
```

#### 测试用例 3: 密码错误
```json
POST {{baseUrl}}/api/auth/login
Body:
{
  "username": "admin",
  "password": "wrongpassword"
}

预期结果: 401 Unauthorized + AUTH_INVALID_CREDENTIALS
```

#### 测试用例 4: 用户名为空
```json
POST {{baseUrl}}/api/auth/login
Body:
{
  "username": "",
  "password": "admin123"
}

预期结果: 400 Bad Request + VALIDATION_FAILED
```

#### 测试用例 5: 密码为空
```json
POST {{baseUrl}}/api/auth/login
Body:
{
  "username": "admin",
  "password": ""
}

预期结果: 400 Bad Request + VALIDATION_FAILED
```

### 第二步：测试健康检查

```
GET {{baseUrl}}/api/auth/health

预期结果: 200 OK + {"status": "UP", "service": "AuthenticationService"}
```

### 第三步：测试 Token 认证（需要其他受保护接口）

1. 先登录获取 token
2. 使用 token 访问受保护的接口
3. 验证是否能正常访问

---

## 🛠️ 测试前准备

### 1. 确保应用已启动

```bash
# 启动应用
mvn spring-boot:run

# 或者运行编译好的 jar
java -jar target/wms-system.jar
```

### 2. 确认数据库中有测试用户

需要在数据库中创建一个测试用户（如果还没有的话）：

```sql
-- 查询现有用户
SELECT * FROM users;

-- 如果没有用户，需要手动创建或运行初始化脚本
-- 注意：密码需要使用 BCrypt 加密
-- BCrypt 加密后的 "admin123": $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
INSERT INTO users (username, password, role, enabled, display_name, created_at, updated_at)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', true, '管理员', NOW(), NOW());
```

### 3. 验证数据库连接

检查 `application.yml` 中的数据库配置是否正确：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: postgres
    password: 123465
```

---

## 🎯 常见问题排查

### 问题 1: 连接被拒绝 (Connection Refused)
**原因**: 应用未启动或端口错误
**解决**:
- 检查应用是否运行: `netstat -ano | findstr :8080` (Windows)
- 确认端口号是否为 8080

### 问题 2: 401 Unauthorized 持续出现
**原因**: 用户名密码错误或用户不存在
**解决**:
- 检查数据库中是否存在该用户
- 确认密码是否正确（注意大小写）
- 查看应用日志确认详细错误

### 问题 3: Token 无法使用
**原因**: Token 未正确保存或已过期
**解决**:
- 重新登录获取新 token
- 检查 Authorization header 格式: `Bearer {token}`
- 确认 token 前有 "Bearer " 前缀且有空格

### 问题 4: 500 Internal Server Error
**原因**: 数据库连接失败或后端代码错误
**解决**:
- 检查应用日志
- 确认数据库服务是否运行
- 验证数据库连接配置

---

## 📦 Postman Collection 导出

完成所有接口配置后，可以导出 Collection：

1. 点击 Collection 右侧的 `...` 菜单
2. 选择 `Export`
3. 选择 `Collection v2.1`
4. 保存为 `WMS_API_Tests.postman_collection.json`

---

## 📚 附录

### A. 默认测试账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| `admin` | `admin123` | ADMIN | 管理员账户（需要手动创建） |

### B. JWT Token 说明

- **有效期**: 24小时 (86400000 毫秒)
- **格式**: `Bearer {token}`
- **存储位置**: 请求 Header 的 `Authorization` 字段
- **刷新机制**: 当前版本需要重新登录（未来版本可添加刷新功能）

### C. 角色权限说明

| 角色 | 权限说明 |
|------|----------|
| `ADMIN` | 管理员，拥有所有权限 |
| `STAFF` | 普通员工，基本操作权限 |

---

## ✅ 测试检查清单

- [ ] 成功登录并获取 token
- [ ] Token 自动保存到环境变量
- [ ] 测试用户名错误的情况
- [ ] 测试密码错误的情况
- [ ] 测试空用户名/密码的验证
- [ ] 健康检查接口正常
- [ ] 使用 token 访问受保护接口（如有）
- [ ] Token 过期后重新登录

---

**最后更新时间**: 2025-01-19
**文档版本**: v1.0
**维护团队**: WMS Development Team
