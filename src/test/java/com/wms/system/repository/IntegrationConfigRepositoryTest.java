package com.wms.system.repository;

import com.wms.system.entity.IntegrationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntegrationConfigRepository 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * V3.9 闂佸搫顑堥崺鏍倵椤栫偞鏅慨婵嗙崱opify 闂傚倸妫楀Λ娆撳垂濮樿埖鐓€鐎广儱娲ㄩ弸鍌滅磼閻欏懐纾块柟?
 *
 * 濠电偞娼欓鍫ユ儊?IntegrationConfigRepository 闂佹眹鍔岀€氼厽鏅跺澶婂珘濠㈣泛楠稿▓浼存倵鐟欏嫮鐓紒鐘靛枛瀵濡烽…鎴濇畱闂佸搫鍊介～澶屾兜閸洘鏅?
 * 1. findByPlatformAndIsActiveTrue() - 闂佸搫琚崕鎾敋濡ゅ懎绠伴柛銉戝懏姣庡Δ鐘靛仜閸熻儻銇愰幘顔藉剭闁告洦鍓欓。鏌ユ煛閸繍妲搁柟顖氶叄閹粙濡搁埡浣歌祴缂?
 *
 * 婵炶揪缍€濞夋洟寮?@DataJpaTest 濠电偛顦崝蹇氼暰闂?
 * - 闂佺厧顨庢禍婊勬叏閳哄懏鐓€鐎广儱娲ㄩ弸?JPA 闂佺儵鏅濋…鍫ュ矗瑜忕槐鎺楀礋椤忓拋鍋?
 * - 濠殿噯绲界换瀣煂濠婂厾鍦偓锝庡幘濡叉悂鏌￠崒婵愭綈缁绢厼鐖奸獮宥堫樁妞ゃ垺鍨垮畷銉︽償閵堝懏顔囬梺鍛婃煟閸斿苯煤閺嵮岀叆婵﹩鍋嗛惃鎴︽煕?
 * - 婵炴垶鎸哥粔瀛樻叏閻愬瓨濮滈柦妯侯槺閺嗘岸鏌℃担鍓插殶婵?Spring 婵炴垶鎸搁敃锝囩箔閸涙潙妫橀柛銉╊棑缁€鍕煙椤戭剙妫楅崢鎾煛閸ャ劍缍戦柕鍫滅矙閺?
 *
 * @author WMS Team
 * @since 2026-02-06
 * @version 3.9 (Shopify Integration)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("case-1")
class IntegrationConfigRepositoryTest {

    @Autowired
    private IntegrationConfigRepository integrationConfigRepository;

    @Autowired
    private TestEntityManager entityManager;

    private IntegrationConfig activeShopifyConfig1;
    private IntegrationConfig activeShopifyConfig2;
    private IntegrationConfig inactiveShopifyConfig;
    private IntegrationConfig activeAmazonConfig;

