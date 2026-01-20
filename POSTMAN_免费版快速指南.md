# Postman 免费版 - WMS API 快速测试指南

> ✅ 本指南所有功能均适用于 Postman 免费版

---

## 🚀 5分钟快速上手

### 第一步：创建 Collection

1. 打开 Postman
2. 点击左侧 **Collections**
3. 点击 **+** 或 **Create a collection**
4. 命名为：`WMS 用户功能测试`

### 第二步：配置环境变量（可选但推荐）

1. 点击右上角 **Environments** (眼睛图标旁边)
2. 点击 **+** 创建新环境
3. 命名为：`本地开发环境`
4. 添加变量：

| Variable | Value |
|----------|-------|
| `baseUrl` | `http://localhost:8080` |
| `token` | 留空 |

5. 点击 **Save**
6. 在右上角选择激活这个环境

> 💡 **提示**：新版Postman只有一个 Value 字段，更简单直接！

---

## 🔐 测试 1: 用户登录

### 创建登录请求

1. 在 Collection 中点击 **Add request**
2. 命名为：`登录 - 成功案例`

### 配置请求

**基本信息：**
- 方法：`POST`
- URL：`http://localhost:8080/api/auth/login`
  - (如果配置了环境变量，使用：`{{baseUrl}}/api/auth/login`)

**Headers：**
- Postman 会自动添加 `Content-Type: application/json`
- 无需手动配置（除非没自动添加）

**Body：**
1. 选择 `Body` 标签
2. 选择 `raw`
3. 右侧下拉选择 `JSON`
4. 输入：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

### 添加自动保存 Token 脚本（重要！）

1. 点击 `Tests` 标签
2. 复制以下代码：

```javascript
// 如果登录成功，自动保存 token
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("token", jsonData.token);
    console.log("✅ Token 已保存: " + jsonData.token);
}
```

### 发送请求

1. 点击右上角蓝色 **Send** 按钮
2. 查看下方响应结果

**成功响应示例：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "username": "admin",
  "role": "ADMIN",
  "expiresIn": 86400000
}
```

3. 点击下方 **Console** 查看日志，确认 token 是否保存成功

---

## ✅ 测试 2: 健康检查（验证服务是否运行）

### 创建健康检查请求

1. 在 Collection 中点击 **Add request**
2. 命名为：`健康检查`

### 配置请求

- 方法：`GET`
- URL：`http://localhost:8080/api/auth/health`
- 无需 Body 和 Headers

### 发送请求

点击 **Send**，应该看到：

```json
{
  "status": "UP",
  "service": "AuthenticationService"
}
```

---

## ❌ 测试 3: 登录失败场景

### 测试错误的用户名

1. 复制刚才的登录请求（右键 → Duplicate）
2. 重命名为：`登录 - 用户名错误`
3. 修改 Body：

```json
{
  "username": "wronguser",
  "password": "admin123"
}
```

4. 发送请求，应该得到 **401** 错误：

```json
{
  "errorKey": "AUTH_INVALID_CREDENTIALS",
  "status": 401,
  ...
}
```

### 测试错误的密码

1. 复制登录请求
2. 重命名为：`登录 - 密码错误`
3. 修改 Body：

```json
{
  "username": "admin",
  "password": "wrongpassword"
}
```

4. 发送请求，应该得到 **401** 错误

### 测试空用户名

```json
{
  "username": "",
  "password": "admin123"
}
```

应该得到 **400** 验证错误

---

## 🔑 使用 Token 访问受保护接口（未来使用）

当你需要测试需要登录才能访问的接口时：

### 方法 1：使用 Authorization 标签（推荐）

1. 在请求中点击 `Authorization` 标签
2. Type 选择：`Bearer Token`
3. Token 输入：`{{token}}`（使用环境变量）
4. Postman 会自动添加 Header：`Authorization: Bearer {你的token}`

### 方法 2：手动添加 Header

1. 点击 `Headers` 标签
2. 添加：
   - Key: `Authorization`
   - Value: `Bearer {{token}}`

---

## 📱 测试流程图

```
开始
  ↓
1. 测试健康检查
  ↓
2. 测试成功登录 → 自动保存 Token
  ↓
3. 测试失败场景（错误用户名/密码/空值）
  ↓
4. 使用 Token 测试其他受保护接口（未来）
  ↓
完成
```

---

## 🛠️ 测试前准备检查清单

### 1. 确认应用已启动

在命令行运行：
```bash
mvn spring-boot:run
```

