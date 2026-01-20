# UserRepositoryTest 索引冲突修复报告

**修复日期**: 2026-01-19
**问题**: PostgreSQL 索引已存在导致测试失败
**测试类**: `UserRepositoryTest`

---

## 🔍 问题诊断

### 错误现象

**错误信息**:
```
org.postgresql.util.PSQLException: 错误: 关系 "idx_location_id" 已经存在
org.postgresql.util.PSQLException: 错误: 关系 "idx_product_id" 已经存在
```

**影响**:
- 所有 14 个 UserRepositoryTest 测试全部失败
- 测试状态显示为 "error" 而非 "failed"

### 根本原因

**问题分析**:

1. **之前的测试遗留**:
   - 之前运行 `AuthControllerIntegrationTest` 时使用了 `ddl-auto: create-drop`
   - 该配置理论上应该在测试结束时删除所有表和索引
   - 但在某些情况下，`create-drop` 的 `drop` 操作没有完全执行

2. **create-drop 的局限性**:
   - `create-drop` 依赖于 SessionFactory 的生命周期
   - 在使用真实数据库（PostgreSQL）时，如果应用异常终止或会话未正确关闭
   - `drop` 操作可能不会执行，导致表和索引残留

3. **@DataJpaTest 的行为**:
   - 当 `@DataJpaTest` 尝试启动时，Hibernate 执行 DDL 创建表
   - 由于索引已经存在，导致 DDL 执行失败
   - Spring 上下文加载失败，所有测试被跳过

### 错误堆栈关键部分

```
Caused by: org.postgresql.util.PSQLException:
错误: 关系 "idx_location_id" 已经存在

at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse
at org.postgresql.core.v3.QueryExecutorImpl.processResults
```

---

## ✅ 修复方案（3 步）

### 修复 #1: 修改 ddl-auto 配置

**文件**: `src/test/resources/application-test.yml`

**修改内容**:

**修改前**:
```yaml
  jpa:
    hibernate:
      ddl-auto: create-drop  # 每次测试启动时重建表结构
    show-sql: false
    properties:
      hibernate:
        format_sql: false
```

**修改后**:
```yaml
  jpa:
    hibernate:
      ddl-auto: create  # 每次测试启动时重建表结构（强制重新创建）
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        # 添加以下属性以确保正确处理已存在的表
        hbm2ddl:
          auto: create
        globally_quoted_identifiers: false
        jdbc:
          lob:
            non_contextual_creation: true
```

**修改说明**:
- ✅ `create-drop` → `create`: 避免依赖 drop 操作
- ✅ 添加 `hbm2ddl.auto: create`: 明确指定创建模式
- ✅ 添加 `globally_quoted_identifiers: false`: 避免标识符引用问题
- ✅ 添加 `jdbc.lob.non_contextual_creation: true`: PostgreSQL 兼容性

---

### 修复 #2: 创建数据库清理脚本

**文件**: `clean-test-database.sql` (新建)

**内容**:
```sql
-- 删除所有表（按照依赖顺序）
DROP TABLE IF EXISTS sys_user_role CASCADE;
DROP TABLE IF EXISTS sys_role_permission CASCADE;
DROP TABLE IF EXISTS sys_role_inherit CASCADE;
DROP TABLE IF EXISTS sys_permission CASCADE;
DROP TABLE IF EXISTS sys_role CASCADE;
DROP TABLE IF EXISTS stock_transactions CASCADE;
DROP TABLE IF EXISTS inventory_batch CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;
DROP TABLE IF EXISTS purchase_order_item CASCADE;
DROP TABLE IF EXISTS purchase_order CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS product_spu CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 删除所有序列
DROP SEQUENCE IF EXISTS inventory_batch_seq CASCADE;
DROP SEQUENCE IF EXISTS inventory_seq CASCADE;
-- ... 其他序列

-- 删除所有索引
DROP INDEX IF EXISTS idx_location_id CASCADE;
DROP INDEX IF EXISTS idx_product_id CASCADE;
-- ... 其他索引

SELECT 'wms_db_test 数据库已清空，可以运行测试' AS status;
```

**用途**:
- 手动清空测试数据库，确保干净的测试环境
- 在运行测试前执行，避免索引冲突

---

### 修复 #3: 创建一键清理脚本

