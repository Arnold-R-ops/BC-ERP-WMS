package com.wms.system.repository;

import com.wms.system.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 注意：v3.3 多角色系统中，用户角色存储在 sys_user_role 表，
 *      User 实体不再包含 role 字段。角色相关查询需使用 UserRoleService
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 3.3 (Updated for multi-role system)
 */
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testAdmin;
    private User testStaff;
    private User testDisabledUser;

    /**
     * 每个测试方法执行前初始化测试数据
     */
    @BeforeEach
    void setUp() {
        // 创建测试用户 - 管理员
        testAdmin = User.builder()
                .username("admin_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") // BCrypt hash
                .displayName("测试管理员")
                .enabled(true)
                .remark("单元测试用管理员账号")
                .build();

        // 创建测试用户 - 普通员工
        testStaff = User.builder()
                .username("staff_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("测试员工")
                .enabled(true)
                .remark("单元测试用员工账号")
                .build();

        // 创建测试用户 - 禁用账号
        testDisabledUser = User.builder()
                .username("disabled_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("禁用测试用户")
                .enabled(false)
                .remark("单元测试用禁用账号")
                .build();

        // 持久化测试数据
        entityManager.persist(testAdmin);
        entityManager.persist(testStaff);
        entityManager.persist(testDisabledUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("根据用户名查询用户 - 成功")
    void findByUsername_Success() {
        // When: 根据用户名查询
        Optional<User> found = userRepository.findByUsername("admin_test");

        // Then: 验证结果
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("admin_test");
        assertThat(found.get().getDisplayName()).isEqualTo("测试管理员");

        // Note: In v3.3, user roles are stored in sys_user_role table
        // and queried via UserRoleService
    }

    @Test
    @DisplayName("根据用户名查询用户 - 用户不存在")
    void findByUsername_NotFound() {
        // When: 查询不存在的用户名
        Optional<User> found = userRepository.findByUsername("nonexistent_user");

        // Then: 应该返回空
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("检查用户名是否存在 - 存在")
    void existsByUsername_Exists() {
        // When: 检查已存在的用户名
        boolean exists = userRepository.existsByUsername("admin_test");

        // Then: 应该返回 true
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("检查用户名是否存在 - 不存在")
    void existsByUsername_NotExists() {
        // When: 检查不存在的用户名
        boolean exists = userRepository.existsByUsername("nonexistent_user");

        // Then: 应该返回 false
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("查询所有启用的用户")
    void findByEnabledTrue() {
        // When: 查询所有启用的用户
        List<User> enabledUsers = userRepository.findByEnabledTrue();

        // Then: 应该有 2 个启用的用户
        assertThat(enabledUsers).hasSize(2);
        assertThat(enabledUsers)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("admin_test", "staff_test");
    }

    @Test
    @DisplayName("根据用户名模糊查询")
    void findByUsernameContaining() {
        // When: 模糊查询包含 "test" 的用户名
        List<User> users = userRepository.findByUsernameContaining("test");

        // Then: 应该找到所有测试用户
        assertThat(users).hasSize(3);
        assertThat(users)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("admin_test", "staff_test", "disabled_test");

        // When: 模糊查询包含 "admin" 的用户名
        List<User> admins = userRepository.findByUsernameContaining("admin");

        // Then: 应该只找到管理员
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getUsername()).isEqualTo("admin_test");
    }

    @Test
    @DisplayName("保存新用户")
    void saveNewUser() {
        // Given: 创建新用户
        User newUser = User.builder()
                .username("new_user_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("新用户")
                .enabled(true)
                .build();

        // When: 保存用户
        User saved = userRepository.save(newUser);

        // Then: 验证保存成功
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 重新查询
        Optional<User> found = userRepository.findByUsername("new_user_test");

        // Then: 应该能找到
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("new_user_test");
    }

    @Test
    @DisplayName("更新用户信息")
    void updateUser() {
        // Given: 获取现有用户
        User user = userRepository.findByUsername("staff_test").orElseThrow();
        String originalUpdatedAt = user.getUpdatedAt().toString();

        // When: 修改用户信息
        user.setDisplayName("更新后的名称");
        user.setRemark("已更新");
        User updated = userRepository.save(user);

        // 清除持久化上下文，强制重新查询
        entityManager.flush();
        entityManager.clear();

        // Then: 验证更新成功
        User reloaded = userRepository.findByUsername("staff_test").orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("更新后的名称");
        assertThat(reloaded.getRemark()).isEqualTo("已更新");
        // 注意: updatedAt 应该被自动更新（JPA Auditing）
    }

    @Test
    @DisplayName("删除用户")
    void deleteUser() {
        // Given: 获取现有用户
        User user = userRepository.findByUsername("staff_test").orElseThrow();
        Long userId = user.getId();

        // When: 删除用户
        userRepository.delete(user);
        entityManager.flush();

        // Then: 验证删除成功
        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isEmpty();

        // When: 再次尝试查询
        Optional<User> foundByUsername = userRepository.findByUsername("staff_test");

        // Then: 应该查询不到
        assertThat(foundByUsername).isEmpty();
    }

    @Test
    @DisplayName("统计用户总数")
    void countUsers() {
        // When: 统计所有用户
        long count = userRepository.count();

        // Then: 应该有 3 个用户
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("查询所有用户")
    void findAll() {
        // When: 查询所有用户
        List<User> allUsers = userRepository.findAll();

        // Then: 应该有 3 个用户
        assertThat(allUsers).hasSize(3);
        assertThat(allUsers)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("admin_test", "staff_test", "disabled_test");
    }

    @Test
    @DisplayName("批量保存用户")
    void saveAll() {
        // Given: 创建多个新用户
        User user1 = User.builder()
                .username("batch_user_1")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(true)
                .build();

        User user2 = User.builder()
                .username("batch_user_2")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(true)
                .build();

        // When: 批量保存
        List<User> saved = userRepository.saveAll(List.of(user1, user2));

        // Then: 验证保存成功
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(u -> u.getId() != null);

        // When: 查询所有用户
        List<User> allUsers = userRepository.findAll();

        // Then: 总数应该是 5 个
        assertThat(allUsers).hasSize(5);
    }
}
