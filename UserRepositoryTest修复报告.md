# UserRepositoryTest 修复报告

**修复日期**: 2026-01-19
**测试类**: `UserRepositoryTest`
**测试数量**: 13 个测试方法

---

## 🔍 问题诊断

### 错误现象

**测试类**: `src/test/java/com/wms/system/repository/UserRepositoryTest.java`

**错误信息**:
```
org.springframework.beans.factory.BeanCreationException:
Error creating bean with name 'dataSource':
Failed to replace DataSource with an embedded database for tests.
If you want an embedded database please put a supported one on the classpath
or tune the replace attribute of @AutoConfigureTestDatabase.
```

### 根本原因

**问题分析**:

1. **@DataJpaTest 默认行为**:
   - `@DataJpaTest` 注解默认会尝试使用嵌入式数据库（如 H2）
   - 这是为了提供快速、隔离的单元测试环境

2. **项目配置变更**:
   - 项目已经移除了 H2 依赖（pom.xml 中没有 H2）
   - 用户明确要求所有测试都使用 PostgreSQL

3. **配置冲突**:
   - Spring Boot 找不到嵌入式数据库（H2 已移除）
   - 但 `@DataJpaTest` 仍然尝试替换数据源
   - 导致 Spring 上下文加载失败

### 错误堆栈关键部分

```
Caused by: java.lang.IllegalStateException:
Failed to replace DataSource with an embedded database for tests.
If you want an embedded database please put a supported one on the classpath
or tune the replace attribute of @AutoConfigureTestDatabase.

at org.springframework.boot.test.autoconfigure.jdbc.TestDatabaseAutoConfiguration
   $EmbeddedDataSourceFactory.getEmbeddedDatabase(TestDatabaseAutoConfiguration.java:188)
```

---

## ✅ 修复方案

### 修复内容

**文件**: `src/test/java/com/wms/system/repository/UserRepositoryTest.java`

**修复位置**: 类注解部分（第 3-33 行）

### 步骤 1: 添加导入语句

**修改前**:
```java
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
```

**修改后**:
```java
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
```

### 步骤 2: 更新类注解

**修改前**:
```java
/**
 * UserRepository 单元测试
 *
 * 使用 @DataJpaTest 注解：
 * - 自动配置内存数据库（H2）
 * - 自动配置 JPA 相关组件
 * - 每个测试方法执行后自动回滚事务
 * - 不加载完整的 Spring 上下文（性能更好）
 *
 * @author WMS Team
 * @since 2026-01-18
 */
@DataJpaTest
@DisplayName("UserRepository 单元测试")
class UserRepositoryTest {
```

**修改后**:
```java
/**
 * UserRepository 单元测试
 *
 * 测试 UserRepository 的所有自定义查询方法
 *
 * 使用 @DataJpaTest 注解：
 * - 自动配置 JPA 相关组件
 * - 每个测试方法执行后自动回滚事务
 * - 不加载完整的 Spring 上下文（性能更好）
 *
 * 使用 @AutoConfigureTestDatabase(replace = NONE)：
 * - 不使用嵌入式数据库（H2）
 * - 使用配置文件中的 PostgreSQL 数据库
 *
 * 使用 @ActiveProfiles("test")：
 * - 使用 application-test.yml 配置文件
 *
 * @author WMS Team
 * @since 2026-01-18
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("UserRepository 单元测试")
class UserRepositoryTest {
```

---

## 📋 修复详解

### @AutoConfigureTestDatabase 注解说明

**作用**: 控制测试数据库的自动配置行为

**参数**:
```java
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
```

**replace 属性值**:

| 值 | 含义 | 使用场景 |
|---|------|---------|
| `ANY` | 替换为任何嵌入式数据库（默认值） | 使用 H2、Derby 等嵌入式数据库 |
| `AUTO_CONFIGURED` | 仅替换自动配置的数据源 | 部分使用嵌入式数据库 |
| **`NONE`** | **不替换数据源，使用配置文件中的数据库** | **使用真实数据库（PostgreSQL）** |

**我们的选择**: `NONE` - 使用 PostgreSQL 测试数据库

### @ActiveProfiles 注解说明

**作用**: 激活指定的 Spring Profile

**参数**:
```java
@ActiveProfiles("test")
```

**效果**:
- 加载 `application-test.yml` 配置文件
- 使用测试环境的数据库配置:
  ```yaml
  spring:
    datasource:
      url: jdbc:postgresql://localhost:5432/wms_db_test
      driver-class-name: org.postgresql.Driver
      username: postgres
      password: 123465
  ```

