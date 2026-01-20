package com.wms.system.controller;

import com.wms.system.dto.*;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.service.PermissionCacheService;
import com.wms.system.service.UserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserController 单元测试
 *
 * 使用 Mockito 模拟所有依赖，专注于测试控制器逻辑。
 *
 * 测试覆盖：
 * 1. 获取所有用户
 * 2. 创建用户（成功/用户名重复/角色不存在）
 * 3. 更新用户（成功/用户不存在/默认角色无效）
 * 4. 删除用户（成功/用户不存在）
 * 5. 批量分配角色（成功/用户不存在/角色不存在）
 * 6. 移除单个角色（成功/最后一个角色/角色未分配）
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role RBAC System)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 单元测试")
@SuppressWarnings("unchecked")
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PermissionCacheService cacheService;

    @InjectMocks
    private UserController userController;

    private User testUser;
    private SysRole warehouseAdminRole;
    private SysRole salespersonRole;
    private SysRole purchaserRole;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("encoded_password")
                .displayName("测试用户")
                .enabled(true)
                .defaultRoleId(3L)
                .remark("测试备注")
                .build();
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());

        // 创建测试角色
        warehouseAdminRole = SysRole.builder()
                .id(3L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("仓库管理员")
                .sortOrder(10)
                .status("ACTIVE")
                .build();

        salespersonRole = SysRole.builder()
                .id(5L)
                .roleCode("SALESPERSON")
                .roleName("销售员")
                .sortOrder(20)
                .status("ACTIVE")
                .build();

        purchaserRole = SysRole.builder()
                .id(7L)
                .roleCode("PURCHASER")
                .roleName("采购员")
                .sortOrder(30)
                .status("ACTIVE")
                .build();
    }

    // ========== 测试：获取所有用户 ==========

    @Test
    @DisplayName("获取所有用户 - 成功")
    void getAllUsers_Success() {
        // Given
        List<User> users = Arrays.asList(testUser);
        when(userRepository.findAll()).thenReturn(users);
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));

        // When
        ResponseEntity<List<UserWithRolesDTO>> response = userController.getAllUsers();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);

        UserWithRolesDTO dto = response.getBody().get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUsername()).isEqualTo("test_user");
        assertThat(dto.getRoleCodes()).containsExactly("WAREHOUSE_ADMIN", "SALESPERSON");
        assertThat(dto.getRoleNames()).containsExactly("仓库管理员", "销售员");
        assertThat(dto.getDefaultRoleCode()).isEqualTo("WAREHOUSE_ADMIN");

        verify(userRepository).findAll();
        verify(userRoleService).getUserRoles(1L);
    }

    @Test
    @DisplayName("获取所有用户 - 空列表")
    void getAllUsers_EmptyList() {
        // Given
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<List<UserWithRolesDTO>> response = userController.getAllUsers();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();

        verify(userRepository).findAll();
        verifyNoInteractions(userRoleService);
    }

    // ========== 测试：创建用户 ==========

    @Test
    @DisplayName("创建用户 - 成功")
    void createUser_Success() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_user")
                .password("Password@123")
                .displayName("新用户")
                .roleIds(Arrays.asList(3L, 5L))
                .enabled(true)
                .remark("新创建的用户")
                .build();

        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));
        when(roleRepository.findById(5L)).thenReturn(Optional.of(salespersonRole));
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return user;
        });
        when(userRoleService.getUserRoles(10L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole));

        // When
        ResponseEntity<UserWithRolesDTO> response = userController.createUser(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsername()).isEqualTo("new_user");
        assertThat(response.getBody().getRoleCodes()).containsExactly("WAREHOUSE_ADMIN", "SALESPERSON");

        verify(userRepository).existsByUsername("new_user");
        verify(passwordEncoder).encode("Password@123");
        verify(userRepository).save(any(User.class));
        verify(userRoleService).assignRolesToUser(eq(10L), any(HashSet.class), eq(10L));
    }

    @Test
    @DisplayName("创建用户 - 用户名已存在")
    void createUser_UsernameExists() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .username("existing_user")
                .password("Password@123")
                .roleIds(Arrays.asList(3L))
                .build();

        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userController.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_ALREADY_EXISTS);

        verify(userRepository).existsByUsername("existing_user");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("创建用户 - 角色不存在")
    void createUser_RoleNotFound() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_user")
                .password("Password@123")
                .roleIds(Arrays.asList(999L))
                .build();

        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_FOUND);

        verify(userRepository, never()).save(any(User.class));
    }

    // ========== 测试：更新用户 ==========

    @Test
    @DisplayName("更新用户 - 成功")
    void updateUser_Success() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("更新后的名称")
                .enabled(false)
                .remark("更新后的备注")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));

        // When
        ResponseEntity<UserWithRolesDTO> response = userController.updateUser(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testUser.getDisplayName()).isEqualTo("更新后的名称");
        assertThat(testUser.getEnabled()).isFalse();
        assertThat(testUser.getRemark()).isEqualTo("更新后的备注");

        verify(userRepository).findById(1L);
        verify(userRepository).save(testUser);
        verify(cacheService).onUserUpdated(1L);
    }

    @Test
    @DisplayName("更新用户 - 用户不存在")
    void updateUser_UserNotFound() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("更新后的名称")
                .build();

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.updateUser(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NOT_FOUND);

        verify(userRepository).findById(999L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 默认角色不存在")
    void updateUser_DefaultRoleNotFound() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .defaultRoleId(999L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.updateUser(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_FOUND);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户 - 默认角色未分配给用户")
    void updateUser_DefaultRoleNotAssigned() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .defaultRoleId(7L) // PURCHASER
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(7L)).thenReturn(Optional.of(purchaserRole));
        when(userRoleService.userHasRole(1L, 7L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userController.updateUser(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_ASSIGNED);

        verify(userRepository, never()).save(any(User.class));
    }

    // ========== 测试：删除用户 ==========

    @Test
    @DisplayName("删除用户 - 成功")
    void deleteUser_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        doNothing().when(userRoleRepository).deleteByUserId(1L);
        doNothing().when(userRepository).delete(testUser);
        doNothing().when(cacheService).onUserDeleted(1L);

        // When
        ResponseEntity<Void> response = userController.deleteUser(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(userRepository).findById(1L);
        verify(userRoleRepository).deleteByUserId(1L);
        verify(userRepository).delete(testUser);
        verify(cacheService).onUserDeleted(1L);
    }

    @Test
    @DisplayName("删除用户 - 用户不存在")
    void deleteUser_UserNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.deleteUser(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NOT_FOUND);

        verify(userRepository).findById(999L);
        verify(userRepository, never()).delete(any(User.class));
    }

    // ========== 测试：批量分配角色 ==========

    @Test
    @DisplayName("批量分配角色 - 成功")
    void assignRoles_Success() {
        // Given
        AssignRolesRequest request = AssignRolesRequest.builder()
                .roleIds(Arrays.asList(3L, 5L, 7L))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));
        when(roleRepository.findById(5L)).thenReturn(Optional.of(salespersonRole));
        when(roleRepository.findById(7L)).thenReturn(Optional.of(purchaserRole));
        doNothing().when(userRoleService).assignRolesToUser(anyLong(), any(HashSet.class), anyLong());
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole, purchaserRole));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));

        // When
        ResponseEntity<UserWithRolesDTO> response = userController.assignRoles(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRoleCodes()).hasSize(3);

        verify(userRoleService).assignRolesToUser(eq(1L), any(HashSet.class), eq(1L));
        verify(cacheService).onUserRoleAssigned(1L);
    }

    @Test
    @DisplayName("批量分配角色 - 用户不存在")
    void assignRoles_UserNotFound() {
        // Given
        AssignRolesRequest request = AssignRolesRequest.builder()
                .roleIds(Arrays.asList(3L))
                .build();

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.assignRoles(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NOT_FOUND);

        verify(userRoleService, never()).assignRolesToUser(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("批量分配角色 - 角色不存在")
    void assignRoles_RoleNotFound() {
        // Given
        AssignRolesRequest request = AssignRolesRequest.builder()
                .roleIds(Arrays.asList(999L))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.assignRoles(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_FOUND);

        verify(userRoleService, never()).assignRolesToUser(anyLong(), any(), anyLong());
    }

    // ========== 测试：移除单个角色 ==========

    @Test
    @DisplayName("移除角色 - 成功")
    void removeRole_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(5L)).thenReturn(Optional.of(salespersonRole));
        when(userRoleService.userHasRole(1L, 5L)).thenReturn(true);
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole)); // 有2个角色
        doNothing().when(userRoleService).removeRoleFromUser(1L, 5L);
        doNothing().when(cacheService).onUserRoleRemoved(1L);

        // When
        ResponseEntity<Void> response = userController.removeRole(1L, 5L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(userRoleService).removeRoleFromUser(1L, 5L);
        verify(cacheService).onUserRoleRemoved(1L);
    }

    @Test
    @DisplayName("移除角色 - 用户不存在")
    void removeRole_UserNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.removeRole(999L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NOT_FOUND);

        verify(userRoleService, never()).removeRoleFromUser(anyLong(), anyLong());
    }

    @Test
    @DisplayName("移除角色 - 角色不存在")
    void removeRole_RoleNotFound() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userController.removeRole(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_FOUND);

        verify(userRoleService, never()).removeRoleFromUser(anyLong(), anyLong());
    }

    @Test
    @DisplayName("移除角色 - 角色未分配给用户")
    void removeRole_RoleNotAssigned() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(7L)).thenReturn(Optional.of(purchaserRole));
        when(userRoleService.userHasRole(1L, 7L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userController.removeRole(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_ASSIGNED);

        verify(userRoleService, never()).removeRoleFromUser(anyLong(), anyLong());
    }

    @Test
    @DisplayName("移除角色 - 不能移除最后一个角色")
    void removeRole_CannotRemoveLastRole() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));
        when(userRoleService.userHasRole(1L, 3L)).thenReturn(true);
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole)); // 只有1个角色

        // When & Then
        assertThatThrownBy(() -> userController.removeRole(1L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(userRoleService, never()).removeRoleFromUser(anyLong(), anyLong());
    }
}