**文件**: `clean-test-db.bat` (新建)

**内容**:
```batch
@echo off
echo 正在清空测试数据库 wms_db_test...
psql -U postgres -d wms_db_test -f clean-test-database.sql

if %errorlevel% equ 0 (
    echo ✅ 测试数据库已清空成功
    echo 现在可以运行测试
) else (
    echo ❌ 清空失败，请检查 PostgreSQL 服务
)
pause
```

**用途**:
- Windows 一键清理脚本
- 自动执行 SQL 清理脚本并显示结果

---

## 🚀 使用步骤

### 步骤 1: 清空测试数据库

**方式 A: 使用批处理脚本（推荐）**

双击运行:
```
clean-test-db.bat
```

**方式 B: 手动执行 SQL**

在 psql 或 pgAdmin 中执行:
```bash
psql -U postgres -d wms_db_test -f clean-test-database.sql
```

**方式 C: 在 pgAdmin 中手动删除**

1. 打开 pgAdmin
2. 连接到 PostgreSQL
3. 展开 `wms_db_test` 数据库
4. 右键 Schemas → public → 删除所有表

---

### 步骤 2: 重新构建项目（可选但推荐）

在 IntelliJ IDEA 中:
```
Build → Rebuild Project
```

---

### 步骤 3: 运行测试

1. 找到测试类: `src/test/java/com/wms/system/repository/UserRepositoryTest.java`
2. **右键测试类**
3. 选择 **Run 'UserRepositoryTest'**

---

### 步骤 4: 检查测试结果

#### ✅ 预期结果

**所有测试应该全部通过**:
```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试输出中应该看到：
- ✅ Spring Boot 成功启动
- ✅ Hibernate 成功创建所有表
- ✅ 14 个测试全部通过（绿色勾号）
- ✅ 每个测试后事务自动回滚
- ✅ 无索引冲突错误

---

## 📝 技术说明

### create vs create-drop 对比

| 模式 | 启动时行为 | 关闭时行为 | 适用场景 | 问题 |
|-----|-----------|-----------|---------|------|
| `create-drop` | 删除并重建表 | 删除所有表 | 嵌入式数据库 | 异常时 drop 不执行 |
| **`create`** | **删除并重建表** | **保留表** | **真实数据库** | **需手动清理** |
| `update` | 更新表结构 | 保留表 | 开发环境 | 不删除旧数据 |
| `validate` | 验证表结构 | 保留表 | 生产环境 | 不修改数据库 |

**为什么选择 create**:
- ✅ 每次测试都重建表，确保干净环境
- ✅ 不依赖关闭时的 drop 操作
- ✅ 更可靠，适合真实数据库
- ❌ 需要在测试前手动清空数据库（一次性操作）

### create-drop 的问题

**理论行为**:
```
测试启动
  ↓
Hibernate 执行 DROP TABLE
  ↓
Hibernate 执行 CREATE TABLE
  ↓
运行测试
  ↓
测试结束
  ↓
