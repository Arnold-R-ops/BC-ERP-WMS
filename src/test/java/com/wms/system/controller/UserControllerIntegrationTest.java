package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
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
import org.springframework.context.annotation.Import;
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
 * UserController 闂傚倸鍊稿Λ妤€螞濞嗘挸鍨傛慨姗嗗劒閸︻厸鍋撻敐搴″箻婵?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄥ閸嬫捇鎮烽悧鍫熸嫳闂佸搫妫寸紞渚€骞?HTTP 闂佽崵濮村ú顓㈠绩闁秵鍎?闂備礁鎲＄换鍌滅矓鐎垫瓕濮抽柟缁樺础鐟欏嫭濯撮悶娑掑墲閻撶娀姊洪幐搴ｂ槈闁兼椿鍨抽埀顒€鐏氶敃銏ょ嵁?Spring Security 闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簻閸愨偓闂佽法鍠撴慨宄扮暦閿濆鐓?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仜閹瑰爼鏌ｉ幋鐐嗘垿鎮甸鐐寸叆?
 * 1. 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墮缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閻忕偟鍋撳▓銏ゆ⒑鐟欏嫭缍戦柛銈嗙墱濡叉劕鈻庨幘鎼⒖闂侀€炲苯澧伴柟?SUPER_ADMIN 闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簼閺?
 * 2. 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鍓х帛閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻銊モ槈濡厧顤€闂佹悶鍔嶅畝绋跨暦?闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴欏灩鐟欙妇鈧箍鍎遍ˇ顖氣枍閵忕媭鐔?闂備礁鎼崯鐗堟叏闂堟侗鐒介柛顐犲劜閳锋棃鏌熼弶鍨暢缂佲偓?
 * 3. 闂備礁鎼ú銈夋偤閵娾晛钃熷┑鐘叉处閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻銊モ槈濡厧顤€闂佹悶鍔嶅畝绋跨暦?闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴炵懅閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓?闂備礁鎼崯鐗堟叏闂堟侗鐒介柛顐犲劜閳锋棃鏌熼弶鍨暢缂佲偓?
 * 4. 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓㈠弰闁诡喕绮欐俊鎼佹晝閳ь剟鎮￠弴銏＄叆婵炴垶顭囨晶娑㈡煕閵婏箑鍝虹€?闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴炵懅閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓?闂備礁鎼崯鐗堟叏闂堟侗鐒介柛顐犲劜閳锋棃鏌熼弶鍨暢缂佲偓?
 * 5. 闂備礁缍婂褏绱炴繝鍥ч棷婵炲樊浜滅粈鍡涙煕閳╁啰鈽夐悽顖涘▕閹嘲鈻庨幇顒傤儎婵犮垻鎳撻敃顏堝极瀹ュ閱囬柣鏃堫棑娴滐綁姊?闂佽崵鍠愰悷锔炬暜閻斿摜鐝跺┑鐘插暟閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓?闂備礁鎼崯鐗堟叏闂堟侗鐒介柛顐犲劜閳锋棃鏌熼弶鍨暢缂佲偓?
 * 6. 缂傚倷绀侀ˇ顖炩€﹀畡鎵虫瀺閹艰揪绲鹃崰鍡涙煙閻戞ɑ绀€妞ゃ儱绻橀弻銊モ槈濡厧顤€闂佹悶鍔嶅畝绋跨暦?闂備礁鎼悧鍐磻閹剧粯鐓曟慨姗嗗墰閸戝湱绱掗弬璺ㄦ憼缂佸顦甸、鏃堝炊閼搁潧浠撮梻?闂備礁鎼崯鐗堟叏闂堟侗鐒介柛顐犲劜閳锋棃鏌熼弶鍨暢缂佲偓?
 * 7. 闂備礁鎼ˇ顖炲疮閺夋埈鐎堕柛婵嗗▕閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻銊モ槈濡厧鈪靛┑?SUPER_ADMIN 闂佽崵濮崇粈浣割焽閳ユ緞娑㈠醇閺囩喎浠洪梺闈涱煭缁犳垿鎮￠弴鐘电＜闁绘瑥鎳愮壕鍧楁煙椤旂》韬鐐村浮婵＄兘濡烽妷褏鈻旈梻?
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
@DisplayName("case-1")
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
        // 婵犵數鍋為幐鎼佸箠閹版澘绠栧┑鐘叉搐閺嬩線鏌ｅΔ鈧悧鍡欑矈?
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // 缂備胶铏庨崣搴ㄥ窗閺囩姵宕叉慨妯块哺閸犲棝鏌熼悜妯荤妞ゃ儱绻橀幃妤€鈽夊▍顓т簻閿曘垽顢旈崼鐔告珫闂佸壊鍋呯换鍕偓鐟邦樀閹綊宕堕妷銉ュБ闂?Flyway 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鍓х帛閺?
        superAdminRole = roleRepository.findByRoleCode("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("SUPER_ADMIN")
                        .roleName("Super Admin")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .build()));

        warehouseAdminRole = roleRepository.findByRoleCode("WAREHOUSE_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("WAREHOUSE_ADMIN")
                        .roleName("Warehouse Admin")
                        .sortOrder(10)
                        .status("ACTIVE")
                        .build()));

        salespersonRole = roleRepository.findByRoleCode("SALESPERSON")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("SALESPERSON")
                        .roleName("Salesperson")
                        .sortOrder(20)
                        .status("ACTIVE")
                        .build()));

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愬煐閹儵鏌涘☉鍗炵仸闁绘稒鎸剧槐鎺楁偑閸涱垳锛熼梺璇″枛閿曨亜鐣烽妸銉庣喖宕楅崗鍏碱吂闂?
        superAdminUser = User.builder()
                .username("super_admin")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("System Super Admin")
                .enabled(true)
                .defaultRoleId(superAdminRole.getId())
                .build();
        superAdminUser = userRepository.save(superAdminUser);

        // 闂備礁鎲＄敮鎺懳涘┑瀣偍?SUPER_ADMIN 闂佽崵鍠愰悷锔炬暜閻斿摜鐝?
        userRoleRepository.save(SysUserRole.builder()
                .userId(superAdminUser.getId())
                .roleId(superAdminRole.getId())
                .assignedBy(superAdminUser.getId())
                .build());

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟椤╃兘鏌熼幆褏锛嶆慨妯稿妿缁辨帡鎮崨顖滐紵闂佽鍠栭敃顏勭暦閵娿儙鐔煎礂閸忓吋顓归梻?
        warehouseUser = User.builder()
                .username("warehouse_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("System Warehouse Admin")
                .enabled(true)
                .defaultRoleId(warehouseAdminRole.getId())
                .build();
        warehouseUser = userRepository.save(warehouseUser);

        // 闂備礁鎲＄敮鎺懳涘┑瀣偍?WAREHOUSE_ADMIN 闂佽崵鍠愰悷锔炬暜閻斿摜鐝?
        userRoleRepository.save(SysUserRole.builder()
                .userId(warehouseUser.getId())
                .roleId(warehouseAdminRole.getId())
                .assignedBy(superAdminUser.getId())
                .build());

        // 闂備焦鐪归崹濠氬窗閹版澘鍨?JWT Token
        superAdminToken = jwtUtil.generateToken(superAdminUser.getUsername(), "SUPER_ADMIN");
        warehouseToken = jwtUtil.generateToken(warehouseUser.getUsername(), "WAREHOUSE_ADMIN");
    }

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥΟ鍝勬毐缂佺媭鍨堕弻娑樷枎閹邦喖顫ф繝鈷€鍐х€殿喖鐏氬鍕沪閻愵剚顓归梻?==========

    @Test
    @DisplayName("case-2")
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
    @DisplayName("case-3")
    void getAllUsers_AsWarehouseAdmin_Forbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + warehouseToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("case-4")
    void getAllUsers_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andDo(print())
                .andExpect(status().isForbidden()); // Spring Security 闂佸搫顦弲婊堝蓟閵娿儍?403
    }

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀闁诲骏绱曢埀顒傚仯閸婃稑鈻嶉敐澶婃瀬闁靛牆顦粻?==========

    @Test
    @DisplayName("case-5")
    void createUser_AsSuperAdmin_Success() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_employee")
                .password("NewPass@123")
                .displayName("New Employee")
                .roleIds(Arrays.asList(warehouseAdminRole.getId(), salespersonRole.getId()))
                .enabled(true)
                .remark("Created by integration test")
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("new_employee")))
                .andExpect(jsonPath("$.displayName", is("New Employee")))
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.roleCodes", hasSize(2)))
                .andExpect(jsonPath("$.roleCodes", containsInAnyOrder("WAREHOUSE_ADMIN", "SALESPERSON")))
                .andExpect(jsonPath("$.defaultRoleCode", is("WAREHOUSE_ADMIN")));
    }

    @Test
    @DisplayName("case-6")
    void createUser_UsernameExists_Conflict() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("super_admin") // 闁诲海鎳撻幉陇銇愰崘顔藉仱闁靛ň鏅涢幑?
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
    @DisplayName("case-7")
    void createUser_RoleNotFound_NotFound() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_user")
                .password("NewPass@123")
                .roleIds(Arrays.asList(999L)) // 濠电偞鍨堕幐鍝ョ矓閹绢喗鍋ら柕濞炬櫅閹瑰爼鏌曟繛褍瀚弳鐘绘煟閻斿摜鎳勯悽顖滃枑缁?
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
    @DisplayName("case-8")
    void createUser_ValidationFailed_BlankUsername() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("") // 缂傚倷绀侀惌浣糕枍閿濆鏋侀柕鍫濐槸缁狅絿鈧懓瀚晶妤呭磹?
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

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀箺缂佺虎鍨堕弻锟犲磼濞戞﹩妫嗛梺杞伴檷閸婃繈鐛?==========

    @Test
    @DisplayName("case-9")
    void updateUser_AsSuperAdmin_Success() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("Updated Warehouse User")
                .enabled(false)
                .remark("Updated by integration test")
                .build();

        mockMvc.perform(put("/api/users/" + warehouseUser.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Updated Warehouse User")))
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.remark", is("Updated by integration test")));
    }

    @Test
    @DisplayName("case-10")
    void updateUser_UserNotFound_NotFound() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("Not Found User")
                .build();

        mockMvc.perform(put("/api/users/999")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey", is("USER_NOT_FOUND")));
    }

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀闁诲繆鏅犲濠氬礋閳轰讲鍋撳Δ鍛瀬闁靛牆顦粻?==========

    @Test
    @DisplayName("case-11")
    void deleteUser_AsSuperAdmin_Success() throws Exception {
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顓炰缓闂侀潧顭粻鎴︽偂閺囩姭鍋撻悷鐗堝暈鐟滄澘鍟村畷纭呫亹閹烘挴鎸?
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(jsonPath("$", hasSize(1))); // 闂備礁鎲￠悷顖涚濠婂嫭娅?super_admin
    }

    @Test
    @DisplayName("case-12")
    void deleteUser_UserNotFound_NotFound() throws Exception {
        mockMvc.perform(delete("/api/users/999")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey", is("USER_NOT_FOUND")));
    }

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀箹妞ゃ儲顨婂娲箵閹烘梻顔囬梺鎼炲妼闁帮綁寮绘繝鍌ゅ悑闁割偒鍋呴崑銉╂⒑?==========

    @Test
    @DisplayName("case-13")
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
    @DisplayName("case-14")
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

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥΟ鑽ゆ▊闁冲搫鎳忛埛鏃堟煏閸繄澧辨繛纭风節閺?==========

    @Test
    @DisplayName("case-15")
    void removeRole_AsSuperAdmin_Success() throws Exception {
        // 闂備胶顭堢换鎰版偋韫囨洖鍨濆ù鐓庣摠閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻娑㈠箳閹寸儐妫﹂梺鍛娗滈崐鏍矙婢舵劕围闁告洦鍨板▓婵嬫煟閻斿摜鎳勯悽顖滃枑缁?
        userRoleRepository.save(SysUserRole.builder()
                .userId(warehouseUser.getId())
                .roleId(salespersonRole.getId())
                .assignedBy(superAdminUser.getId())
                .build());

        // 缂傚倷绀侀ˇ顖炩€﹀畡鎵虫瀺?SALESPERSON 闂佽崵鍠愰悷锔炬暜閻斿摜鐝?
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId() + "/roles/" + salespersonRole.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勭仦鎯ф瀭闂佸湱鍎ゅú鏍夎箛鏇楀亾鐟欏嫮鎽冮悘蹇旂懁缁ㄦ椽姊?
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(jsonPath("$[?(@.username == 'warehouse_user')].roleCodes", contains(contains("WAREHOUSE_ADMIN"))));
    }

    @Test
    @DisplayName("case-16")
    void removeRole_CannotRemoveLastRole_Forbidden() throws Exception {
        // warehouseUser 闂備礁鎲￠悷顖涚濠靛棴鑰垮ù锝呮贡閳绘棃鏌嶈閸撴氨绮欐径鎰垫晜闁搞儴鍩栭崑銉╂⒑?WAREHOUSE_ADMIN
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId() + "/roles/" + warehouseAdminRole.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("OPERATION_NOT_ALLOWED")));
    }

    @Test
    @DisplayName("case-17")
    void removeRole_RoleNotAssigned_Forbidden() throws Exception {
        // warehouseUser 婵犵數鍋涙径鍥礈濠靛棴鑰?SALESPERSON 闂佽崵鍠愰悷锔炬暜閻斿摜鐝?
        mockMvc.perform(delete("/api/users/" + warehouseUser.getId() + "/roles/" + salespersonRole.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("ROLE_NOT_ASSIGNED")));
    }

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀箺缂傚秮鍋撻梻鍌氬€哥€氼參宕濋弽顐ｆ殰闁割偅娲栫粈?==========

    @Test
    @DisplayName("case-18")
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
    @DisplayName("case-19")
    void permissionTest_WarehouseAdminDeleteUser_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/users/" + superAdminUser.getId())
                        .header("Authorization", "Bearer " + warehouseToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }
}
