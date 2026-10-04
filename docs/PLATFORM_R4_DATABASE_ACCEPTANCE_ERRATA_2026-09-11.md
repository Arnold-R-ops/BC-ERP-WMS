# r4 验收报告字段名勘误（补充记录）

日期：2026-09-11。本补充不替换、不改写原 r4 封存报告、输入快照、数据库结果、XML 或哈希记录。

原报告：[PLATFORM_R4_DATABASE_ACCEPTANCE_2026-09-11.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_R4_DATABASE_ACCEPTANCE_2026-09-11.md>)，第 106 行。

原报告 SHA-256 保持：`90854795447ce0cd968631b114ad81b2076dbad45968e95d59bb68d9ec58d8b5`。

勘误内容：原文中的 `browser_session_hash` 为文档笔误，实际字段应为 **`browser_binding_hash`**。准确表述为：

> V4_90 后，旧会话原字段保持不变，新增 `browser_binding_hash` 保持 NULL；没有回填成可重新使用的浏览器会话。

核对依据：

- [V4_90 实际迁移 SQL](<D:/ERP_WMS/BC warehouse/2G/src/main/resources/db/migration/V4_90__Platform_Browser_Session_Scope.sql:3>) 新增的是 `platform_sessions.browser_binding_hash varchar(64)`，允许 NULL 或 64 位小写十六进制。
- 该 SQL SHA-256：`12a355a7707a06e060b23a3c40902fb189404def1e9b59afa4c7d62e0df9a1c2`，本次未修改。
- [哨兵核验模块](<D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-upgrade-sentinels.mjs:94>) 实际检查的同样是 `browser_binding_hash`；这是文档勘误，不是数据库字段改名或迁移变更。

原结果不变：r4 完整迁移通过、37 项数据库业务测试通过、26 项未执行，原批次仍为 FAILED，已正常关闭。解析工具修复和对留存 XML 的只读回归不把 r4 改判为整批通过，也不构成发布候选版本或上线授权。
