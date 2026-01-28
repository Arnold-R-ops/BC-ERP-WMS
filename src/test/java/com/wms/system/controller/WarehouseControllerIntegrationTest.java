package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.controller.WarehouseController.CreateWarehouseRequest;
import com.wms.system.controller.WarehouseController.UpdateWarehouseRequest;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.entity.Warehouse;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WarehouseController 集成测试
 *
 * 使用 @SpringBootTest 启动完整的 Spring 容器
 * 使用 MockMvc 模拟 HTTP 请求
 * 使用真实的数据库进行测试
 *
 * 测试覆盖：
 * 1. 完整的 HTTP 请求/响应流程
 * 2. 数据库持久化验证
 * 3. 业务逻辑验证
 * 4. 异常处理验证
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("WarehouseController 集成测试")
class WarehouseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

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

    private Warehouse testWarehouse;
    private User warehouseAdminUser;
    private SysRole warehouseAdminRole;
    private String warehouseAdminToken;

    private final String TEST_PASSWORD = "Test@123456";

    @BeforeEach
    void setUp() {
        // 清理数据
        warehouseRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // 确保角色存在 - 使用 SUPER_ADMIN 以绕过权限检查
        warehouseAdminRole = roleRepository.findByRoleCode("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("SUPER_ADMIN")
                        .roleName("超级管理员")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .build()));

        // 创建测试用户
        warehouseAdminUser = User.builder()
                .username("warehouse_admin")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("仓库管理员")
                .enabled(true)
                .defaultRoleId(warehouseAdminRole.getId())
                .build();
        warehouseAdminUser = userRepository.save(warehouseAdminUser);

        // 分配角色
        userRoleRepository.save(SysUserRole.builder()
                .userId(warehouseAdminUser.getId())
                .roleId(warehouseAdminRole.getId())
                .assignedBy(warehouseAdminUser.getId())
                .build());

        // 生成 JWT Token
        warehouseAdminToken = jwtUtil.generateToken(warehouseAdminUser.getUsername(), "SUPER_ADMIN");

        // 创建测试数据
        testWarehouse = Warehouse.builder()
                .code("WH01")
                .name("Main Warehouse")
                .address("123 Main St")
                .contact("John Doe")
                .isActive(true)
                .build();
        testWarehouse = warehouseRepository.save(testWarehouse);
    }

    @AfterEach
    void tearDown() {
        warehouseRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("获取所有仓库 - 成功")
    void getAllWarehouses_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses")
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("WH01"))
                .andExpect(jsonPath("$[0].name").value("Main Warehouse"));
    }

    @Test
    @DisplayName("获取所有激活的仓库 - 成功")
    void getAllActiveWarehouses_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/active")
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    @DisplayName("根据ID获取仓库 - 成功")
    void getWarehouseById_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testWarehouse.getId()))
                .andExpect(jsonPath("$.code").value("WH01"));
    }

    @Test
    @DisplayName("根据ID获取仓库 - 不存在")
    void getWarehouseById_NotFound() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", 999L)
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("根据编码获取仓库 - 成功")
    void getWarehouseByCode_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/code/{code}", "WH01")
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WH01"));
    }

    @Test
    @DisplayName("创建仓库 - 成功")
    void createWarehouse_Success() throws Exception {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH02",
                "New Warehouse",
                "456 New St",
                "Jane Doe"
        );

        // When & Then
        mockMvc.perform(post("/api/warehouses")
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WH02"))
                .andExpect(jsonPath("$.name").value("New Warehouse"));

        // Verify database
        assertThat(warehouseRepository.findByCode("WH02")).isPresent();
    }

    @Test
    @DisplayName("创建仓库 - 编码已存在")
    void createWarehouse_CodeAlreadyExists() throws Exception {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH01",
                "Duplicate Warehouse",
                null,
                null
        );

        // When & Then
        mockMvc.perform(post("/api/warehouses")
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("WAREHOUSE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("创建仓库 - 验证失败（编码格式错误）")
    void createWarehouse_ValidationFailed() throws Exception {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "invalid",  // 小写，不符合格式要求
                "Invalid Warehouse",
                null,
                null
        );

        // When & Then
        mockMvc.perform(post("/api/warehouses")
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("更新仓库 - 成功")
    void updateWarehouse_Success() throws Exception {
        // Given
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Updated Name",
                "Updated Address",
                "Updated Contact"
        );

        // When & Then
        mockMvc.perform(put("/api/warehouses/{id}", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testWarehouse.getId()));

        // Verify database
        Warehouse updated = warehouseRepository.findById(testWarehouse.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getAddress()).isEqualTo("Updated Address");
        assertThat(updated.getContact()).isEqualTo("Updated Contact");
    }

    @Test
    @DisplayName("激活仓库 - 成功")
    void activateWarehouse_Success() throws Exception {
        // Given - 先停用仓库
        testWarehouse.setIsActive(false);
        warehouseRepository.save(testWarehouse);

        // When & Then
        mockMvc.perform(put("/api/warehouses/{id}/activate", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));

        // Verify database
        Warehouse activated = warehouseRepository.findById(testWarehouse.getId()).orElseThrow();
        assertThat(activated.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("停用仓库 - 成功")
    void deactivateWarehouse_Success() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Verify database
        Warehouse deactivated = warehouseRepository.findById(testWarehouse.getId()).orElseThrow();
        assertThat(deactivated.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("获取仓库的库位数量 - 成功")
    void getLocationCount_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}/location-count", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNumber());
    }
}
