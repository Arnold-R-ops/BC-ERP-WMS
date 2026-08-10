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
import com.wms.system.service.SecurityVersionService;
import com.wms.system.service.UserManagementService;
import com.wms.system.service.UserRoleService;
import com.wms.system.service.UserWarehouseService;
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
 * UserController 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * 濠电偠鎻紞鈧繛澶嬫礋瀵?Mockito 婵犵妲呴崹顏堝礈濠靛牃鍋撳顓犳噰妤犵偛绉归崺鈧い鎺戝鐎氬顭跨捄渚Ш閻犳劏鏅犻幃纭咁槼闁绘鍘惧Σ鎰攽閸モ斁鏋栭梺瑙勫劤閻°劑寮抽崼婢濈懓顭ㄩ崘顭戝妷缂備椒绀侀顓㈠箯閻樿鍗抽柣鏃囨腹缁垶姊洪崨濠傜婵炲绋戦埢宥夊箳濡や胶鍔甸梺鍝勫缁绘帞鏁崱娑欑厪?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ矋閸熸椽鏌涢埄鍐噭缁惧彞鍗抽弻?
 * 1. 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墮缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閻忕偟鍋撳▓銏ゆ⒑?
 * 2. 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鍓х帛閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻銊モ槈濡厧顤€闂佹悶鍔嶅畝绋跨暦?闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴欏灩鐟欙妇鈧箍鍎遍ˇ顖氣枍閵忕媭鐔?闂佽崵鍠愰悷锔炬暜閻斿摜鐝跺┑鐘插暟閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏃囧吹閸樻粎绱?
 * 3. 闂備礁鎼ú銈夋偤閵娾晛钃熷┑鐘叉处閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻銊モ槈濡厧顤€闂佹悶鍔嶅畝绋跨暦?闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴炵懅閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓?濠殿喗甯楃粙鎺椻€﹂崼銉晣闁煎鍊栭崰鍡涙煙閻戞ɑ绀€妞ゃ儱绻橀弻锟犲礃椤忓嫅锝夋煛閸涱偄鍔﹂柡?
 * 4. 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓㈠弰闁诡喕绮欐俊鎼佹晝閳ь剟鎮￠弴銏＄叆婵炴垶顭囨晶娑㈡煕閵婏箑鍝虹€?闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴炵懅閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏃囧吹閸樻粎绱?
 * 5. 闂備礁缍婂褏绱炴繝鍥ч棷婵炲樊浜滅粈鍡涙煕閳╁啰鈽夐悽顖涘▕閹嘲鈻庨幇顒傤儎婵犮垻鎳撻敃顏堝极瀹ュ閱囬柣鏃堫棑娴滐綁姊?闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴炵懅閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓?闂佽崵鍠愰悷锔炬暜閻斿摜鐝跺┑鐘插暟閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏃囧吹閸樻粎绱?
 * 6. 缂傚倷绀侀ˇ顖炩€﹀畡鎵虫瀺閹兼番鍔岀涵鈧梺鍝勬处瀹€鎼佸吹鐎ｎ喗鍋℃繛鍡樺姇缁椻晛顭块悷甯含闁轰礁绉瑰畷濂告偄妞嬪簼娴烽梻?闂備礁鎼悧鍐磻閹剧粯鐓曟慨姗嗗墰閸戝湱绱掗弬璺ㄦ憼缂佸顦甸、鏃堝炊閼搁潧浠撮梻?闂佽崵鍠愰悷锔炬暜閻斿摜鐝跺┑鐘叉搐鐎氬銇勮箛鎾愁仼闁诲骸顭峰娲级鐠恒劉鏋岀紓?
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role RBAC System)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
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

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private SecurityVersionService securityVersionService;

    @Mock
    private UserWarehouseService userWarehouseService;

    @InjectMocks
    private UserController userController;

    private User testUser;
    private SysRole warehouseAdminRole;
    private SysRole salespersonRole;
    private SysRole purchaserRole;

    @BeforeEach
    void setUp() {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锝夛綖椤掆偓婵′粙鏌?
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("encoded_password")
                .displayName("Test User")
                .enabled(true)
                .defaultRoleId(3L)
                .remark("Test user remark")
                .build();
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞幃宄扳枎閹邦剛顑勬繝?
        warehouseAdminRole = SysRole.builder()
                .id(3L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("Warehouse Admin")
                .sortOrder(10)
                .status("ACTIVE")
                .build();

        salespersonRole = SysRole.builder()
                .id(5L)
                .roleCode("SALESPERSON")
                .roleName("Salesperson")
                .sortOrder(20)
                .status("ACTIVE")
                .build();

        purchaserRole = SysRole.builder()
                .id(7L)
                .roleCode("PURCHASER")
                .roleName("Purchaser")
                .sortOrder(30)
                .status("ACTIVE")
                .build();
    }

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥΟ鍝勬毐缂佺媭鍨堕弻娑樷枎閹邦喖顫ф繝鈷€鍐х€殿喖鐏氬鍕沪閻愵剚顓归梻?==========

    @Test
    @DisplayName("case-2")
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
        assertThat(dto.getRoleNames()).containsExactly("Warehouse Admin", "Salesperson");
        assertThat(dto.getDefaultRoleCode()).isEqualTo("WAREHOUSE_ADMIN");

        verify(userRepository).findAll();
        verify(userRoleService).getUserRoles(1L);
    }

    @Test
    @DisplayName("case-3")
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

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀闁诲骏绱曢埀顒傚仯閸婃稑鈻嶉敐澶婃瀬闁靛牆顦粻?==========

    @Test
    @DisplayName("case-4")
    void createUser_Success() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_user")
                .password("Password@123")
                .displayName("New User")
                .roleIds(Arrays.asList(3L, 5L))
                .enabled(true)
                .remark("Created in unit test")
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
        verify(userRoleService).assignRolesToUser(eq(10L), anySet(), eq(10L));
    }

    @Test
    @DisplayName("case-5")
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
    @DisplayName("case-6")
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

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀箺缂佺虎鍨堕弻锟犲磼濞戞﹩妫嗛梺杞伴檷閸婃繈鐛?==========

    @Test
    @DisplayName("case-7")
    void updateUser_Success() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("Updated User")
                .enabled(false)
                .remark("Updated in unit test")
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
        assertThat(testUser.getDisplayName()).isEqualTo("Updated User");
        assertThat(testUser.getEnabled()).isFalse();
        assertThat(testUser.getRemark()).isEqualTo("Updated in unit test");

        verify(userRepository).findById(1L);
        verify(userRepository).save(testUser);
        verify(cacheService).onUserUpdated(1L);
    }

    @Test
    @DisplayName("case-8")
    void updateUser_UserNotFound() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("User Not Found")
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
    @DisplayName("case-9")
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
    @DisplayName("case-10")
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

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀闁诲繆鏅犲濠氬礋閳轰讲鍋撳Δ鍛瀬闁靛牆顦粻?==========

    @Test
    @DisplayName("case-11")
    void deleteUser_Success() {
        // Given
        doNothing().when(userManagementService).deleteUser(1L, 0L, "unknown");

        // When
        ResponseEntity<Void> response = userController.deleteUser(1L, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(userManagementService).deleteUser(1L, 0L, "unknown");
    }

    @Test
    @DisplayName("case-12")
    void deleteUser_UserNotFound() {
        // Given
        doThrow(new BusinessException(
                ErrorKeys.USER_NOT_FOUND,
                Map.of("userId", 999L)
        )).when(userManagementService).deleteUser(999L, 0L, "unknown");

        // When & Then
        assertThatThrownBy(() -> userController.deleteUser(999L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.USER_NOT_FOUND);

        verify(userManagementService).deleteUser(999L, 0L, "unknown");
    }

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥ鍡楀箹妞ゃ儲顨婂娲箵閹烘梻顔囬梺鎼炲妼闁帮綁寮绘繝鍌ゅ悑闁割偒鍋呴崑銉╂⒑?==========

    @Test
    @DisplayName("case-13")
    void assignRoles_Success() {
        // Given
        AssignRolesRequest request = AssignRolesRequest.builder()
                .roleIds(Arrays.asList(3L, 5L, 7L))
                .defaultRoleId(5L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));
        when(roleRepository.findById(5L)).thenReturn(Optional.of(salespersonRole));
        when(roleRepository.findById(7L)).thenReturn(Optional.of(purchaserRole));
        doNothing().when(userRoleService).assignRolesToUser(anyLong(), anySet(), anyLong());
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole, purchaserRole));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));

        // When
        ResponseEntity<UserWithRolesDTO> response = userController.assignRoles(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRoleCodes()).hasSize(3);
        assertThat(response.getBody().getDefaultRoleCode()).isEqualTo("SALESPERSON");

        verify(userRoleService).assignRolesToUser(eq(1L), anySet(), eq(1L));
        verify(userRepository).save(testUser);
        verify(cacheService).onUserRoleAssigned(1L);
    }

    @Test
    @DisplayName("case-13b")
    void assignRoles_DefaultRoleMustBelongToReplacementSet() {
        AssignRolesRequest request = AssignRolesRequest.builder()
                .roleIds(List.of(3L))
                .defaultRoleId(5L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));

        assertThatThrownBy(() -> userController.assignRoles(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.ROLE_NOT_ASSIGNED);

        verify(userRoleService, never()).assignRolesToUser(anyLong(), anySet(), anyLong());
    }

    @Test
    @DisplayName("case-14")
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
    @DisplayName("case-15")
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

    // ========== 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閺咁剚鎱ㄥΟ鑽ゆ▊闁冲搫鎳忛埛鏃堟煏閸繃鍣圭紒鈧€ｎ亖妲堥柟鍨暕濞撮攱銇勯锝庢疁闁?==========

    @Test
    @DisplayName("case-16")
    void removeRole_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(5L)).thenReturn(Optional.of(salespersonRole));
        when(userRoleService.userHasRole(1L, 5L)).thenReturn(true);
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole, salespersonRole)); // 闂?濠电偞鍨堕幖鈺傜閿濆缍栨俊銈呮噺閸?
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
    @DisplayName("case-17")
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
    @DisplayName("case-18")
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
    @DisplayName("case-19")
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
    @DisplayName("case-20")
    void removeRole_CannotRemoveLastRole() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(warehouseAdminRole));
        when(userRoleService.userHasRole(1L, 3L)).thenReturn(true);
        when(userRoleService.getUserRoles(1L))
                .thenReturn(Arrays.asList(warehouseAdminRole)); // 闂備礁鎲￠悷顖涚濠靛棴鑰?濠电偞鍨堕幖鈺傜閿濆缍栨俊銈呮噺閸?

        // When & Then
        assertThatThrownBy(() -> userController.removeRole(1L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(userRoleService, never()).removeRoleFromUser(anyLong(), anyLong());
    }
}