    /**
     * 濠殿噯绲界换瀣煂濠婂厾鍦偓锝庡幘濡叉悂鏌￠崒婵愭綈缁绢厼鐖奸獮宥堫樁妞ゃ垺鍨垮畷婊冾吋閸涱厾浠存繝娈垮枛椤戝懐鈧灚褰冮湁閻庯綆鍘惧Σ鎼佹煛娴ｅ搫顣肩€?
     */
    @BeforeEach
    void setUp() {
        // 闂佸憡甯楃粙鎴犵磽閹捐瑙︽い鏍ㄧ矋閺嗗繘鏌?Shopify 闂備焦婢樼粔鍫曟偪?1
        activeShopifyConfig1 = IntegrationConfig.builder()
                .platform("SHOPIFY")
                .storeUrl("store1.myshopify.com")
                .apiKey("api-key-1")
                .accessToken("shpat_test_token_1")
                .isActive(true)
                .lastSyncAt(LocalDateTime.now().minusHours(1))
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹捐瑙︽い鏍ㄧ矋閺嗗繘鏌?Shopify 闂備焦婢樼粔鍫曟偪?2
        activeShopifyConfig2 = IntegrationConfig.builder()
                .platform("SHOPIFY")
                .storeUrl("store2.myshopify.com")
                .apiKey("api-key-2")
                .accessToken("shpat_test_token_2")
                .isActive(true)
                .lastSyncAt(LocalDateTime.now().minusMinutes(30))
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹惧墎鐭夊ù锝囧劋閺嗗繘鏌?Shopify 闂備焦婢樼粔鍫曟偪?
        inactiveShopifyConfig = IntegrationConfig.builder()
                .platform("SHOPIFY")
                .storeUrl("store3.myshopify.com")
                .apiKey("api-key-3")
                .accessToken("shpat_test_token_3")
                .isActive(false)
                .lastSyncAt(LocalDateTime.now().minusDays(1))
                .build();

        // 闂佸憡甯楃粙鎴犵磽閹捐瑙︽い鏍ㄧ矋閺嗗繘鏌?Amazon 闂備焦婢樼粔鍫曟偪閸℃稒鏅柛顐犲労閺嗘洟鏌ｉ敐鍡欐噭婵犫偓椤撱垹绾ч柕澶涚畱閳锋牠鎮橀悙瀛樼┛缂?
        activeAmazonConfig = IntegrationConfig.builder()
                .platform("AMAZON")
                .storeUrl("amazon-seller-id-123")
                .apiKey("amazon-api-key")
                .accessToken("amazon-access-token")
                .isActive(true)
                .lastSyncAt(null)
                .build();

        // 闂佸綊鏅插鎺旂不濞嗘挸绀岄柡宥冨妿閵堟挳鎮归崶銊︾闁哄棛鍠栭獮?
        entityManager.persist(activeShopifyConfig1);
        entityManager.persist(activeShopifyConfig2);
        entityManager.persist(inactiveShopifyConfig);
        entityManager.persist(activeAmazonConfig);
        entityManager.flush();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗幘閸欌偓闁荤姴娲㈤崹褰掑箚鎼淬劍鍋ㄩ柕濞у嫮鏆?Shopify 闂備焦婢樼粔鍫曟偪?- 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐?婵炴垶鎼╂禍顏堝储閵堝洨纾?
     */
    @Test
    @DisplayName("case-2")
    void testFindByPlatformAndIsActiveTrue_Shopify_ShouldReturnTwoConfigs() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎瑙︽い鏍ㄧ矋閺嗗繘鏌?Shopify 闂備焦婢樼粔鍫曟偪?
        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY");

        // Then: 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐?婵炴垶鎼╂禍顏堝储閵堝洨纾?
        assertThat(configs).hasSize(2);
        assertThat(configs).extracting(IntegrationConfig::getStoreUrl)
                .containsExactlyInAnyOrder("store1.myshopify.com", "store2.myshopify.com");
        assertThat(configs).allMatch(IntegrationConfig::getIsActive);
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗幘閸欌偓闁荤姴娲㈤崹褰掑箚鎼淬劍鍋ㄩ柕濞у嫮鏆?Amazon 闂備焦婢樼粔鍫曟偪?- 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐?婵炴垶鎼╂禍顏堝储閵堝洨纾?
     */
    @Test
    @DisplayName("case-3")
    void testFindByPlatformAndIsActiveTrue_Amazon_ShouldReturnOneConfig() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎瑙︽い鏍ㄧ矋閺嗗繘鏌?Amazon 闂備焦婢樼粔鍫曟偪?
        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue("AMAZON");

        // Then: 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐?婵炴垶鎼╂禍顏堝储閵堝洨纾?
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).getStoreUrl()).isEqualTo("amazon-seller-id-123");
        assertThat(configs.get(0).getIsActive()).isTrue();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗幘閸欌偓闁荤姴娴傚鈧紒妤€顦遍埀顒佺⊕閿氭繝鈧鍫熷剭闁告洦鍋婇幐顒勬煕?- 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐婇柣鎰暯閺佸嫰鏌涢幒鎿冩畽闁?
     */
    @Test
    @DisplayName("case-4")
    void testFindByPlatformAndIsActiveTrue_NonExistentPlatform_ShouldReturnEmptyList() {
        // When: 闂佸搫琚崕鎾敋濡や胶鈻旂€广儱鎳愰幗鐘绘煕閿斿搫濡挎繛鍫熷灴閻涱噣宕橀幓鎺楀彙
        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue("EBAY");

        // Then: 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐婇柣鎰暯閺佸嫰鏌涢幒鎿冩畽闁?
        assertThat(configs).isEmpty();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗幘閸欌偓闁荤姴娲㈤崹鐑樻櫠瀹ュ瀚夊鑸靛姇鐢磭绱撻崘鎯ф珯缂佽鲸鐟╁畷鐘诲川椤旂⒈浠剧紓浣稿€烽懗鍫曞极閵堝鍎嶉柛鏇炲缁€? 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐?婵炴垶鎼╂禍顏堝储閵堝洨纾?
     */
    @Test
    @DisplayName("case-5")
    void testFindAll_ShouldReturnAllConfigs() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎绠ラ柍褜鍓熷鍨緞閹邦剙璧嬬紓?
        List<IntegrationConfig> configs = integrationConfigRepository.findAll();

        // Then: 闁圭厧鐡ㄥΛ浣烘崲閹达箑鐐?婵炴垶鎼╂禍顏堝储閵堝洨纾?
        assertThat(configs).hasSize(4);
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗亞缁犱粙鎮楀☉娅虫垿寮绘繝鍥ㄧ厐鐎广儱娲ㄩ弸?- 闁圭厧鐡ㄥ褰掑垂濮樿泛绀夐柣鏂垮缁犱粙鎮?
     */
    @Test
    @DisplayName("case-6")
    void testSave_NewConfig_ShouldSucceed() {
        // Given: 闂佸憡甯楃粙鎴犵磽閹捐妫樺ù鍏兼綑鐢磭绱?
        IntegrationConfig newConfig = IntegrationConfig.builder()
                .platform("SHOPIFY")
                .storeUrl("new-store.myshopify.com")
                .accessToken("new-token")
                .isActive(true)
                .build();

        // When: 婵烇絽娲︾换鍌炴偤閵娾晜鐓€鐎广儱娲ㄩ弸?
        IntegrationConfig savedConfig = integrationConfigRepository.save(newConfig);
        entityManager.flush();

        // Then: 闁圭厧鐡ㄥ褰掑垂濮樿泛绀夐柣鏂垮缁犱粙鎮楀☉娅亪鎳熼悢鍏煎仺闁绘梻顭堥悘?ID
        assertThat(savedConfig.getId()).isNotNull();
        assertThat(savedConfig.getStoreUrl()).isEqualTo("new-store.myshopify.com");
        assertThat(savedConfig.getCreatedAt()).isNotNull();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗幗缁绢垶鏌￠崒娆忓祮闁告ǜ鍊楃槐鏃堫敊閻愵剛鏆?lastSyncAt - 闁圭厧鐡ㄥ褰掑垂濮樿泛绀夐柣鏂挎啞缁绢垶鏌?
     */
    @Test
    @DisplayName("case-7")
    void testUpdate_LastSyncAt_ShouldSucceed() {
        // Given: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鍋濋柣妤€鐗婄粻鎺楁⒑閺夎法肖闁?
        IntegrationConfig config = integrationConfigRepository.findById(activeShopifyConfig1.getId()).orElseThrow();
        LocalDateTime oldSyncTime = config.getLastSyncAt();
        LocalDateTime newSyncTime = LocalDateTime.now();

        // When: 闂佸搫娲ら悺銊╁蓟?lastSyncAt
        config.setLastSyncAt(newSyncTime);
        integrationConfigRepository.save(config);
        entityManager.flush();
        entityManager.clear();

        // Then: 闁圭厧鐡ㄥ褰掑垂濮樿泛绀夐柣鏂挎啞缁绢垶鏌?
        IntegrationConfig updatedConfig = integrationConfigRepository.findById(activeShopifyConfig1.getId()).orElseThrow();
        assertThat(updatedConfig.getLastSyncAt()).isNotEqualTo(oldSyncTime);
        assertThat(updatedConfig.getLastSyncAt()).isEqualToIgnoringNanos(newSyncTime);
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗€纭稿ú锝夋煟椤剙濮傞柛妯稿€楃槐?- 闂佸搫琚崕鎾敋濡ゅ懎绫嶉柡鍫㈡暩閻熸繈骞栫€涙ɑ顥嗙紒缁樺灴瀹?
     */
    @Test
    @DisplayName("case-8")
    void testDisableConfig_ShouldNotReturnInActiveQuery() {
        // Given: 缂備礁鍊烽懗鍫曞极閵堝棛鈻旈柍褜鍓氱粙澶愵敂閸繂璧嬬紓?
        IntegrationConfig config = integrationConfigRepository.findById(activeShopifyConfig1.getId()).orElseThrow();
        config.setIsActive(false);
        integrationConfigRepository.save(config);
        entityManager.flush();

        // When: 闂佸搫琚崕鎾敋濡ゅ懎瑙︽い鏍ㄧ矋閺嗗繘鏌?Shopify 闂備焦婢樼粔鍫曟偪?
        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY");

        // Then: 闁圭厧鐡ㄩ弻銊ㄣ亹瑜庡濠氬棘閹稿海顦?婵炴垶鎼╂禍顏堝储閵堝洨纾炬い鏇炴缁€鍒焎tiveShopifyConfig2闂?
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).getStoreUrl()).isEqualTo("store2.myshopify.com");
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墮閻忊晠姊婚崟鈺佲偓婵嬪储閵堝洨纾?- 闁圭厧鐡ㄥ褰掑垂濮樿泛绀夐柣鏂垮槻閻忊晠姊?
     */
    @Test
    @DisplayName("case-9")
    void testDelete_Config_ShouldSucceed() {
        // Given: 闂佸吋鍎抽崲鑼躲亹閸ヮ剚鐓€鐎广儱娲ㄩ弸?ID
        Long configId = activeShopifyConfig1.getId();

        // When: 闂佸憡甯炴繛鈧繛鍛叄閺屽﹤顓奸崶鈺傜€?
        integrationConfigRepository.deleteById(configId);
        entityManager.flush();

        // Then: 闁圭厧鐡ㄥ鐟拔涢妶鍛灃闁哄洨鍋涢弲娆撴煛鐏炶鍔ユい鏇燁殜瀹曟岸骞忓畝濠傛畽闂備焦婢樼粔鍫曟偪?
        assertThat(integrationConfigRepository.findById(configId)).isEmpty();

        // 闂佸搫琚崕鎾敋濡ゅ懎瑙︽い鏍ㄧ矋閺嗗繘鏌?Shopify 闂備焦婢樼粔鍫曟偪閸℃ɑ鍎熼柡鍌氱仢濞懷囧级閳哄倹鐓ユ繛?婵?
        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY");
        assertThat(configs).hasSize(1);
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗幘閸欌偓闁荤姴娲㈤崹浠嬪储閵堝洨纾炬い鏃囨閻?lastSyncAt 闂佸湱鍎ょ敮鎺旇姳?- 婵°倗濮撮惌渚€鎯佹径鎰睄闁割偅娲橀敍鐔奉渻閵堝懏鎯堢紒?
     */
    @Test
    @DisplayName("case-10")
    void testConfigTimestamps_ShouldBeValid() {
        // When: 闂佸搫琚崕鎾敋濡ゅ懎瑙︽い鏍ㄧ矋閺嗗繘鏌?Shopify 闂備焦婢樼粔鍫曟偪?
        List<IntegrationConfig> configs = integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY");

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰睄闁割偅娲橀敍鐔兼倵濞戞瑯娈曟い?
        for (IntegrationConfig config : configs) {
            assertThat(config.getCreatedAt()).isNotNull();
            assertThat(config.getLastSyncAt()).isNotNull();
            assertThat(config.getLastSyncAt()).isBefore(LocalDateTime.now().plusSeconds(1));
        }
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨妯挎硾鐢磭绱撻崘鎯ф灓婵炲牊鍨堕—鈧俊顖涱儥閸氬洭鏌?- 婵°倗濮撮惌渚€鎯?Builder 婵帗绋掗…鍫ヮ敇婵犳艾纾?
     */
    @Test
    @DisplayName("case-11")
    void testConfigDefaults_ShouldBeCorrect() {
        // Given: 闂佸憡甯楃粙鎴犵磽閹剧粯鐓€鐎广儱娲ㄩ弸鍌炴煛閸愨晜鏋勭紒妤€顦甸獮鎰板炊瑜忛弳?platform 闂?isActive
        IntegrationConfig config = IntegrationConfig.builder()
                .storeUrl("test.myshopify.com")
                .accessToken("test-token")
                .build();

        // When: 婵烇絽娲︾换鍌炴偤閵娾晜鐓€鐎广儱娲ㄩ弸?
        IntegrationConfig savedConfig = integrationConfigRepository.save(config);
        entityManager.flush();

        // Then: 婵°倗濮撮惌渚€鎯佹径瀣垫付婵☆垱顑欓崥鍥煕?
        assertThat(savedConfig.getPlatform()).isEqualTo("SHOPIFY");
        assertThat(savedConfig.getIsActive()).isTrue();
    }
}
