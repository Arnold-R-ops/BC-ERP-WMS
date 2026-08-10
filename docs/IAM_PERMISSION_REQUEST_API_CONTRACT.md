# 岗位权限申请接口契约

状态：**已实现并完成真实 API 验收**。数据库迁移为 `V4_43__Permission_Request_Workflow.sql`，前端已接入真实接口；本流程会实际授予或撤销角色。

## 1. 已确认规则

- 仅当前激活角色为 `SUPER_ADMIN`、`SECURITY_ADMIN` 的管理员可代目标用户发起申请；不开放员工自助申请。
- 只能申请已启用、可分配的普通岗位权限包：系统业务模板，或 `CUSTOM + APPROVED + ACTIVE` 自定义包。
- `SUPER_ADMIN`、`SECURITY_ADMIN` 及其他受保护特权角色不可申请；`SECURITY_ADMIN` 不可查看、发起或处理受保护身份的申请。
- 管理员不得为自己发起或批准授权申请。
- 审批通过只为目标用户新增请求角色，保留既有角色集和默认角色。
- 申请 `WAREHOUSE_STAFF` 必须携带一个或多个启用仓库；批准时再次校验，角色与仓库分配在同一事务中完成。
- 普通风险申请允许同一有权 IAM 管理员提交和复核；包含 `HIGH` 权限时，必须由不同于申请人的另一名 `SUPER_ADMIN` 批准。`SECURITY_ADMIN` 可驳回高风险申请，但不得批准。
- 拒绝与撤销必须填写意见。撤销不需要再次审批，立即移除本申请授予的角色并使目标用户旧 JWT 失效。
- 不包含有效期、临时权限、跨仓临时授权、员工自助申请、通用多级审批、会签或职责冲突引擎。

## 2. 状态机

| 状态 | 含义 | 可迁移至 |
|---|---|---|
| `PENDING_REVIEW` | 已提交，尚未产生授权 | `APPROVED`、`REJECTED` |
| `APPROVED` | 已原子新增角色并写入审计 | `REVOKED` |
| `REJECTED` | 已驳回，未改变用户角色 | 终态 |
| `REVOKED` | 本申请授予的角色已直接撤销 | 终态 |

`REVOKED` 是授权证据状态，不代表撤销需要审批。

## 3. 资源结构

典型 `PermissionRequestDTO`：

```json
{
  "id": 101,
  "targetUserId": 12,
  "targetUsername": "warehouse.user",
  "requestedRoleId": 7,
  "requestedRoleCode": "WAREHOUSE_STAFF",
  "requestedRoleName": "库管员",
  "warehouses": [{ "id": 48, "code": "WH-SH", "name": "上海仓" }],
  "requestReason": "夜班仓库作业安排",
  "status": "PENDING_REVIEW",
  "highRiskPermissionCount": 3,
  "submittedByUsername": "admin",
  "submittedAt": "2026-08-08T14:00:00",
  "reviewedByUsername": null,
  "reviewedAt": null,
  "reviewComment": null,
  "revokedByUsername": null,
  "revokedAt": null,
  "revocationComment": null
}
```

## 4. 接口

### `GET /api/permission-requests`

仅 IAM 管理员可访问。查询参数：

- `status`：四种状态之一；空值表示全部。
- `targetUserId`、`requestedRoleId`：可选精确过滤。
- `page`：从 0 开始，负数按 0 处理。
- `size`：限制为 1–100。

按提交时间倒序返回 `PermissionRequestPageResponse`：`items`、`total`、`page`、`size`。`SECURITY_ADMIN` 的结果在服务端排除受保护身份。

### `POST /api/permission-requests`

```json
{
  "targetUserId": 12,
  "requestedRoleId": 7,
  "warehouseIds": [48],
  "requestReason": "夜班仓库作业安排"
}
```

成功返回 `201` 和 `PENDING_REVIEW` 资源。创建时服务端重新校验操作者、目标账号、角色状态、可分配性、特权边界、有效权限风险、仓库有效性、角色已分配状态和重复待审申请。提交只创建申请与审计，不授予角色。

### `POST /api/permission-requests/{id}/review`

```json
{
  "approved": true,
  "comment": "排班已核实"
}
```