---

## 🔄 测试流程

### 修复前（失败）

```
1. @DataJpaTest 启动
   ↓
2. Spring Boot 尝试替换数据源为嵌入式数据库
   ↓
3. 查找 H2 依赖 → 未找到
   ↓
4. 抛出异常: "Failed to replace DataSource with an embedded database"
   ↓
5. ❌ Spring 上下文加载失败
   ↓
6. ❌ 所有测试跳过
```

### 修复后（成功）

```
1. @DataJpaTest 启动
   ↓
2. @AutoConfigureTestDatabase(replace = NONE) 禁止替换数据源
   ↓
3. @ActiveProfiles("test") 加载 application-test.yml
   ↓
4. 使用配置文件中的 PostgreSQL 数据源
   ↓
5. ✅ Spring 上下文加载成功
   ↓
6. ✅ 连接到 wms_db_test 数据库
   ↓
7. ✅ 执行测试（每个测试后自动回滚）
```

---

## 📊 测试用例列表

### 基础查询测试（4 个）
1. ✅ 根据用户名查询用户 - 成功
2. ✅ 根据用户名查询用户 - 用户不存在
3. ✅ 检查用户名是否存在 - 存在
4. ✅ 检查用户名是否存在 - 不存在

### 条件查询测试（4 个）
5. ✅ 根据角色查询用户列表
6. ✅ 查询所有启用的用户
7. ✅ 根据用户名模糊查询
8. ✅ 根据角色和启用状态查询

### CRUD 操作测试（5 个）
9. ✅ 保存新用户
10. ✅ 更新用户信息
11. ✅ 删除用户
12. ✅ 统计用户总数
13. ✅ 查询所有用户
14. ✅ 批量保存用户

---

## 🚀 验证步骤

### 步骤 1: 确认 PostgreSQL 测试数据库存在

**检查数据库**:
```sql
-- 连接到 PostgreSQL
psql -U postgres

-- 检查测试数据库是否存在
\l wms_db_test
```

**如果不存在，创建数据库**:
```sql
CREATE DATABASE wms_db_test
    WITH OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'Chinese (Simplified)_China.936'
    LC_CTYPE = 'Chinese (Simplified)_China.936'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
```

### 步骤 2: 重新构建项目（可选但推荐）

在 IntelliJ IDEA 中:
```
Build → Rebuild Project
```

### 步骤 3: 运行测试

1. 找到测试类: `src/test/java/com/wms/system/repository/UserRepositoryTest.java`
2. **右键测试类**
3. 选择 **Run 'UserRepositoryTest'**
4. 等待测试运行完成

### 步骤 4: 检查测试结果

#### ✅ 预期结果

**所有测试应该全部通过**:
```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试输出中应该看到：
- ✅ Spring Boot 成功启动
- ✅ 连接到 PostgreSQL 测试数据库
- ✅ 14 个测试全部通过（绿色勾号）
- ✅ 每个测试后事务自动回滚
- ✅ 无红色错误或异常

---

## 📝 技术说明

### @DataJpaTest vs @SpringBootTest

| 注解 | 加载范围 | 数据库配置 | 事务管理 | 性能 |
|-----|---------|-----------|---------|------|
| `@DataJpaTest` | 只加载 JPA 组件 | 默认使用嵌入式数据库 | 自动回滚 | 快 ⚡ |
| `@SpringBootTest` | 加载完整应用上下文 | 使用配置文件中的数据库 | 需手动配置 | 慢 🐌 |

**我们的选择**:
- 使用 `@DataJpaTest` (性能更好)
- 添加 `@AutoConfigureTestDatabase(replace = NONE)` (使用 PostgreSQL)
- 添加 `@ActiveProfiles("test")` (使用测试配置)

**优势**:
- ✅ 快速启动（只加载 JPA 组件）
- ✅ 自动事务回滚（测试隔离）
- ✅ 使用真实数据库（PostgreSQL）
- ✅ 测试真实的 SQL 查询

### PostgreSQL vs H2 对比

| 特性 | H2（嵌入式） | PostgreSQL（真实） |
|-----|------------|-------------------|
| 启动速度 | 极快 ⚡ | 较快 |
| SQL 兼容性 | 部分兼容 | 100% 兼容 |
| 测试准确性 | 中等 | 高 ✅ |
| 环境依赖 | 无 | 需要 PostgreSQL 服务 |
| 适用场景 | 单元测试 | 集成测试、Repository 测试 |

**我们的选择**: PostgreSQL
- ✅ 测试真实的 PostgreSQL 特性
- ✅ 验证实际的 SQL 查询
- ✅ 发现数据库特定的问题

### 事务回滚机制

**@DataJpaTest 自动配置**:
```java
@Transactional  // 每个测试方法都在事务中
@Rollback       // 测试完成后自动回滚
```

**效果**:
```
测试开始
  ↓
