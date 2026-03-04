package com.wms.system.repository;

import com.wms.system.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRepository 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 濠电偞娼欓鍫ユ儊?UserRepository 闂佹眹鍔岀€氼厽鏅跺澶婂珘濠㈣泛楠稿▓浼存倵鐟欏嫮鐓紒鐘靛枛瀵濡烽…鎴濇畱闂佸搫鍊介～澶屾兜?
 *
 * 婵炶揪缍€濞夋洟寮?@DataJpaTest 濠电偛顦崝蹇氼暰闂?
 * - 闂佺厧顨庢禍婊勬叏閳哄懏鐓€鐎广儱娲ㄩ弸?JPA 闂佺儵鏅濋…鍫ュ矗瑜忕槐鎺楀礋椤忓拋鍋?
 * - 濠殿噯绲界换瀣煂濠婂厾鍦偓锝庡幘濡叉悂鏌￠崒婵愭綈缁绢厼鐖奸獮宥堫樁妞ゃ垺鍨垮畷銉︽償閵堝懏顔囬梺鍛婃煟閸斿苯煤閺嵮岀叆婵﹩鍋嗛惃鎴︽煕?
 * - 婵炴垶鎸哥粔瀛樻叏閻愬瓨濮滈柦妯侯槺閺嗘岸鏌℃担鍓插殶婵?Spring 婵炴垶鎸搁敃锝囩箔閸涙潙妫橀柛銉╊棑缁€鍕煙椤戭剙妫楅崢鎾煛閸ャ劍缍戦柕鍫滅矙閺?
 *
 * 婵炶揪缍€濞夋洟寮?@AutoConfigureTestDatabase(replace = NONE)闂?
 * - 婵炴垶鎸哥粔铏箾閸ヮ剚鍋ㄩ柕濞垮劤閵堢敻鏌涜箛瀣姎缂佹唻绻濆顐︽偋閸繄銈﹂柟鐓庣摠閹碱偆妲愬?闂?
 * - 婵炶揪缍€濞夋洟寮妶澶嬬厐鐎广儱娲ㄩ弸鍌炴煛閸屾碍鐭楁繛鍡愬灪缁嬪顢橀悩宕囨殸 PostgreSQL 闂佽桨鑳舵晶妤€鐣垫担瑙勫劅?
 *
 * 婵炶揪缍€濞夋洟寮?@ActiveProfiles("test")闂?
 * - 婵炶揪缍€濞夋洟寮?application-test.yml 闂備焦婢樼粔鍫曟偪閸℃稑妫橀柛銉檮椤?
 *
 * 濠电偛顦崝宥夊礈娴煎瓨鏅慨?.3 婵犮垼鍩栨穱娲綖濡ゅ懏鍤岄柤纰卞墴閸忓洨绱撴担鍝勬瀺闁煎灚鍨块弫宥呯暆閳ь剟寮妶澶婄闁汇値鍨煎锟犳煠鐟欏嫬绲婚柣掳鍔戝畷鎺楀Ω閵夛箒鍚?sys_user_role 闁荤偞渚楅悡澶屾?
 *      User 闁诲骸婀遍崑妯肩礊鐎ｎ偆鈻旂€广儱鎳庨弲娆撴煕閺嵮勫櫣闁?role 闁诲孩绋掗〃鍡涱敊瀹€鍕ラ柛灞剧箥濞硷繝鏌ょ涵鍛毢婵炴潙顦靛畷妤呮惞椤愩倕寮ㄩ柣鐘叉储閸ㄨ棄銆掗懜鍨閻犳亽鍔嶉弳?UserRoleService
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 3.3 (Updated for multi-role system)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("case-1")
class UserRepositoryTest {

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鐓€鐎广儱娲ㄩ弸?
     * 闂佸湱绮崝鎺旀?PasswordEncoder bean闂佹寧绋戦懟顖毭哄鍕枖?@DataJpaTest 婵炴垶鎸哥粔宕囨娴兼潙绀夐柣妯煎劋缁?Security 闂備焦婢樼粔鍫曟偪?
     * 闂佸憡鑹鹃張顒€顪冮崒婊勫暫闁糕剝鐟︾壕?initAdminUser闂佹寧绋戦惌鍌毼ｇ拠宸桨闁靛骏绲藉▓浼存煕閺傝濡奸柛銊ュ船椤曟瑩鎼圭拠鈥虫倎闂佽崵鍋涘Λ妤呭箟閹惰姤鍋ㄩ柕濠忕畱閻撴洘顨ラ悙鎻掔骇濠㈢懓鐗嗛湁閻庯綆鍘惧Σ?
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        /**
         * 闁荤喐娲栧Λ娑樏?WmsSystemApplication 婵炴垶鎼╅崢鎯р枔?initAdminUser bean
         * 闂侀潻璐熼崝宥囩矈鐎靛憡瀚氶柡鍥ㄤ亢閸橆剙鈽夐幘宕囆ｅ褏鏅幃鎵沪閸忕厧搴婃繛杈剧稻濞叉﹢骞栭柨瀣婵犲﹥鍔楃粈澶愭⒑椤掆偓閻忔繈宕㈤妶澶嬪殜妞ゅ繐瀚闂佸憡甯楃粙鎴犵磽閹惧墎涓嶉柨娑樺閸婄偤鏌涘☉娆樼劷闁轰降鍊濋獮?
         */
        @Bean
        public CommandLineRunner initAdminUser() {
            return args -> {
                // 濠电偞娼欓鍫ユ儊椤栨稓鈻旀い鎾寸箘閻熸繈鏌涢幒鎾垛槈缂傚倹鎸剧划濠氭晬閸曨剙鈧偤鏌涘☉娆樼劷闁轰降鍊濋獮?
            };
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testAdmin;
    private User testStaff;
    private User testDisabledUser;

    /**
     * 濠殿噯绲界换瀣煂濠婂厾鍦偓锝庡幘濡叉悂鏌￠崒婵愭綈缁绢厼鐖奸獮宥堫樁妞ゃ垺鍨垮畷婊冾吋閸涱厾浠存繝娈垮枛椤戝懐鈧灚褰冮湁閻庯綆鍘惧Σ鎼佹煛娴ｅ搫顣肩€?
     */
    @BeforeEach
    void setUp() {
        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡介柛?- 缂備胶濯寸槐鏇㈠箖婵犲洤宸?
        testAdmin = User.builder()
                .username("admin_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") // BCrypt hash
                .displayName("濠电偞娼欓鍫ユ儊椤栨粎涓嶉柨娑樺閸婄偤鏌?")
                .enabled(true)
                .remark("闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡挎い鎾存倐閹爼宕卞Δ浣告瀫闁荤姵鍔х粻鎴ｃ亹?")
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡介柛?- 闂佸搫鎷嬮崳锝夊焵椤掍浇澹橀柟鎻掑暱椤?
        testStaff = User.builder()
                .username("staff_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("濠电偞娼欓鍫ユ儊椤栫偛宸濇俊顖濇〃缁?")
                .enabled(true)
                .remark("闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡奸柟鎻掑暱椤斿繘濡烽妸銉П闂?")
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡介柛?- 缂備礁鍊烽懗鍫曞极閵堝洦瀚婚柨鏇楀亾鐟?
        testDisabledUser = User.builder()
                .username("disabled_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("缂備礁鍊烽懗鍫曞极閵堝悿鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡介柛?")
                .enabled(false)
                .remark("闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡挎い鈺嬬畵閹粙濡搁妸銉П闂?")
                .build();

        // 闂佸綊鏅插鎺旂不濞嗘挸绀岄柡宥冨妿閵堟挳鎮归崶銊︾闁哄棛鍠栭獮?
        entityManager.persist(testAdmin);
        entityManager.persist(testStaff);
        entityManager.persist(testDisabledUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("case-2")
    void findByUsername_Success() {
        // When: 闂佸搫绉烽～澶婄暤娓氣偓閹粙濡搁敃鈧悡鏇㈡煕濮橆剛校闁绘搫绱曢幏?
        Optional<User> found = userRepository.findByUsername("admin_test");

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("admin_test");
        assertThat(found.get().getDisplayName()).isEqualTo("濠电偞娼欓鍫ユ儊椤栨粎涓嶉柨娑樺閸婄偤鏌?");

        // Note: In v3.3, user roles are stored in sys_user_role table
        // and queried via UserRoleService
    }

    @Test
    @DisplayName("case-3")
    void findByUsername_NotFound() {
        // When: 闂佸搫琚崕鎾敋濡や胶鈻旂€广儱鎳愰幗鐘绘煕閿斿搫濡挎繛鍫熷灴閹粙濡搁敃鈧悡鏇㈡煕?
        Optional<User> found = userRepository.findByUsername("nonexistent_user");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧紓?
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("case-4")
    void existsByUsername_Exists() {
        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姎闁告埊绱曢埀顒佺⊕閿氭繝鈧鍫熷剭闁告洦鍘介弳蹇涙煙閺夋垵妲婚柟?
        boolean exists = userRepository.existsByUsername("admin_test");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?true
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("case-5")
    void existsByUsername_NotExists() {
        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姍缂佹顦遍埀顒佺⊕閿氭繝鈧鍫熷剭闁告洦鍘介弳蹇涙煙閺夋垵妲婚柟?
        boolean exists = userRepository.existsByUsername("nonexistent_user");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?false
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("case-6")
    void findByEnabledTrue() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞婵犲啫鍓婚梺娲绘娇閸斿骸鈻撻幋锔藉仺闁靛绠戦悡?
        List<User> enabledUsers = userRepository.findByEnabledTrue();

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺哄珘?2 婵炴垶鎼╂禍婊堝箚鎼淬劍鍋ㄩ柕濞у嫮鏆犻梺娲绘娇閸斿秹宕?
        assertThat(enabledUsers).hasSize(2);
        assertThat(enabledUsers)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("admin_test", "staff_test");
    }

    @Test
    @DisplayName("case-7")
    void findByUsernameContaining() {
        // When: 濠碘槅鍨界槐鏇犳兜閿曞倸钃熼柕澶樼厛閸ゅ嫰鏌涢弽褎鍣归柟?"test" 闂佹眹鍔岀€氼噣寮妶澶婄鐎瑰嫮澧楅崐?
        List<User> users = userRepository.findByUsernameContaining("test");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺虹闁诡垎鍐帓闂佸湱顣介崑鎾绘煛閸繍妲圭紒浣割嚟閹风娀寮撮悩铏闂?
        assertThat(users).hasSize(3);
        assertThat(users)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("admin_test", "staff_test", "disabled_test");

        // When: 濠碘槅鍨界槐鏇犳兜閿曞倸钃熼柕澶樼厛閸ゅ嫰鏌涢弽褎鍣归柟?"admin" 闂佹眹鍔岀€氼噣寮妶澶婄鐎瑰嫮澧楅崐?
        List<User> admins = userRepository.findByUsernameContaining("admin");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺虹煑妞ゅ繐鎳庨ˉ婵嬫煕閹烘挸顥嬫い鎾存倐閹爼宕卞Δ浣告瀫
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getUsername()).isEqualTo("admin_test");
    }

    @Test
    @DisplayName("case-8")
    void saveNewUser() {
        // Given: 闂佸憡甯楃粙鎴犵磽閹捐妫橀柟娈垮枟閺嗗繘鏌?
        User newUser = User.builder()
                .username("new_user_test")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .displayName("闂佸搫鍊规竟鍡涘极閵堝绠?")
                .enabled(true)
                .build();

        // When: 婵烇絽娲︾换鍌炴偤閵娾晜鍋ㄩ柕濠忕畱閻?
        User saved = userRepository.save(newUser);

        // Then: 婵°倗濮撮惌渚€鎯佹径瀣攳婵犻潧妫涢幗鐘绘煙鐎涙ê濮囧┑?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂備焦褰冪粔鐢稿蓟婵犲洤钃熼柕澶樼厛閸?
        Optional<User> found = userRepository.findByUsername("new_user_test");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬鑸靛殑闁兼亽鍎遍ˉ婵嬫煕?
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("new_user_test");
    }

    @Test
    @DisplayName("case-9")
    void updateUser() {
        // Given: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋濋柣妤€鐗婄粻鎺楁煟椤剙濡介柛?
        User user = userRepository.findByUsername("staff_test").orElseThrow();
        String originalUpdatedAt = user.getUpdatedAt().toString();

        // When: 婵烇絽娴傞崰妤呭极婵傚憡鍋ㄩ柕濠忕畱閻撴洖菐閸ワ絽澧插ù?
        user.setDisplayName("闂佸搫娲ら悺銊╁蓟婵犲洤瑙﹂幖杈剧稻閻ｉ亶鏌涘顒傂ょ悮?");
        user.setRemark("閻庡湱顭堝璺好洪崸妤€妫?");
        User updated = userRepository.save(user);

        // 濠电偞鎸搁幊妯衡枍鎼淬劌绠板ù锝夘棑閻ｄ粙鏌涢弽銊уⅶ缂佹鍊圭粙澶屸偓锝庡亝閻庮噣鏌ㄥ☉妯垮鐎规悂浜跺畷姘跺醇閺囩偞顏熼梺鍝勫€绘晶妤呮偂閿涘嫭瀚?
        entityManager.flush();
        entityManager.clear();

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰嵆閻庢稒蓱閻撯偓闂佺懓鐡ㄩ崝鏇熸叏?
        User reloaded = userRepository.findByUsername("staff_test").orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("闂佸搫娲ら悺銊╁蓟婵犲洤瑙﹂幖杈剧稻閻ｉ亶鏌涘顒傂ょ悮?");
        assertThat(reloaded.getRemark()).isEqualTo("閻庡湱顭堝璺好洪崸妤€妫?");
        // 濠电偛顦崝宥夊礈? updatedAt 闁圭厧鐡ㄥΛ渚€顢氬杈ㄥ仏妞ゆ劑鍎卞▓浼存煕閺傝濡芥繛鎻掓健瀵剛鏁鍓ь槱JPA Auditing闂?
    }

    @Test
    @DisplayName("case-10")
    void deleteUser() {
        // Given: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋濋柣妤€鐗婄粻鎺楁煟椤剙濡介柛?
        User user = userRepository.findByUsername("staff_test").orElseThrow();
        Long userId = user.getId();

        // When: 闂佸憡甯炴繛鈧繛鍛叄閹粙濡搁敃鈧悡?
        userRepository.delete(user);
        entityManager.flush();

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰闁绘绮悵鐔兼煙鐎涙ê濮囧┑?
        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isEmpty();

        // When: 闂佸憡鍔曠粔鐢割敃閼测晙鐒婃繝闈涳功濡叉悂鏌＄仦璇插姤妞?
        Optional<User> foundByUsername = userRepository.findByUsername("staff_test");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺鸿摕闁靛鐓堥崵鍕槈閹惧磭孝闁?
        assertThat(foundByUsername).isEmpty();
    }

    @Test
    @DisplayName("case-11")
    void countUsers() {
        // When: 缂傚倷鑳堕崰鏇㈩敇閹间礁绠ラ柍褜鍓熷鍨緞鐏炵偓娈㈤梺?
        long count = userRepository.count();

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺哄珘?3 婵炴垶鎼╂禍鐐哄极閵堝绠?
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("case-12")
    void findAll() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞鐏炵偓娈㈤梺?
        List<User> allUsers = userRepository.findAll();

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺哄珘?3 婵炴垶鎼╂禍鐐哄极閵堝绠?
        assertThat(allUsers).hasSize(3);
        assertThat(allUsers)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("admin_test", "staff_test", "disabled_test");
    }

    @Test
    @DisplayName("case-13")
    void saveAll() {
        // Given: 闂佸憡甯楃粙鎴犵磽閹炬潙绶炴慨姗嗗亰閸ゅ鏌￠崒娑橆棆闁轰降鍊濋獮?
        User user1 = User.builder()
                .username("batch_user_1")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(true)
                .build();

        User user2 = User.builder()
                .username("batch_user_2")
                .password("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .enabled(true)
                .build();

        // When: 闂佸綊娼х紞濠囧闯閻戞鈹嶆繝闈涙閹?
        List<User> saved = userRepository.saveAll(List.of(user1, user2));

        // Then: 婵°倗濮撮惌渚€鎯佹径瀣攳婵犻潧妫涢幗鐘绘煙鐎涙ê濮囧┑?
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(u -> u.getId() != null);

        // When: 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞鐏炵偓娈㈤梺?
        List<User> allUsers = userRepository.findAll();

        // Then: 闂佽鍓涚划顖炲汲閻斿憡鍎熼柡鍐ㄦ祩閸ゅ鏌?5 婵?
        assertThat(allUsers).hasSize(5);
    }
}
