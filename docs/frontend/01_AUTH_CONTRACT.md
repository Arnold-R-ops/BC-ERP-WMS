# 认证契约（前端必读）

## 1. 登录

```
POST /api/auth/login
Body: { "username": "...", "password": "..." }
200 → {
  "token": "eyJ...",            // JWT
  "tokenType": "Bearer",
  "username": "admin",
  "currentRole": "SUPER_ADMIN",  // 当前生效角色（权限按它算）
  "availableRoles": ["SUPER_ADMIN", "WAREHOUSE_ADMIN"],
  "expiresIn": 86400000,         // 毫秒（24h）
  "mustChangePassword": false    // ⚠️ 见第 4 节
}
401 → { "errorKey": "AUTH_INVALID_CREDENTIALS", ... }   // 不区分账号/密码错（防枚举）
403 → USER_ACCOUNT_DISABLED / USER_NO_ROLES
```

之后所有请求带头：`Authorization: Bearer {token}`。

## 2. 角色切换（多角色用户免重登）

```
POST /api/auth/switch-role
Body: { "targetRoleCode": "WAREHOUSE_ADMIN" }
200 → { "token": "新JWT", "currentRole": "...", ... }
```
**旧 token 仍有效但角色未变——切换后必须立刻用新 token 覆盖本地存储并刷新权限状态。**

## 3. 改密码

```
PUT /api/users/me/password        // 任何登录用户可调
Body: { "oldPassword": "...", "newPassword": "..." }
204 成功
400 → PASSWORD_INCORRECT（旧密码错）/ PASSWORD_TOO_WEAK（8~64位且含字母和数字）/ PASSWORD_SAME_AS_OLD
```

管理员重置他人密码：`POST /api/users/{id}/reset-password` → 返回一次性 `temporaryPassword`（界面上醒目展示一次并提示复制，刷新即不可再见）。

## 4. 强制改密流程（重点实现）

登录响应 `mustChangePassword=true` 时（管理员刚重置过该账号密码）：

1. 前端**立即弹出不可关闭的改密对话框**（除登出外无其他出路）
2. 此状态下后端已在服务端拦截：除 `/api/auth/**` 和 `PUT /api/users/me/password` 外，**一切请求都会 403**——不要试图绕过
3. 改密成功（204）后：让用户用新密码重新登录（清 token 回登录页）

## 5. 401 / 403 语义区分（全局拦截器）

| 状态 | 含义 | 前端动作 |
|---|---|---|
| 401 | 未登录 / token 过期或无效 | 清本地 token → 跳登录页 |
| 403 + 正在强制改密 | 见第 4 节 | 保持改密对话框 |
| 403 其他 | 已登录但当前角色无权限 | toast「无权限」，**不要**跳登录页；若 availableRoles>1 可提示切换角色 |

## 6. 错误响应统一结构

```
{ "errorKey": "STOCK_INSUFFICIENT", "message": "...", "params": { ... }, "timestamp": "..." }
```
`errorKey` 是稳定契约（用于 i18n 映射），`message` 是后端兜底文案。

## 7. Token 存储与过期

- 存 localStorage（本系统当前无 refresh token 机制；expiresIn 24h，过期即 401 → 重登录）
- 登出：前端清 token 即可（后端无黑名单，属已知取舍）
