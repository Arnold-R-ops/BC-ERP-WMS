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
 * DynamicPermissionService 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * 闂傚倷鐒﹁ぐ鍐矓閸洖纾婚柨婵嗘偪閸︻厸鍋撻敐搴″箻婵″弶鎮傞幃宄扳枎閹邦剛顑勬繝銏㈡嚀閿曪妇妲愰幒鏇ㄦЪ婵犮垼顫夐悷鈺佺暦椤忓棔娌柣鎰靛墰缁夘剟姊婚崒姘仾闁告梹顭堥。楣冩⒑閸濆嫷鍎愰柛鏃€娲橀幈銊モ槈閵忊€虫畱?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仜閹瑰爼鏌ｉ幋鐐嗘垿鎮甸鐐寸叆?
 * 1. 闂備礁鎲￠〃鍡椕哄┑瀣剁稏婵°倕鎳忛崵宀勬煕濞嗗浚妲圭紓宥佸亾闂傚倸鍊哥€氼參宕濋弽顓熷亗闁挎稑瀚€?
 * 2. 闂備焦鍨濈欢銈囧緤閼测晞濮抽柕濞炬櫆閳锋劙鏌ら幇浣哥伇婵炵》绻濋弻銈囨兜閸涱厾姣㈤梺鐟邦嚟閸忔﹢鐛澶婄闁挎稑瀚ˇ鈺傜節閵忥絽鐓愮€光偓閹间礁闂柛婵勫劤绾捐偐鎲告惔銏㈢彾婵炲棙鎸婚弲?
 * 3. 濠电姰鍨奸崺鏍ь潩閵婏富娈介柛銉㈡櫆閸犲棝鏌熼悜妯荤妞ゃ儱绻掔槐鎾存媴閸繂顏╂い銉у仱閺屻劌鈽夊Ο鐓庘叺闂侀€炲苯澧悽顖涘笧缁辩偞绻濋崶銊︽珫?
 * 4. 闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簻閸屻劌鈹戦悩瀹犲婵?
 * 5. 缂傚倷绀侀惌鍌滅磽濮樿缍栨俊銈呮噺閸ゅ矂鏌熺紒妯轰刊婵℃煡浜堕弻?
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
        // 闂備礁鎲＄敮妤冩崲閸岀儑缍栭柟鐗堟緲缁€宀勬煛瀹ュ啫濡块柕鍫熸尦閹綊宕堕妸锔绢槷濡炪値鍋呴〃濠囧箠?
        chairmanRole = SysRole.builder()
                .id(1L)
                .roleCode("CHAIRMAN")
                .roleName("闂備焦鍨濈欢銈囧緤閼测晞濮抽柕濞炬櫆閳?")
                .status("ACTIVE")
                .build();

        warehouseAdminRole = SysRole.builder()
                .id(2L)
                .roleCode("WAREHOUSE_ADMIN")
                .roleName("濠电偛顕慨鐢稿箰閸濄儴濮抽弶鍫氭櫇娑撳秹鏌ㄥ☉妯侯仾闁稿﹦鍋ら弻?")
                .status("ACTIVE")
                .build();

        buyerRole = SysRole.builder()
                .id(3L)
                .roleCode("BUYER")
                .roleName("闂傚倷鐒﹁ぐ鍐洪埡鍛獥婵せ鍋撶€?")
                .status("ACTIVE")
                .build();

        sellerRole = SysRole.builder()
                .id(4L)
                .roleCode("SELLER")
                .roleName("闂傚倷绀佺花閬嶅磻閹剧粯鐓曢柣鎴濇閺嬫盯鏌?")
                .status("ACTIVE")
                .build();

        // 闂備礁鎲＄敮妤冩崲閸岀儑缍栭柟鐗堟緲缁€宀勬煛瀹ュ啫濡块柕鍫熸尦閹綊宕堕妸锔绢槰濠电偟鍘ч悧鎾愁潖?
        inventoryViewPermission = SysPermission.builder()
                .id(101L)
                .permissionCode("inventory:view")
                .permissionName("闂備礁鎼悮顐﹀磿閸愯鑰块柛娑卞枟閸庣喖鏌熼幆褏锛嶉柟?")
                .permissionType("API")
                .resourcePath("/api/inventory/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(1)
                .build();

        purchaseCreatePermission = SysPermission.builder()
                .id(102L)
                .permissionCode("purchase:create")
                .permissionName("闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鍓х帛閻撳倿鏌涢妷顔荤盎闁哄濞€閹鎷呴棃娑氫患閻?")
                .permissionType("API")
                .resourcePath("/api/purchase")
                .httpMethod("POST")
                .status("ACTIVE")
                .sortOrder(2)
                .build();

        salesViewPermission = SysPermission.builder()
                .id(103L)
                .permissionCode("sales:view")
                .permissionName("闂備礁鎼悮顐﹀磿閸愯鑰块柛娑樼摠閻撱儵鏌嶈閸撶喎鐣?")
                .permissionType("API")
                .resourcePath("/api/sales/**")
                .httpMethod("GET")
                .status("ACTIVE")
                .sortOrder(3)
                .build();

        globalViewPermission = SysPermission.builder()
                .id(104L)
                .permissionCode("global:view")
                .permissionName("闂備胶顭堢换鍫ュ礉瀹€鍕剳妞ゆ帒瀚弸渚€鏌ｅΔ鈧悧鍡欑矈閿曞倹鐓涢悘鐐额嚙閸斻倖绻濋埀?")
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
        // Given: 闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴欏灩閻銇勮箛鎾村櫤缂佺姵甯掗湁闁绘娅曠亸顓犵磼閵娿劋鍚紒顔碱煼閺佸秹宕熼鈧埀顒傚仱閺屾稑鈽夊▎妯煎姼濡炪値鍋呴〃濠囧箠?
        Long userId = 1L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L)); // WAREHOUSE_ADMIN

        // Given: 濠电偛顕慨鐢稿箰閸濄儴濮抽弶鍫氭櫇娑撳秹鏌ㄥ☉妯侯仾闁稿﹦鍋ら弻娑樷槈濞呰櫕鍨佃灋闁靛牆顦伴崑澶愭煙椤栫偛浜版繛纭风節閺?
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(2L))
                .thenReturn(Set.of());

        // Given: 濠电偛顕慨鐢稿箰閸濄儴濮抽弶鍫氭櫇娑撳秹鏌ㄥ☉妯侯仾闁稿﹦鍋ら弻娑樷槈濞呰櫕鍨甸敃?1 濠电偞鍨堕幖鈺傜濠靛棭鐒介柛顐犲劜閳?
        SysRolePermission rolePermission = new SysRolePermission();
        rolePermission.setRoleId(2L);
        rolePermission.setPermissionId(101L);
        rolePermission.setPermission(inventoryViewPermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(2L)))
                .thenReturn(List.of(rolePermission));

        // Given: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞閸犲棝鏌熼悜妯荤妞ゃ儱绻愯彁闁搞儻绲芥晶鎻捗?
        when(roleRepository.findByIdIn(Set.of(2L)))
                .thenReturn(List.of(warehouseAdminRole));

        // When: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻鈩冨緞婵犲倹娈诲┑?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧壕濂告煙閹屽殶濞?
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
        // Given: 闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴欏灩閸欏﹥銇勯弽銊х畺闁革綇绲介湁婵犲﹤鍠氶崕鏃堟煛?
        Long userId = 2L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(1L)); // CHAIRMAN

        // Given: 闂備焦鍨濈欢銈囧緤閼测晞濮抽柕濞炬櫆閳锋劙鎮归幁鎺戝闁糕晜鐩弻?3 濠电偞鍨堕幖鈺傜閿濆缍栨俊銈呮噺閸?
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(1L))
                .thenReturn(Set.of(2L, 3L, 4L)); // WAREHOUSE_ADMIN, BUYER, SELLER

        // Given: 闂備胶绮悧妤呭磿婵傞潻缍栨俊銈呮噺閸ゅ矂鏌涘▎蹇ｆЧ闁跨喆鍎崇槐鎾存媴閸繂顏╂い?
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(2L))
                .thenReturn(Set.of());
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(3L))
                .thenReturn(Set.of());
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(4L))
                .thenReturn(Set.of());

        // Given: 闂備礁鎲￠懝鍓р偓姘煎灦閿濈偛螖閸涱喖娈為梺鑲┾拡閸撴岸鎮鹃柆宥嗙厸濠㈣泛锕ら弳锝嗕繆?
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

        // Given: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞閸犲棝鏌熼悜妯荤妞ゃ儱绻愯彁闁搞儻绲芥晶鎻捗?
        when(roleRepository.findByIdIn(Set.of(1L)))
                .thenReturn(List.of(chairmanRole));

        // When: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯婵ジ鏌ㄥ┑鍡楊伀闁汇劍鍨垮濠氬焵椤掑嫬绠伴幖娣灮缁夘剟姊?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧壕濂告煙閹屽殶濞?
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).containsExactly(1L);
        assertThat(result.getRoleCodes()).containsExactly("CHAIRMAN");
        assertThat(result.getEffectiveRoleIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傞獓闂佸憡鍔﹂崰妤咁敁濞嗘劖鍙忛柣鐔哄婢跺嫰鏌嶈閸忔稓妲愰鐐潟婵炲棙鍔栭崕鐔兼煛閸愩劍绁╅柛銈咁樀閺?4 濠电偞鍨堕幖鈺傜濠靛棭鐒介柛顐犲劜閳锋棃鏌熼弶鍨暢缂佲偓?
        assertThat(result.getPermissions()).hasSize(4);
        assertThat(result.getPermissionCodes()).containsExactlyInAnyOrder(
                "global:view", "inventory:view", "purchase:create", "sales:view"
        );

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧壕鑲╂喐鎼淬垻鐝舵繛鍡樻尰閸庡秹鏌涢弴銊ュ缂傚秮鍋撻梻?
        assertThat(result.hasPermission("inventory:view")).isTrue(); // 缂傚倸鍊风紞鈧柛鏇ㄥ亽濡繝姊洪悡搴疇濞存粍鐟ч崚鎺楀Ω閿旇棄鍔呴梺褰掔畺椤ゅ倿宕ラ埀顒勬⒑閼姐倕浠滄俊顐ｎ殜楠?
        assertThat(result.hasPermission("purchase:create")).isTrue(); // 缂傚倸鍊风紞鈧柛鏇ㄥ亽濡繝姊洪悡搴疇濞存粠浜畷鎶藉箥椤旇棄顏稿銈嗘尵閸嬬偤宕?
        assertThat(result.hasPermission("sales:view")).isTrue(); // 缂傚倸鍊风紞鈧柛鏇ㄥ亽濡繝姊洪悡搴疇濞存粠浜銊╁閵堝懓鍩炲銈嗘煥閸氬宕?
        assertThat(result.hasPermission("global:view")).isTrue(); // 闂備焦鍨濈欢銈囧緤閼测晞濮抽柕濞炬櫆閳锋劕顭跨捄鐑樻喐闁荤啿鏅犻幃?
    }

    @Test
    @DisplayName("case-4")
    void getInheritedRoleIds_ThreeLevelInheritance() {
        // Given: A -> B -> C 濠电偞鍨堕幐鎼佀囬鐐村剳闁规鍠氱壕鑲╂喐鎼淬垻鐝?
        // 闂佽崵鍠愰悷锔炬暜閻斿摜鐝跺璺哄⒔缁辨挻鎷呴崼婵嗩仼妞ゃ儳鍋ら幃宄扳枎閹邦剛顑勬繝銏㈡嚀閵囩喖姊洪幐搴ｂ槈闁活厺绶氶敐鐐参旈崨顔兼疄闂佹椿鍋嗛崰鎾舵閹烘洦妲兼繝銏ｎ潐閻熝囧箟閹绢喖绠抽柡鍥╁剱濡瓔
        Long roleA = 10L;
        Long roleB = 20L;
        Long roleC = 30L;

        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleA))
                .thenReturn(Set.of(roleB));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleB))
                .thenReturn(Set.of(roleC));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(roleC))
                .thenReturn(Set.of());

        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞閸犲棝鏌熼悜妯荤妞ゃ儱绮愰梻浣圭湽閸斿瞼鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾閺堫剟宕哄☉銏＄厵濞达絽鍟崝鍨亜椤愶綆娈滈柟?
        Set<Long> result = permissionService.getInheritedRoleIds(Set.of(roleA));

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顦遍梺鍛婁緱閸樹粙宕?A, B, C
        assertThat(result).containsExactlyInAnyOrder(roleA, roleB, roleC);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顓犲姷闂佸湱鍎ら崹鍦矓濞差亝鍋ｉ悗锝庝簻閺嗙喖鏌?
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleA);
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleB);
        verify(roleInheritRepository, times(1)).findParentRoleIdsByChildRoleId(roleC);
    }

    @Test
    @DisplayName("case-5")
    void getUserPermissions_DuplicatePermissions() {
        // Given: 闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴欏灩鐎?2 濠电偞鍨堕幖鈺傜閿濆缍栨俊銈呮噺閸ゅ矂鎮归崶鍥ф噽椤︻噣鏌ｆ惔锝嗗殌闁哥啿鏅涢…銊╁箣閿曗偓鐎氬顭块懜闈涘婵炲懌鍨归…璺ㄦ崉閾忓墣锝嗙箾閻撳海澧︽慨?
        Long userId = 3L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(2L, 3L)); // WAREHOUSE_ADMIN, BUYER

        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        // Given: 濠电偞鍨堕幐鍫曞磹閹剧粯鐓傛繝濠傛噺閸犲棝鏌熼悜妯荤妞ゃ儱绻樺娲敊閸啩鎾寸節閳?inventory:view 闂備礁鎼ˇ顖炲疮閺夋埈鐎?
        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(2L);
        rp1.setPermissionId(101L);
        rp1.setPermission(inventoryViewPermission);

        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(3L);
        rp2.setPermissionId(101L); // 闂備礁鎲￠懝鐐附閺冨倻鍗氶悗娑欘焽閳绘梹銇勮箛鎾村櫤缂傚秮鍋撻梻鍌氬€哥€氼參鍩€椤掍緡鍎?
        rp2.setPermission(inventoryViewPermission);

        SysRolePermission rp3 = new SysRolePermission();
        rp3.setRoleId(3L);
        rp3.setPermissionId(102L);
        rp3.setPermission(purchaseCreatePermission);

        when(rolePermissionRepository.findByRoleIdInWithPermission(Set.of(2L, 3L)))
                .thenReturn(List.of(rp1, rp2, rp3));

        when(roleRepository.findByIdIn(Set.of(2L, 3L)))
                .thenReturn(List.of(warehouseAdminRole, buyerRole));

        // When: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻鈩冨緞婵犲倹娈诲┑?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 闂備礁鎼ˇ顖炲疮閺夋埈鐎堕柣鎴炆戦崕鐔兼煛閸愩劍绁╅柛銈咁樀閺屾稑螣娓氼垳鍚嬮梺闈╃稻閹倿寮鍥︽勃闁芥ê顦伴柨顓㈡⒑?2 濠电偞鍨堕幖鈺傜濠靛棭鐒介柛顐犲劜閳?
        assertThat(result.getPermissions()).hasSize(2);
        assertThat(result.getPermissionCodes()).containsExactlyInAnyOrder(
                "inventory:view", "purchase:create"
        );
    }

    @Test
    @DisplayName("case-6")
    void getUserPermissions_NoRoles() {
        // Given: 闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴炃氶弻锕傛煃閳轰礁鏆炵紒鐘冲浮閹嘲鈻庨幇顒傤儎婵?
        Long userId = 4L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of());

        // When: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻鈩冨緞婵犲倹娈诲┑?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担鍝ョ伇闁稿簺鍊曢…鍥醇閵夛腹鎸?
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRoleIds()).isEmpty();
        assertThat(result.getPermissions()).isEmpty();
        assertThat(result.hasPermission("any:permission")).isFalse();

        // Then: 濠电偞鍨堕幐鍝ョ矓鐎垫瓕濮抽柤纰卞墯鐎氭岸鏌曟径娑橆洭闁告瑢鍋撻梺鑽ゅТ濞层垽宕归崫鍕电劷闁割偁鍎查埛?
        verify(rolePermissionRepository, never()).findByRoleIdInWithPermission(any());
    }

    @Test
    @DisplayName("case-7")
    void hasPermission() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋撳顓犳噰闁诡喕绮欐俊鎼佹晝閳ь剟鎮￠弴銏＄厸濠㈣泛锕ら弳锝嗕繆?
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

        // When & Then: 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑濠电偟鍘ч悧鎾愁潖?
        assertThat(permissionService.hasPermission(userId, "inventory:view")).isTrue();
        assertThat(permissionService.hasPermission(userId, "purchase:create")).isFalse();
    }

    @Test
    @DisplayName("case-8")
    void hasAnyPermission() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋撳顓犳噰闁诡喕绮欐俊鎼佹晝閳ь剟鎮￠弴銏＄厸濠㈣泛锕ら弳锝嗕繆?
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

        // When & Then: 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑濠碘€冲级閹倸鐣烽妷鈺傛櫆闁兼亽鍎抽悾铏箾鐎电啸缂侇噮鍨跺畷婊勫閺夋垹楠囬梺鍛婂姦閸犳顢?
        assertThat(permissionService.hasAnyPermission(userId, "inventory:view", "purchase:create")).isTrue();
        assertThat(permissionService.hasAnyPermission(userId, "sales:view", "purchase:create")).isFalse();
    }

    @Test
    @DisplayName("case-9")
    void hasAllPermissions() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋撳顓犳噰闁诡喕绮欐俊鎼佹晝閳ь剟鎮￠弴銏＄厸?2 濠电偞鍨堕幖鈺傜濠靛棭鐒介柛顐犲劜閳?
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

        // When & Then: 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑濠碘€冲级閹倸鐣烽妷鈺傛櫆闁兼亽鍎抽悾鎶芥⒑閸︻収鏆柛瀣崌閺岋繝宕煎┑鎰у┑鐐靛帶閻楁挸顫?
        assertThat(permissionService.hasAllPermissions(userId, "inventory:view", "purchase:create")).isTrue();
        assertThat(permissionService.hasAllPermissions(userId, "inventory:view", "sales:view")).isFalse();
    }

    @Test
    @DisplayName("case-10")
    void getUserPermissions_PermissionTypeClassification() {
        // Given: 闂備焦妞垮鍧楀礉瀹ュ鏄ユ繛鎴欏灩鐎氬顭跨捄渚Ш闁荤喐绻堥弻娑橆潩椤掑倻浼囬柣鐐村嚬閸嬪﹤鐣烽崷顓涘亾閿濆骸澧柣锝変憾閺屸剝寰勬繝鍌涙濠?
        Long userId = 8L;
        when(userRoleRepository.findRoleIdsByUserId(userId))
                .thenReturn(Set.of(1L));
        when(roleInheritRepository.findParentRoleIdsByChildRoleId(any()))
                .thenReturn(Set.of());

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘梻鈧箍鍎遍幊蹇涘磹鏉堚晝纾兼い鎰╁労閸ゆ瑩鏌ｆ惔锝呭幋闁诡垰鍟村畷鐔碱敆娴ｈ櫣袣闂?
        SysPermission menuPermission = SysPermission.builder()
                .id(201L)
                .permissionCode("menu:inventory")
                .permissionName("闂佸湱鍘ч悺銊╁箰閹间焦鍋ら柕濞炬櫆閸ゆ洘绻濇繝鍌氭殭缂佲偓?")
                .permissionType("MENU")
                .status("ACTIVE")
                .sortOrder(1)
                .build();

        SysPermission buttonPermission = SysPermission.builder()
                .id(202L)
                .permissionCode("button:delete")
                .permissionName("删除按钮权限")
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

        // When: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻鈩冨緞婵犲倹娈诲┑?
        UserPermissionDTO result = permissionService.getUserPermissions(userId);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傞獓闂佸憡鍔﹂崰妤咁敁濞嗘挻鐓曢柟鐑樺灦椤ョ娀鎮?
        assertThat(result.getMenuPermissions()).hasSize(1);
        assertThat(result.getApiPermissions()).hasSize(1);
        assertThat(result.getButtonPermissions()).hasSize(1);
        assertThat(result.getPermissions()).hasSize(3);
    }
}
