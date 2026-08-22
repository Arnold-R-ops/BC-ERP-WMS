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
 * WarehouseController 闂傚倸鍊稿Λ妤€螞濞嗘挸鍨傛慨姗嗗劒閸︻厸鍋撻敐搴″箻婵?
 *
 * 濠电偠鎻紞鈧繛澶嬫礋瀵?@SpringBootTest 闂備礁鎲￠崙褰掑垂閻楀牊鍙忛柍鍝勫暊閸嬫捇鎮烽悧鍫熸嫳闂佸搫妫寸紞渚€骞?Spring 闂佽楠稿﹢閬嶅箠閹炬枼鏋?
 * 濠电偠鎻紞鈧繛澶嬫礋瀵?MockMvc 婵犵妲呴崹顏堝礈濠靛牃鍋?HTTP 闂佽崵濮村ú顓㈠绩闁秵鍎?
 * 濠电偠鎻紞鈧繛澶嬫礋瀵偊濡舵径瀣壋闂佺粯妫冮ˉ鎾诲级娴犲鐓熼柕濞垮劚椤忣亪鏌￠崱娆忔灈妤犵偞鍨块、娆撴嚃閳哄倻娈ら梺鍝勵槴閺呮粎绮欓弽顓溾偓渚€骞嬪婵嗘贡閳ь剨缍嗛崢鎯?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ矋閸熸椽鏌涢埄鍐噭缁惧彞鍗抽弻? * 1. 闂佽娴烽幊鎾诲嫉椤掑嫬姹查柨婵嗩槹閸?HTTP 闂佽崵濮村ú顓㈠绩闁秵鍎?闂備礁鎲＄换鍌滅矓鐎垫瓕濮抽柟缁樺础鐟欏嫭濯撮悶娑掑墲閻?
 * 2. 闂備浇妗ㄩ懗鑸垫櫠濡も偓閻ｅ灚鎷呯憴鍕妳闂佸湱鍋撳娆撴儊椤斿皷妲堥柡鍌涘閸ｅ綊鎮楅棃娑樼骇妞ゃ劊鍎遍悾婵嬪礃椤忓拋娼?
 * 3. 濠电偞鍨堕幐濠氭嚌閻愵剚鍙忛柣鏂垮悑閻掑鏌￠崟顐ょ閻㈩垰妫楄灃闁绘灏欓悞鐑芥煟?
 * 4. 闁诲孩顔栭崰鏍磹閹间焦鍋夐柤鎼佹涧缁剁偤鏌涢弴銊ュ箺闁稿﹦鍋涜灃闁绘灏欓悞鐑芥煟?
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
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
        // 婵犵數鍋為幐鎼佸箠閹版澘绠栧┑鐘叉搐閺嬩線鏌ｅΔ鈧悧鍡欑矈?
        warehouseRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // Ensure TENANT_ADMIN role exists for integration tests
        warehouseAdminRole = roleRepository.findByRoleCode("TENANT_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("TENANT_ADMIN")
                        .roleName("闂佺儵鍓濈敮鎺楀箠閹邦収娈介柛銉㈡櫇娑撳秹鏌ㄥ☉妯侯仾闁稿﹦鍋ら弻?")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .build()));

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锝夛綖椤掆偓婵′粙鏌?
        warehouseAdminUser = User.builder()
                .username("warehouse_admin")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("濠电偛顕慨鐢稿箰閸濄儴濮抽弶鍫氭櫇娑撳秹鏌ㄥ☉妯侯仾闁稿﹦鍋ら弻?")
                .enabled(true)
                .defaultRoleId(warehouseAdminRole.getId())
                .build();
        warehouseAdminUser = userRepository.save(warehouseAdminUser);

        // 闂備礁鎲＄敮鎺懳涘┑瀣偍闁靛牆娲﹂崰鍡涙煙閻戞ɑ绀€妞?
        userRoleRepository.save(SysUserRole.builder()
                .userId(warehouseAdminUser.getId())
                .roleId(warehouseAdminRole.getId())
                .assignedBy(warehouseAdminUser.getId())
                .build());

        // 闂備焦鐪归崹濠氬窗閹版澘鍨?JWT Token
        warehouseAdminToken = jwtUtil.generateTenantToken(
            warehouseAdminUser.getId(), warehouseAdminUser.getCompanyId(),
            warehouseAdminUser.getUsername(), "TENANT_ADMIN",
            warehouseAdminUser.getSecurityVersion());

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻鈩冩媴閸濆嫷鏆悗?
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
    @DisplayName("case-2")
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
    @DisplayName("case-3")
    void getAllActiveWarehouses_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/active")
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    @DisplayName("case-4")
    void getWarehouseById_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testWarehouse.getId()))
                .andExpect(jsonPath("$.code").value("WH01"));
    }

    @Test
    @DisplayName("case-5")
    void getWarehouseById_NotFound() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", 999L)
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("case-6")
    void getWarehouseByCode_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/code/{code}", "WH01")
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WH01"));
    }

    @Test
    @DisplayName("case-7")
    void createWarehouse_Success() throws Exception {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH02",
                "New Warehouse",
                "456 New St",
                "Jane Doe",
                "13900139000"
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
    @DisplayName("case-8")
    void createWarehouse_CodeAlreadyExists() throws Exception {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH01",
                "Duplicate Warehouse",
                null,
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
    @DisplayName("case-9")
    void createWarehouse_ValidationFailed() throws Exception {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "invalid",  // 闂佽绻愮换鎰崲閹版澘鐤柟鍓х帛閺咁剟鎮橀悙璺轰汗闁荤喐绻勭槐鎺懳旂€ｎ剛鏆犻梺璇″枛闁帮絽顕ｆ禒瀣亗閹艰揪绲块、鍛存煟閻斿憡纾婚柣鎺炵畵閹?
                "Invalid Warehouse",
                null,
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
    @DisplayName("case-10")
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
    @DisplayName("case-11")
    void activateWarehouse_Success() throws Exception {
        // Given - 闂備胶顭堢换鎰版偋閸℃顩烽柣妯肩帛閸嬨劑鏌曟繛鍨偓妤€鈻旈姀銈呯?        testWarehouse.setIsActive(false);
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
    @DisplayName("case-12")
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
    @DisplayName("case-13")
    void getLocationCount_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}/location-count", testWarehouse.getId())
                        .header("Authorization", "Bearer " + warehouseAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNumber());
    }
}
