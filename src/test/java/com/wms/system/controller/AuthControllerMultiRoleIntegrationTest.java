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
 * AuthController 闂傚倸妫楀Λ娆撳垂濮橆優鍦偓锝庡幘濡?- 婵犮垼鍩栨穱娲綖濡ゅ懏鍤岄柤纰卞墴閸忓洨绱?
 *
 * 濠电偞娼欓鍫ユ儊椤栨壕鍋撻悷鐗堟拱闁哄棴缍侀幆?HTTP 闁荤姴娲弨閬嶆儑?闂佸憡绻傜粔瀵歌姳閹绘崡瑙勬媴鐞涒剝鐓犻梺鎸庣☉閼活垳鈧灚锕㈤獮蹇涱敆婵犲嫮鐛?
 * 1. 婵犮垼鍩栨穱娲綖濡ゅ懏鍤岄柤纰卞墯閺嗗繘鏌熼幘顕呮婵炲懌鍎撮妵?
 * 2. 闂佸憡顨嗗ú婵嬶綖濡ゅ懏鍤岄柤纰卞墯閺嗗繘鏌熼幘顕呮婵炲懌鍎撮妵?
 * 3. 闁荤喐鐟︾敮鐔哥珶婵犲洤绀嗛柛銉ｅ妼鎼村﹪鏌熺€涙ê濮囧┑?婵犮垺鍎肩划鍓ф喆閿曞倸鎹堕柣鎴炆戦悵?
 * 4. 婵帗绋掗…鍫ヮ敇閼姐倖鍠嗛柟鐑樻礀椤ュ繘姊洪銏╂Ч閻庢哎鍔戦弻鍛村及韫囨洖绔?
 * 5. JWT Token 婵°倗濮撮惌渚€鎯?
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
        // 濠电偞鎸搁幊鎰板箖婵犲洤鏋侀柣妤€鐗嗙粊?
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        // 缂佺虎鍙庨崰鏇犳崲濮樿鲸鍠嗛柟鐑樻礀椤ュ繘鎮楀☉娅亜锕?
        superAdminRole = roleRepository.findByRoleCode("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("SUPER_ADMIN")
                        .roleName("闁烩剝甯掗幊鎰殽閸モ晝涓嶉柨娑樺閸婄偤鏌?")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .build()));

        warehouseAdminRole = roleRepository.findByRoleCode("WAREHOUSE_ADMIN")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("WAREHOUSE_ADMIN")
                        .roleName("婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌?")
                        .sortOrder(10)
                        .status("ACTIVE")
                        .build()));

        salespersonRole = roleRepository.findByRoleCode("SALESPERSON")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("SALESPERSON")
                        .roleName("闂備礁绨遍崑鎾绘煕閻戝棗鏋涢柟?")
                        .sortOrder(20)
                        .status("ACTIVE")
                        .build()));

        purchaserRole = roleRepository.findByRoleCode("PURCHASER")
                .orElseGet(() -> roleRepository.save(SysRole.builder()
                        .roleCode("PURCHASER")
                        .roleName("闂備焦褰冨ú鈺呭窗濮椻偓瀹?")
                        .sortOrder(30)
                        .status("ACTIVE")
                        .build()));

        // 闂佸憡甯楃粙鎴犵磽閹炬剚鍟呴柤纰卞墻濞诧綁鏌ｉ～顒€濡挎繛鍫熷灩閹叉挳骞掗弴鐑嗘
        disabledRole = roleRepository.save(SysRole.builder()
                .roleCode("DISABLED_TEST_ROLE")
                .roleName("閻庤鐡曠亸娆撱€呴敃鍌涘仺闁靛濡囬妶鎾偣閸ャ劍绀夋い顐ｎ殜閹?")
                .sortOrder(99)
                .status("DISABLED")
                .build());

        // 闂佸憡甯楃粙鎴犵磽閹炬潙绶炴慨妯诲墯濞硷繝鏌ょ涵鍛毢闁轰降鍊濋獮?
        multiRoleUser = User.builder()
                .username("multi_role_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("婵犮垼鍩栨穱娲綖濡ゅ懏鍤岄柛娆忣槺閵堟挳鎮归崶銊︾闁轰降鍊濋獮?")
                .enabled(true)
                .defaultRoleId(warehouseAdminRole.getId())
                .build();
        multiRoleUser = userRepository.save(multiRoleUser);

        // 闂佸憡甯掑Λ婵嬪储閵堝棗绶炴慨姗嗗亰閸ゅ鎮峰▎鎰濠?
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

        // 闂佸憡甯楃粙鎴犵磽閹捐纭€闁哄洠妲呭锟犳煠绾懎鐨洪柡浣靛€濋獮?
        singleRoleUser = User.builder()
                .username("single_role_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("闂佸憡顨嗗ú婵嬶綖濡ゅ懏鍤岄柛娆忣槺閵堟挳鎮归崶銊︾闁轰降鍊濋獮?")
                .enabled(true)
                .defaultRoleId(superAdminRole.getId())
                .build();
        singleRoleUser = userRepository.save(singleRoleUser);

        userRoleRepository.save(SysUserRole.builder()
                .userId(singleRoleUser.getId())
                .roleId(superAdminRole.getId())
                .assignedBy(1L)
                .build());

        // 闂佸憡甯楃粙鎴犵磽閹惧墎鐭夊ù锝囧劋閺嗗繘鎮归幇鍫曟闁?
        disabledUser = User.builder()
                .username("disabled_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("缂備礁鍊烽懗鍫曞极閵堝鍋ㄩ柕濠忕畱閻?")
                .enabled(false)
                .build();
        disabledUser = userRepository.save(disabledUser);

        userRoleRepository.save(SysUserRole.builder()
                .userId(disabledUser.getId())
                .roleId(warehouseAdminRole.getId())
                .assignedBy(1L)
                .build());

        // 闂佸憡甯楃粙鎴犵磽閹捐绫嶉柣妯硅濞硷繝鏌ょ涵鍛毢闁轰降鍊濋獮?
        noRolesUser = User.builder()
                .username("no_roles_user")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .displayName("闂佸搫鍟版慨楣冿綖濡ゅ懏鍤岄柤纰卞墯閺嗗繘鏌?")
                .enabled(true)
                .build();
        noRolesUser = userRepository.save(noRolesUser);
        // 闂佽桨绀侀幊蹇涘礈閻楀牏鈻旂€广儱鎳庨悗濠氭⒑閺夎法小闁瑰箍鍨洪幏鍛村即閳藉棙鍋ラ梺?
    }

    // ========== 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墻濡鎮峰▎鎰濠㈢懓锕幆鍌滄嫚閼碱剛协 ==========

    @Test
    @DisplayName("case-2")
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
                .andExpect(jsonPath("$.currentRole", is("WAREHOUSE_ADMIN"))) // 婵帗绋掗…鍫ヮ敇閼姐倖鍠嗛柟鐑樻礀椤?
                .andExpect(jsonPath("$.availableRoles", hasSize(3)))
                .andExpect(jsonPath("$.availableRoles", containsInAnyOrder(
                        "WAREHOUSE_ADMIN", "SALESPERSON", "PURCHASER"
                )))
                .andExpect(jsonPath("$.expiresIn", notNullValue()))
                .andReturn();

        // 婵°倗濮撮惌渚€鎯佹径瀣氦闁哄倹瀵х粈鈧梺?Token 婵炴垶鎸哥粔宕囨嫻閻旇櫣鐭?
        String responseBody = result.getResponse().getContentAsString();
        LoginResponse response = objectMapper.readValue(responseBody, LoginResponse.class);
        assertThat(response.getToken()).isNotEmpty();
        assertThat(response.getAvailableRoles()).hasSize(3);
    }

    @Test
    @DisplayName("case-3")
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
    @DisplayName("case-4")
    void login_UseDefaultRole_Success() throws Exception {
        // multiRoleUser 闂佹眹鍔岀€氭壆鍒掗婊勫闁靛牆鐗滃锟犳煠閻熸澘绾ф俊?WAREHOUSE_ADMIN
        LoginRequest request = new LoginRequest("multi_role_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRole", is("WAREHOUSE_ADMIN")));
    }

    @Test
    @DisplayName("case-5")
    void login_SelectMinSortOrder_Success() throws Exception {
        // 濠电偞鎸搁幊妯衡枍鎼淬埄娓舵俊顖涱儥閸氬洭鎮峰▎鎰濠?
        multiRoleUser.setDefaultRoleId(null);
        userRepository.save(multiRoleUser);

        LoginRequest request = new LoginRequest("multi_role_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRole", is("WAREHOUSE_ADMIN"))); // sortOrder=10 闂佸搫鐗冮崑鎾绘倶?
    }

    @Test
    @DisplayName("case-6")
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
    @DisplayName("case-7")
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
    @DisplayName("case-8")
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
    @DisplayName("case-9")
    void login_AccountDisabled_Forbidden() throws Exception {
        LoginRequest request = new LoginRequest("disabled_user", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorKey", is("USER_ACCOUNT_DISABLED")));
    }

    // ========== 濠电偞娼欓鍫ユ儊椤栫偞鏅慨妯诲墯濞硷繝鏌ょ憴鍕祷闁搞劌绻橀獮?==========

    @Test
    @DisplayName("case-10")
    void switchRole_Success() throws Exception {
        // 闂佺绻愰悧蹇撯枍閵夈劊浜归柡鍥ｂ偓宕囶唵闂?Token
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 闂佸憡甯掑ú锕€鐣烽弻銉ョ?SALESPERSON 闁荤喐鐟︾敮鐔哥珶?
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
                .andExpect(jsonPath("$.message", containsString("SALESPERSON")))
                .andReturn();

        // 婵°倗濮撮惌渚€鎯佹径鎰?Token 婵炴垶鎸哥粔鎾箖閹惧顩查幖娣灪閿?Token
        String switchResponseBody = switchResult.getResponse().getContentAsString();
        SwitchRoleResponse switchResponse = objectMapper.readValue(switchResponseBody, SwitchRoleResponse.class);
        assertThat(switchResponse.getToken()).isNotEqualTo(token);
    }

    @Test
    @DisplayName("case-11")
    void switchRole_AllAvailableRoles_Success() throws Exception {
        // 闂佽皫鍡╁殭缂?
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 婵炴挻纰嶇换鍐敃婵傜绀嗛柛銉ｅ妼鎼村﹪鏌涢幒鏂款暭濠⒀冪Ч瀵灚寰勬惔顔藉仴闂?
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

            // 闂佸搫娲ら悺銊╁蓟?token 婵炴垶鎸鹃崕銈夊蓟婵犲洦鍎?token
            String responseBody = result.getResponse().getContentAsString();
            SwitchRoleResponse response = objectMapper.readValue(responseBody, SwitchRoleResponse.class);
            token = response.getToken();
        }
    }

    @Test
    @DisplayName("case-12")
    void switchRole_RoleNotFound_NotFound() throws Exception {
        // 闂佽皫鍡╁殭缂?
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 闂佸憡甯掑ú锕€鐣烽弻銉ョ濡鑳堕悷婵嬫倵濞戞顏勶耿椤忓牊鍎嶉柛鏇ㄥ櫘濞硷繝鏌?
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
    @DisplayName("case-13")
    void switchRole_RoleNotAssigned_Forbidden() throws Exception {
        // 闂佽皫鍡╁殭缂?
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 闂佸憡甯掑ú锕€鐣烽弻銉ョ闁绘鐗婂鎾绘煕閹烘垶顥㈤柛妯稿€濋幆?SUPER_ADMIN 闁荤喐鐟︾敮鐔哥珶?
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
    @DisplayName("case-14")
    void switchRole_RoleDisabled_Forbidden() throws Exception {
        // 缂傚倷鐒﹂悷褔寮妶澶婄鐎瑰嫭婢橀悗濠氭⒑閺夎法孝闁告埊绱曠划瀣媴閻戞ɑ娈㈤梺姹囧妼鐎氼垶锝炲Δ鍛殞?
        userRoleRepository.save(SysUserRole.builder()
                .userId(multiRoleUser.getId())
                .roleId(disabledRole.getId())
                .assignedBy(1L)
                .build());

        // 闂佽皫鍡╁殭缂?
        LoginRequest loginRequest = new LoginRequest("multi_role_user", TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginResponseBody, LoginResponse.class);
        String token = loginResponse.getToken();

        // 闁诲繐绻戠换鍡涙儊椤栫偛绀嗛柛銉ｅ妼鎼村﹪鏌涢幒鎾寸凡闁告埊绱曠划瀣媴閻戞ɑ娈㈤梺姹囧妼鐎氼垶锝炲Δ鍛殞?
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
    @DisplayName("case-15")
    void switchRole_NoToken_Forbidden() throws Exception {
        SwitchRoleRequest switchRequest = new SwitchRoleRequest("SALESPERSON");

        mockMvc.perform(post("/api/auth/switch-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchRequest)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("case-16")
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
