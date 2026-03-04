package com.wms.system.service;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DynamicPermissionService 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 闂備焦褰冪粔鍫曞磻閿濆悿鍦偓锝庡幘濡叉悂鎮峰▎鎰濠㈢懓锕︾槐鎺曨槼濠㈣鐟╁畷顏嗕沪閻愵剛绉梻鍌氬閸旀顣鹃梺鍝勵儐閸旀洘鎱ㄥ☉銏″殑?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偛鎹堕柣鎴炆戦悵顖炴煥?
 * 1. 闂佸憡顨嗗ú婵嬶綖濡ゅ懏鍤岄柛娆忣槹缂嶁偓闂傚倸瀚崝鏍偂閿涘嫭瀚?
 * 2. 闂佹垝绶ょ徊鑲╄姳閵娾晜鈷愰柤鎰佸灱濞硷繝鏌ょ涵鍛毢闁瑰鍏橀獮宥夊礌閿涘嫮顦╂繝銏ｅ煐瀹€鎼佸闯閸濄儳纾肩憸搴㈢珶濞嗘挻鏅?
 * 3. 婵犮垼鍩栧銊︻殽閸モ晜鍠嗛柟鐑樻礀椤ュ繒绱撴担鍫濆椤ョ偤鏌ㄥ☉妯煎ⅵ闁逞屽墯鐢帞绱炴繝鍥ㄦ櫖?
 * 4. 闂佸搫顦崯鏉戭瀶濞差亜鍌ㄥ┑鐘宠壘濞?
 * 5. 缂備礁鐭傜紓姘讹綖濡ゅ懏鍤岄柟缁樺俯濡查亶鏌?
 *
 * @author WMS Team
 * @since 2026-01-18
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
@SuppressWarnings("unchecked")
class DynamicPermissionServiceTest {

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private SysRoleInheritRepository roleInheritRepository;

    @Mock
    private SysRolePermissionRepository rolePermissionRepository;

    @Mock
    private SysPermissionRepository permissionRepository;

    @InjectMocks
    private DynamicPermissionService permissionService;

    private SysRole chairmanRole;
    private SysRole warehouseAdminRole;
    private SysRole buyerRole;
    private SysRole sellerRole;

    private SysPermission inventoryViewPermission;
    private SysPermission purchaseCreatePermission;
    private SysPermission salesViewPermission;
    private SysPermission globalViewPermission;

