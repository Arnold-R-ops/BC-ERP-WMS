# Postman 超简化版 - 手动测试指南

> ✅ 适用于最新版免费 Postman，无需任何脚本

---

## 🎯 测试策略

**不需要环境变量，不需要脚本，直接手动复制粘贴 Token！**

---

## 第一步：测试健康检查（验证服务运行）

### 创建请求

1. 打开 Postman
2. 点击 **New** → **HTTP Request**
3. 配置：
   - 方法：`GET`
   - URL：`http://localhost:8080/api/auth/health`
4. 点击 **Send**

### 预期结果

```json
{
  "status": "UP",
  "service": "AuthenticationService"
}
```

✅ 如果看到这个，说明服务正常运行！

---

## 第二步：测试用户登录

### 创建登录请求

1. 点击 **New** → **HTTP Request**
2. 配置请求：
   - 方法：`POST`
   - URL：`http://localhost:8080/api/auth/login`

3. 配置 Body：
   - 点击 **Body** 标签
   - 选择 **raw**
   - 右侧下拉选择 **JSON**
   - 输入以下内容：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

4. 点击 **Send**

### 预期结果（成功）

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTY0MzY3NjAwMCwiZXhwIjoxNjQzNzYyNDAwfQ.xxxxxxxxxxxxx",
  "tokenType": "Bearer",
  "username": "admin",
  "role": "ADMIN",
  "expiresIn": 86400000
}
```

### 🔑 保存 Token（重要！）

**手动复制 Token：**
1. 在响应中找到 `token` 字段
2. **双击 token 的值**（那一长串字符）
3. **Ctrl+C 复制**
4. 粘贴到记事本或其他地方保存

示例：
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTY0MzY3NjAwMCwiZXhwIjoxNjQzNzYyNDAwfQ.xxxxxxxxxxxxx
```

---

## 第三步：测试失败场景

### 测试 1：错误的用户名

复制刚才的登录请求，修改 Body：

```json
{
  "username": "wronguser",
  "password": "admin123"
}
```

**预期结果**：401 错误
```json
{
  "errorKey": "AUTH_INVALID_CREDENTIALS",
  "status": 401
}
```

### 测试 2：错误的密码

```json
{
  "username": "admin",
  "password": "wrongpassword"
}
```

**预期结果**：401 错误

### 测试 3：空用户名

```json
{
  "username": "",
  "password": "admin123"
}
```

**预期结果**：400 验证错误

### 测试 4：空密码

```json
{
  "username": "admin",
  "password": ""
}
```

**预期结果**：400 验证错误

---

## 🔐 第四步：使用 Token 访问受保护接口（未来使用）

当你需要测试需要登录的接口时：

### 方法 1：使用 Authorization 标签（推荐）

1. 创建新请求（例如：`GET http://localhost:8080/api/users/me`）
2. 点击 **Authorization** 标签
3. Type 选择：**Bearer Token**
4. 在 Token 框中粘贴你之前保存的 token

### 方法 2：手动添加 Header

1. 点击 **Headers** 标签
2. 添加一行：
   - Key: `Authorization`
   - Value: `Bearer ` + 你的token

**注意**：`Bearer` 后面有一个空格！

示例：
```
Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTY0MzY3NjAwMCwiZXhwIjoxNjQzNzYyNDAwfQ.xxxxxxxxxxxxx
```

---

## 📋 测试检查清单

### 测试前准备

- [ ] 应用已启动（端口 8080）
- [ ] PostgreSQL 数据库运行中
- [ ] 数据库中有测试用户 `admin`

### 接口测试

- [ ] 健康检查返回 200 OK
- [ ] 成功登录返回 200 + token
- [ ] 手动复制并保存了 token
- [ ] 错误用户名返回 401
- [ ] 错误密码返回 401
- [ ] 空用户名返回 400
- [ ] 空密码返回 400

---

## 🛠️ 测试前准备：创建测试用户

