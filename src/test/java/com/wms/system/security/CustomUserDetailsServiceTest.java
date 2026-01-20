package com.wms.system.security;

import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CustomUserDetailsService 单元测试
 *
 * 测试用户详情加载服务的核心功能
 *
 * 使用 Mockito 进行单元测试：
 * - @ExtendWith(MockitoExtension.class): 启用 Mockito 扩展
 * - @Mock: 模拟依赖的 Repository
 * - @InjectMocks: 自动注入 Mock 对象到被测试类
 *
 * 注意：v3.3 多角色系统中，用户权限从 JWT Token 动态加载，
 *      User 实体不再包含 role 字段和 getAuthorities() 实现
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 3.3 (Updated for multi-role system)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService 单元测试")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("测试用户")
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("根据用户名加载用户详情 - 成功")
    void loadUserByUsername_Success() {
        // Given: 模拟 Repository 返回用户
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 调用 loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("test_user");

        // Then: 验证返回的是 SecurityUser 包装类
        assertThat(userDetails).isNotNull();
        assertThat(userDetails).isInstanceOf(SecurityUser.class);

        SecurityUser securityUser = (SecurityUser) userDetails;
        assertThat(securityUser.getUsername()).isEqualTo("test_user");
        assertThat(securityUser.getPassword()).isEqualTo(testUser.getPassword());
        assertThat(securityUser.getId()).isEqualTo(1L);
        assertThat(securityUser.isEnabled()).isTrue();

        // Note: In v3.3, authorities are loaded from JWT Token, not from User entity
        // The getAuthorities() method in User now returns an empty list

        // Then: 验证 Repository 被调用一次
        verify(userRepository, times(1)).findByUsername("test_user");
    }

    @Test
    @DisplayName("根据用户名加载用户详情 - 用户不存在")
    void loadUserByUsername_UserNotFound() {
        // Given: 模拟 Repository 返回空
        when(userRepository.findByUsername(anyString()))
                .thenReturn(Optional.empty());

        // When & Then: 应该抛出 UsernameNotFoundException
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Invalid username or password");

        // Then: 验证 Repository 被调用
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("根据用户名加载用户详情 - 禁用账号")
    void loadUserByUsername_DisabledAccount() {
        // Given: 创建禁用的用户
        User disabledUser = User.builder()
                .id(2L)
                .username("disabled_user")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(false)
                .build();

        when(userRepository.findByUsername("disabled_user"))
                .thenReturn(Optional.of(disabledUser));

        // When: 调用 loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("disabled_user");

        // Then: 应该返回用户，但 isEnabled() 为 false
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isFalse();

        // 注意：Spring Security 会在认证过程中检查 isEnabled()，
        // 如果为 false 会抛出 DisabledException，但 loadUserByUsername 本身不会抛异常
    }

    @Test
    @DisplayName("根据用户名加载用户详情（抛出业务异常） - 成功")
    void loadUserByUsernameWithException_Success() {
        // Given: 模拟 Repository 返回用户
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 调用 loadUserByUsernameWithException
        User user = userDetailsService.loadUserByUsernameWithException("test_user");

        // Then: 验证返回的是 User 实体
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("test_user");
        assertThat(user.getId()).isEqualTo(1L);

        // Then: 验证 Repository 被调用一次
        verify(userRepository, times(1)).findByUsername("test_user");
    }

    @Test
    @DisplayName("根据用户名加载用户详情（抛出业务异常） - 用户不存在")
    void loadUserByUsernameWithException_UserNotFound() {
        // Given: 模拟 Repository 返回空
        when(userRepository.findByUsername(anyString()))
                .thenReturn(Optional.empty());

        // When & Then: 应该抛出 BusinessException
        assertThatThrownBy(() -> userDetailsService.loadUserByUsernameWithException("nonexistent"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "AUTH_INVALID_CREDENTIALS");

        // Then: 验证 Repository 被调用
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("检查用户名是否存在 - 存在")
    void existsByUsername_Exists() {
        // Given: 模拟 Repository 返回 true
        when(userRepository.existsByUsername("test_user"))
                .thenReturn(true);

        // When: 调用 existsByUsername
        boolean exists = userDetailsService.existsByUsername("test_user");

        // Then: 应该返回 true
        assertThat(exists).isTrue();

        // Then: 验证 Repository 被调用一次
        verify(userRepository, times(1)).existsByUsername("test_user");
    }

    @Test
    @DisplayName("检查用户名是否存在 - 不存在")
    void existsByUsername_NotExists() {
        // Given: 模拟 Repository 返回 false
        when(userRepository.existsByUsername(anyString()))
                .thenReturn(false);

        // When: 调用 existsByUsername
        boolean exists = userDetailsService.existsByUsername("nonexistent");

        // Then: 应该返回 false
        assertThat(exists).isFalse();

        // Then: 验证 Repository 被调用一次
        verify(userRepository, times(1)).existsByUsername("nonexistent");
    }

    @Test
    @DisplayName("加载不同角色的用户 - 测试多角色系统")
    void loadUserByUsername_MultiRoleSystem() {
        // Given: 创建测试用户（v3.3 不再有 role 字段）
        User staffUser = User.builder()
                .id(3L)
                .username("staff_user")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(true)
                .build();

        when(userRepository.findByUsername("staff_user"))
                .thenReturn(Optional.of(staffUser));

        // When: 调用 loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("staff_user");

        // Then: 验证用户基本信息（角色权限在 v3.3 从 JWT Token 加载）
        SecurityUser securityUser = (SecurityUser) userDetails;
        assertThat(securityUser.getUsername()).isEqualTo("staff_user");
        assertThat(securityUser.getId()).isEqualTo(3L);
        assertThat(securityUser.isEnabled()).isTrue();

        // Note: In v3.3, user roles are stored in sys_user_role table
        // and loaded into JWT token during authentication
    }

    @Test
    @DisplayName("验证 SecurityUser 包装类的账户状态")
    void verifySecurityUserAccountStatus() {
        // Given: 模拟 Repository 返回用户
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 调用 loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("test_user");

        // Then: 验证账户状态
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("验证多次加载同一用户 - Repository 应被多次调用")
    void loadSameUserMultipleTimes() {
        // Given: 模拟 Repository 返回用户
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 多次加载同一用户
        userDetailsService.loadUserByUsername("test_user");
        userDetailsService.loadUserByUsername("test_user");
        userDetailsService.loadUserByUsername("test_user");

        // Then: Repository 应被调用 3 次（因为 Service 层没有缓存）
        verify(userRepository, times(3)).findByUsername("test_user");
    }
}
