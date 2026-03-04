package com.wms.system.repository;

import com.wms.system.entity.SystemConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SystemConfigRepository 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * V3.7 闂佸搫顑堥崺鏍倵椤栫偞鏅慨姗€浜堕崗鍥╃磽娴ｅ搫鏋涢柛妯稿€楃槐鏃堫敊缂併垹鎮侀梺?
 *
 * 濠电偞娼欓鍫ユ儊?SystemConfigRepository 闂佹眹鍔岀€氼厽鏅跺澶婂珘濠㈣泛楠稿▓浼存倵鐟欏嫮鐓紒鐘靛枛瀵濡烽…鎴濇畱闂佸搫鍊介～澶屾兜閸洘鏅?
 * 1. findByConfigKey() - 闂佸搫绉烽～澶婄暤娓氣偓閺屽﹤顓奸崶鈺傜€梻浣诡儥閸犳鎮￠敍鍕珰闁靛繈鍊曠敮宕囩磽?
 * 2. existsByConfigKey() - 濠碘槅鍋€閸嬫捇鏌＄仦璇插姦闁告ǜ鍊楃槐鏃堫敋閳ь剟寮銏犲強妞ゆ牗纰嶉崕濠囨倵濞戞顏勶耿?
 *
 * 婵炶揪缍€濞夋洟寮?@DataJpaTest 濠电偛顦崝蹇氼暰闂?
 * - 闂佺厧顨庢禍婊勬叏閳哄懏鐓€鐎广儱娲ㄩ弸?JPA 闂佺儵鏅濋…鍫ュ矗瑜忕槐鎺楀礋椤忓拋鍋?
 * - 濠殿噯绲界换瀣煂濠婂厾鍦偓锝庡幘濡叉悂鏌￠崒婵愭綈缁绢厼鐖奸獮宥堫樁妞ゃ垺鍨垮畷銉︽償閵堝懏顔囬梺鍛婃煟閸斿苯煤閺嵮岀叆婵﹩鍋嗛惃鎴︽煕?
 * - 婵炴垶鎸哥粔瀛樻叏閻愬瓨濮滈柦妯侯槺閺嗘岸鏌℃担鍓插殶婵?Spring 婵炴垶鎸搁敃锝囩箔閸涙潙妫橀柛銉╊棑缁€鍕煙椤戭剙妫楅崢鎾煛閸ャ劍缍戦柕鍫滅矙閺?
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@DisplayName("case-1")
class SystemConfigRepositoryTest {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SystemConfig approvalThresholdConfig;
    private SystemConfig expiryWarningDaysConfig;
    private SystemConfig autoApprovalConfig;

