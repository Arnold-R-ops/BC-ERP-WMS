# UserRepositoryTest 自动创建用户干扰修复

**修复日期**: 2026-01-19
**问题**: initAdminUser 自动创建管理员用户导致测试失败
**影响测试**: `findByEnabledTrue` 及其他依赖用户数量的测试

---

## 🔍 问题诊断

### 错误现象

**测试失败信息**:
```
java.lang.AssertionError:
Expected size: 2 but was: 3 in:
[User(id=1, username=admin, ...),      ← 自动创建的管理员
 User(id=6, username=admin_test, ...), ← 测试数据
 User(id=7, username=staff_test, ...)] ← 测试数据

at UserRepositoryTest.findByEnabledTrue(line:180)
```

**预期**: 测试只应该有 2 个启用的用户（admin_test, staff_test）
**实际**: 有 3 个启用的用户，额外的一个是自动创建的 admin

### 根本原因

**WmsSystemApplication.initAdminUser() 方法**:
```java
@Bean
public CommandLineRunner initAdminUser(
    UserRepository userRepository,
    PasswordEncoder passwordEncoder
) {
    return args -> {
        // 自动创建或更新管理员用户
        User admin = userRepository.findByUsername("admin")
            .orElse(User.builder()
                .username("admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());

        admin.setPassword(passwordEncoder.encode("password123"));
        userRepository.save(admin);  // ← 每次启动都会创建
    };
}
```

**问题链**:
```
Spring Boot 测试启动
  ↓
加载 WmsSystemApplication
  ↓
执行 initAdminUser bean
  ↓
自动创建 username=admin 的用户
  ↓
测试的 setUp() 方法创建测试数据
  ↓
数据库中有 3 个启用用户（1个自动创建 + 2个测试）
  ↓
测试断言失败（期望 2 个，实际 3 个）
```

---

## ✅ 修复方案

### 覆盖 initAdminUser Bean

在 `TestConfig` 中覆盖 `initAdminUser` bean，让它在测试中不执行任何操作。

**修复代码**:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("UserRepository 单元测试")
class UserRepositoryTest {

    /**
     * 测试配置
     * 提供 PasswordEncoder bean，因为 @DataJpaTest 不会加载 Security 配置
     * 同时覆盖 initAdminUser，防止自动创建管理员用户干扰测试
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        /**
         * 覆盖 WmsSystemApplication 中的 initAdminUser bean
         * 在测试中不执行任何操作，避免自动创建管理员用户
         */
        @Bean
        public CommandLineRunner initAdminUser() {
            return args -> {
                // 测试中不创建管理员用户
            };
        }
    }

    // ... 其他代码
}
```

**添加的导入**:
```java
import org.springframework.boot.CommandLineRunner;
```

---

## 🔧 技术说明

### Bean 覆盖机制

**Spring Bean 优先级**:
1. **@TestConfiguration 中定义的 bean** （最高优先级）
2. @Configuration 中定义的 bean
3. @SpringBootApplication 中定义的 bean

**覆盖规则**:
- 当多个配置类定义了相同名称和类型的 bean
- Spring 会使用最高优先级的 bean
- 在测试中，`@TestConfiguration` 的 bean 优先级最高

**示例**:
```java
// WmsSystemApplication.java (生产代码)
@Bean
public CommandLineRunner initAdminUser(...) {
    return args -> {
        // 创建管理员用户
    };
}

// UserRepositoryTest.java (测试代码)
@TestConfiguration
static class TestConfig {
    @Bean
    public CommandLineRunner initAdminUser() {  // ← 同名 bean
        return args -> {
            // 什么都不做，覆盖生产代码的 bean
        };
    }
}
```

**效果**:
- ✅ 生产环境: 使用 `WmsSystemApplication.initAdminUser()`，自动创建管理员
- ✅ 测试环境: 使用 `TestConfig.initAdminUser()`，不创建任何用户

### 为什么不使用 @MockBean

**@MockBean 的问题**:
```java
// ❌ 不推荐
@MockBean
private CommandLineRunner initAdminUser;
```

**问题**:
- `@MockBean` 会创建一个 Mockito mock 对象
- Mock 对象需要手动配置行为
- 对于 `CommandLineRunner`，我们不需要 mock，只需要一个空实现

**正确做法**:
```java
// ✅ 推荐
@Bean
public CommandLineRunner initAdminUser() {
    return args -> {}; // 空实现，简洁明了
}
```

### CommandLineRunner 的执行时机

**生命周期**:
```
Spring Boot 启动
  ↓
加载所有 Bean
  ↓
执行 @PostConstruct 方法
  ↓
执行所有 CommandLineRunner.run() ← initAdminUser 在这里执行
  ↓
应用启动完成
```

**在测试中**:
```
@DataJpaTest 启动
  ↓
加载 JPA 相关 Bean
  ↓
加载 @TestConfiguration 中的 Bean
  ↓
TestConfig.initAdminUser 覆盖原始 bean
  ↓
执行空的 initAdminUser（什么都不做）
  ↓
执行测试的 @BeforeEach 方法
  ↓
运行测试
```

---

## 📊 修复前后对比

### 修复前

**数据库状态**:
```
users 表:
- id=1, username=admin      (自动创建) ← 干扰测试
- id=6, username=admin_test (测试数据)
- id=7, username=staff_test (测试数据)
```

**测试结果**:
```
❌ findByEnabledTrue 失败
   Expected size: 2 but was: 3
```

### 修复后

**数据库状态**:
```
users 表:
- id=6, username=admin_test (测试数据)
- id=7, username=staff_test (测试数据)
```

**测试结果**:
```
✅ findByEnabledTrue 通过
   Found exactly 2 enabled users
