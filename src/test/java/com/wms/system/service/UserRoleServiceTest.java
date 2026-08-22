package com.wms.system.service;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * UserRoleService 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偞鍋ㄩ柕濠忕畱閻?闁荤喐鐟︾敮鐔哥珶婵犲洤绀嗛柛鈩冪☉鐢娊鏌￠崼婵埿㈠┑顔惧枛閹啴宕熼鍌氼棎闂婎偄娲ら崯顐ｆ叏濞戙垺鍤?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偛鎹堕柣鎴炆戦悵顖炴煥?
 * 1. 闂佸憡甯掑Λ婵嬪储閵堝洦鍠嗛柟鐑樻礀椤ュ繒绱撴担鍦噮闁轰降鍊濋獮?
 * 2. 缂備礁顦…宄扳枍鎼淬劍鍋ㄩ柕濠忕畱閻撴洟鎮峰▎鎰濠?
 * 3. 闂佸綊娼х紞濠囧闯濞差亜绀嗛柛鈩冪☉鐢娊鎮峰▎鎰濠?
 * 4. 闂佸搫琚崕鎾敋濡ゅ懏鍋ㄩ柕濠忕畱閻撴洟鎮峰▎鎰濠?
 * 5. 闂佸搫琚崕鎾敋濡ゅ啯鍠嗛柟鐑樻礀椤ュ繘鏌ｉ妸銉ヮ伀闁轰降鍊濋獮?
 * 6. 缂傚倸鍊归幐鎼佹偤閵婏箑绶為弶鍫涘妽濞呭繐螖閻樿尙鐒烽柣?
 *
 * 濠电偛顦崝宥夊礈娴煎瓨鏅慨?.3 婵犮垼鍩栨穱娲綖濡ゅ懏鍤岄柤纰卞墴閸忓洨绱撴担鍝勬瀺闁煎灚鍨块弫宥呯暆閳ь剟寮妶澶婄闁汇値鍨煎锟犳煠鐟欏嫬绲婚柣掳鍔戝畷鎺楀Ω閵夛箒鍚?sys_user_role 闁荤偞渚楅悡澶屾?
 *      User 闁诲骸婀遍崑妯肩礊鐎ｎ偆鈻旂€广儱鎳庨弲娆撴煕閺嵮勫櫣闁?role 闁诲孩绋掗〃鍡涱敊?
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 3.3 (Updated for multi-role system)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class UserRoleServiceTest {

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private PermissionCacheService cacheService;

    @Mock
    private SecurityVersionService securityVersionService;

    @InjectMocks
    private UserRoleService userRoleService;

    private User testUser;
    private SysRole chairmanRole;
    private SysRole warehouseAdminRole;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByIdAndCompanyId(anyLong(), eq(1L)))
            .thenAnswer(invocation -> userRepository.findById(invocation.getArgument(0)));
        lenient().when(roleRepository.findByCompanyIdAndId(eq(1L), anyLong()))
            .thenAnswer(invocation -> roleRepository.findById(invocation.getArgument(1)));
        lenient().when(roleRepository.findByCompanyIdAndIdIn(eq(1L), any()))
            .thenAnswer(invocation -> roleRepository.findByIdIn(invocation.getArgument(1)));
        lenient().when(userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                eq(1L), anyLong(), anyLong()))
            .thenAnswer(invocation -> userRoleRepository.existsByUserIdAndRoleId(
                invocation.getArgument(1), invocation.getArgument(2)));
        lenient().when(userRoleRepository.findRoleIdsByCompanyIdAndUserId(eq(1L), anyLong()))
            .thenAnswer(invocation -> userRoleRepository.findRoleIdsByUserId(invocation.getArgument(1)));
        lenient().when(userRoleRepository.findUserIdsByCompanyIdAndRoleId(eq(1L), anyLong()))
            .thenAnswer(invocation -> userRoleRepository.findUserIdsByRoleId(invocation.getArgument(1)));
        lenient().when(userRepository.findAllByCompanyIdAndIdIn(eq(1L), any()))
            .thenAnswer(invocation -> userRepository.findAllById((Iterable<Long>) invocation.getArgument(1)));
        lenient().when(userRoleRepository.countByCompanyIdAndRoleId(eq(1L), anyLong()))
            .thenAnswer(invocation -> userRoleRepository.countByRoleId(invocation.getArgument(1)));
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("password")
                .enabled(true)
                .build();

        chairmanRole = SysRole.builder()
                .id(10L)
                .roleCode("CHAIRMAN")
                .roleName("闂佹垝绶ょ徊鑲╄姳閵娾晜鈷?")
                .status("ACTIVE")
                .build();

        warehouseAdminRole = SysRole.builder()
                .id(20L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌?")
                .status("ACTIVE")
                .build();
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("case-2")
    void assignRoleToUser_Success() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹妞界€光偓閸愮偓鍋ラ梺鑹邦潐閻擄繝宕楀Ο灏栧亾濞戞顏勶耿?
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(chairmanRole));
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L)).thenReturn(false);

        // When: 闂佸憡甯掑Λ婵嬪储閵堝洦鍠嗛柟鐑樻礀椤?
        userRoleService.assignRoleToUser(1L, 10L, 999L);

        // Then: 婵°倗濮撮惌渚€鎯佹径瀣攳婵犻潧妫涢幗鐘绘煙閸喚小缂?
        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleRepository, times(1)).save(captor.capture());

        SysUserRole saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRoleId()).isEqualTo(10L);
        assertThat(saved.getAssignedBy()).isEqualTo(999L);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾介柟鎯х－閹界姴顭块幆浼村摵闁?
        verify(cacheService, times(1)).onUserRoleAssigned(1L);
        verify(securityVersionService).bumpForUser(1L);
    }

    @Test
    @DisplayName("cannot assign another company's role even when the role ID exists globally")
    void assignRoleToUser_RejectsCrossCompanyRole() {
        TenantContextHolder.set(new TenantContext(
            20L, "beta", "beta.bcwms.com", RequestSurface.TENANT,
            TenantStatus.ACTIVE));
        User companyBUser = User.builder()
            .id(501L)
            .username("operator")
            .password("password")
            .enabled(true)
            .build();
        companyBUser.setCompanyId(20L);

        when(userRepository.findByIdAndCompanyId(501L, 20L))
            .thenReturn(Optional.of(companyBUser));
        when(roleRepository.findByCompanyIdAndId(20L, 10L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            userRoleService.assignRoleToUser(501L, 10L, 999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Role not found");

        verify(roleRepository).findByCompanyIdAndId(20L, 10L);
        verify(roleRepository, never()).findById(10L);
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("case-3")
    void assignRoleToUser_UserNotFound() {
        // Given: 闂佹椿娼块崝宥夊春濞戞瑧鈻旂€广儱鎳愰幗鐘绘煕?
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        // When & Then: 闁圭厧鐡ㄥΛ渚€顢氬璺虹婵炴垶锚濮ｅ鈧鍠栭崐鎼佹偉?
        assertThatThrownBy(() -> userRoleService.assignRoleToUser(1L, 10L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        // Then: 婵炴垶鎸哥粔瀵歌姳閼碱剚瀚氶柕澶堝€楃粻浠嬫倵?
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("case-4")
    void assignRoleToUser_RoleNotFound() {
        // Given: 闂佹椿娼块崝宥夊春濞戞埃鍋撳☉娅亜锕㈤鍫熸櫖鐎光偓閸愮偓鍋ラ梺鑲╊攰瀵挾绮径灞稿亾濞戞顏勶耿?
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(anyLong())).thenReturn(Optional.empty());

        // When & Then: 闁圭厧鐡ㄥΛ渚€顢氬璺虹婵炴垶锚濮ｅ鈧鍠栭崐鎼佹偉?
        assertThatThrownBy(() -> userRoleService.assignRoleToUser(1L, 10L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found");

        // Then: 婵炴垶鎸哥粔瀵歌姳閼碱剚瀚氶柕澶堝€楃粻浠嬫倵?
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("case-5")
    void assignRoleToUser_AlreadyAssigned() {
        // Given: 闁荤喐鐟︾敮鐔哥珶婵犲偆鍟呴柟缁樺笒閻庡姊?
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(chairmanRole));
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L)).thenReturn(true);

        // When: 闁诲繐绻戠换鍡涙儊椤栫偞鐓傜€广儱鎷嬪Σ濠氭煕閹烘垶顥㈤柛?
        userRoleService.assignRoleToUser(1L, 10L, 999L);

        // Then: 婵炴垶鎸哥粔瀵歌姳閼碱剚瀚氶柕澶堝€楃粻浠嬫倵濞戞瑱鍏紒杈ㄧ懅閹瑰嫮鈧湱濮风粻鏍⒑閹绘帞孝妞わ附鐓″畷姘跺幢濞戞ê璧嬮梺?
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("case-6")
    void removeRoleFromUser_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(chairmanRole));

        // When: 缂備礁顦…宄扳枍鎼达絾鍠嗛柟鐑樻礀椤?
        userRoleService.removeRoleFromUser(1L, 10L);

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰闁绘绮悵鐔兼煙閸喚小缂?
        verify(userRoleRepository, times(1))
                .deleteByCompanyIdAndUserIdAndRoleId(1L, 1L, 10L);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾介柟鎯х－閹界姴顭块幆浼村摵闁?
        verify(cacheService, times(1)).onUserRoleRemoved(1L);
        verify(securityVersionService).bumpForUser(1L);
    }

    @Test
    @DisplayName("case-7")
    void assignRolesToUser_Success() {
        // Given: 闂佹椿娼块崝宥夊春濞戞埃鍋撳☉娅亜锕?
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByIdIn(Set.of(10L, 20L)))
                .thenReturn(List.of(chairmanRole, warehouseAdminRole));

        // When: 闂佸綊娼х紞濠囧闯濞差亜绀嗛柛鈩冪☉鐢?2 婵炴垶鎼╂禍锝夛綖濡ゅ懏鍤?
        Set<Long> roleIds = Set.of(10L, 20L);
        userRoleService.assignRolesToUser(1L, roleIds, 999L);

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰闁割偅绻傞悘鈺呮⒒閸曗晛鈧牗鏅跺澶婂珘濠㈣泛瀵掑锟犳煠?
        verify(userRoleRepository, times(1)).deleteByCompanyIdAndUserId(1L, 1L);

        // Then: 婵°倗濮撮惌渚€鎯佹径瀣攳婵犻潧妫涢幗?2 濠电偛妫屽Σ鍕? 婵炴垶鎼╂禍锝夛綖濡ゅ懏鍤岄悹鍥囧懐顦?
        verify(userRoleRepository, times(2)).save(any(SysUserRole.class));

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾介柟鎯х－閹界姴顭块幆浼村摵闁?
        verify(cacheService, times(1)).onUserRoleAssigned(1L);
    }

    @Test
    @DisplayName("case-8")
    void assignRolesToUser_RejectsNonExistentRolesBeforeDeletingExistingAssignments() {
        // Given: 闂佹椿娼块崝宥夊春濞戞埃鍋撳☉娅亜锕㈤鍫熸櫖閻忕偠鍋愮粙濠氭煛鐏炴儳濮冪紒銊ㄦ閹叉挳骞掗弴鐑嗘婵炴垶鎸哥粔鎾偤閵娾晛鎹?
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByIdIn(Set.of(10L, 999L))).thenReturn(List.of(chairmanRole));

        // When: 闂佸綊娼х紞濠囧闯濞差亜绀嗛柛鈩冪☉鐢娊鏌ㄥ☉妯煎閻庡灚锕㈠畷銉╊敃閿涘嫮鎲归柣搴㈢⊕閿氭繝鈧鍫熷剭闁告洦鍣锟犳煠绾拋鍤嬬紒?
        Set<Long> roleIds = Set.of(10L, 999L);
        assertThatThrownBy(() -> userRoleService.assignRolesToUser(1L, roleIds, 888L))
                .isInstanceOf(com.wms.system.exception.BusinessException.class);

        // Then: 闂佸憡鐟禍娆戞崲濮樻墎鍋撳☉娅亪鎮洪妸鈺佹嵍闁靛ě鍕殸闁荤喐鐟︾敮鐔哥珶?
        verify(userRoleRepository, never()).deleteByUserId(anyLong());
        verify(userRoleRepository, never()).save(any(SysUserRole.class));
    }

    @Test
    @DisplayName("case-9")
    void getUserRoleIds_Success() {
        // Given: 濠碘槅鍨崜婵堚偓姘懄濞煎寮幐搴ｎ槬闁荤喐鐟︾敮鐔哥珶婵傚膊闂傚倸妫楀Λ妤呭箖?
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(10L, 20L));

        // When: 闂佸搫琚崕鎾敋濡ゅ懏鍋ㄩ柕濠忕畱閻撴洟鎮峰▎鎰濠?
        Set<Long> roleIds = userRoleService.getUserRoleIds(1L);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(roleIds).containsExactlyInAnyOrder(10L, 20L);
        verify(userRoleRepository, times(1)).findRoleIdsByUserId(1L);
    }

    @Test
    @DisplayName("case-10")
    void getUserRoles_Success() {
        // Given: 濠碘槅鍨崜婵堚偓姘懄濞煎寮幐搴ｎ槬闁荤喐鐟︾敮鐔哥珶婵傚膊闂佸憡绮岄惌渚€锝炲Δ鍛殞闁煎ジ顤傞崵濠囨煙?
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(10L, 20L));
        when(roleRepository.findByIdIn(Set.of(10L, 20L)))
                .thenReturn(List.of(chairmanRole, warehouseAdminRole));

        // When: 闂佸搫琚崕鎾敋濡ゅ懏鍋ㄩ柕濠忕畱閻撴洟鎮峰▎鎰濠㈢懓锕﹂幏鐘绘晜閽樺澹?
        List<SysRole> roles = userRoleService.getUserRoles(1L);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(SysRole::getRoleCode)
                .containsExactlyInAnyOrder("CHAIRMAN", "WAREHOUSE_ADMIN");
    }

    @Test
    @DisplayName("case-11")
    void getUserRoles_NoRoles() {
        // Given: 闂佹椿娼块崝宥夊春濞戞ǚ鏌﹂柍鈺佸暞缁犳帡鎮峰▎鎰濠?
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of());

        // When: 闂佸搫琚崕鎾敋濡ゅ懏鍋ㄩ柕濠忕畱閻撴洟鎮峰▎鎰濠㈢懓锕﹂幏鐘绘晜閽樺澹?
        List<SysRole> roles = userRoleService.getUserRoles(1L);

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧紓浣歌嫰閹碱偊宕归鍡樺仒?
        assertThat(roles).isEmpty();

        // Then: 婵炴垶鎸哥粔瀵歌姳閼碱剚瀚氶柕澶涘閸欌偓闁荤姴娲㈤崹濂革綖濡ゅ懏鍤岄柤濂割杺閸ゅ﹪鏌?
        verify(roleRepository, never()).findByIdIn(any());
    }

    @Test
    @DisplayName("case-12")
    void getRoleUserIds_Success() {
        // Given: 濠碘槅鍨崜婵堚偓姘懄濞煎寮幐搴ｎ槬闂佹椿娼块崝宥夊春濮圭拏闂傚倸妫楀Λ妤呭箖?
        when(userRoleRepository.findUserIdsByRoleId(10L))
                .thenReturn(Set.of(1L, 2L, 3L));

        // When: 闂佸搫琚崕鎾敋濡ゅ啯鍠嗛柟鐑樻礀椤ュ繘鏌ｉ妸銉ヮ伀闁轰降鍊濋獮?
        Set<Long> userIds = userRoleService.getRoleUserIds(10L);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(userIds).containsExactlyInAnyOrder(1L, 2L, 3L);
        verify(userRoleRepository, times(1)).findUserIdsByRoleId(10L);
    }

    @Test
    @DisplayName("case-13")
    void getRoleUsers_Success() {
        // Given: 濠碘槅鍨崜婵堚偓姘懄濞煎寮幐搴ｎ槬闂佹椿娼块崝宥夊春濮圭拏闂佸憡绮岄惉濂稿极閵堝绠ｉ柣銈庡灱閸ゅ﹪鏌?
        User user1 = User.builder().id(1L).username("user1").build();
        User user2 = User.builder().id(2L).username("user2").build();

        when(userRoleRepository.findUserIdsByRoleId(10L))
                .thenReturn(Set.of(1L, 2L));
        when(userRepository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(user1, user2));

        // When: 闂佸搫琚崕鎾敋濡ゅ啯鍠嗛柟鐑樻礀椤ュ繘鏌ｉ妸銉ヮ伀闁轰降鍊濋獮瀣偪椤栵絽娈滈梺?
        List<User> users = userRoleService.getRoleUsers(10L);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getUsername)
                .containsExactlyInAnyOrder("user1", "user2");
    }

    @Test
    @DisplayName("case-14")
    void userHasRole_True() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹瀚夊璺哄瘨閸ゅ鎮峰▎鎰濠?
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L))
                .thenReturn(true);

        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姢闁轰降鍊濋獮瀣煥鐎ｎ倣锕傛煕濮橀箖妾繝鈧担鐑樺枂闁圭儤娲栭ˉ?
        boolean hasRole = userRoleService.userHasRole(1L, 10L);

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?true
        assertThat(hasRole).isTrue();
    }

    @Test
    @DisplayName("case-15")
    void userHasRole_False() {
        // Given: 闂佹椿娼块崝宥夊春濞戞ǚ鏌﹂柍鈺佸暞缁犳帡鎮归崶銉ュ姤妞ゎ偅顨婇幊?
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L))
                .thenReturn(false);

        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姢闁轰降鍊濋獮瀣煥鐎ｎ倣锕傛煕濮橀箖妾繝鈧担鐑樺枂闁圭儤娲栭ˉ?
        boolean hasRole = userRoleService.userHasRole(1L, 10L);

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?false
        assertThat(hasRole).isFalse();
    }

    @Test
    @DisplayName("case-16")
    void userHasRoleCode_True() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹瀚夊璺哄瘨閸ゅ鎮峰▎鎰濠?
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(10L));
        when(roleRepository.findByIdIn(Set.of(10L)))
                .thenReturn(List.of(chairmanRole));

        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姢闁轰降鍊濋獮瀣煥鐎ｎ倣锕傛煕濮橀箖妾繝鈧担鐑樺枂闁圭儤娲栭ˉ蹇曠磽閸屾稓澧遍柣?
        boolean hasRole = userRoleService.userHasRoleCode(1L, "CHAIRMAN");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?true
        assertThat(hasRole).isTrue();
    }

    @Test
    @DisplayName("case-17")
    void userHasRoleCode_False() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹瀚夊璺猴工瀵版挸霉閻樺磭澧虫い顐ｎ殜閹?
        when(userRoleRepository.findRoleIdsByUserId(1L))
                .thenReturn(Set.of(20L));
        when(roleRepository.findByIdIn(Set.of(20L)))
                .thenReturn(List.of(warehouseAdminRole));

        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姢闁轰降鍊濋獮瀣煥鐎ｎ倣锕傛煕濮橀箖妾繝鈧担鐑樺枂闁圭儤娲栭ˉ蹇曠磽閸屾稓澧遍柣?
        boolean hasRole = userRoleService.userHasRoleCode(1L, "CHAIRMAN");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?false
        assertThat(hasRole).isFalse();
    }

    @Test
    @DisplayName("case-18")
    void getUserCountForRole() {
        // Given: 闁荤喐鐟︾敮鐔哥珶婵犲洤瀚?5 婵炴垶鎼╂禍鐐哄极閵堝绠?
        when(userRoleRepository.countByRoleId(10L))
                .thenReturn(5L);

        // When: 闂佸搫琚崕鎾敋濡ゅ懏鍋ㄩ柕濠忕畱閻撴洟鏌℃担宄板祮闁?
        long count = userRoleService.getUserCountForRole(10L);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(count).isEqualTo(5L);
    }
}