如果数据库中没有用户，需要先创建。

### 方法 1：使用数据库工具执行 SQL

打开 DBeaver 或 pgAdmin，连接到数据库，执行：

```sql
-- 查看现有用户
SELECT * FROM users;

-- 创建管理员测试账户
-- 用户名: admin
-- 密码: admin123（已加密）
INSERT INTO users (username, password, role, enabled, display_name, created_at, updated_at)
VALUES (
  'admin',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'ADMIN',
  true,
  '管理员',
  NOW(),
  NOW()
);

-- 验证创建成功
SELECT id, username, role, enabled FROM users;
```

### 方法 2：使用 Postman 发送创建用户请求（如果有接口）

如果你的系统有注册接口，可以通过 API 创建。

---

## 🐛 常见问题

### ❓ 发送请求显示 "Error: connect ECONNREFUSED"

**原因**：应用未启动

**解决**：
1. 在命令行运行：`mvn spring-boot:run`
2. 或在 IDEA 中运行主类
3. 等待看到 "Started Application in X seconds"

### ❓ 返回 401 错误

**原因**：用户名或密码错误

**解决**：
1. 检查数据库中是否有 `admin` 用户
2. 确认密码是 `admin123`
3. 检查 Body 中的 JSON 格式是否正确

### ❓ 返回 500 错误

**原因**：数据库连接失败

**解决**：
1. 确认 PostgreSQL 正在运行
2. 检查 `application.yml` 中的数据库配置：
   - url: `jdbc:postgresql://localhost:5432/wms_db`
   - username: `postgres`
   - password: `123465`

### ❓ Token 无法使用

**原因**：Token 格式错误或已过期

**解决**：
1. 确认 Authorization header 格式：`Bearer {token}`
2. 注意 `Bearer` 后面有一个空格
3. 重新登录获取新 token（有效期24小时）

---

## 💡 快速测试技巧

### 1. 保存请求到 Collection

测试完一个接口后：
1. 点击右上角 **Save**
2. 创建或选择 Collection
3. 命名请求（如：登录-成功案例）

### 2. 复制请求快速创建测试用例

1. 在左侧请求列表中，右键请求
2. 选择 **Duplicate**
3. 修改名称和参数

### 3. 使用 History 查看历史请求

左侧 **History** 标签可以查看所有历史请求，方便重新测试。

---

## 📊 测试结果记录

| 测试用例 | 方法 | 结果 | 状态码 | 通过 |
|---------|------|------|-------|------|
| 健康检查 | GET | | | ⬜ |
| 成功登录 | POST | | | ⬜ |
| 用户名错误 | POST | | | ⬜ |
| 密码错误 | POST | | | ⬜ |
| 空用户名 | POST | | | ⬜ |
| 空密码 | POST | | | ⬜ |

---

## 🎯 完整测试流程

```
1. 启动应用
   ↓
2. 测试健康检查（GET /api/auth/health）
   ↓
3. 测试成功登录（POST /api/auth/login）
   ↓
4. 手动复制保存 Token
   ↓
5. 测试各种失败场景（401、400）
   ↓
6. 使用 Token 测试受保护接口（未来）
```

---

## 🎓 测试账号信息

| 字段 | 值 |
|------|-----|
| 用户名 | `admin` |
| 密码 | `admin123` |
| 角色 | `ADMIN` |
| Token 有效期 | 24小时 |

---

## ✅ 测试完成标准

当你完成以下所有测试，说明用户功能正常：

1. ✅ 健康检查接口正常响应
2. ✅ 正确的用户名密码能成功登录
3. ✅ 获取到有效的 JWT Token
4. ✅ 错误的凭据返回 401
5. ✅ 空值验证返回 400
6. ✅ Token 可以用于访问受保护接口

---

**快速参考**
- Base URL: `http://localhost:8080`
- 测试账号: `admin` / `admin123`
- Token 格式: `Bearer {token}`
- 文档版本: v1.0 (超简化手动版)