Hibernate 执行 DROP TABLE ← 理论上应该清理
```

**实际问题**:
- 如果测试异常终止，SessionFactory 未正确关闭
- 如果有其他连接占用数据库
- 如果 Spring 上下文加载失败
- **→ DROP 操作不会执行，表和索引残留**

### 为什么使用 CASCADE

```sql
DROP TABLE IF EXISTS users CASCADE;
DROP INDEX IF EXISTS idx_location_id CASCADE;
```

**CASCADE 的作用**:
- 自动删除依赖于该表的外键约束
- 自动删除依赖于该表的索引
- 自动删除依赖于该表的视图和触发器

**不使用 CASCADE 的后果**:
```
ERROR: cannot drop table users because other objects depend on it
DETAIL: constraint fk_user_role_user depends on table users
```

---

## ⚠️ 如果测试仍然失败

### 场景 A: 仍然报索引已存在

**原因**: 数据库未清空

**解决方案**:
1. 确保 `clean-test-db.bat` 成功执行
2. 或手动在 pgAdmin 中删除所有表和索引
3. 或重新创建测试数据库:
   ```sql
   DROP DATABASE wms_db_test;
   CREATE DATABASE wms_db_test;
   ```

---

### 场景 B: 清理脚本执行失败

**错误信息**: `psql: command not found`

**解决方案**:
1. 确保 PostgreSQL bin 目录在 PATH 环境变量中
2. 或使用完整路径:
   ```batch
   "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d wms_db_test -f clean-test-database.sql
   ```

---

### 场景 C: 数据库连接失败

**错误信息**: `Connection refused` 或 `password authentication failed`

**解决方案**:
1. 检查 PostgreSQL 服务是否启动
2. 检查 `application-test.yml` 中的密码是否正确
3. 检查数据库 `wms_db_test` 是否存在

---

### 场景 D: Hibernate 仍然报错

**错误信息**: 各种 Hibernate DDL 错误

**解决方案**:
1. 完全关闭 IntelliJ IDEA
2. 删除 `target` 目录
3. 清空测试数据库（执行 `clean-test-db.bat`）
4. 重新打开项目
5. `Build` → `Rebuild Project`
6. 再次运行测试

---

## 📚 相关文件

### 修改的文件
- ✅ `src/test/resources/application-test.yml` - 修改 ddl-auto 配置

### 新建的文件
- ✅ `clean-test-database.sql` - 数据库清理 SQL 脚本
- ✅ `clean-test-db.bat` - Windows 一键清理脚本

### 相关测试类
- `src/test/java/com/wms/system/repository/UserRepositoryTest.java` - 待运行的测试类

---

## ✅ 修复完成清单

在运行测试前，请确认：

- [ ] 已执行 `clean-test-db.bat` 清空数据库
- [ ] 清理脚本成功执行（显示 ✅ 成功消息）
- [ ] `application-test.yml` 的 `ddl-auto` 已改为 `create`
- [ ] IntelliJ IDEA 中没有红色编译错误
- [ ] `Build → Rebuild Project` 成功完成
- [ ] PostgreSQL 服务正在运行
- [ ] 测试数据库 `wms_db_test` 存在

完成以上步骤后，运行测试：
```
右键 UserRepositoryTest → Run 'UserRepositoryTest'
```

---

## 🎊 修复总结

**修复日期**: 2026-01-19
**修复内容**:
1. 修改 `ddl-auto` 从 `create-drop` 到 `create`
2. 创建数据库清理脚本 `clean-test-database.sql`
3. 创建一键清理工具 `clean-test-db.bat`

**根本原因**: `create-drop` 模式在真实数据库环境下不可靠
**解决方案**: 使用 `create` 模式 + 手动清理数据库

**最终结果**: ✅ **预期所有 14 个测试全部通过**

---

## 💡 最佳实践建议

### 测试数据库管理策略

**推荐做法**:
1. ✅ 使用独立的测试数据库（`wms_db_test`）
2. ✅ 每次运行测试前清空数据库（执行清理脚本）
3. ✅ 使用 `create` 模式而不是 `create-drop`
4. ✅ 每个测试后自动回滚（`@Transactional`）
5. ✅ 定期备份测试数据库（可选）

**避免的做法**:
- ❌ 在测试和生产中使用同一个数据库
- ❌ 依赖 `create-drop` 自动清理
- ❌ 在测试中使用真实数据
- ❌ 测试失败后不清理数据库

### 何时清空测试数据库

**必须清空**:
- ✅ 运行 Repository 测试（`@DataJpaTest`）之前
- ✅ 修改实体类（Entity）之后
- ✅ 修改数据库配置之后
- ✅ 测试报索引/表已存在错误时

**可以不清空**:
- 连续运行同一个测试类多次
- 运行集成测试（`@SpringBootTest`，因为它使用 `validate` 模式）

### CI/CD 集成建议

在持续集成环境中（如 Jenkins、GitLab CI）:
```yaml
# .gitlab-ci.yml 示例
test:
  before_script:
    - psql -U postgres -d wms_db_test -f clean-test-database.sql
  script:
    - mvn clean test -Dspring.profiles.active=test
```

这样确保每次 CI 运行时都有干净的测试环境。

---

## 🚀 立即行动

**第 1 步: 清空数据库**
```
双击运行: clean-test-db.bat
```

**第 2 步: 运行测试**
```
右键 UserRepositoryTest → Run 'UserRepositoryTest'
```

**预期输出**:
```
✅ Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
✅ BUILD SUCCESS
```

如果全部通过，恭喜！Repository 测试修复完成。
如果仍有失败，请提供新的错误信息。
