package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.LoginRequest;
import com.wms.system.dto.LoginResponse;
import com.wms.system.dto.SwitchRoleRequest;
import com.wms.system.dto.SwitchRoleResponse;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试 - 多角色系统
 *
 * 测试完整的 HTTP 请求/响应流程，包括：
 * 1. 多角色用户登录
 * 2. 单角色用户登录
 * 3. 角色切换成功/失败场景
 * 4. 默认角色选择逻辑
 * 5. JWT Token 验证
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role RBAC System)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("AuthController 集成测试 - 多角色系统")
@SuppressWarnings("unchecked")
class AuthControllerMultiRoleIntegrationTest {

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

    private User multiRoleUser;
    private User singleRoleUser;
    private User disabledUser;
    private User noRolesUser;
    private SysRole superAdminRole;
    private SysRole warehouseAdminRole;
    private SysRole salespersonRole;
    private SysRole purchaserRole;
    private SysRole disabledRole;

    private final String TEST_PASSWORD = "Test@123456";

    @BeforeEach
    void setUp() {
        // 清理数据
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // 确保角色存在
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

        purchaserRole = roleRepository.findByRoleCode("PURCHASER")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("PURCHASER")
                        .roleName("采购员")
                        .sortOrder(30)
                        .status("ACTIVE")
                        .build()));

        // 创建已禁用的角色
        disabledRole = roleRepository.save(SysRole.builder()
                .roleCode("DISABLED_TEST_ROLE")
                .roleName("已禁用测试角色")
                .sortOrder(99)
                .status("DISABLED")
                .build());

        // 创建多角色用户
        multiRoleUser = User.builder()
                .username("multi_role_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("多角色测试用户")
                .enabled(true)
                .defaultRoleId(warehouseAdminRole.getId())
                .build();
        multiRoleUser = userRepository.save(multiRoleUser);

        // 分配多个角色
        userRoleRepository.save(SysUserRole.builder()
                .userId(multiRoleUser.getId())
                .roleId(warehouseAdminRole.getId())
                .assignedBy(1L)
                .build());
        userRoleRepository.save(SysUserRole.builder()
                .userId(multiRoleUser.getId())
                .roleId(salespersonRole.getId())
                .assignedBy(1L)
                .build());
        userRoleRepository.save(SysUserRole.builder()
                .userId(multiRoleUser.getId())
                .roleId(purchaserRole.getId())
                .assignedBy(1L)
                .build());

        // 创建单角色用户
        singleRoleUser = User.builder()
                .username("single_role_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("单角色测试用户")
                .enabled(true)
                .defaultRoleId(superAdminRole.getId())
                .build();
        singleRoleUser = userRepository.save(singleRoleUser);

        userRoleRepository.save(SysUserRole.builder()
                .userId(singleRoleUser.getId())
                .roleId(superAdminRole.getId())
                .assignedBy(1L)
                .build());

        // 创建禁用账户
        disabledUser = User.builder()
                .username("disabled_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("禁用用户")
                .enabled(false)
                .build();
        disabledUser = userRepository.save(disabledUser);

        userRoleRepository.save(SysUserRole.builder()
                .userId(disabledUser.getId())
                .roleId(warehouseAdminRole.getId())
                .assignedBy(1L)
                .build());

        // 创建无角色用户
        noRolesUser = User.builder()
                .username("no_roles_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("无角色用户")
                .enabled(true)
                .build();
        noRolesUser = userRepository.save(noRolesUser);
        // 故意不分配任何角色
    }

    // ========== 测试：多角色登录 ==========

    @Test
    @DisplayName("登录成功 - 多角色用户 - 返回所有角色")
    void login_MultiRoleUser_Success() throws Exception {
        LoginRequest request = new LoginRequest("multi_role_user", TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.username", is("multi_role_user")))
                .andExpect(jsonPath("$.currentRole", is("WAREHOUSE_ADMIN"))) // 默认角色
                .andExpect(jsonPath("$.availableRoles", hasSize(3)))
                .andExpect(jsonPath("$.availableRoles", containsInAnyOrder(
                        "WAREHOUSE_ADMIN", "SALESPERSON", "PURCHASER"
                )))
                .andExpect(jsonPath("$.expiresIn", notNullValue()))
                .andReturn();

        // 验证返回的 Token 不为空
        String responseBody = result.getResponse().getContentAsString();
        LoginResponse response = objectMapper.readValue(responseBody, LoginResponse.class);
        assertThat(response.getToken()).isNotEmpty();
        assertThat(response.getAvailableRoles()).hasSize(3);
    }

    @Test
    @DisplayName("登录成功 - 单角色用户")
    void login_SingleRoleUser_Success() throws Exception {
        LoginRequest request = new LoginRequest("single_role_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("single_role_user")))
                .andExpect(jsonPath("$.currentRole", is("SUPER_ADMIN")))
                .andExpect(jsonPath("$.availableRoles", hasSize(1)))
                .andExpect(jsonPath("$.availableRoles", contains("SUPER_ADMIN")));
    }

    @Test
    @DisplayName("登录成功 - 使用用户默认角色")
    void login_UseDefaultRole_Success() throws Exception {
        // multiRoleUser 的默认角色是 WAREHOUSE_ADMIN
        LoginRequest request = new LoginRequest("multi_role_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRole", is("WAREHOUSE_ADMIN")));
    }

    @Test
    @DisplayName("登录成功 - 无默认角色时选择最小 sortOrder")
    void login_SelectMinSortOrder_Success() throws Exception {
        // 清除默认角色
        multiRoleUser.setDefaultRoleId(null);
        userRepository.save(multiRoleUser);

        LoginRequest request = new LoginRequest("multi_role_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRole", is("WAREHOUSE_ADMIN"))); // sortOrder=10 最小
    }

    @Test
    @DisplayName("登录失败 - 用户无角色 - 403")
    void login_NoRoles_Forbidden() throws Exception {
        LoginRequest request = new LoginRequest("no_roles_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("USER_NO_ROLES")));
    }

    @Test
    @DisplayName("登录失败 - 密码错误 - 401")
    void login_WrongPassword_Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest("multi_role_user", "wrong_password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorKey", is("AUTH_INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("登录失败 - 用户名不存在 - 401")
    void login_UserNotFound_Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorKey", is("AUTH_INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("登录失败 - 账户已禁用 - 403")
    void login_AccountDisabled_Forbidden() throws Exception {
        LoginRequest request = new LoginRequest("disabled_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("USER_ACCOUNT_DISABLED")));
    }

    // ========== 测试：角色切换 ==========

    @Test
    @DisplayName("切换角色 - 成功")
    void switchRole_Success() throws Exception {
        // 先登录获取 Token
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 切换到 SALESPERSON 角色
        SwitchRoleRequest switchRequest = new SwitchRoleRequest("SALESPERSON");

        MvcResult switchResult = mockMvc.perform(post("/api/auth/switch-role")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.currentRole", is("SALESPERSON")))
                .andExpect(jsonPath("$.message", containsString("SALESPERSON")))
                .andExpect(jsonPath("$.message", containsString("销售员")))
                .andReturn();

        // 验证新 Token 不同于旧 Token
        String switchResponseBody = switchResult.getResponse().getContentAsString();
        SwitchRoleResponse switchResponse = objectMapper.readValue(switchResponseBody, SwitchRoleResponse.class);
        assertThat(switchResponse.getToken()).isNotEqualTo(token);
    }

    @Test
    @DisplayName("切换角色 - 切换到所有可用角色")
    void switchRole_AllAvailableRoles_Success() throws Exception {
        // 登录
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 依次切换到所有角色
        for (String roleCode : new String[]{"SALESPERSON", "PURCHASER", "WAREHOUSE_ADMIN"}) {
            SwitchRoleRequest switchRequest = new SwitchRoleRequest(roleCode);

            MvcResult result = mockMvc.perform(post("/api/auth/switch-role")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(switchRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentRole", is(roleCode)))
                    .andReturn();

            // 更新 token 为新的 token
            String responseBody = result.getResponse().getContentAsString();
            SwitchRoleResponse response = objectMapper.readValue(responseBody, SwitchRoleResponse.class);
            token = response.getToken();
        }
    }

    @Test
    @DisplayName("切换角色 - 目标角色不存在 - 404")
    void switchRole_RoleNotFound_NotFound() throws Exception {
        // 登录
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 切换到不存在的角色
        SwitchRoleRequest switchRequest = new SwitchRoleRequest("NONEXISTENT_ROLE");

        mockMvc.perform(post("/api/auth/switch-role")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchRequest)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey", is("ROLE_NOT_FOUND")));
    }

    @Test
    @DisplayName("切换角色 - 角色未分配给用户 - 403")
    void switchRole_RoleNotAssigned_Forbidden() throws Exception {
        // 登录
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 切换到未分配的 SUPER_ADMIN 角色
        SwitchRoleRequest switchRequest = new SwitchRoleRequest("SUPER_ADMIN");

        mockMvc.perform(post("/api/auth/switch-role")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchRequest)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("ROLE_NOT_ASSIGNED")));
    }

    @Test
    @DisplayName("切换角色 - 目标角色已禁用 - 403")
    void switchRole_RoleDisabled_Forbidden() throws Exception {
        // 给用户分配已禁用的角色
        userRoleRepository.save(SysUserRole.builder()
                .userId(multiRoleUser.getId())
                .roleId(disabledRole.getId())
                .assignedBy(1L)
                .build());

        // 登录
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 尝试切换到已禁用的角色
        SwitchRoleRequest switchRequest = new SwitchRoleRequest("DISABLED_TEST_ROLE");

        mockMvc.perform(post("/api/auth/switch-role")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchRequest)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("ROLE_DISABLED")));
    }

    @Test
    @DisplayName("切换角色 - 无 Token - 403")
    void switchRole_NoToken_Forbidden() throws Exception {
        SwitchRoleRequest switchRequest = new SwitchRoleRequest("SALESPERSON");

        mockMvc.perform(post("/api/auth/switch-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchRequest)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("切换角色 - 无效 Token - 403")
    void switchRole_InvalidToken_Forbidden() throws Exception {
        SwitchRoleRequest switchRequest = new SwitchRoleRequest("SALESPERSON");

        mockMvc.perform(post("/api/auth/switch-role")
                        .header("Authorization", "Bearer invalid_token_xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchRequest)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }
}