    @BeforeEach
    void setUp() {
        // 闂佸憡甯楃换鍌烇綖閹版澘绀岄柡宥冨妿閵堟挳鎮归崶銊︾妞ゎ偅顨婇幊?
        chairmanRole = SysRole.builder()
                .id(1L)
                .roleCode("CHAIRMAN")
                .roleName("闂佹垝绶ょ徊鑲╄姳閵娾晜鈷?")
                .status("ACTIVE")
                .build();

        warehouseAdminRole = SysRole.builder()
                .id(2L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌?")
                .status("ACTIVE")
                .build();

        buyerRole = SysRole.builder()
                .id(3L)
                .roleCode("BUYER")
                .roleName("闂備焦褰冨ú鈺呭窗濮椻偓瀹?")
                .status("ACTIVE")
                .build();

        sellerRole = SysRole.builder()
                .id(4L)
                .roleCode("SELLER")
                .roleName("闂備礁绨遍崑鎾绘煕閻戝棗鏋涢柟?")
                .status("ACTIVE")
                .build();

        // 闂佸憡甯楃换鍌烇綖閹版澘绀岄柡宥冨妿閵堟挳鎮归崶銊︾婵炵厧鐗撳?
        inventoryViewPermission = SysPermission.builder()
                .id(101L)
                .permissionCode("inventory:view")
                .permissionName("闂佸搫琚崕鍐诧耿閸涱喗鍎熼柟鎯х－閹?")
                .permissionType("API")
                .resourcePath("/api/inventory/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(1)
                .build();

        purchaseCreatePermission = SysPermission.builder()
                .id(102L)
                .permissionCode("purchase:create")
                .permissionName("闂佸憡甯楃粙鎴犵磽閹剧粯鐓傞柛銉簻閺嬬娀鎮规担闈涚仼鐎?")
                .permissionType("API")
                .resourcePath("/api/purchase")
                .httpMethod("POST")
                .status("ACTIVE")
                .sortOrder(2)
                .build();

        salesViewPermission = SysPermission.builder()
                .id(103L)
                .permissionCode("sales:view")
                .permissionName("闂佸搫琚崕鍐诧耿閸涘瓨鐓ラ柍褜鍓熷畷?")
                .permissionType("API")
                .resourcePath("/api/sales/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(3)
                .build();

        globalViewPermission = SysPermission.builder()
                .id(104L)
                .permissionCode("global:view")
                .permissionName("闂佺绻堥崝宀勬儑椤掑嫬鏋侀柣妤€鐗嗙粊锕傛煛鐏炶鍔ゆ繝鈧?")
                .permissionType("API")
                .resourcePath("/api/reports/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(4)
                .build();
    }

    @Test
    @DisplayName("case-2")
    void getUserPermissions_SingleRole_NoInheritance() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹鐭楁い蹇撴噺缁犳帒霉閻樿櫕灏紒銊ㄤ含缁鏁嶉崟顒€鈧偤鏌涘☉娆樼劸妞ゎ偅顨婇幊?
        Long userId = 1L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L)); // WAREHOUSE_ADMIN

        // Given: 婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌涘☉娅虫垵螞閵堝鍋夐柟顖炲亰濞硷繝鏌?
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(2L))
                .thenReturn(Set.of());

        // Given: 婵炲濮甸幐鍝ヨ姳鏉堚晝涓嶉柨娑樺閸婄偤鏌涘☉娅虫垵锕?1 婵炴垶鎼╂禍婵嗩焽閸儲鈷?
        SysRolePermission rolePermission = new SysRolePermission();
        rolePermission.setRoleId(2L);
        rolePermission.setPermissionId(101L);
        rolePermission.setPermission(inventoryViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(2L)))
                .thenReturn(List.of(rolePermission));

        // Given: 闂佸搫琚崕鎾敋濡ゅ啯鍠嗛柟鐑樻礀椤ュ繐菐閸ワ絽澧插ù?
        when(roleRepository.findByIdIn(Set.of(2L)))
                .thenReturn(List.of(warehouseAdminRole));