- 批准：锁定申请、目标用户和请求角色；重算有效权限快照；验证审批隔离；追加角色；处理仓库范围；保留默认角色；推进安全版本；清除权限缓存；写入批准审计。全部处于同一事务。
- 驳回：`comment` 必填，只迁移为 `REJECTED` 并写审计，不修改用户角色、仓库或令牌。
- 非 `PENDING_REVIEW` 状态再次复核返回 `409`。
- 角色有效权限自提交后发生变化时返回 `409`，管理员必须重新申请。

### `POST /api/permission-requests/{id}/revoke`

```json
{
  "comment": "岗位调整，立即收权"
}
```

仅接受 `APPROVED` 申请。服务端锁定申请与目标用户，只移除本申请对应角色；用户必须保留至少一个角色。若移除的是默认角色，选择一个剩余角色作为默认角色；若移除 `WAREHOUSE_STAFF`，同时清空员工仓库分配。角色移除推进安全版本并清理权限缓存，旧 JWT 立即失效。成功后状态为 `REVOKED`。

### `GET /api/permission-requests/{id}/audit`

返回按时间倒序排列的不可变审计快照，动作包括 `CREATE`、`APPROVE`、`REJECT`、`REVOKE`。审计包含操作者身份/当前角色、目标用户与角色快照、状态迁移、仓库 ID、风险计数、权限代码、理由和时间。

审计查询不依赖目标账号仍处于活动状态；目标账号逻辑删除后，`SUPER_ADMIN` 仍可读取历史。`SECURITY_ADMIN` 对受保护身份的隔离仍然有效。

## 5. 数据、并发与审计约束

- `sys_permission_request` 保存申请状态和提交时权限指纹；使用 JPA 乐观版本并在复核/撤销时加悲观写锁。
- `sys_permission_request_warehouse` 保存申请仓库集合。
- `sys_permission_request_audit` 保存无生命周期外键依赖的快照；数据库触发器拒绝 `UPDATE` 和 `DELETE`。
- 部分唯一索引保证同一公司、目标用户和请求角色最多只有一个 `PENDING_REVIEW` 申请。
- 用户和角色在批准时重新锁定、重新校验，不能依赖前端过滤或提交时旧快照。
- 授予、撤销沿用 `UserRoleService` 安全边界，推进 `users.security_version` 并使旧 JWT 在下一请求失败。

## 6. 主要错误与 HTTP 状态

- `400`：角色不可申请、仓库缺失/多余、拒绝或撤销意见缺失。
- `403`：非 IAM 操作者、管理员自授予、`SECURITY_ADMIN` 操作受保护目标、高风险审批人不符合隔离规则。
- `404`：申请、目标用户或请求角色不存在。
- `409`：重复待审申请、角色已分配、目标状态失效、申请状态冲突、权限快照过期。

错误响应继续使用统一 `ErrorResponse.errorKey`，前端通过中英文词条显示。

## 7. 前端接入

- “身份与权限 → 权限申请”页面使用真实 API 分页、筛选、创建、批准、驳回和立即撤销。
- 前端隐藏受保护角色/身份并限制明显不允许的操作，但服务端始终独立校验。
- 页面明确提示批准与撤销会真实改变角色并使目标用户旧令牌失效。
- 实时 OpenAPI 已同步到 `docs/frontend/openapi.json`，类型已生成到 `frontend/src/api/schema.d.ts`。

## 8. 验收结果（2026-08-08）

- Flyway 成功应用 V4.43；3 张表、部分唯一索引和不可变审计触发器均实测存在。
- 数据库实测拒绝审计 `UPDATE` 并回滚。
- 真实 API 30 项通过：普通用户 IAM 越权、自授予、特权角色申请、重复待审、重复审批、高风险同人批准均被拒；不同 `SUPER_ADMIN` 可批准高风险申请。
- 普通风险批准只追加角色并保留默认角色；撤销只移除请求角色；两次变更后的旧 JWT 均被拒。
- `CREATE/APPROVE/REVOKE`、`CREATE/REJECT` 审计链完整；目标用户逻辑删除后审计仍可读取。
- 临时目标账号和第二审批人账号均已逻辑删除，没有活动验收账号残留。
