# H2 测试数据库修复方案

## 🔴 当前问题
```
ApplicationContext failure threshold (1) exceeded
Caused by: Cannot load driver class: org.h2.Driver
```

**根本原因**：Spring 测试上下文第一次加载失败，错误被缓存，导致后续测试都跳过。

---

## ✅ 已执行的修复

### 1. 修改 pom.xml
```xml
<!-- H2 Database (内存数据库用于测试) -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <!-- 使用 Spring Boot 管理的版本 -->
    <scope>runtime</scope>
</dependency>
```

**关键变更**：
- ✅ 移除硬编码版本号，使用 Spring Boot 3.2.11 推荐版本
- ✅ 使用 `runtime` scope（运行时和测试时都可用）

### 2. 简化 application-test.yml
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb  # 移除 PostgreSQL 兼容模式
    driver-class-name: org.h2.Driver
    username: sa
    password:
```

**关键变更**：
- ✅ 移除 `MODE=PostgreSQL` 参数（可能导致兼容性问题）
- ✅ 移除 `DB_CLOSE_DELAY` 和 `DB_CLOSE_ON_EXIT` 参数
- ✅ 简化配置，使用 H2 默认行为

### 3. 创建诊断工具
- ✅ `H2ConnectionTest.java` - 简单的连接测试
- ✅ `clean-test-cache.bat` - 自动清理脚本

---

## 🚀 修复步骤（请按顺序执行）

### 步骤 1: 清理缓存
```bash
# 运行清理脚本
clean-test-cache.bat

# 或手动执行：
rmdir /s /q target
rmdir /s /q "%USERPROFILE%\.m2\repository\com\h2database"
```

### 步骤 2: 清理 IDE 缓存

#### IntelliJ IDEA
```
File → Invalidate Caches
选择 "Invalidate and Restart"
```

#### Eclipse
```
Project → Clean
勾选 "Clean all projects"
```

### 步骤 3: 刷新 Maven 项目

#### IntelliJ IDEA
1. 打开右侧 **Maven** 面板
2. 点击 **🔄 Reload All Maven Projects**
3. 等待下载完成（观察进度条）

#### 命令行（如果 Maven 可用）
```bash
mvn clean install -U -DskipTests
```

### 步骤 4: 重新构建项目
```
IntelliJ IDEA: Build → Rebuild Project
Eclipse: Project → Build All
```

### 步骤 5: 运行诊断测试

**首先运行简单测试**：
```
右键: src/test/java/com/wms/system/H2ConnectionTest.java
选择: Run 'H2ConnectionTest'
```

**预期输出**：
```
✅ H2 数据库连接成功！
Driver: H2 JDBC Driver
URL: jdbc:h2:mem:testdb

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

### 步骤 6: 运行单元测试

**按顺序运行**：
1. `UserRepositoryTest` - 不依赖完整上下文
2. `CustomUserDetailsServiceTest` - Mockito 测试
3. `DynamicPermissionServiceTest` - Service 层测试
4. `AuthControllerIntegrationTest` - 完整集成测试

---

## 🔍 验证 H2 是否正确安装

### 方法 1: 检查 Maven 依赖树
```bash
mvn dependency:tree | findstr h2

# 预期输出：
# [INFO] +- com.h2database:h2:jar:2.2.224:runtime
```

### 方法 2: 检查本地仓库
```bash
dir "%USERPROFILE%\.m2\repository\com\h2database\h2"

# 应该看到版本目录（如 2.2.224）
```

### 方法 3: 在 IDE 中查看
```
IntelliJ IDEA: View → Tool Windows → Maven
展开: Dependencies → h2
应该显示: com.h2database:h2:2.2.224 (或其他版本)
```

---

## ⚠️ 如果问题仍然存在

### 方案 A: 手动下载 H2
```bash
# 强制下载 H2 依赖
mvn dependency:get -Dartifact=com.h2database:h2:2.2.224

# 验证下载
dir "%USERPROFILE%\.m2\repository\com\h2database\h2\2.2.224"
```

### 方案 B: 使用备用测试配置

创建 `src/test/resources/application.properties`：
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

然后修改测试类：
```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
public class AuthControllerIntegrationTest {
    // ...
}
```

### 方案 C: 暂时禁用集成测试

在 `pom.xml` 中添加：
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>**/AuthControllerIntegrationTest.java</exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

这样可以先让单元测试通过，然后单独解决集成测试问题。

---

## 📊 测试优先级

### 第一优先级：单元测试（不依赖数据库）
✅ `CustomUserDetailsServiceTest` - 使用 Mockito
✅ `DynamicPermissionServiceTest` - 使用 Mockito
✅ `UserRoleServiceTest` - 使用 Mockito

### 第二优先级：Repository 测试（使用 H2）
🔧 `UserRepositoryTest` - 使用 @DataJpaTest

### 第三优先级：集成测试（完整上下文）
🔧 `H2ConnectionTest` - 简单连接测试
🔧 `AuthControllerIntegrationTest` - 完整集成测试

---

## 🆘 终极解决方案：使用 TestContainers

如果 H2 始终无法工作，可以考虑使用真实的 PostgreSQL 测试容器：

### 1. 添加依赖
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

### 2. 修改测试类
```java
@SpringBootTest
@Testcontainers
public class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**注意**：需要 Docker 环境。

---

## 📝 诊断清单

在寻求帮助前，请确认：

- [ ] 已运行 `clean-test-cache.bat`
- [ ] 已在 IDE 中 Invalidate Caches
- [ ] 已刷新 Maven 项目（Reload All Maven Projects）
- [ ] 已重新构建项目（Rebuild Project）
- [ ] `H2ConnectionTest` 可以通过
- [ ] 单元测试（Mockito）可以通过
- [ ] Java 版本：`java -version` (应该是 17)
- [ ] Maven 版本：`mvn -version`
- [ ] IDE 版本

---

## 💡 常见原因

1. **Maven 本地仓库损坏**
   - 解决：删除 `~/.m2/repository/com/h2database`

2. **IDE 缓存过期**
   - 解决：Invalidate Caches and Restart

3. **测试上下文被缓存**
   - 解决：删除 `target` 目录

4. **版本不兼容**
   - 解决：使用 Spring Boot 管理的版本（已修复）

5. **企业代理或防火墙**
   - 解决：配置 Maven 代理设置

---

**最后更新**: 2026-01-18
**适用版本**: Spring Boot 3.2.11, Java 17