    /**
     * 濠殿噯绲界换瀣煂濠婂厾鍦偓锝庡幘濡叉悂鏌￠崒婵愭綈缁绢厼鐖奸獮宥堫樁妞ゃ垺鍨垮畷婊冾吋閸涱厾浠存繝娈垮枛椤戝懐鈧灚褰冮湁閻庯綆鍘惧Σ鎼佹煛娴ｅ搫顣肩€?
     */
    @BeforeEach
    void setUp() {
        // 闂佸憡甯楃粙鎴犵磽閹惧灈鍋撻崗澶婂⒉濠㈣甯￠弻宀勫箣閿旂粯婢撻梻鍌氬暙閻楀棝鍩€椤掆偓閵堟悂宕㈤妶鍥╃＞?
        approvalThresholdConfig = SystemConfig.builder()
                .configKey("sales.approval.amount_threshold")
                .configValue("50000.00")
                .description("闂備礁绨遍崑鎾绘煕閻戝棗鏋︽い鎾崇秺瀹曪繝寮撮悜鍡楁倎闂佸綊娼х紞濠囧闯閻愵剨绱ｆ繝闈涱儐椤矂鏌?")
                .configType("DECIMAL")
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹惧鈻旈悗娑櫳戦崺鍌毼涢弶鍨伂妞ゆ帞鍠愬鍕煛閸屾稒顔嶉梻浣规緲缁夊爼鎮?
        expiryWarningDaysConfig = SystemConfig.builder()
                .configKey("inventory.warning.days_before_expiry")
                .configValue("90")
                .description("婵炴垶鎸搁悺銊ワ耿閳╁喛绱ｉ柛鏇ㄥ櫘閸斿懎顭块崹娑欐珕闁?")
                .configType("INTEGER")
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹剧粯鍤婃い蹇撳琚熼柣搴″帨閸撴繃绔熼幒鎴殨闁逞屽墴瀹曟骞庨挊澶婅祴缂?
        autoApprovalConfig = SystemConfig.builder()
                .configKey("system.feature.enable_auto_approval")
                .configValue("false")
                .description("闂佸搫瀚烽崹浼村箚娓氣偓瀹曘儵顢涘鍕闂佺厧顨庢禍婊勬叏閳哄啠鍋撻崗澶婂⒉濠?")
                .configType("BOOLEAN")
                .build();

        // 闂佸綊鏅插鎺旂不濞嗘挸绀岄柡宥冨妿閵堟挳鎮归崶銊︾闁哄棛鍠栭獮?
        entityManager.persist(approvalThresholdConfig);
        entityManager.persist(expiryWarningDaysConfig);
        entityManager.persist(autoApprovalConfig);
        entityManager.flush();
    }

    @Test
    @DisplayName("case-2")
    void testFindByConfigKey_Success() {
        // When: 闂佸搫绉烽～澶婄暤娓氣偓閺屽﹤顓奸崶鈺傜€梻浣诡儥閸犳鎮￠敍鍕珰?
        Optional<SystemConfig> found = systemConfigRepository.findByConfigKey("sales.approval.amount_threshold");

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(found).isPresent();
        assertThat(found.get().getConfigKey()).isEqualTo("sales.approval.amount_threshold");
        assertThat(found.get().getConfigValue()).isEqualTo("50000.00");
        assertThat(found.get().getConfigType()).isEqualTo("DECIMAL");
        assertThat(found.get().getDescription()).isEqualTo("闂備礁绨遍崑鎾绘煕閻戝棗鏋︽い鎾崇秺瀹曪繝寮撮悜鍡楁倎闂佸綊娼х紞濠囧闯閻愵剨绱ｆ繝闈涱儐椤矂鏌?");
    }

    @Test
    @DisplayName("case-3")
    void testFindByConfigKey_NotFound() {
        // When: 闂佸搫琚崕鎾敋濡や胶鈻旂€广儱鎳愰幗鐘绘煕閿斿搫濡挎繛鍫熷灴閺屽﹤顓奸崶鈺傜€梻?
        Optional<SystemConfig> found = systemConfigRepository.findByConfigKey("nonexistent.config.key");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧紓?
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("case-4")
    void testFindByConfigKey_IntegerType() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎鏋侀悗娑櫳戝▓鍓佺磼椤愩儺鍤欓柣搴ｅ厴閺屽﹤顓奸崶鈺傜€?
        Optional<SystemConfig> found = systemConfigRepository.findByConfigKey("inventory.warning.days_before_expiry");

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(found).isPresent();
        assertThat(found.get().getConfigValue()).isEqualTo("90");
        assertThat(found.get().getConfigType()).isEqualTo("INTEGER");
    }

    @Test
    @DisplayName("case-5")
    void testFindByConfigKey_BooleanType() {
        // When: 闂佸搫琚崕鎾敋濡ゅ啯鏆滈柛鎰╁妿濮ｆ粎绱掗銉殭闁诲海鍏橀弻濠傤吋閸モ晜鐎?
        Optional<SystemConfig> found = systemConfigRepository.findByConfigKey("system.feature.enable_auto_approval");

        // Then: 婵°倗濮撮惌渚€鎯佹径宀€纾奸柟鎯ь嚟娴?
        assertThat(found).isPresent();
        assertThat(found.get().getConfigValue()).isEqualTo("false");
        assertThat(found.get().getConfigType()).isEqualTo("BOOLEAN");
    }