或者直接在 IDEA 中运行 Application 主类。

### 2. 确认数据库中有测试用户

打开数据库工具（如 DBeaver、pgAdmin），连接到 PostgreSQL，执行：

```sql
-- 查看现有用户
SELECT * FROM users;
```

**如果没有用户，创建测试用户：**
```sql
-- 创建管理员账户（密码：admin123）
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
```

> 💡 密码说明：`$2a$10$N9qo8u...` 是 `admin123` 经过 BCrypt 加密后的结果

### 3. 验证服务是否可访问

在浏览器中打开：`http://localhost:8080/api/auth/health`

应该看到：
```json
{"status":"UP","service":"AuthenticationService"}
```

---

## 🐛 常见问题解决

### ❓ 发送请求后显示 "Could not send request"

**原因**：应用未启动或端口错误

**解决**：
1. 检查应用是否运行
2. 检查控制台是否显示 "Started Application in X seconds"
3. 确认端口是 8080

### ❓ 返回 401 "AUTH_INVALID_CREDENTIALS"

**原因**：用户名或密码错误

**解决**：
1. 检查数据库中是否有 `admin` 用户
2. 确认密码是 `admin123`（注意大小写）
3. 查看应用控制台日志

### ❓ 返回 500 错误

**原因**：数据库连接失败或后端错误

**解决**：
1. 检查 PostgreSQL 是否运行
2. 检查 `application.yml` 中的数据库配置
3. 查看应用控制台的详细错误日志

### ❓ Token 保存失败

**原因**：未配置环境变量或 Tests 脚本有误

**解决**：
1. 确认已创建并激活环境
2. 检查 Tests 脚本是否正确
3. 查看 Postman Console（View → Show Postman Console）

---

## 💡 免费版 Postman 小技巧

### 1. 使用 Collection Runner 批量测试

1. 点击 Collection 右侧的 `...`
2. 选择 `Run collection`
3. 选择要运行的请求
4. 点击 `Run` 自动执行所有测试

### 2. 复制请求快速创建测试用例

- 右键请求 → `Duplicate`
- 修改名称和参数即可

### 3. 查看请求历史

- 左侧 `History` 标签可以查看所有历史请求
- 方便重新测试

### 4. 导出 Collection 分享给团队

1. 右键 Collection → `Export`
2. 选择 `Collection v2.1`
3. 保存为 JSON 文件
4. 团队成员可以 `Import` 这个文件

---

## 📊 测试结果记录表

| 测试用例 | 请求方法 | URL | 预期状态码 | 实际结果 | 通过 |
|---------|---------|-----|-----------|---------|------|
| 健康检查 | GET | /api/auth/health | 200 | | ⬜ |
| 成功登录 | POST | /api/auth/login | 200 | | ⬜ |
| 用户名错误 | POST | /api/auth/login | 401 | | ⬜ |
| 密码错误 | POST | /api/auth/login | 401 | | ⬜ |
| 空用户名 | POST | /api/auth/login | 400 | | ⬜ |
| 空密码 | POST | /api/auth/login | 400 | | ⬜ |

---

## 📸 关键步骤截图参考

### 创建环境变量的位置
```
右上角 → Environments → + → 添加变量 → Save
```

### 配置 Bearer Token 的位置
```
请求页面 → Authorization 标签 → Type: Bearer Token → Token: {{token}}
```

### 查看 Console 日志
```
底部 Console 标签 或 View → Show Postman Console
```

---

## ✅ 完成检查清单

测试完成后，确认以下项目：

- [ ] 应用成功启动在 8080 端口
- [ ] 数据库中有测试用户 `admin`
- [ ] 健康检查接口返回 200
- [ ] 成功登录并获取 token
- [ ] Token 自动保存到环境变量
- [ ] 测试用户名错误返回 401
- [ ] 测试密码错误返回 401
- [ ] 测试空值验证返回 400
- [ ] 能够使用 token 访问受保护接口（如有）

---

## 🎯 下一步

测试完用户登录功能后，您可以：

1. **测试其他业务接口**（库存、采购单等）
2. **创建更多测试用例**（边界值、并发等）
3. **集成到 CI/CD**（使用 Newman 命令行工具）

---

**📌 快速参考**

- **Base URL**: `http://localhost:8080`
- **测试账号**: `admin` / `admin123`
- **Token 有效期**: 24小时
- **文档版本**: v1.0 (免费版专用)

**有问题？** 检查应用控制台日志 或 Postman Console 日志