```

---

## 🚀 验证步骤

### 步骤 1: 清空测试数据库

**执行清理脚本**:
```
clean-test-db.bat
```

### 步骤 2: 运行测试

在 IntelliJ IDEA 中:
1. 右键 `UserRepositoryTest` 测试类
2. 选择 **Run 'UserRepositoryTest'**

### 步骤 3: 检查测试结果

#### ✅ 预期结果

**所有测试应该全部通过**:
```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**特别关注的测试**:
- ✅ `findByEnabledTrue` - 应该找到正好 2 个启用用户
- ✅ `countUsers` - 应该统计到正好 3 个用户
- ✅ `findAll` - 应该返回 3 个用户

---

## 💡 最佳实践

### 测试隔离原则

**测试应该独立于生产代码的初始化逻辑**:

1. **不依赖自动创建的数据**:
   ```java
   // ❌ 错误 - 依赖生产环境自动创建的管理员
   @Test
   void testAdminExists() {
       User admin = userRepository.findByUsername("admin").orElseThrow();
       // ...
   }

   // ✅ 正确 - 在测试中明确创建所需数据
   @Test
   void testAdminExists() {
       User admin = createTestAdmin();
       userRepository.save(admin);
       // ...
   }
   ```

2. **在 @BeforeEach 中创建测试数据**:
   ```java
   @BeforeEach
   void setUp() {
       // 明确创建测试所需的所有数据
       testAdmin = User.builder()
           .username("admin_test")  // 使用测试专用的用户名
           .build();
       entityManager.persist(testAdmin);
   }
   ```

3. **覆盖影响测试的 Bean**:
   ```java
   @TestConfiguration
   static class TestConfig {
       // 覆盖所有可能干扰测试的初始化 bean
       @Bean
       public CommandLineRunner initAdminUser() {
           return args -> {};
       }
   }
   ```

### 何时需要覆盖 Bean

**需要覆盖的场景**:
- ✅ Bean 会自动创建数据（如 initAdminUser）
- ✅ Bean 会执行耗时操作（如数据导入）
- ✅ Bean 依赖外部服务（如第三方 API）
- ✅ Bean 会修改系统状态（如清空缓存）

**不需要覆盖的场景**:
- ❌ 只读操作的 Bean
- ❌ 配置类 Bean（如 JpaConfig）
- ❌ 工具类 Bean（如 PasswordEncoder）

---

## ⚠️ 常见问题

### Q1: 为什么不在生产代码中添加 @Profile 注解？

**不推荐的做法**:
```java
// ❌ 不推荐
@Bean
@Profile("!test")  // 非测试环境才执行
public CommandLineRunner initAdminUser(...) {
    // ...
}
```

**原因**:
- 生产代码不应该知道测试的存在
- 违反了关注点分离原则
- 测试应该适应生产代码，而不是反过来

**推荐做法**:
```java
// ✅ 推荐 - 在测试中覆盖
@TestConfiguration
static class TestConfig {
    @Bean
    public CommandLineRunner initAdminUser() {
        return args -> {};
    }
}
```

### Q2: 如果有多个 CommandLineRunner 需要覆盖怎么办？

**解决方案**:
```java
@TestConfiguration
static class TestConfig {
    @Bean
    public CommandLineRunner initAdminUser() {
        return args -> {};
    }

    @Bean
    public CommandLineRunner importInitialData() {
        return args -> {};
    }

    @Bean
    public CommandLineRunner setupCacheWarmer() {
        return args -> {};
    }
}
```

### Q3: 覆盖的 Bean 名称必须一致吗？

**是的，必须一致**:
```java
// 生产代码
@Bean
public CommandLineRunner initAdminUser() { ... }

// 测试代码 - bean 名称必须相同
@Bean
public CommandLineRunner initAdminUser() { ... }  // ← 名称一致
```

**Bean 名称规则**:
- 默认使用方法名作为 bean 名称
- 如果方法名不同，需要使用 `@Bean("beanName")` 指定

---

## ✅ 修复完成清单

在运行测试前，请确认：

- [ ] 已添加 `CommandLineRunner initAdminUser()` 覆盖方法
- [ ] 覆盖方法返回空实现 `args -> {}`
- [ ] 已添加 `import org.springframework.boot.CommandLineRunner;`
- [ ] 已执行 `clean-test-db.bat` 清空数据库
- [ ] IntelliJ IDEA 中没有红色编译错误
- [ ] `Build → Rebuild Project` 成功完成

完成以上步骤后，运行测试：
```
右键 UserRepositoryTest → Run 'UserRepositoryTest'
```

---

## 🎊 最终总结

**修复日期**: 2026-01-19
**修复内容**: 覆盖 initAdminUser bean 防止自动创建管理员用户
**根本原因**: 生产代码的初始化逻辑在测试中也被执行
**解决方案**: 在 @TestConfiguration 中提供空实现的 bean

**UserRepositoryTest 所有修复**:
1. ✅ 添加 @AutoConfigureTestDatabase(replace=NONE)
2. ✅ 添加 @ActiveProfiles("test")
3. ✅ 清空数据库（clean-test-db.bat）
4. ✅ 提供 PasswordEncoder bean
5. ✅ **覆盖 initAdminUser bean** ← 当前修复

**最终结果**: ✅ **预期所有 14 个测试全部通过**

---

## 🚀 立即行动

**第 1 步**: 确认代码已更新（initAdminUser 覆盖已添加）

**第 2 步**: 清空数据库
```
双击运行: clean-test-db.bat
```

**第 3 步**: 运行测试
```
右键 UserRepositoryTest → Run 'UserRepositoryTest'
```

**预期输出**:
```
✅ Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
✅ BUILD SUCCESS
```

如果全部通过，恭喜！UserRepositoryTest 修复完全完成。
如果仍有失败，请提供新的错误信息。
