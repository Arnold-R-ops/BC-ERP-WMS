package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.controller.LocationController.CreateLocationRequest;
import com.wms.system.controller.LocationController.UpdateLocationRequest;
import com.wms.system.entity.Location;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.LocationRepository;
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
 * LocationController 集成测试
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
@DisplayName("LocationController 集成测试")
class LocationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocationRepository locationRepository;

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
    private Location testLocation;
    private User warehouseAdminUser;
    private SysRole warehouseAdminRole;
    private String warehouseAdminToken;

    private final String TEST_PASSWORD = "Test@123456";

    @BeforeEach
    void setUp() {
        // 清理数据
        locationRepository.deleteAll();
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

        // 创建测试仓库
        testWarehouse = Warehouse.builder()
                .code("WH01")
                .name("Main Warehouse")
                .address("123 Main St")
                .contact("John Doe")
                .isActive(true)
                .build();
        testWarehouse = warehouseRepository.save(testWarehouse);

        // 创建测试库位
        testLocation = Location.builder()
                .warehouse(testWarehouse)
                .zone(Zone.ZONE_A)
                .shelfNumber("A-01")
                .positionNumber("001")
                .enabled(true)
                .remark("Test location")
                .build();
        testLocation = locationRepository.save(testLocation);
    }

    @AfterEach
    void tearDown() {
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("根据ID获取库位 - 成功")
    void getLocationById_Success() throws Exception {
        mockMvc.perform(get("/api/locations/{id}", testLocation.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testLocation.getId()))
                .andExpect(jsonPath("$.zone").value("ZONE_A"))
                .andExpect(jsonPath("$.shelfNumber").value("A-01"))
                .andExpect(jsonPath("$.positionNumber").value("001"));
    }

    @Test
    @DisplayName("根据ID获取库位 - 不存在")
    void getLocationById_NotFound() throws Exception {
        mockMvc.perform(get("/api/locations/{id}", 999L)
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("LOCATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("根据仓库ID获取所有库位 - 成功")
    void getLocationsByWarehouse_Success() throws Exception {
        mockMvc.perform(get("/api/locations/warehouse/{warehouseId}", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].zone").value("ZONE_A"));
    }

    @Test
    @DisplayName("获取指定仓库的空闲库位 - 成功")
    void getEmptyLocationsByWarehouse_Success() throws Exception {
        mockMvc.perform(get("/api/locations/warehouse/{warehouseId}/empty", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    @DisplayName("创建库位 - 成功")
    void createLocation_Success() throws Exception {
        // Given
        CreateLocationRequest request = new CreateLocationRequest(
                testWarehouse.getId(),
                Zone.ZONE_B,
                "B-01",
                "001",
                "New location"
        );

        // When & Then
        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zone").value("ZONE_B"))
                .andExpect(jsonPath("$.shelfNumber").value("B-01"))
                .andExpect(jsonPath("$.positionNumber").value("001"))
                .andExpect(jsonPath("$.warehouseCode").value("WH01"));

        // Verify database
        assertThat(locationRepository.findByWarehouseId(testWarehouse.getId())).hasSize(2);
    }

    @Test
    @DisplayName("创建库位 - 仓库不存在")
    void createLocation_WarehouseNotFound() throws Exception {
        // Given
        CreateLocationRequest request = new CreateLocationRequest(
                999L,
                Zone.ZONE_A,
                "A-01",
                "002",
                null
        );

        // When & Then
        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("创建库位 - 库位已存在")
    void createLocation_LocationAlreadyExists() throws Exception {
        // Given - 尝试创建相同的库位
        CreateLocationRequest request = new CreateLocationRequest(
                testWarehouse.getId(),
                Zone.ZONE_A,
                "A-01",
                "001",
                null
        );

        // When & Then
        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("LOCATION_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("创建库位 - 验证失败（货架号为空）")
    void createLocation_ValidationFailed() throws Exception {
        // Given
        CreateLocationRequest request = new CreateLocationRequest(
                testWarehouse.getId(),
                Zone.ZONE_A,
                "",  // 空货架号
                "001",
                null
        );

        // When & Then
        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("更新库位 - 成功")
    void updateLocation_Success() throws Exception {
        // Given
        UpdateLocationRequest request = new UpdateLocationRequest("Updated remark");

        // When & Then
        mockMvc.perform(put("/api/locations/{id}", testLocation.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testLocation.getId()));

        // Verify database
        Location updated = locationRepository.findById(testLocation.getId()).orElseThrow();
        assertThat(updated.getRemark()).isEqualTo("Updated remark");
    }

    @Test
    @DisplayName("启用库位 - 成功")
    void enableLocation_Success() throws Exception {
        // Given - 先禁用库位
        testLocation.setEnabled(false);
        locationRepository.save(testLocation);

        // When & Then
        mockMvc.perform(put("/api/locations/{id}/enable", testLocation.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Verify database
        Location enabled = locationRepository.findById(testLocation.getId()).orElseThrow();
        assertThat(enabled.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("禁用库位 - 成功")
    void disableLocation_Success() throws Exception {
        mockMvc.perform(put("/api/locations/{id}/disable", testLocation.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Verify database
        Location disabled = locationRepository.findById(testLocation.getId()).orElseThrow();
        assertThat(disabled.getEnabled()).isFalse();
    }
}

