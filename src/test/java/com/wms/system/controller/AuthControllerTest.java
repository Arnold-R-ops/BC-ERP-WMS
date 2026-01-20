package com.wms.system.controller;

import com.wms.system.dto.*;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.JwtUtil;
import com.wms.system.service.UserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthController 单元测试（多角色系统）
 *
 * 使用 Mockito 模拟所有依赖，专注于测试控制器逻辑。
 *
 * 测试覆盖：
 * 1. 多角色登录（成功/无角色/无活跃角色/凭证错误）
 * 2. 角色切换（成功/角色不存在/角色未分配/角色已禁用）
 * 3. 默认角色选择逻辑
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role RBAC System)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 单元测试 - 多角色系统")
@SuppressWarnings("unchecked")
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private UserRoleService userRoleService;

    @InjectMocks
    private AuthController authController;

    private User testUser;
    private SysRole warehouseAdminRole;
    private SysRole salespersonRole;
    private SysRole purchaserRole;
    private SysRole disabledRole;

    @BeforeEach
    void setUp() {
        // 设置 JWT 过期时间
        ReflectionTestUtils.setField(authController, "jwtExpiration", 86400000L);

        // 创建测试用户
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("encoded_password")
                .displayName("测试用户")
                .enabled(true)
                .defaultRoleId(3L) // WAREHOUSE_ADMIN
                .build();

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

        disabledRole = SysRole.builder()
                .id(9L)
                .roleCode("DISABLED_ROLE")
                .roleName("已禁用角色")
                .sortOrder(40)
                .status("DISABLED") // 已禁用
                .build();
    }

    // ========== 测试：多角色登录 ==========

    @Test
    @DisplayName("登录成功 - 多角色用户")
    void login_Success_MultipleRoles() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole));
        when(jwtUtil.generateTokenWithRoles(
                eq("test_user"),
                eq("WAREHOUSE_ADMIN"),
                anyList()
        )).thenReturn("mock_jwt_token");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        LoginResponse body = response.getBody();
        assertThat(body.getToken()).isEqualTo("mock_jwt_token");
        assertThat(body.getUsername()).isEqualTo("test_user");
        assertThat(body.getCurrentRole()).isEqualTo("WAREHOUSE_ADMIN");
        assertThat(body.getAvailableRoles()).containsExactly("WAREHOUSE_ADMIN", "SALESPERSON");
        assertThat(body.getExpiresIn()).isEqualTo(86400000L);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRoleService).getUserRoles(1L);
        verify(jwtUtil).generateTokenWithRoles(eq("test_user"), eq("WAREHOUSE_ADMIN"), anyList());
    }

    @Test
    @DisplayName("登录成功 - 使用默认角色")
    void login_Success_WithDefaultRole() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        testUser.setDefaultRoleId(5L); // 设置默认角色为 SALESPERSON

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole));
        when(jwtUtil.generateTokenWithRoles(anyString(), anyString(), anyList()))
                .thenReturn("mock_jwt_token");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Then
        assertThat(response.getBody().getCurrentRole()).isEqualTo("SALESPERSON");

        verify(jwtUtil).generateTokenWithRoles(eq("test_user"), eq("SALESPERSON"), anyList());
    }

    @Test
    @DisplayName("登录成功 - 选择最小 sortOrder 角色")
    void login_Success_SelectMinSortOrderRole() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        testUser.setDefaultRoleId(null); // 无默认角色

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(purchaserRole, salespersonRole, warehouseAdminRole)); // 乱序
        when(jwtUtil.generateTokenWithRoles(anyString(), anyString(), anyList()))
                .thenReturn("mock_jwt_token");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Then
        // 应该选择 sortOrder 最小的 warehouseAdminRole (sortOrder=10)
        assertThat(response.getBody().getCurrentRole()).isEqualTo("WAREHOUSE_ADMIN");

        verify(jwtUtil).generateTokenWithRoles(eq("test_user"), eq("WAREHOUSE_ADMIN"), anyList());
    }

    @Test
    @DisplayName("登录失败 - 用户无角色")
    void login_Fail_NoRoles() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L)).thenReturn(Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NO_ROLES);

        verify(userRoleService).getUserRoles(1L);
        verify(jwtUtil, never()).generateTokenWithRoles(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("登录失败 - 用户无活跃角色")
    void login_Fail_NoActiveRoles() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(disabledRole)); // 只有已禁用的角色

        // When & Then
        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NO_ACTIVE_ROLES);

        verify(jwtUtil, never()).generateTokenWithRoles(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("登录失败 - 凭证错误")
    void login_Fail_BadCredentials() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "wrong_password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // When & Then
        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.AUTH_INVALID_CREDENTIALS);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("登录失败 - 账号已禁用")
    void login_Fail_AccountDisabled() {
        // Given
        LoginRequest request = new LoginRequest("disabled_user", "password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("Account disabled"));

        // When & Then
        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_ACCOUNT_DISABLED);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    // ========== 测试：角色切换 ==========

    @Test
    @DisplayName("切换角色 - 成功")
    void switchRole_Success() {
        // Given
        SwitchRoleRequest request = new SwitchRoleRequest("SALESPERSON");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test_user");

        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(roleRepository.findByRoleCode("SALESPERSON")).thenReturn(Optional.of(salespersonRole));
        when(userRoleService.userHasRole(1L, 5L)).thenReturn(true);
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole));
        when(jwtUtil.generateTokenWithRoles(
                eq("test_user"),
                eq("SALESPERSON"),
                anyList()
        )).thenReturn("new_mock_jwt_token");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<SwitchRoleResponse> response = authController.switchRole(request, authentication);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        SwitchRoleResponse body = response.getBody();
        assertThat(body.getToken()).isEqualTo("new_mock_jwt_token");
        assertThat(body.getCurrentRole()).isEqualTo("SALESPERSON");
        assertThat(body.getMessage()).contains("SALESPERSON");
        assertThat(body.getMessage()).contains("销售员");
        assertThat(testUser.getDefaultRoleId()).isEqualTo(5L);

        verify(roleRepository).findByRoleCode("SALESPERSON");
        verify(userRoleService).userHasRole(1L, 5L);
        verify(jwtUtil).generateTokenWithRoles(eq("test_user"), eq("SALESPERSON"), anyList());
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("切换角色 - 目标角色不存在")
    void switchRole_Fail_RoleNotFound() {
        // Given
        SwitchRoleRequest request = new SwitchRoleRequest("NONEXISTENT_ROLE");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test_user");

        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(roleRepository.findByRoleCode("NONEXISTENT_ROLE")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authController.switchRole(request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_FOUND);

        verify(roleRepository).findByRoleCode("NONEXISTENT_ROLE");
        verify(userRoleService, never()).userHasRole(anyLong(), anyLong());
        verify(jwtUtil, never()).generateTokenWithRoles(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("切换角色 - 角色未分配给用户")
    void switchRole_Fail_RoleNotAssigned() {
        // Given
        SwitchRoleRequest request = new SwitchRoleRequest("PURCHASER");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test_user");

        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(roleRepository.findByRoleCode("PURCHASER")).thenReturn(Optional.of(purchaserRole));
        when(userRoleService.userHasRole(1L, 7L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authController.switchRole(request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_ASSIGNED);

        verify(userRoleService).userHasRole(1L, 7L);
        verify(jwtUtil, never()).generateTokenWithRoles(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("切换角色 - 目标角色已禁用")
    void switchRole_Fail_RoleDisabled() {
        // Given
        SwitchRoleRequest request = new SwitchRoleRequest("DISABLED_ROLE");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test_user");

        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(roleRepository.findByRoleCode("DISABLED_ROLE")).thenReturn(Optional.of(disabledRole));
        when(userRoleService.userHasRole(1L, 9L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authController.switchRole(request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_DISABLED);

        verify(userRoleService).userHasRole(1L, 9L);
        verify(jwtUtil, never()).generateTokenWithRoles(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("切换角色 - 用户不存在")
    void switchRole_Fail_UserNotFound() {
        // Given
        SwitchRoleRequest request = new SwitchRoleRequest("SALESPERSON");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("nonexistent_user");

        when(userRepository.findByUsername("nonexistent_user")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authController.switchRole(request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NOT_FOUND);

        verify(userRepository).findByUsername("nonexistent_user");
        verify(roleRepository, never()).findByRoleCode(anyString());
    }
}