    @Test
    @DisplayName("case-6")
    void testExistsByConfigKey_Exists() {
        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姎闁告埊绱曢埀顒佺⊕閿氭繝鈧鍫熷剭闁告洦鍨扮敮宕囩磽閸愭儳娅嶉柡?
        boolean exists = systemConfigRepository.existsByConfigKey("sales.approval.amount_threshold");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?true
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("case-7")
    void testExistsByConfigKey_NotExists() {
        // When: 濠碘槅鍋€閸嬫捇鏌＄仦璇插姍缂佹顦遍埀顒佺⊕閿氭繝鈧鍫熷剭闁告洦鍨扮敮宕囩磽閸愭儳娅嶉柡?
        boolean exists = systemConfigRepository.existsByConfigKey("nonexistent.config.key");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬顓熶氦闁哄倹瀵х粈鈧?false
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("case-8")
    void testSaveNewConfig() {
        // Given: 闂佸憡甯楃粙鎴犵磽閹捐妫樺ù鍏兼綑鐢磭绱?
        SystemConfig newConfig = SystemConfig.builder()
                .configKey("system.feature.enable_notifications")
                .configValue("true")
                .description("闂佸搫瀚烽崹浼村箚娓氣偓瀹曘儵顢涘鍕闂備緡鍋呭銊╂偂閿熺姴绀夐柣鏃囶嚙閸?")
                .configType("BOOLEAN")
                .build();

        // When: 婵烇絽娲︾换鍌炴偤閵娾晜鐓€鐎广儱娲ㄩ弸?
        SystemConfig saved = systemConfigRepository.save(newConfig);

        // Then: 婵°倗濮撮惌渚€鎯佹径瀣攳婵犻潧妫涢幗鐘绘煙鐎涙ê濮囧┑?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂備焦褰冪粔鐢稿蓟婵犲洤钃熼柕澶樼厛閸?
        Optional<SystemConfig> found = systemConfigRepository.findByConfigKey("system.feature.enable_notifications");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬鑸靛殑闁兼亽鍎遍ˉ婵嬫煕?
        assertThat(found).isPresent();
        assertThat(found.get().getConfigValue()).isEqualTo("true");
    }

    @Test
    @DisplayName("case-9")
    void testUpdateConfigValue() {
        // Given: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋濋柣妤€鐗婄粻鎺楁⒑閺夎法肖闁?
        SystemConfig config = systemConfigRepository.findByConfigKey("sales.approval.amount_threshold").orElseThrow();

        // When: 婵烇絽娴傞崰妤呭极婵傚憡鐓€鐎广儱娲ㄩ弸鍌炴煕?
        config.setConfigValue("80000.00");
        config.setDescription("闂佸搫娲ら悺銊╁蓟婵犲洤瑙﹂幖杈剧稻閻ｉ亶鎮楅崗澶婂⒉濠㈣甯￠弻宀勫箣閿旂粯婢撻梻鍌氬暙閻楀棝鍩€?");
        systemConfigRepository.save(config);

        // 濠电偞鎸搁幊妯衡枍鎼淬劌绠板ù锝夘棑閻ｄ粙鏌涢弽銊уⅶ缂佹鍊圭粙澶屸偓锝庡亝閻庮噣鏌ㄥ☉妯垮鐎规悂浜跺畷姘跺醇閺囩偞顏熼梺鍝勫€绘晶妤呮偂閿涘嫭瀚?
        entityManager.flush();
        entityManager.clear();

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰嵆閻庢稒蓱閻撯偓闂佺懓鐡ㄩ崝鏇熸叏?
        SystemConfig reloaded = systemConfigRepository.findByConfigKey("sales.approval.amount_threshold").orElseThrow();
        assertThat(reloaded.getConfigValue()).isEqualTo("80000.00");
        assertThat(reloaded.getDescription()).isEqualTo("闂佸搫娲ら悺銊╁蓟婵犲洤瑙﹂幖杈剧稻閻ｉ亶鎮楅崗澶婂⒉濠㈣甯￠弻宀勫箣閿旂粯婢撻梻鍌氬暙閻楀棝鍩€?");
    }

    @Test
    @DisplayName("case-10")
    void testDeleteConfig() {
        // Given: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋濋柣妤€鐗婄粻鎺楁⒑閺夎法肖闁?
        SystemConfig config = systemConfigRepository.findByConfigKey("system.feature.enable_auto_approval").orElseThrow();
        Long configId = config.getId();

        // When: 闂佸憡甯炴繛鈧繛鍛叄閺屽﹤顓奸崶鈺傜€?
        systemConfigRepository.delete(config);
        entityManager.flush();

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰闁绘绮悵鐔兼煙鐎涙ê濮囧┑?
        Optional<SystemConfig> found = systemConfigRepository.findById(configId);
        assertThat(found).isEmpty();

        // When: 闂佸憡鍔曠粔鐢割敃閼测晙鐒婃繝闈涳功濡叉悂鏌＄仦璇插姤妞?
        Optional<SystemConfig> foundByKey = systemConfigRepository.findByConfigKey("system.feature.enable_auto_approval");

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺鸿摕闁靛鐓堥崵鍕槈閹惧磭孝闁?
        assertThat(foundByKey).isEmpty();
    }

    @Test
    @DisplayName("case-11")
    void testCountConfigs() {
        // When: 缂傚倷鑳堕崰鏇㈩敇閹间礁绠ラ柍褜鍓熷鍨緞閹邦剙璧嬬紓?
        long count = systemConfigRepository.count();

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺哄珘?3 婵炴垶鎼╂禍顏堝储閵堝洨纾?
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("case-12")
    void testFindAll() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞閹邦剙璧嬬紓?
        List<SystemConfig> allConfigs = systemConfigRepository.findAll();

        // Then: 闁圭厧鐡ㄥΛ渚€顢氬璺哄珘?3 婵炴垶鎼╂禍顏堝储閵堝洨纾?
        assertThat(allConfigs).hasSize(3);
        assertThat(allConfigs)
                .extracting(SystemConfig::getConfigKey)
                .containsExactlyInAnyOrder(
                        "sales.approval.amount_threshold",
                        "inventory.warning.days_before_expiry",
                        "system.feature.enable_auto_approval"
                );
    }

    @Test
    @DisplayName("case-13")
    void testSaveAll() {
        // Given: 闂佸憡甯楃粙鎴犵磽閹炬潙绶炴慨姗嗗亰閸ゅ鏌￠崒娆忓祮闁告ǜ鍊楃槐?
        SystemConfig config1 = SystemConfig.builder()
                .configKey("batch.config.1")
                .configValue("value1")
                .description("闂佸綊娼х紞濠囧闯濞差亝鐓€鐎广儱娲ㄩ弸?")
                .configType("STRING")
                .build();

        SystemConfig config2 = SystemConfig.builder()
                .configKey("batch.config.2")
                .configValue("value2")
                .description("闂佸綊娼х紞濠囧闯濞差亝鐓€鐎广儱娲ㄩ弸?")
                .configType("STRING")
                .build();

        // When: 闂佸綊娼х紞濠囧闯閻戞鈹嶆繝闈涙閹?
        List<SystemConfig> saved = systemConfigRepository.saveAll(List.of(config1, config2));

        // Then: 婵°倗濮撮惌渚€鎯佹径瀣攳婵犻潧妫涢幗鐘绘煙鐎涙ê濮囧┑?
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(c -> c.getId() != null);

        // When: 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞閹邦剙璧嬬紓?
        List<SystemConfig> allConfigs = systemConfigRepository.findAll();

        // Then: 闂佽鍓涚划顖炲汲閻斿憡鍎熼柡鍐ㄦ祩閸ゅ鏌?5 婵?
        assertThat(allConfigs).hasSize(5);
    }
}
