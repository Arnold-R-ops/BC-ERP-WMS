package com.wms.system.controller;

import com.wms.system.dto.*;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.JwtUtil;
import com.wms.system.service.DynamicPermissionService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthController 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡叉悂鏌ㄥ☉妯煎妞わ箒娉曢幉鎾箳閺囩儐妫岀紓渚囧灥瀹曠數鍒掑ú顏呮櫖?
 *
 * 婵炶揪缍€濞夋洟寮?Mockito 濠碘槅鍨崜婵堚偓姘懇楠炲秹鍩€椤掑嫬瀚夊璺侯槺鐠愨晠鎮硅閻楊厾妲愬┑鍥┾枖闁规儳鐡ㄩ弳鍫澝瑰鍐劉缂佷礁顕幏鐘诲即閻旇渹绮梺鍛婂笩濞夋稑鈻嶉幒妤佺劵闁哄嫬绻掔敮鍡涙煏?
 *
 * 濠电偞娼欓鍫ユ儊椤栨粍鍟洪柛鈩冪懄绾句即鏌?
 * 1. 婵犮垼鍩栨穱娲綖濡ゅ懏鍤岄柤纰卞墯椤忋垻鎲搁悧鍫熺┛缂佽鲸鐟╅獮瀣箛椤掆偓椤?闂佸搫鍟版慨楣冿綖濡ゅ懏鍤?闂佸搫鍟版慨闈涱渻鐠恒劍宕夐柛鎰靛弾濞硷繝鏌?闂佸憡鍩堥崣鈧柣锕€顦甸弻銊モ枎閹烘繂娈╅梺?
 * 2. 闁荤喐鐟︾敮鐔哥珶婵犲洤绀嗛柛銉ｅ妼鎼村﹪鏌ㄥ☉妯煎闁搞劍宀稿畷?闁荤喐鐟︾敮鐔哥珶婵犲啰鈻旂€广儱鎳愰幗鐘绘煕?闁荤喐鐟︾敮鐔哥珶婵犲洤瀚夋い蹇撳閻庡姊?闁荤喐鐟︾敮鐔哥珶婵犲偆鍟呴柤纰卞墻濞诧綁鏌ｉ姀鈺冨帨缂?
 * 3. 婵帗绋掗…鍫ヮ敇閼姐倖鍠嗛柟鐑樻礀椤ュ繘姊洪銏╂Ч閻庢哎鍔戦弻鍛村及韫囨洖绔?
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role RBAC System)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
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

    @Mock
    private DynamicPermissionService dynamicPermissionService;

    @InjectMocks
    private AuthController authController;

    private User testUser;
    private SysRole warehouseAdminRole;
    private SysRole salespersonRole;
    private SysRole purchaserRole;
    private SysRole disabledRole;

    @BeforeEach
    void setUp() {
        // 闁荤姳绀佹晶浠嬫偪?JWT 闁哄鏅涘ú锕€锕㈤敓鐘茬睄闁割偅娲橀敍?
        ReflectionTestUtils.setField(authController, "jwtExpiration", 86400000L);

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡介柛?
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("encoded_password")
                .displayName("濠电偞娼欓鍫ユ儊椤栫偞鍋ㄩ柕濠忕畱閻?")
                .enabled(true)
                .defaultRoleId(3L) // WAREHOUSE_ADMIN
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鎮峰▎鎰濠?
        warehouseAdminRole = SysRole.builder()
                .id(3L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌?")
                .sortOrder(10)
                .status("ACTIVE")
                .build();

        salespersonRole = SysRole.builder()
                .id(5L)
                .roleCode("SALESPERSON")
                .roleName("闂備礁绨遍崑鎾绘煕閻戝棗鏋涢柟?")
                .sortOrder(20)
                .status("ACTIVE")
                .build();

        purchaserRole = SysRole.builder()
                .id(7L)
                .roleCode("PURCHASER")
                .roleName("闂備焦褰冨ú鈺呭窗濮椻偓瀹?")
                .sortOrder(30)
                .status("ACTIVE")
                .build();

        disabledRole = SysRole.builder()
                .id(9L)
                .roleCode("DISABLED_ROLE")
                .roleName("閻庤鐡曠亸娆撱€呴敃鍌涘仺闁靛鐓堝锟犳煠?")
                .sortOrder(40)
                .status("DISABLED") // 閻庤鐡曠亸娆撱€呴敃鍌涘仺?
                .build();

        lenient().when(dynamicPermissionService.getUserPermissionsForRole(
                anyLong(), anyString(), anyLong()))
            .thenReturn(UserPermissionDTO.builder()
                .permissionCodes(Set.of("inventory:view", "sales:view"))
                .build());
    }

    // ========== 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墻濡鎮峰▎鎰濠㈢懓锕幆鍌滄嫚閼碱剛协 ==========

    @Test
    @DisplayName("case-2")
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
                anyList(),
                eq(1L)
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
        assertThat(body.getPermissionCodes()).containsExactly("inventory:view", "sales:view");
        assertThat(body.getExpiresIn()).isEqualTo(86400000L);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRoleService).getUserRoles(1L);
        verify(jwtUtil).generateTokenWithRoles(
                eq("test_user"), eq("WAREHOUSE_ADMIN"), anyList(), eq(1L));
    }

    @Test
    @DisplayName("case-3")
    void login_Success_WithDefaultRole() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        testUser.setDefaultRoleId(5L); // 闁荤姳绀佹晶浠嬫偪閸℃﹩娓舵俊顖涱儥閸氬洭鎮峰▎鎰濠㈢懓锕ョ粙?SALESPERSON

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole));
        when(jwtUtil.generateTokenWithRoles(anyString(), anyString(), anyList(), anyLong()))
                .thenReturn("mock_jwt_token");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Then
        assertThat(response.getBody().getCurrentRole()).isEqualTo("SALESPERSON");

        verify(jwtUtil).generateTokenWithRoles(
                eq("test_user"), eq("SALESPERSON"), anyList(), eq(1L));
    }

    @Test
    @DisplayName("case-4")
    void login_Success_SelectMinSortOrderRole() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        testUser.setDefaultRoleId(null); // 闂佸搫鍟版繛鈧紒顕呭灣閹峰濡堕崼顐ｅ仴闂?

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(purchaserRole, salespersonRole, warehouseAdminRole)); // 婵炴垶鏌ㄥ畷顒傝姳?
        when(jwtUtil.generateTokenWithRoles(anyString(), anyString(), anyList(), anyLong()))
                .thenReturn("mock_jwt_token");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Then
        // 闁圭厧鐡ㄥΛ渚€顢氬鑸电劵濠㈣泛顑呴?sortOrder 闂佸搫鐗冮崑鎾绘倶韫囨挾绠虫繛?warehouseAdminRole (sortOrder=10)
        assertThat(response.getBody().getCurrentRole()).isEqualTo("WAREHOUSE_ADMIN");

        verify(jwtUtil).generateTokenWithRoles(
                eq("test_user"), eq("WAREHOUSE_ADMIN"), anyList(), eq(1L));
    }

    @Test
    @DisplayName("case-5")
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
        verify(jwtUtil, never()).generateTokenWithRoles(
                anyString(), anyString(), anyList(), anyLong());
    }

    @Test
    @DisplayName("case-6")
    void login_Fail_NoActiveRoles() {
        // Given
        LoginRequest request = new LoginRequest("test_user", "password123");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(disabledRole)); // 闂佸憡鐟禍婵嗭耿娴ｅ壊鍟呴柤纰卞墻濞诧綁鏌ｉ～顒€濡挎繛鍫熷灩閹叉挳骞掗弴鐑嗘

        // When & Then
        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NO_ACTIVE_ROLES);

        verify(jwtUtil, never()).generateTokenWithRoles(
                anyString(), anyString(), anyList(), anyLong());
    }

    @Test
    @DisplayName("case-7")
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
    @DisplayName("case-8")
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

    // ========== 濠电偞娼欓鍫ユ儊椤栫偞鏅慨妯诲墯濞硷繝鏌ょ憴鍕祷闁搞劌绻橀獮?==========

    @Test
    @DisplayName("case-9")
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
                anyList(),
                anyLong()
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
        assertThat(body.getPermissionCodes()).containsExactly("inventory:view", "sales:view");
        assertThat(body.getMessage()).contains("SALESPERSON");
        assertThat(body.getMessage()).contains("闂備礁绨遍崑鎾绘煕閻戝棗鏋涢柟?");
        assertThat(testUser.getDefaultRoleId()).isEqualTo(5L);

        verify(roleRepository).findByRoleCode("SALESPERSON");
        verify(userRoleService).userHasRole(1L, 5L);
        verify(jwtUtil).generateTokenWithRoles(
                eq("test_user"), eq("SALESPERSON"), anyList(), eq(2L));
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("case-10")
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
        verify(jwtUtil, never()).generateTokenWithRoles(
                anyString(), anyString(), anyList(), anyLong());
    }

    @Test
    @DisplayName("case-11")
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
        verify(jwtUtil, never()).generateTokenWithRoles(
                anyString(), anyString(), anyList(), anyLong());
    }

    @Test
    @DisplayName("case-12")
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
        verify(jwtUtil, never()).generateTokenWithRoles(
                anyString(), anyString(), anyList(), anyLong());
    }

    @Test
    @DisplayName("case-13")
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