开启事务
  ↓
执行 setUp() - 创建测试数据
  ↓
执行测试方法
  ↓
测试通过/失败
  ↓
回滚事务 ← 数据库恢复到测试前状态
  ↓
下一个测试从干净状态开始
```

**优点**:
- ✅ 测试之间完全隔离
- ✅ 无需手动清理数据
- ✅ 测试可以并行运行

---

## ⚠️ 如果测试仍然失败

### 场景 A: PostgreSQL 数据库不存在

**错误信息**:
```
PSQLException: FATAL: database "wms_db_test" does not exist
```

**解决方案**:
运行 SQL 创建测试数据库（详见上面"步骤 1"）

### 场景 B: PostgreSQL 服务未启动

**错误信息**:
```
PSQLException: Connection refused
```

**解决方案**:
1. 启动 PostgreSQL 服务
   - Windows: `net start postgresql-x64-16`
   - macOS: `brew services start postgresql`
   - Linux: `sudo systemctl start postgresql`

### 场景 C: 数据库密码错误

**错误信息**:
```
PSQLException: FATAL: password authentication failed for user "postgres"
```

**解决方案**:
修改 `application-test.yml` 中的密码为正确的密码

### 场景 D: 编译缓存问题

**解决方案**:
1. 完全关闭 IntelliJ IDEA
2. 删除 `target` 目录
3. 重新打开项目
4. `Build` → `Rebuild Project`
5. 再次运行测试

---

## 📚 相关文档

修改的文件：
- ✅ `src/test/java/com/wms/system/repository/UserRepositoryTest.java`

相关配置文件（未修改）：
- `src/test/resources/application-test.yml` - PostgreSQL 测试配置

相关测试类（类似修复可能需要）：
- 所有使用 `@DataJpaTest` 的 Repository 测试类都需要相同的修复

---

## ✅ 修复完成清单

修复完成后，请确认：

- [ ] IntelliJ IDEA 中没有红色编译错误
- [ ] PostgreSQL 服务正在运行
- [ ] 测试数据库 `wms_db_test` 存在
- [ ] `Build → Rebuild Project` 成功完成（无错误）
- [ ] `UserRepositoryTest` 运行时，Spring Boot 启动成功
- [ ] **14 个测试全部通过（14/14）** ⭐
- [ ] 测试输出中无异常堆栈
- [ ] 日志中无 ERROR 级别日志

---

## 🎊 修复总结

**修复日期**: 2026-01-19
**修复内容**: 配置 @DataJpaTest 使用 PostgreSQL 而不是嵌入式数据库
**修复方式**:
- 添加 `@AutoConfigureTestDatabase(replace = NONE)` 注解
- 添加 `@ActiveProfiles("test")` 注解

**影响范围**: 所有使用 `@DataJpaTest` 的 Repository 测试类
**最终结果**: ✅ **预期所有 14 个测试全部通过**

---

## 🚀 立即行动

**现在请在 IntelliJ IDEA 中运行测试！**

```
右键 UserRepositoryTest → Run 'UserRepositoryTest'
```

**预期输出**:
```
✅ Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
✅ BUILD SUCCESS
```

如果全部通过，恭喜！Repository 测试修复完成。
如果仍有失败，请提供错误信息以便进一步分析。

---

## 💡 最佳实践建议

### 未来添加新的 Repository 测试时

**模板**:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("YourRepository 单元测试")
class YourRepositoryTest {
    // 测试代码
}
```

### 何时使用 @DataJpaTest vs @SpringBootTest

**使用 @DataJpaTest 当**:
- ✅ 只测试 Repository 层
- ✅ 需要快速测试
- ✅ 测试 JPA 查询方法

**使用 @SpringBootTest 当**:
- ✅ 测试完整的业务流程
- ✅ 需要 Service、Controller 等多层交互
- ✅ 集成测试（如 AuthControllerIntegrationTest）

### 测试数据库管理

**推荐做法**:
1. 使用独立的测试数据库（`wms_db_test`）
2. 每次测试后自动回滚（`@Transactional`）
3. 定期清理测试数据库（可选）
4. 不要在测试数据库中存储重要数据
