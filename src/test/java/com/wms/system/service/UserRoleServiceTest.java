package com.wms.system.service;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * UserRoleService 单元测试
 *
 * 测试用户-角色分配服务的核心功能
 *
 * 测试场景：
 * 1. 分配角色给用户
 * 2. 移除用户角色
 * 3. 批量分配角色
 * 4. 查询用户角色
 * 5. 查询角色的用户
 * 6. 缓存失效验证
 *
 * 注意：v3.3 多角色系统中，用户角色存储在 sys_user_role 表，
 *      User 实体不再包含 role 字段
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 3.3 (Updated for multi-role system)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserRoleService 单元测试")
class UserRoleServiceTest {

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private PermissionCacheService cacheService;

    @InjectMocks
    private UserRoleService userRoleService;

    private User testUser;
    private SysRole chairmanRole;
    private SysRole warehouseAdminRole;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("password")
                .enabled(true)
                .build();

        chairmanRole = SysRole.builder()
                .id(10L)
                .roleCode("CHAIRMAN")
                .roleName("董事长")
                .status("ACTIVE")
                .build();

        warehouseAdminRole = SysRole.builder()
                .id(20L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("仓库管理员")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("分配角色给用户 - 成功")
    void assignRoleToUser_Success() {
        // Given: 用户和角色都存在
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(chairmanRole));
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L)).thenReturn(false);

        // When: 分配角色
        userRoleService.assignRoleToUser(1L, 10L, 999L);

        // Then: 验证保存操作
        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleRepository, times(1)).save(captor.capture());

        SysUserRole saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRoleId()).isEqualTo(10L);
        assertThat(saved.getAssignedBy()).isEqualTo(999L);

        // Then: 验证缓存失效
        verify(cacheService, times(1)).onUserRoleAssigned(1L);
    }

    @Test
    @DisplayName("分配角色给用户 - 用户不存在")
    void assignRoleToUser_UserNotFound() {
        // Given: 用户不存在
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        // When & Then: 应该抛出异常
        assertThatThrownBy(() -> userRoleService.assignRoleToUser(1L, 10L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        // Then: 不应该保存
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("分配角色给用户 - 角色不存在")
    void assignRoleToUser_RoleNotFound() {
        // Given: 用户存在，角色不存在
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(anyLong())).thenReturn(Optional.empty());

        // When & Then: 应该抛出异常
        assertThatThrownBy(() -> userRoleService.assignRoleToUser(1L, 10L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found");

        // Then: 不应该保存
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("分配角色给用户 - 角色已分配")
    void assignRoleToUser_AlreadyAssigned() {
        // Given: 角色已分配
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(chairmanRole));
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L)).thenReturn(true);

        // When: 尝试重复分配
        userRoleService.assignRoleToUser(1L, 10L, 999L);

        // Then: 不应该保存（跳过重复分配）
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("移除用户角色 - 成功")
    void removeRoleFromUser_Success() {
        // When: 移除角色
        userRoleService.removeRoleFromUser(1L, 10L);

        // Then: 验证删除操作
        verify(userRoleRepository, times(1)).deleteByUserIdAndRoleId(1L, 10L);

        // Then: 验证缓存失效
        verify(cacheService, times(1)).onUserRoleRemoved(1L);
    }

    @Test
    @DisplayName("批量分配角色 - 成功")
    void assignRolesToUser_Success() {
        // Given: 用户存在
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.existsById(10L)).thenReturn(true);
        when(roleRepository.existsById(20L)).thenReturn(true);

        // When: 批量分配 2 个角色
        Set<Long> roleIds = Set.of(10L, 20L);
        userRoleService.assignRolesToUser(1L, roleIds, 999L);

        // Then: 验证先删除所有角色
        verify(userRoleRepository, times(1)).deleteByUserId(1L);

        // Then: 验证保存 2 次（2 个角色）
        verify(userRoleRepository, times(2)).save(any(SysUserRole.class));

        // Then: 验证缓存失效
        verify(cacheService, times(1)).onUserRoleAssigned(1L);
    }

    @Test
    @DisplayName("批量分配角色 - 跳过不存在的角色")
    void assignRolesToUser_SkipNonExistentRoles() {
        // Given: 用户存在，但某些角色不存在
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.existsById(10L)).thenReturn(true);
        when(roleRepository.existsById(999L)).thenReturn(false); // 不存在的角色

        // When: 批量分配（包含不存在的角色）
        Set<Long> roleIds = Set.of(10L, 999L);
        userRoleService.assignRolesToUser(1L, roleIds, 888L);

        // Then: 只保存存在的角色
        verify(userRoleRepository, times(1)).save(any(SysUserRole.class));
    }

    @Test
    @DisplayName("查询用户角色ID - 成功")
    void getUserRoleIds_Success() {
        // Given: 模拟返回角色ID集合
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(10L, 20L));

        // When: 查询用户角色
        Set<Long> roleIds = userRoleService.getUserRoleIds(1L);

        // Then: 验证结果
        assertThat(roleIds).containsExactlyInAnyOrder(10L, 20L);
        verify(userRoleRepository, times(1)).findRoleIdsByUserId(1L);
    }

    @Test
    @DisplayName("查询用户角色详情 - 成功")
    void getUserRoles_Success() {
        // Given: 模拟返回角色ID和角色详情
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(10L, 20L));
        when(roleRepository.findByIdIn(Set.of(10L, 20L)))
                .thenReturn(List.of(chairmanRole, warehouseAdminRole));

        // When: 查询用户角色详情
        List<SysRole> roles = userRoleService.getUserRoles(1L);

        // Then: 验证结果
        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(SysRole::getRoleCode)
                .containsExactlyInAnyOrder("CHAIRMAN", "WAREHOUSE_ADMIN");
    }

    @Test
    @DisplayName("查询用户角色详情 - 用户无角色")
    void getUserRoles_NoRoles() {
        // Given: 用户没有角色
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of());

        // When: 查询用户角色详情
        List<SysRole> roles = userRoleService.getUserRoles(1L);

        // Then: 应该返回空列表
        assertThat(roles).isEmpty();

        // Then: 不应该查询角色详情
        verify(roleRepository, never()).findByIdIn(any());
    }

    @Test
    @DisplayName("查询角色的用户ID - 成功")
    void getRoleUserIds_Success() {
        // Given: 模拟返回用户ID集合
        when(userRoleRepository.findUserIdsByRoleId(10L))
                .thenReturn(Set.of(1L, 2L, 3L));

        // When: 查询角色的用户
        Set<Long> userIds = userRoleService.getRoleUserIds(10L);

        // Then: 验证结果
        assertThat(userIds).containsExactlyInAnyOrder(1L, 2L, 3L);
        verify(userRoleRepository, times(1)).findUserIdsByRoleId(10L);
    }

    @Test
    @DisplayName("查询角色的用户详情 - 成功")
    void getRoleUsers_Success() {
        // Given: 模拟返回用户ID和用户详情
        User user1 = User.builder().id(1L).username("user1").build();
        User user2 = User.builder().id(2L).username("user2").build();

        when(userRoleRepository.findUserIdsByRoleId(10L))
                .thenReturn(Set.of(1L, 2L));
        when(userRepository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(user1, user2));

        // When: 查询角色的用户详情
        List<User> users = userRoleService.getRoleUsers(10L);

        // Then: 验证结果
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getUsername)
                .containsExactlyInAnyOrder("user1", "user2");
    }

    @Test
    @DisplayName("检查用户是否有特定角色 - 有")
    void userHasRole_True() {
        // Given: 用户有该角色
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L))
                .thenReturn(true);

        // When: 检查用户是否有角色
        boolean hasRole = userRoleService.userHasRole(1L, 10L);

        // Then: 应该返回 true
        assertThat(hasRole).isTrue();
    }

    @Test
    @DisplayName("检查用户是否有特定角色 - 没有")
    void userHasRole_False() {
        // Given: 用户没有该角色
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L))
                .thenReturn(false);

        // When: 检查用户是否有角色
        boolean hasRole = userRoleService.userHasRole(1L, 10L);

        // Then: 应该返回 false
        assertThat(hasRole).isFalse();
    }

    @Test
    @DisplayName("通过角色编码检查用户是否有角色 - 有")
    void userHasRoleCode_True() {
        // Given: 用户有该角色
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(10L));
        when(roleRepository.findByIdIn(Set.of(10L)))
                .thenReturn(List.of(chairmanRole));

        // When: 检查用户是否有角色编码
        boolean hasRole = userRoleService.userHasRoleCode(1L, "CHAIRMAN");

        // Then: 应该返回 true
        assertThat(hasRole).isTrue();
    }

    @Test
    @DisplayName("通过角色编码检查用户是否有角色 - 没有")
    void userHasRoleCode_False() {
        // Given: 用户有其他角色
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(20L));
        when(roleRepository.findByIdIn(Set.of(20L)))
                .thenReturn(List.of(warehouseAdminRole));

        // When: 检查用户是否有角色编码
        boolean hasRole = userRoleService.userHasRoleCode(1L, "CHAIRMAN");

        // Then: 应该返回 false
        assertThat(hasRole).isFalse();
    }

    @Test
    @DisplayName("查询角色的用户数量")
    void getUserCountForRole() {
        // Given: 角色有 5 个用户
        when(userRoleRepository.countByRoleId(10L))
                .thenReturn(5L);

        // When: 查询用户数量
        long count = userRoleService.getUserCountForRole(10L);

        // Then: 验证结果
        assertThat(count).isEqualTo(5L);
    }
}
