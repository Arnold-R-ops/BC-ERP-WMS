package com.wms.system.security;

import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CustomUserDetailsService 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼幃褰掑炊閻戣姤顎嶉梺绋块缁绘ê鐣峰┑瀣亹闁绘垶顭囬妶浼存⒑閸濆嫮澧曠紒澶婄摠閹便劑鎮㈤崗鐓庡壄闂佸憡娲﹂崜娑欑珶鐎ｎ喚鍙撻柛銉戝啯娈繝娈垮枟閻╊垶骞?
 *
 * 濠电偠鎻紞鈧繛澶嬫礋瀵?Mockito 闂佸搫顦弲婊呯矙閺嶎厹鈧線骞嬮敃鈧涵鈧梺鍝勬川閸嬫稓鏁Δ浣虹闁哄鍩堥崕鎾绘煟閿旇鐏﹂柡?
 * - @ExtendWith(MockitoExtension.class): 闂備礁鎲￠崙褰掑垂閹惰棄鏋?Mockito 闂備礁婀遍。浠嬪疾濞戙垺鍎?
 * - @Mock: 婵犵妲呴崹顏堝礈濠靛牃鍋撳顓犳噭缂佹鍠庨埞鎴炵節閸曞灚鑸归梻?Repository
 * - @InjectMocks: 闂備胶鍘ч〃搴㈢濠婂嫭鍙忛柍杞拌閺嬫牠鏌曟繛鍨姎鐎?Mock 闂佽娴烽弫鎼併€佹繝鍥ㄥ瘶闁告洦鍨扮粈鍡涙煙瀹勬壆鐒炬繛鑹板煐缁绘盯寮堕幋顓炲壍闂佷紮瀵岄崹鎶芥偖?
 *
 * 婵犵數鍋涢ˇ顓㈠礉瀹ュ绀堝ù鐓庣摠閺咁剚鎱?.3 濠电姰鍨奸崺鏍ㄧ┍濞差亷缍栨俊銈呮噺閸ゅ矂鏌ょ喊鍗炲⒋闁稿繐娲ㄧ槐鎾存媴閸濆嫭鐎洪梺鐓庣仛閸ㄥ潡寮鍛殕闁逞屽墴瀵偊濡舵径濠勵吅闂備礁鐏濋鍡欑礊閳ь剟姊婚崒姘仾闁告梹甯為崚?JWT Token 闂備礁鎲￠弻锝夊礉瀹ュ鐒垫い鎴ｆ硶椤︼箑鈹戦鎯т沪婵炵⒈浜顒勫Χ閸モ晩妲?
 *      User 闂佽楠稿﹢閬嶅磻濡偐绀婇悗锝庡亞閳绘梻鈧箍鍎遍幊搴ㄥ疾濞嗘挻鐓曢柡宓嫬娅ｉ梺?role 闂佽瀛╃粙鎺椼€冮崱娑辨晩鐎光偓閸曨偅銇?getAuthorities() 闂佽楠稿﹢閬嶅磻閻旇偐宓?
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 3.3 (Updated for multi-role system)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锝夛綖椤掆偓婵′粙鏌?
        testUser = User.builder()
                .id(1L)
                .username("test_user")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("Test User")
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("case-2")
    void loadUserByUsername_Success() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍娲冀椤撶喎浠洪梺闈涱煭缁犳垿鎮?
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 闂佽崵濮撮鍛村疮娴兼潙鏋?loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("test_user");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偂姘﹂梺鍝勫€圭€笛呯矆閳ь剟姊哄Ч鍥у閻庢凹鍘煎嵄?SecurityUser 闂備礁鎲￠悧鏇㈠箠閹惧嚢澶嬬節閸曨厼鏆?
        assertThat(userDetails).isNotNull();
        assertThat(userDetails).isInstanceOf(SecurityUser.class);

        SecurityUser securityUser = (SecurityUser) userDetails;
        assertThat(securityUser.getUsername()).isEqualTo("test_user");
        assertThat(securityUser.getPassword()).isEqualTo(testUser.getPassword());
        assertThat(securityUser.getId()).isEqualTo(1L);
        assertThat(securityUser.isEnabled()).isTrue();

        // Note: In v3.3, authorities are loaded from JWT Token, not from User entity
        // The getAuthorities() method in User now returns an empty list

        // Then: 濠德板€楁慨鎾儗娓氣偓閹?Repository 闂佽崵鍋為崙褰掑磻婢舵劖鍎嶉柣鏂垮悑閸嬨劑鏌曟繛鍨偓妤咁敂鏉堛劎绠?
        verify(userRepository, times(1)).findByUsername("test_user");
    }

    @Test
    @DisplayName("case-3")
    void loadUserByUsername_UserNotFound() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍娲冀椤愩倗鐓?
        when(userRepository.findByUsername(anyString()))
                .thenReturn(Optional.empty());

        // When & Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顔呭┑鐐村灦閿氭慨?UsernameNotFoundException
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Invalid username or password");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹?Repository 闂佽崵鍋為崙褰掑磻婢舵劖鍎嶉柣鏂垮悑閸?
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("case-4")
    void loadUserByUsername_DisabledAccount() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯у閻霉閿濆洤鍔嬮柡鍡楃箻閺岋綁濡搁妷銉紑闂佽桨闄嶉崐婵嬬嵁?
        User disabledUser = User.builder()
                .id(2L)
                .username("disabled_user")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(false)
                .build();

        when(userRepository.findByUsername("disabled_user"))
                .thenReturn(Optional.of(disabledUser));

        // When: 闂佽崵濮撮鍛村疮娴兼潙鏋?loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("disabled_user");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剟姊哄ú缁樺▏闁告柨绉瑰畷鍝勨槈閵忊剝娅栭柣蹇曞仩閸嬫劗绮?isEnabled() 濠?false
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isFalse();

        // 婵犵數鍋涢ˇ顓㈠礉瀹ュ绀堝ù鐓庣摠閺咁剚鎱ㄥ┑鍡欏醇ring Security 濠电偞娼欓崥瀣嚌閹嶈€挎い蹇撴婵ジ鏌曢崼婵堝ⅱ婵℃彃鐗撳鍫曞煛娴ｇ懓绐涢梺瀹狀嚙閸氬绮欐径鎰垫晣闁绘洑鐒﹂埛鏇㈡⒑?isEnabled()闂?
        // 濠电姷顣介埀顒€鍟块埀顒€缍婇幃妯诲緞鐎ｎ兘鏋?false 濠电偞娼欓崥瀣枈瀹ュ棙鍙忛煫鍥ㄧ☉缁€?DisabledException闂備焦瀵х粙鎴炵附閺冨倸绶?loadUserByUsername 闂備礁鎼悧婊堝礈濠靛顥婇柍鍝勫€婚埢鏃傗偓骞垮劚鐎氼喚绮ｅΔ鍛厵闁煎摜鏁搁埥澶岀磼鏉堛劍灏甸柡?
    }

    @Test
    @DisplayName("case-5")
    void loadUserByUsernameWithException_Success() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍娲冀椤撶喎浠洪梺闈涱煭缁犳垿鎮?
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 闂佽崵濮撮鍛村疮娴兼潙鏋?loadUserByUsernameWithException
        User user = userDetailsService.loadUserByUsernameWithException("test_user");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偂姘﹂梺鍝勫€圭€笛呯矆閳ь剟姊哄Ч鍥у閻庢凹鍘煎嵄?User 闂佽楠稿﹢閬嶅磻濡偐绀?
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("test_user");
        assertThat(user.getId()).isEqualTo(1L);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹?Repository 闂佽崵鍋為崙褰掑磻婢舵劖鍎嶉柣鏂垮悑閸嬨劑鏌曟繛鍨偓妤咁敂鏉堛劎绠?
        verify(userRepository, times(1)).findByUsername("test_user");
    }

    @Test
    @DisplayName("case-6")
    void loadUserByUsernameWithException_UserNotFound() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍娲冀椤愩倗鐓?
        when(userRepository.findByUsername(anyString()))
                .thenReturn(Optional.empty());

        // When & Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顔呭┑鐐村灦閿氭慨?BusinessException
        assertThatThrownBy(() -> userDetailsService.loadUserByUsernameWithException("nonexistent"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "AUTH_INVALID_CREDENTIALS");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹?Repository 闂佽崵鍋為崙褰掑磻婢舵劖鍎嶉柣鏂垮悑閸?
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("case-7")
    void existsByUsername_Exists() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍?true
        when(userRepository.existsByUsername("test_user"))
                .thenReturn(true);

        // When: 闂佽崵濮撮鍛村疮娴兼潙鏋?existsByUsername
        boolean exists = userDetailsService.existsByUsername("test_user");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳?true
        assertThat(exists).isTrue();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹?Repository 闂佽崵鍋為崙褰掑磻婢舵劖鍎嶉柣鏂垮悑閸嬨劑鏌曟繛鍨偓妤咁敂鏉堛劎绠?
        verify(userRepository, times(1)).existsByUsername("test_user");
    }

    @Test
    @DisplayName("case-8")
    void existsByUsername_NotExists() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍?false
        when(userRepository.existsByUsername(anyString()))
                .thenReturn(false);

        // When: 闂佽崵濮撮鍛村疮娴兼潙鏋?existsByUsername
        boolean exists = userDetailsService.existsByUsername("nonexistent");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳?false
        assertThat(exists).isFalse();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹?Repository 闂佽崵鍋為崙褰掑磻婢舵劖鍎嶉柣鏂垮悑閸嬨劑鏌曟繛鍨偓妤咁敂鏉堛劎绠?
        verify(userRepository, times(1)).existsByUsername("nonexistent");
    }

    @Test
    @DisplayName("case-9")
    void loadUserByUsername_MultiRoleSystem() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锝夛綖椤掆偓婵′粙鏌涢埡鍌滄创闁轰礁绉瑰畷?.3 濠电偞鍨堕幐鍝ョ矓閹绢喖鐤柍褜鍓熼弻?role 闂佽瀛╃粙鎺椼€冮崱娑辨晩鐎光偓閸曨剚娅?
        User staffUser = User.builder()
                .id(3L)
                .username("staff_user")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(true)
                .build();

        when(userRepository.findByUsername("staff_user"))
                .thenReturn(Optional.of(staffUser));

        // When: 闂佽崵濮撮鍛村疮娴兼潙鏋?loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("staff_user");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顓炰缓闂侀潧顭粻鎴︽偂閺囥垺鐓曢柍鍝勫暙閸斿绻濋埀顒勬偋閸懇鏀抽梺鏂ユ櫅閸熲晝妲愰弽顓熺叆婵炴垶顭囨晶锝嗐亜椤愶綆娈滈柟铏箞瀹曪絾寰勭€ｎ剛袣闂傚倸鍊哥€氼參宕濋弴鐑囪€?v3.3 濠?JWT Token 闂備礁鎲″缁樻叏閹灐褰掑炊椤掍焦娅?
        SecurityUser securityUser = (SecurityUser) userDetails;
        assertThat(securityUser.getUsername()).isEqualTo("staff_user");
        assertThat(securityUser.getId()).isEqualTo(3L);
        assertThat(securityUser.isEnabled()).isTrue();

        // Note: In v3.3, user roles are stored in sys_user_role table
        // and loaded into JWT token during authentication
    }

    @Test
    @DisplayName("case-10")
    void verifySecurityUserAccountStatus() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍娲冀椤撶喎浠洪梺闈涱煭缁犳垿鎮?
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 闂佽崵濮撮鍛村疮娴兼潙鏋?loadUserByUsername
        UserDetails userDetails = userDetailsService.loadUserByUsername("test_user");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭仦鎯ь伕闂佹寧妫佸Λ鍕偂閺囥垺鐓熸俊顖氱仢閻撴劙鏌?
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("case-11")
    void loadSameUserMultipleTimes() {
        // Given: 婵犵妲呴崹顏堝礈濠靛牃鍋?Repository 闂佸搫顦弲婊堝蓟閵娿儍娲冀椤撶喎浠洪梺闈涱煭缁犳垿鎮?
        when(userRepository.findByUsername("test_user"))
                .thenReturn(Optional.of(testUser));

        // When: 濠电姰鍨奸崺鏍枈瀹ュ鏁冨┑鍌滎焾缁€澶愭煟濡厧鍔嬬紒浣峰嵆閺屾稑顫濋鈧〃娆戠磼閺冣偓閹告娊骞冩禒瀣╅柨鏇楀亾闁?
        userDetailsService.loadUserByUsername("test_user");
        userDetailsService.loadUserByUsername("test_user");
        userDetailsService.loadUserByUsername("test_user");

        // Then: Repository 闂佸湱鍘ч悺銊ノ涙笟鈧敐鐐烘晜閻愵剙顏╅梺鍛婂姦娴滄繈寮?3 婵犵數鍋涘Λ灞轿ｉ崟顓燁潟婵炲棙鎸搁悙濠囨煟濡法绨跨紒鈧?Service 闂佽绻掗崑娑㈠磹瑜版帗鍎夐柛娑欐綑鐎氬顭跨捄铏圭伇濠㈣泛瀚伴幃妤€鈽夊▎娆忓彆缂?
        verify(userRepository, times(3)).findByUsername("test_user");
    }
}