        // When: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋ㄩ柕濠忕畱閻撴洟鏌℃径濠傛殻婵?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).containsExactly(2L);
        assertThat(result.getRoleCodes()).containsExactly("WAREHOUSE_ADMIN");
        assertThat(result.getEffectiveRoleIds()).containsExactly(2L);
        assertThat(result.getPermissions()).hasSize(1);
        assertThat(result.getPermissions().get(0).getPermissionCode()).isEqualTo("inventory:view");
        assertThat(result.getApiPermissions()).hasSize(1);
        assertThat(result.hasPermission("inventory:view")).isTrue();
        assertThat(result.hasPermission("purchase:create")).isFalse();
    }

    @Test
    @DisplayName("case-3")
    void getUserPermissions_ChairmanRole_MultipleInheritance() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹鍙婃い鏍ㄧ箖閸ｏ絽霉濠婂喚鍎旈柡?
        Long userId = 2L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(1L)); // CHAIRMAN

        // Given: 闂佹垝绶ょ徊鑲╄姳閵娾晜鈷愰悹鎭掑妽閸╂盯鏌?3 婵炴垶鎼╂禍锝夛綖濡ゅ懏鍤?
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(1L))
                .thenReturn(Set.of(2L, 3L, 4L)); // WAREHOUSE_ADMIN, BUYER, SELLER

        // Given: 闂佺粯鐗楅崕濂革綖濡ゅ懏鍤岄柛娆忣槹閿熴儳绱撴担鍫濆椤?
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(2L))
                .thenReturn(Set.of());
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(3L))
                .thenReturn(Set.of());
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(4L))
                .thenReturn(Set.of());

        // Given: 闂佸憡鑹剧€氼垶锝炲Δ鍛殞闁肩⒈鍓氶悾閬嶆煛婢跺﹤鏆ｆ俊?
        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(1L);
        rp1.setPermissionId(104L);
        rp1.setPermission(globalViewPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(2L);
        rp2.setPermissionId(101L);
        rp2.setPermission(inventoryViewPermission);

        SysRolePermission rp3 = new SysRolePermission();
        rp3.setRoleId(3L);
        rp3.setPermissionId(102L);
        rp3.setPermission(purchaseCreatePermission);

        SysRolePermission rp4 = new SysRolePermission();
        rp4.setRoleId(4L);
        rp4.setPermissionId(103L);
        rp4.setPermission(salesViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(rp1, rp2, rp3, rp4));

        // Given: 闂佸搫琚崕鎾敋濡ゅ啯鍠嗛柟鐑樻礀椤ュ繐菐閸ワ絽澧插ù?
        when(roleRepository.findByIdIn(Set.of(1L)))
                .thenReturn(List.of(chairmanRole));

        // When: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚濯奸柨婵嗗閻ㄦ垿姊婚埀顒勫箰鎼淬垻绉梻?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).containsExactly(1L);
        assertThat(result.getRoleCodes()).containsExactly("CHAIRMAN");
        assertThat(result.getEffectiveRoleIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰骇闁告劦鍠楅娆愭叏閻熺増澶勯柍褜鍏涚槐顔炬濞嗘劖鍎熼柡鍐ㄦ祩閸ゅ鏌?4 婵炴垶鎼╂禍婵嗩焽閸儲鈷旈柟鏉垮缁€?
        assertThat(result.getPermissions()).hasSize(4);
        assertThat(result.getPermissionCodes()).containsExactlyInAnyOrder(
                "global:view", "inventory:view", "purchase:create", "sales:view"
        );

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾肩憸搴㈢珶濞嗘挻鍎嶉柛鏇ㄥ墯缂嶁偓闂?
        assertThat(result.hasPermission("inventory:view")).isTrue(); // 缂傚倷缍€閸曨偒妫￠梺鐓庮殠娴滄瑧鍒掗妸锔藉劅闁归箖顤傞崥鈧梺鑽ゅ仜濡骞?
        assertThat(result.hasPermission("purchase:create")).isTrue(); // 缂傚倷缍€閸曨偒妫￠梺鐓庮殠娴滎亪宕抽幍顔藉妞ゆ挾鍋為崰?
        assertThat(result.hasPermission("sales:view")).isTrue(); // 缂傚倷缍€閸曨偒妫￠梺鐓庮殠娴滎亪寮ㄩ姀銈呰埞妞ゆ柨鍚嬮崰?
        assertThat(result.hasPermission("global:view")).isTrue(); // 闂佹垝绶ょ徊鑲╄姳閵娾晜鈷愬璺烘憸閻熲晠鎮?
    }

    @Test
    @DisplayName("case-4")
    void getInheritedRoleIds_ThreeLevelInheritance() {
        // Given: A -> B -> C 婵炴垶鎸搁ˇ顖炴儑閹殿喚纾肩憸搴㈢珶?
        // 闁荤喐鐟︾敮鐔哥珶婢跺墽绱撴担鍫濆椤ョ偤鎮峰▎鎰濠㈢懓銇熼梺鎸庣☉閻線锝炲Δ鍛殞闁活偆鍠撶槐鎺曨槼濠㈣鐟ч幉鎾箳閺囩儐妫孋
        Long roleA = 10L;
        Long roleB = 20L;
        Long roleC = 30L;

        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleA))
                .thenReturn(Set.of(roleB));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleB))
                .thenReturn(Set.of(roleC));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleC))
                .thenReturn(Set.of());

        // When: 闂佸搫琚崕鎾敋濡ゅ啯鍠嗛柟鐑樻礀椤ュ粐闂佹眹鍔岀€氼厽鏅跺澶婂珘濠㈣泛鏈崺娑㈡煙娴ｅ啫鍔垫い顐ｎ殜閹?
        Set<Long> result = permissionService.getInheritedRoleIds(Set.of(roleA));

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺虹闁告侗鍘介崕?A, B, C
        assertThat(result).containsExactlyInAnyOrder(roleA, roleB, roleC);

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰劵闁圭儤鍨圭粔娲偣鐎ｎ亜鏆熼柡?
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleA);
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleB);
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleC);
    }

    @Test
    @DisplayName("case-5")
    void getUserPermissions_DuplicatePermissions() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹瀚?2 婵炴垶鎼╂禍锝夛綖濡ゅ懏鍤岄悹鍥囧懐顦柣搴ｆ嚀閸熲晛顭ㄩ幋锕€瀚夊鑸靛姇濞呫垹顭跨捄铏剐ｆ繛鐓庣墦濮?
        Long userId = 3L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L, 3L)); // WAREHOUSE_ADMIN, BUYER

        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        // Given: 婵炴垶鎸堕崐鎾绘煂濠婂懏鍠嗛柟鐑樻礀椤ュ繘姊洪鍨撴繝鈧?inventory:view 闂佸搫顦崯鏉戭瀶?
        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(2L);
        rp1.setPermissionId(101L);
        rp1.setPermission(inventoryViewPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(3L);
        rp2.setPermissionId(101L); // 闂佸憡鑹炬總鏃傜博鐎涙鈻旀い蹇撴噺缂嶁偓闂傚倸瀚埀顒侇儚
        rp2.setPermission(inventoryViewPermission);

        SysRolePermission rp3 = new SysRolePermission();
        rp3.setRoleId(3L);
        rp3.setPermissionId(102L);
        rp3.setPermission(purchaseCreatePermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(2L, 3L)))
                .thenReturn(List.of(rp1, rp2, rp3));

        when(roleRepository.findByIdIn(Set.of(2L, 3L)))
                .thenReturn(List.of(warehouseAdminRole, buyerRole));

        // When: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋ㄩ柕濠忕畱閻撴洟鏌℃径濠傛殻婵?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 闂佸搫顦崯鏉戭瀶閻戞ɑ鍎熼柡鍐ㄦ祩閸ゅ鏌涘Ο渚吋闁革絾鎮傞弫宥囦沪閽樺閿梺?2 婵炴垶鎼╂禍婵嗩焽閸儲鈷?
        assertThat(result.getPermissions()).hasSize(2);
        assertThat(result.getPermissionCodes()).containsExactlyInAnyOrder(
                "inventory:view", "purchase:create"
        );
    }

    @Test
    @DisplayName("case-6")
    void getUserPermissions_NoRoles() {
        // Given: 闂佹椿娼块崝宥夊春濞戞ǚ鏌﹂柍鈺佸暞缁犳帡鎮峰▎鎰濠?
        Long userId = 4L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of());

        // When: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋ㄩ柕濠忕畱閻撴洟鏌℃径濠傛殻婵?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧紓浣哥灱閸庛倕顭囬崼銉︹挃?
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).isEmpty();
        assertThat(result.getPermissions()).isEmpty();
        assertThat(result.hasPermission("any:permission")).isFalse();

        // Then: 婵炴垶鎸哥粔瀵歌姳閼碱剚瀚氶柕澶涘閸欌偓闁荤姴娲㈤崹鍝勵焽閸儲鈷?
        verify(rolePermissionRepository, never()).findByRoleIdInWithPermission(any());
    }

    @Test
    @DisplayName("case-7")
    void hasPermission() {
        // Given: 濠碘槅鍨崜婵堚偓姘懇閹粙濡搁敃鈧悡鏇㈡煛婢跺﹤鏆ｆ俊?
        Long userId = 5L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        SysRolePermission rp = new SysRolePermission();
        rp.setRoleId(2L);
        rp.setPermissionId(101L);
        rp.setPermission(inventoryViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(warehouseAdminRole));

        // When & Then: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姕婵炵厧鐗撳?
        assertThat(permissionService.hasPermission(userId, "inventory:view")).isTrue();
        assertThat(permissionService.hasPermission(userId, "purchase:create")).isFalse();
    }

    @Test
    @DisplayName("case-8")
    void hasAnyPermission() {
        // Given: 濠碘槅鍨崜婵堚偓姘懇閹粙濡搁敃鈧悡鏇㈡煛婢跺﹤鏆ｆ俊?
        Long userId = 6L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        SysRolePermission rp = new SysRolePermission();
        rp.setRoleId(2L);
        rp.setPermissionId(101L);
        rp.setPermission(inventoryViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(warehouseAdminRole));

        // When & Then: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姕婵″弶鎮傚畷銉╂晜閼恒儳鐣虫繛瀵稿Х缁垶宕滄导鏉戠骇闁告劦鍠楅?
        assertThat(permissionService.hasAnyPermission(userId, "inventory:view", "purchase:create")).isTrue();
        assertThat(permissionService.hasAnyPermission(userId, "sales:view", "purchase:create")).isFalse();
    }

    @Test
    @DisplayName("case-9")
    void hasAllPermissions() {
        // Given: 濠碘槅鍨崜婵堚偓姘懇閹粙濡搁敃鈧悡鏇㈡煛?2 婵炴垶鎼╂禍婵嗩焽閸儲鈷?
        Long userId = 7L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(2L);
        rp1.setPermissionId(101L);
        rp1.setPermission(inventoryViewPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(2L);
        rp2.setPermissionId(102L);
        rp2.setPermission(purchaseCreatePermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp1, rp2));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(warehouseAdminRole));

        // When & Then: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姕婵″弶鎮傚畷銉╂晜閼恒儳鐣抽梺鍦暯閸嬫捇鏌￠崼婵愭Ч婵炵厧鐗撳?
        assertThat(permissionService.hasAllPermissions(userId, "inventory:view", "purchase:create")).isTrue();
        assertThat(permissionService.hasAllPermissions(userId, "inventory:view", "sales:view")).isFalse();
    }

    @Test
    @DisplayName("case-10")
    void getUserPermissions_PermissionTypeClassification() {
        // Given: 闂佹椿娼块崝宥夊春濞戙垹瀚夊璺侯槺閻熸繈鏌涘顒傜伇閻炴凹鍋婂畷鍦偓锝庡墯閻ｉ亶鏌℃径濠傛殻婵?
        Long userId = 8L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(1L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        // 闂佸憡甯楃粙鎴犵磽閹惧鈻旂€广儱鎳忛崐杈╃磼椤愩儺鍤欓柣搴ｅ厴閹啴宕熼浣虹К闂?
        SysPermission menuPermission = SysPermission.builder()
                .id(201L)
                .permissionCode("menu:inventory")
                .permissionName("闁圭厧鐡ㄩ幐鎼佹偤閵娾晜鍤曟繝濠傚暙缁€?")
                .permissionType("MENU")
                .status("ACTIVE")
                .sortOrder(1)
                .build();

        SysPermission buttonPermission = SysPermission.builder()
                .id(202L)
                .permissionCode("button:delete")
                .permissionName("闂佸憡甯炴繛鈧繛鍛叄楠炴劖寰勯幇顓炲攭")
                .permissionType("BUTTON")
                .status("ACTIVE")
                .sortOrder(2)
                .build();

        SysRolePermission rp1 = new SysRolePermission();
        rp1.setPermission(menuPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setPermission(inventoryViewPermission); // API type

        SysRolePermission rp3 = new SysRolePermission();
        rp3.setPermission(buttonPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(any()))
                .thenReturn(List.of(rp1, rp2, rp3));
        when(roleRepository.findByIdIn(any()))
                .thenReturn(List.of(chairmanRole));

        // When: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋ㄩ柕濠忕畱閻撴洟鏌℃径濠傛殻婵?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰骇闁告劦鍠楅娆撴煕閹烘垶顥犻悶?
        assertThat(result.getMenuPermissions()).hasSize(1);
        assertThat(result.getApiPermissions()).hasSize(1);
        assertThat(result.getButtonPermissions()).hasSize(1);
        assertThat(result.getPermissions()).hasSize(3);
    }
}
