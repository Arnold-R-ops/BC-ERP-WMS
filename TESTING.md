# 用户功能测试文档

## 📋 测试概览

本项目已完成用户功能的完整测试套件，包括**单元测试**和**集成测试（接口测试）**。

---

## 🎯 测试覆盖范围

### 1. 单元测试（Unit Tests）

#### 1.1 UserRepositoryTest
- **文件**: `src/test/java/com/wms/system/repository/UserRepositoryTest.java`
- **测试内容**:
  - ✅ 根据用户名查询用户（成功/失败）
  - ✅ 检查用户名是否存在
  - ✅ 根据角色查询用户列表
  - ✅ 查询所有启用的用户
  - ✅ 根据用户名模糊查询
  - ✅ 根据角色和启用状态查询
  - ✅ 保存新用户
  - ✅ 更新用户信息
  - ✅ 删除用户
  - ✅ 统计用户总数
  - ✅ 批量保存用户
- **测试技术**: `@DataJpaTest`, H2 内存数据库, TestEntityManager
- **测试方法数**: 12 个

#### 1.2 CustomUserDetailsServiceTest
- **文件**: `src/test/java/com/wms/system/security/CustomUserDetailsServiceTest.java`
- **测试内容**:
  - ✅ 根据用户名加载用户详情（成功）
  - ✅ 用户不存在时抛出异常
  - ✅ 加载禁用账号
  - ✅ 加载不同角色的用户（ADMIN, STAFF）
  - ✅ 验证 SecurityUser 包装类
  - ✅ 验证账户状态（isEnabled, isAccountNonLocked 等）
  - ✅ 检查用户名是否存在
  - ✅ 多次加载同一用户验证
- **测试技术**: `@ExtendWith(MockitoExtension.class)`, Mockito
- **测试方法数**: 10 个

#### 1.3 DynamicPermissionServiceTest
- **文件**: `src/test/java/com/wms/system/service/DynamicPermissionServiceTest.java`
- **测试内容**:
  - ✅ 单角色权限查询（无继承）
  - ✅ **董事长权限查询（多重角色继承）** ⭐
  - ✅ **递归角色继承（3 层继承）** ⭐
  - ✅ 权限去重（多个角色有相同权限）
  - ✅ 用户无角色返回空权限
  - ✅ 检查用户是否有特定权限
  - ✅ 检查用户是否有任意权限
  - ✅ 检查用户是否有所有权限
  - ✅ 权限按类型分类（MENU, API, BUTTON）
- **测试技术**: Mockito, 完整模拟 RBAC 继承逻辑
- **测试方法数**: 9 个
- **核心测试**: 验证董事长角色继承功能

#### 1.4 UserRoleServiceTest
- **文件**: `src/test/java/com/wms/system/service/UserRoleServiceTest.java`
- **测试内容**:
  - ✅ 分配角色给用户（成功/失败）
  - ✅ 用户或角色不存在时抛出异常
  - ✅ 角色已分配时跳过
  - ✅ 移除用户角色
  - ✅ 批量分配角色
  - ✅ 查询用户角色（ID/详情）
  - ✅ 查询角色的用户（ID/详情）
  - ✅ 检查用户是否有特定角色
  - ✅ 通过角色编码检查
  - ✅ 缓存失效验证
- **测试技术**: Mockito, ArgumentCaptor
- **测试方法数**: 12 个

---

### 2. 集成测试（Integration Tests）

#### 2.1 AuthControllerIntegrationTest
- **文件**: `src/test/java/com/wms/system/controller/AuthControllerIntegrationTest.java`
- **测试内容**:
  - ✅ 登录成功（管理员账号）
  - ✅ 登录成功（普通员工账号）
  - ✅ 登录失败（用户名不存在）
  - ✅ 登录失败（密码错误）
  - ✅ 登录失败（账号已禁用）
  - ✅ 登录失败（用户名为空）
  - ✅ 登录失败（密码为空）
  - ✅ 登录失败（请求体为空）
  - ✅ 登录失败（Content-Type 错误）
  - ✅ 验证 JWT Token 格式
  - ✅ 健康检查接口
  - ✅ 并发登录测试
  - ✅ 验证返回的用户信息完整性
  - ✅ 用户名包含特殊字符（SQL 注入防护）
  - ✅ 响应时间测试
- **测试技术**: `@SpringBootTest`, `@AutoConfigureMockMvc`, MockMvc, `@Transactional`
- **测试方法数**: 15 个
- **测试类型**: 完整的端到端测试（模拟真实 HTTP 请求）

---

## 🚀 运行测试

