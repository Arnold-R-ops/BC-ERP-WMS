package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.*;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 集成测试
 *
 * 测试完整的 HTTP 请求/响应流程，包括 Spring Security 权限校验。
 *
 * 测试场景：
 * 1. 获取所有用户（需要 SUPER_ADMIN 权限）
 * 2. 创建用户（成功/用户名重复/无权限）
 * 3. 更新用户（成功/用户不存在/无权限）
 * 4. 删除用户（成功/用户不存在/无权限）
 * 5. 批量分配角色（成功/角色不存在/无权限）
 * 6. 移除角色（成功/最后一个角色/无权限）
 * 7. 权限测试（非 SUPER_ADMIN 访问用户管理接口）
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role RBAC System)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@DisplayName("UserController 集成测试")
@SuppressWarnings("unchecked")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private User superAdminUser;
    private User warehouseUser;
    private SysRole superAdminRole;
    private SysRole warehouseAdminRole;
    private SysRole salespersonRole;
    private String superAdminToken;
    private String warehouseToken;

    private final String TEST_PASSWORD = "Test@123456";

    @BeforeEach
    void setUp() {
        // 清理数据
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // 确保角色存在（应该由 Flyway 创建）
        superAdminRole = roleRepository.findByRoleCode("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("SUPER_ADMIN")
                        .roleName("超级管理员")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .build()));

        warehouseAdminRole = roleRepository.findByRoleCode("WAREHOUSE_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("WAREHOUSE_ADMIN")
                        .roleName("仓库管理员")
                        .sortOrder(10)
                        .status("ACTIVE")
                        .build()));

        salespersonRole = roleRepository.findByRoleCode("SALESPERSON")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("SALESPERSON")
                        .roleName("销售员")
                        .sortOrder(20)
                        .status("ACTIVE")
                        .build()));

        // 创建超级管理员用户
        superAdminUser = User.builder()
                .username("super_admin")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("超级管理员")
                .enabled(true)
                .defaultRoleId(superAdminRole.getId())
                .build();
        superAdminUser = userRepository.save(superAdminUser);

        // 分配 SUPER_ADMIN 角色
        userRoleRepository.save(SysUserRole.builder()
                .userId(superAdminUser.getId())
                .roleId(superAdminRole.getId())
                .assignedBy(superAdminUser.getId())
                .build());

        // 创建仓库管理员用户
        warehouseUser = User.builder()
                .username("warehouse_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("仓库管理员")
                .enabled(true)
                .defaultRoleId(warehouseAdminRole.getId())
                .build();
        warehouseUser = userRepository.save(warehouseUser);

        // 分配 WAREHOUSE_ADMIN 角色
        userRoleRepository.save(SysUserRole.builder()
                .userId(warehouseUser.getId())
                .roleId(warehouseAdminRole.getId())
                .assignedBy(superAdminUser.getId())
                .build());

        // 生成 JWT Token
        superAdminToken = jwtUtil.generateToken(superAdminUser.getUsername(), "SUPER_ADMIN");
        warehouseToken = jwtUtil.generateToken(warehouseUser.getUsername(), "WAREHOUSE_ADMIN");
    }

    // ========== 测试：获取所有用户 ==========

    @Test
    @DisplayName("获取所有用户 - SUPER_ADMIN - 成功")
    void getAllUsers_AsSuperAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username", is("super_admin")))
                .andExpect(jsonPath("$[0].roleCodes", contains("SUPER_ADMIN")))
                .andExpect(jsonPath("$[1].username", is("warehouse_user")))
                .andExpect(jsonPath("$[1].roleCodes", contains("WAREHOUSE_ADMIN")));
    }

    @Test
    @DisplayName("获取所有用户 - WAREHOUSE_ADMIN - 权限不足")
    void getAllUsers_AsWarehouseAdmin_Forbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + warehouseToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("获取所有用户 - 无 Token - 未授权")
    void getAllUsers_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isForbidden()); // Spring Security 返回 403
    }

    // ========== 测试：创建用户 ==========

    @Test
    @DisplayName("创建用户 - SUPER_ADMIN - 成功")
    void createUser_AsSuperAdmin_Success() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_employee")
                .password("NewPass@123")
                .displayName("新员工")
                .roleIds(Arrays.asList(warehouseAdminRole.getId(), salespersonRole.getId()))
                .enabled(true)
                .remark("集成测试创建")
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("new_employee")))
                .andExpect(jsonPath("$.displayName", is("新员工")))
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.roleCodes", hasSize(2)))
                .andExpect(jsonPath("$.roleCodes", containsInAnyOrder("WAREHOUSE_ADMIN", "SALESPERSON")))
                .andExpect(jsonPath("$.defaultRoleCode", is("WAREHOUSE_ADMIN")));
    }

    @Test
    @DisplayName("创建用户 - 用户名已存在 - 409 冲突")
    void createUser_UsernameExists_Conflict() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("super_admin") // 已存在
                .password("NewPass@123")
                .roleIds(Arrays.asList(warehouseAdminRole.getId()))
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey", is("USER_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("创建用户 - 角色不存在 - 404")
    void createUser_RoleNotFound_NotFound() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_user")
                .password("NewPass@123")
                .roleIds(Arrays.asList(999L)) // 不存在的角色
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey", is("ROLE_NOT_FOUND")));
    }

    @Test
    @DisplayName("创建用户 - 参数校验失败 - 空用户名")
    void createUser_ValidationFailed_BlankUsername() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("") // 空用户名
                .password("NewPass@123")
                .roleIds(Arrays.asList(warehouseAdminRole.getId()))
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ========== 测试：更新用户 ==========

    @Test
    @DisplayName("更新用户 - SUPER_ADMIN - 成功")
    void updateUser_AsSuperAdmin_Success() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("更新后的显示名")
                .enabled(false)
                .remark("已禁用账户")
                .build();

        mockMvc.perform(put("/api/users/" + warehouseUser.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("更新后的显示名")))
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.remark", is("已禁用账户")));
    }

    @Test
    @DisplayName("更新用户 - 用户不存在 - 404")
    void updateUser_UserNotFound_NotFound() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("测试")
                .build();

        mockMvc.perform(put("/api/users/999")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey", is("USER_NOT_FOUND")));
    }

    // ========== 测试：删除用户 ==========

    @Test
    @DisplayName("删除用户 - SUPER_ADMIN - 成功")
    void deleteUser_AsSuperAdmin_Success() throws Exception {
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // 验证用户已删除
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(jsonPath("$", hasSize(1))); // 只剩 super_admin
    }

    @Test
    @DisplayName("删除用户 - 用户不存在 - 404")
    void deleteUser_UserNotFound_NotFound() throws Exception {
        mockMvc.perform(delete("/api/users/999")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey", is("USER_NOT_FOUND")));
    }

    // ========== 测试：批量分配角色 ==========

    @Test
    @DisplayName("批量分配角色 - SUPER_ADMIN - 成功")
    void assignRoles_AsSuperAdmin_Success() throws Exception {
        AssignRolesRequest request = AssignRolesRequest.builder()
                .roleIds(Arrays.asList(
                        warehouseAdminRole.getId(),
                        salespersonRole.getId()
                ))
                .build();

        mockMvc.perform(post("/api/users/" + warehouseUser.getId() + "/roles")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes", hasSize(2)))
                .andExpect(jsonPath("$.roleCodes", containsInAnyOrder("WAREHOUSE_ADMIN", "SALESPERSON")));
    }

    @Test
    @DisplayName("批量分配角色 - 角色不存在 - 404")
    void assignRoles_RoleNotFound_NotFound() throws Exception {
        AssignRolesRequest request = AssignRolesRequest.builder()
                .roleIds(Arrays.asList(999L))
                .build();

        mockMvc.perform(post("/api/users/" + warehouseUser.getId() + "/roles")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey", is("ROLE_NOT_FOUND")));
    }

    // ========== 测试：移除角色 ==========

    @Test
    @DisplayName("移除角色 - SUPER_ADMIN - 成功")
    void removeRole_AsSuperAdmin_Success() throws Exception {
        // 先给用户分配两个角色
        userRoleRepository.save(SysUserRole.builder()
                .userId(warehouseUser.getId())
                .roleId(salespersonRole.getId())
                .assignedBy(superAdminUser.getId())
                .build());

        // 移除 SALESPERSON 角色
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId() + "/roles/" + salespersonRole.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // 验证角色已移除
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(jsonPath("$[?(@.username == 'warehouse_user')].roleCodes", contains(contains("WAREHOUSE_ADMIN"))));
    }

    @Test
    @DisplayName("移除角色 - 不能移除最后一个角色 - 403")
    void removeRole_CannotRemoveLastRole_Forbidden() throws Exception {
        // warehouseUser 只有一个角色 WAREHOUSE_ADMIN
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId() + "/roles/" + warehouseAdminRole.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("OPERATION_NOT_ALLOWED")));
    }

    @Test
    @DisplayName("移除角色 - 角色未分配 - 403")
    void removeRole_RoleNotAssigned_Forbidden() throws Exception {
        // warehouseUser 没有 SALESPERSON 角色
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId() + "/roles/" + salespersonRole.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("ROLE_NOT_ASSIGNED")));
    }

    // ========== 测试：权限控制 ==========

    @Test
    @DisplayName("权限测试 - WAREHOUSE_ADMIN 尝试创建用户 - 403")
    void permissionTest_WarehouseAdminCreateUser_Forbidden() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("test_user")
                .password("Test@123")
                .roleIds(Arrays.asList(warehouseAdminRole.getId()))
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("权限测试 - WAREHOUSE_ADMIN 尝试删除用户 - 403")
    void permissionTest_WarehouseAdminDeleteUser_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/users/" + superAdminUser.getId())
                        .header("Authorization", "Bearer " + warehouseToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }
}
