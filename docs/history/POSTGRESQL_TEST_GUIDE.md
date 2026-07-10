# PostgreSQL 测试环境配置指南

## ✅ 已完成的修改

### 1. 测试配置文件更新
**文件**: `src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db_test
    driver-class-name: org.postgresql.Driver
    username: postgres
    password: 123465
  jpa:
    database: POSTGRESQL
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: create-drop  # 测试时自动创建表，测试后自动删除
```

**关键变更**：
- ✅ 从 H2 内存数据库改为 PostgreSQL
- ✅ 使用独立的测试数据库 `wms_db_test`（不影响生产数据）
- ✅ `ddl-auto: create-drop` - 每次测试启动时重建表，测试后清理

### 2. Maven 依赖更新
**文件**: `pom.xml`

- ❌ 移除了 H2 依赖（不再需要）
- ✅ PostgreSQL 驱动已存在（共用生产环境依赖）

### 3. 清理无用文件
- ❌ 删除了 `H2ConnectionTest.java`（不再需要）
- ❌ 移除了 Maven Surefire 排除测试配置（现在可以运行所有测试）

---

## 🚀 设置测试数据库（首次运行前必须执行）

### 方法 1：使用 psql 命令行（推荐）

**步骤 1**：打开命令行，连接到 PostgreSQL

```bash
psql -U postgres -h localhost
# 输入密码：123465
```

**步骤 2**：执行数据库创建脚本

```bash
\i "D:\\ERP_WMS\\BC warehouse\\2G\\create-test-database.sql"
```

或者手动执行：

```sql
DROP DATABASE IF EXISTS wms_db_test;
CREATE DATABASE wms_db_test
    WITH OWNER = postgres ENCODING = 'UTF8';
GRANT ALL PRIVILEGES ON DATABASE wms_db_test TO postgres;
```

**步骤 3**：验证数据库创建成功

```sql
\l
-- 应该能看到 wms_db_test 数据库
```

---

### 方法 2：使用 pgAdmin 图形界面

**步骤 1**：打开 pgAdmin，连接到 PostgreSQL 服务器

**步骤 2**：右键点击 `Databases` → `Create` → `Database...`

**步骤 3**：填写数据库信息
- **Database**: `wms_db_test`
- **Owner**: `postgres`
- **Encoding**: `UTF8`

**步骤 4**：点击 `Save` 保存

---

## 🧪 运行测试

### 在 IDE 中运行（推荐）

#### IntelliJ IDEA：

1. **刷新 Maven 项目**：
   - 右侧 Maven 面板 → 点击 🔄 Reload All Maven Projects
   - 等待依赖加载完成

2. **运行单个测试类**：
   - 右键 `CustomUserDetailsServiceTest.java` → `Run 'CustomUserDetailsServiceTest'`
   - 右键 `AuthControllerIntegrationTest.java` → `Run 'AuthControllerIntegrationTest'`

3. **运行所有测试**：
   - 右键 `src/test/java` 目录 → `Run 'All Tests'`

#### Eclipse：

1. **更新 Maven 项目**：
   - 右键项目 → `Maven` → `Update Project...` → `OK`

2. **运行测试**：
   - 右键测试类 → `Run As` → `JUnit Test`

---

## 📋 测试列表

### ✅ 单元测试（使用 Mockito，不依赖数据库）

| 测试类 | 说明 | 预期状态 |
|--------|------|---------|
| `CustomUserDetailsServiceTest` | 用户详情服务测试 | ✅ 应该通过 |
| `DynamicPermissionServiceTest` | 动态权限服务测试 | ✅ 应该通过 |
| `UserRoleServiceTest` | 用户角色服务测试 | ✅ 应该通过 |

### ✅ 集成测试（使用 PostgreSQL 测试数据库）

| 测试类 | 说明 | 预期状态 |
|--------|------|---------|
| `AuthControllerIntegrationTest` | 认证控制器集成测试 | ✅ 应该通过 |
| `UserRepositoryTest` | 用户仓库测试 | ✅ 应该通过 |

---

## ⚠️ 常见问题

### 问题 1: `org.postgresql.util.PSQLException: FATAL: database "wms_db_test" does not exist`

**原因**：测试数据库未创建

**解决**：执行上面的"设置测试数据库"步骤

---

### 问题 2: `Connection to localhost:5432 refused`

**原因**：PostgreSQL 服务未启动

**解决**：

**Windows**：
```bash
# 启动 PostgreSQL 服务
net start postgresql-x64-16
```

**查看服务状态**：
```bash
sc query postgresql-x64-16
```

---

### 问题 3: `password authentication failed for user "postgres"`

**原因**：密码不正确

**解决**：

1. 修改 `application-test.yml` 中的密码：
```yaml
spring:
  datasource:
    password: 你的实际密码
```

2. 或者修改 PostgreSQL 用户密码：
```sql
psql -U postgres
ALTER USER postgres WITH PASSWORD '123465';
```

---

### 问题 4: Maven 依赖无法下载

**原因**：网络无法访问 Maven Central

**解决**：配置阿里云镜像

**创建或编辑文件**：`%USERPROFILE%\.m2\settings.xml`

```xml
<settings>
    <mirrors>
        <mirror>
            <id>aliyun-maven</id>
            <mirrorOf>central</mirrorOf>
            <name>阿里云公共仓库</name>
            <url>https://maven.aliyun.com/repository/public</url>
        </mirror>
    </mirrors>
</settings>
```

**然后在 IDE 中重新加载 Maven 项目**。

---

## 🎯 测试数据管理

### 自动清理机制

- 每次测试启动时，Hibernate 会 **自动创建** 所有表（`ddl-auto: create-drop`）
- 测试结束后，Spring 会 **自动删除** 所有表和数据
- 下次测试运行时从空数据库开始

### 优点

✅ **测试隔离**：每次测试都从干净的数据库开始
✅ **不影响生产**：使用独立的 `wms_db_test` 数据库
✅ **自动清理**：无需手动清理测试数据

### 缺点

⚠️ **速度较慢**：每次都要重建表（相比 H2 内存数据库）
⚠️ **需要 PostgreSQL 服务**：不像 H2 那样嵌入式运行

---

## 📈 性能对比

| 特性 | H2 内存数据库 | PostgreSQL 测试数据库 |
|------|--------------|----------------------|
| 速度 | 很快 | 稍慢（需要网络连接） |
| 真实性 | 低（语法可能不同） | 高（与生产环境一致） |
| 可靠性 | 低（兼容性问题） | 高（完全兼容） |
| 设置难度 | 简单 | 中等（需要创建数据库） |

**建议**：使用 PostgreSQL 测试数据库，确保测试结果与生产环境一致。

---

## ✅ 验证清单

在运行测试前，请确认：

- [ ] PostgreSQL 服务已启动
- [ ] 测试数据库 `wms_db_test` 已创建
- [ ] `application-test.yml` 中的用户名和密码正确
- [ ] Maven 依赖已成功下载（如果有网络问题，配置阿里云镜像）
- [ ] IDE 已刷新 Maven 项目

---

**文档创建日期**: 2026-01-18
**适用版本**: Spring Boot 3.2.11, Java 17, PostgreSQL 16