### 方式 1: 使用 Maven 命令

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=UserRepositoryTest
mvn test -Dtest=AuthControllerIntegrationTest

# 运行特定测试方法
mvn test -Dtest=UserRepositoryTest#findByUsername_Success

# 跳过测试（用于快速构建）
mvn clean install -DskipTests
```

### 方式 2: 使用 IDE

#### IntelliJ IDEA
1. 右键点击测试类/方法
2. 选择 "Run 'XXXTest'" 或 "Run 'testMethodName()'"
3. 查看测试结果面板

#### Eclipse
1. 右键点击测试类
2. 选择 "Run As" → "JUnit Test"

### 方式 3: 使用 Gradle（如果项目使用 Gradle）

```bash
# 运行所有测试
./gradlew test

# 运行单个测试类
./gradlew test --tests UserRepositoryTest
```

---

## 📊 测试统计

| 测试类型 | 测试类数量 | 测试方法数量 | 覆盖范围 |
|---------|-----------|-------------|---------|
| 单元测试 | 4 | 43 | Repository, Service, Security |
| 集成测试 | 1 | 15 | API 接口（完整流程） |
| **总计** | **5** | **58** | **用户功能完整覆盖** |

---

## 🎯 测试重点

### ✅ 核心功能验证
1. **用户认证流程**: 登录成功/失败的各种场景
2. **JWT Token 生成**: 格式验证、过期时间验证
3. **角色权限继承**: 董事长角色自动继承 3 个角色权限
4. **递归查询**: 支持多级角色继承（最多 10 层）
5. **权限去重**: 多个角色有相同权限时自动去重
6. **缓存管理**: 角色/权限变更时正确失效缓存

### ✅ 安全性测试
1. **密码加密**: BCrypt 加密存储和验证
2. **防止用户名枚举**: 登录失败时不透露用户名是否存在
3. **SQL 注入防护**: 用户名包含特殊字符的处理
4. **账号状态检查**: 禁用账号无法登录
5. **参数验证**: 空用户名/密码拒绝请求

### ✅ 性能测试
1. **响应时间**: 登录接口响应时间监控
2. **并发测试**: 多用户同时登录
3. **缓存效果**: 验证权限缓存功能

---

## 🔧 测试配置

### 测试环境配置文件
- **文件**: `src/test/resources/application-test.yml`
- **数据库**: H2 内存数据库（PostgreSQL 兼容模式）
- **JPA**: `ddl-auto=create-drop`（每次测试重建表结构）
- **JWT**: 测试专用密钥

### 依赖配置
已在 `pom.xml` 中添加：
- `spring-boot-starter-test`: JUnit 5, Mockito, AssertJ
- `h2database`: 内存数据库
- `spring-security-test`: Spring Security 测试工具

---

## 📝 测试结果示例

### 成功的测试输出
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.wms.system.repository.UserRepositoryTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Running com.wms.system.security.CustomUserDetailsServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Running com.wms.system.service.DynamicPermissionServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Running com.wms.system.service.UserRoleServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Running com.wms.system.controller.AuthControllerIntegrationTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 🐛 常见问题

### 1. 测试失败：数据库连接错误
**原因**: 测试环境配置未正确加载
**解决**: 确保 `src/test/resources/application-test.yml` 文件存在

### 2. JWT Token 验证失败
**原因**: 测试环境 JWT 密钥与生产环境不同
**解决**: 使用 `@ActiveProfiles("test")` 加载测试配置

### 3. 测试数据残留
**原因**: `@Transactional` 未生效
**解决**: 确保测试类上有 `@Transactional` 注解

### 4. H2 数据库兼容性问题
**原因**: H2 与 PostgreSQL 语法差异
**解决**: 使用 `MODE=PostgreSQL` 参数

---

## 📖 扩展测试建议

### 1. 性能测试
- 使用 JMeter 进行负载测试
- 测试 1000+ 并发登录请求
- 验证权限缓存命中率

### 2. 安全测试
- 暴力破解防护（连续失败登录限制）
- Token 过期处理
- HTTPS 强制使用

### 3. 边界测试
- 极长用户名/密码
- Unicode 字符测试
- 特殊字符组合

---

## ✅ 测试完成清单

- [x] Repository 层单元测试
- [x] Service 层单元测试
- [x] Security 层单元测试
- [x] Controller 层集成测试
- [x] 测试配置文件
- [x] 测试依赖配置
- [x] 测试文档

---

## 📞 联系方式

如有测试相关问题，请联系：
- **开发团队**: WMS Team
- **文档日期**: 2026-01-18
- **版本**: v2.0 (Dynamic RBAC System)
