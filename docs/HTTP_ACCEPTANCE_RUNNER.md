# 独立 HTTP 验收集执行器

## 状态

已提供可复现的**只读预检**入口：`src/test/scripts/invoke-http-acceptance-suite.ps1`。

当前登记的 V4.5 与 V4.5.2 套件会创建主数据和业务单据，并包含收货、销售审批、出库确认等库存写入。因此清单将两套套件均标记为 `safeToExecute=false`、`cleanupStrategy=NOT_AUTOMATED`；执行器会明确拒绝真实执行。

## 只读预检

```powershell
./src/test/scripts/invoke-http-acceptance-suite.ps1 -Mode Preflight -Suite All
```

预检只会：

- 读取套件清单、PowerShell 脚本和 `.http` 文件；
- 计算 SHA-256、HTTP 方法数量和潜在写操作数量；
- 发送 `GET /health/check`；
- 在 `target/http-acceptance-preflight/` 输出 JSON 报告。

预检不会登录、创建数据、确认 ASN、收货、拣货、盘点、批准订单、调整库存或删除数据。

## 未来真实执行门禁

真实执行被以下条件共同限制：

1. 用户对实际写入的明确批准；
2. `-Mode Execute -AllowBusinessWrites`；
3. 已存在的、经审阅的清理方案文件 `-CleanupPlanPath`；
4. 非回环地址还必须显式传入 `-AllowRemoteHost`；
5. 清单中每个套件必须声明并实现 `safeToExecute=true` 的自动清理策略。

当前两套历史套件没有自动清理策略，因此即使提供上述参数，执行器也会拒绝执行。不能把此拒绝绕过为直接运行原脚本。

## 套件清单

套件登记在 `src/test/scripts/http-acceptance-suites.json`。新增或调整套件时必须同步填写：脚本路径、`.http` 文件、写入影响、清理策略和执行资格。
