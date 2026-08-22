package com.wms.system.scheduler;

import com.wms.system.service.ShopifyIntegrationService;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.task.ActiveCompanyTaskRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import java.util.function.Consumer;

/**
 * IntegrationScheduler 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * V3.9 闂佸搫顑堥崺鏍倵椤栫偞鏅慨婵嗙崱opify 闂傚倸妫楀Λ娆撳垂濮樻墎鍋撶憴鍕暡婵＄偛鍊圭粋鎺旀嫚閹绘帩娼?
 *
 * 濠电偞娼欓鍫ユ儊?IntegrationScheduler 闂佹眹鍔岀€氼剟鎮炬ィ鍐ㄧ睄闁哄牆鐏氬畷鏌ユ煕閺傚搫澧插褏鏅幃鏉跨暆鐎ｎ剛鐛?
 * 1. syncShopifyOrders() - 闂佸憡鑹鹃張顒勵敆?Shopify 闁荤姳闄嶉崹鐟扮暦?
 * 2. 閻庢鍠栭崐鎼佹偉閼搁潧绶為柛鏇ㄥ幗閸?- 缂佺虎鍙庨崰鏇犳崲濮橆剦鍤曢柛灞炬皑閸╂鈽夐幘宕囆＄紒杈╁缁嬪顢橀悢鍝ュ姺闁荤姴顑呴崯顐も偓?
 *
 * @author WMS Team
 * @since 2026-02-06
 * @version 3.9 (Shopify Integration)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class IntegrationSchedulerTest {

    @Mock
    private ShopifyIntegrationService shopifyIntegrationService;

    @Mock
    private ActiveCompanyTaskRunner activeCompanyTaskRunner;

    @InjectMocks
    private IntegrationScheduler integrationScheduler;

    private ShopifyIntegrationService.SyncResult successResult;
    private ShopifyIntegrationService.SyncResult mixedResult;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Tenant> action = invocation.getArgument(1);
            try {
                action.accept(Tenant.builder().id(1L).slug("legacy").build());
            } catch (RuntimeException ignored) {
                // Production ActiveCompanyTaskRunner logs per-company failures
                // and continues with the remaining companies.
            }
            return null;
        }).when(activeCompanyTaskRunner).forEachActiveCompany(anyString(), any());
        // 闂佸憡甯楃粙鎴犵磽閹捐绠ｉ柟閭﹀墮椤娀鏌ｉ妸銉ヮ仼闁诡喗鎸搁～銏ゅΨ瑜忓▔銏ゆ煛?
        successResult = new ShopifyIntegrationService.SyncResult();
        successResult.incrementSuccess();
        successResult.incrementSuccess();
        successResult.incrementSuccess();

        // 闂佸憡甯楃粙鎴犵磽閹惧煄搴＄暋閻楀牆鈧倗绱撴担瑙勫鞍闁诲繐顦甸弫宥夊醇閻斿摜鐣抽梺鐟扮摠閸旀洘鎱ㄥ☉銏犖ュù锝囨櫕閸庢煡寮堕埡浣瑰偍闁逞屽厸缁€渚€濡甸幋鐘冲闁靛鍨崇粈?
        mixedResult = new ShopifyIntegrationService.SyncResult();
        mixedResult.incrementSuccess();
        mixedResult.incrementSuccess();
        mixedResult.incrementSkipped();
        mixedResult.incrementFailed();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墰閺嗕即鏌￠崘鈺傛瀯闁瑰箍鍨藉畷婵嬪焺閸愩劎浜ｉ梺鍛婃⒒閸犳劖鏅堕悾灞惧仒?- 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺?syncOrders 闂佸搫鍊介～澶屾兜?
     */
    @Test
    @DisplayName("case-2")
    void testSyncShopifyOrders_Success_ShouldCallSyncOrders() {
        // Given: Mock 闁哄鏅滈弻銊ッ洪弽顓炵闁归偊鍓欓～鐘电磽娴ｈ灏伴柣?
        when(shopifyIntegrationService.syncOrders()).thenReturn(successResult);

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤?
        integrationScheduler.syncShopifyOrders();

        // Then: 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺闁靛鍊楅杈ㄧ箾?syncOrders 闂佸搫鍊介～澶屾兜?
        verify(shopifyIntegrationService, times(1)).syncOrders();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墰閺嗕即鏌￠崘鈺傛瀯闁瑰箍鍨藉畷婵嬪灳閹颁焦袩闂佽崵鍋涘Λ娆戞嫻閳哄懎瑙﹂柛顐犲灮濞夈垽鏌?- 闁圭厧鐡ㄥ褰掝敆濠婂懏鏆滅紒瀣硶閺嗘岸鏌?
     */
    @Test
    @DisplayName("case-3")
    void testSyncShopifyOrders_MixedResult_ShouldComplete() {
        // Given: Mock 闁哄鏅滈弻銊ッ洪弽顬ュ骸鐣￠悧鍫濃偓銈囩磽娴ｈ灏伴柣?
        when(shopifyIntegrationService.syncOrders()).thenReturn(mixedResult);

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤?
        integrationScheduler.syncShopifyOrders();

        // Then: 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺闁靛鍊楅杈ㄧ箾?syncOrders 闂佸搫鍊介～澶屾兜?
        verify(shopifyIntegrationService, times(1)).syncOrders();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墰閺嗕即鏌￠崘鈺傛瀯闁瑰箍鍨藉畷婵嬫晸閻樿鲸鎹ｉ梺鍛婂笚濠㈡妲愰幘鑸垫殰?- 闁圭厧鐡ㄥ鐟扮暦閻斿吋鍤旂€瑰嫭澹嗙壕浠嬫偨椤栫偞娅滅紒妤€顦扮粙澶愵敇閻斿摜鍔烽柣鐘差儏閸燁偆鈧?
     */
    @Test
    @DisplayName("case-4")
    void testSyncShopifyOrders_Exception_ShouldCatchAndLog() {
        // Given: Mock 闂佺鍩栫粙鎴﹀吹椤撶儐鍤曢柛灞炬皑閸?
        when(shopifyIntegrationService.syncOrders())
                .thenThrow(new RuntimeException("Database connection failed"));

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤倝鏌ㄥ☉妯煎缂佹顦伴幆鏃堝籍閳ь剚鎱ㄨ箛娑樼闁告繂瀚壕浠嬫偨椤栫偞浜ょ紒?
        integrationScheduler.syncShopifyOrders();

        // Then: 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺闁靛鍊楅杈ㄧ箾?syncOrders 闂佸搫鍊介～澶屾兜?
        verify(shopifyIntegrationService, times(1)).syncOrders();
        // 閻庢鍠栭崐鎼佹偉閸撲焦鍋栨い鎰剁到缁€瀣煠閹冩缂佽鲸绻冪粙澶婎吋閸曨厾鐛ラ梺鍛婄閸ㄥ磭绮崒娑橆嚤闁绘娅曠亸?
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墰閺嗕即鏌￠崘鈺傛瀯闁瑰箍鍨藉畷婵嬪灳閹颁焦鈻煎┑鐐叉閸撴繃鏅堕悾灞惧仒?- 闁圭厧鐡ㄥ褰掓儊閳ユ枼鏋庨柨鐔哄Т閸樻挳鎮圭€ｎ亜鏆熼柡?syncOrders
     */
    @Test
    @DisplayName("case-5")
    void testSyncShopifyOrders_MultipleExecutions_ShouldCallEachTime() {
        // Given: Mock 闁哄鏅滈弻銊ッ洪弽顓炵闁归偊鍓欓～鐘电磽娴ｈ灏伴柣?
        when(shopifyIntegrationService.syncOrders()).thenReturn(successResult);

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤?濠?
        integrationScheduler.syncShopifyOrders();
        integrationScheduler.syncShopifyOrders();
        integrationScheduler.syncShopifyOrders();

        // Then: 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺?濠?syncOrders 闂佸搫鍊介～澶屾兜?
        verify(shopifyIntegrationService, times(3)).syncOrders();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墰閺嗕即鏌￠崘鈺傛瀯闁瑰箍鍨藉畷婵嬪灳閾忣偉鍚悗娈垮枛閸婃悂鎮ラ崼鏇炶Е閹兼惌婢€閸掓帒顭?- 闁圭厧鐡ㄥ濠氬箣妞嬪海纾兼い鎾跺枎閳锋棃鎮?
     */
    @Test
    @DisplayName("case-6")
    void testSyncShopifyOrders_RecoverAfterException_ShouldContinue() {
        // Given: 缂備焦顨忛崗娑氱博閺夋垟鏋庨柍鈺佸暙椤棃鏌涢幋婵囨儓缂佽鲸鎸鹃弫顕€妫冮埡鍐槷缂備焦顨忛崗娑氳姳閳轰讲鏋庨柍鈺佸暙閻忓洭鏌?
        when(shopifyIntegrationService.syncOrders())
                .thenThrow(new RuntimeException("Temporary error"))
                .thenReturn(successResult);

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤?濠?
        integrationScheduler.syncShopifyOrders(); // 缂備焦顨忛崗娑氱博閺夋垟鏋庨柍銉у仺娴滃ジ鎮?
        integrationScheduler.syncShopifyOrders(); // 缂備焦顨忛崗娑氳姳閳轰讲鏋庨柍鈺佸暙閻忓洭鏌?

        // Then: 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺?濠?syncOrders 闂佸搫鍊介～澶屾兜?
        verify(shopifyIntegrationService, times(2)).syncOrders();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墰閺嗕即鏌￠崘鈺傛瀯闁瑰箍鍨藉畷婵嬫缂佺粯鍨垮畷鍫曟倷绾版ɑ鏅炵紓鍌欑劍閹稿鎮?- 闁圭厧鐡ㄥ褰掝敆濠婂懏鏆滅紒瀣儥濡查亶鏌?
     */
    @Test
    @DisplayName("case-7")
    void testSyncShopifyOrders_EmptyResult_ShouldHandle() {
        // Given: Mock 闁哄鏅滈弻銊ッ洪弽顐ょ煔闁惧繐婀卞▔銏ゆ煛鐎ｎ偆鐜荤紒杈ㄧ懇楠炲秹鍩€椤掑嫬瀚夊璺哄瘨閸氣偓闂佽桨绶氶。锔炬嫻?闂?
        ShopifyIntegrationService.SyncResult emptyResult = new ShopifyIntegrationService.SyncResult();
        when(shopifyIntegrationService.syncOrders()).thenReturn(emptyResult);

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤?
        integrationScheduler.syncShopifyOrders();

        // Then: 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺闁靛鍊楅杈ㄧ箾?syncOrders 闂佸搫鍊介～澶屾兜?
        verify(shopifyIntegrationService, times(1)).syncOrders();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨姗嗗墰閺嗕即鏌￠崘鈺傛瀯闁瑰箍鍨藉畷婵嬪灳閹颁焦袩闂?NullPointerException - 闁圭厧鐡ㄥ鐟扮暦閻斿吋鍤旂€瑰嫭澹嗙壕浠嬫偨?
     */
    @Test
    @DisplayName("case-8")
    void testSyncShopifyOrders_NullPointerException_ShouldCatch() {
        // Given: Mock 闂佺鍩栫粙鎴﹀吹?NullPointerException
        when(shopifyIntegrationService.syncOrders())
                .thenThrow(new NullPointerException("Null config"));

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤倝鏌ㄥ☉妯煎缂佹顦伴幆鏃堝籍閳ь剚鎱ㄨ箛娑樼闁告繂瀚壕浠嬫偨椤栫偞浜ょ紒?
        integrationScheduler.syncShopifyOrders();

        // Then: 闁圭厧鐡ㄥΛ渚€鎯冮悢鍏煎仺闁靛鍊楅杈ㄧ箾?syncOrders 闂佸搫鍊介～澶屾兜?
        verify(shopifyIntegrationService, times(1)).syncOrders();
    }

    /**
     * 濠电偞娼欓鍫ユ儊椤栫偞鏅慨妯哄瀹曪綁鎮归崶锔绢槮闁伙綁绠栧顕€寮甸崹顐㈠簥闂佸憡鏌￠埀顒冨皺閻熸繂霉閸忕厧鍝洪柛锝呮啞瀵板嫬顓奸崼銏☆啀闂?- 婵炶揪缍€濞夋洟寮?verifyNoMoreInteractions
     */
    @Test
    @DisplayName("case-9")
    void testSyncShopifyOrders_NoExtraInvocations_ShouldVerify() {
        // Given: Mock 闁哄鏅滈弻銊ッ洪弽顓炵闁归偊鍓欓～鐘电磽娴ｈ灏伴柣?
        when(shopifyIntegrationService.syncOrders()).thenReturn(successResult);

        // When: 闂佸湱鐟抽崱鈺傛杸闁诲氦顫夌喊宥咁渻閸屾稓顩烽悹鍥ㄥ絻椤倕鈽夐幘顖氫壕濠?
        integrationScheduler.syncShopifyOrders();

        // Then: 婵°倗濮撮惌渚€鎯佹径鎰煑妞ゅ繐娲ㄥ▓鍫曟煟椤剙濡虹紒鏃€娼欓埢搴ㄥ煡閸涱垳顦┑鐐插閸撴繂锕㈡笟鈧畷妤呭嫉閻㈢敻鎼ㄦ繛瀛樺殠閸婃挾鑺?
        verify(shopifyIntegrationService, times(1)).syncOrders();
        verifyNoMoreInteractions(shopifyIntegrationService);
    }
}
