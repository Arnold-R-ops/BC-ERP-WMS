package com.wms.system.service;

import com.wms.system.dto.inventory.InventoryDetailDto;
import com.wms.system.dto.inventory.InventorySummaryDto;
import com.wms.system.dto.inventory.LocationViewDto;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.StockStatus;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * InventoryQueryService 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫鎾绘偐椤旂懓浜鹃柛鎰靛枛楠炪垺淇婇悙瀛樼婵＄偠妫勯锝嗙鐎ｅ灚鏅ｉ梺缁樺姈瑜板啯绂嶉崼鏇熲拻濞达絿鐡斿鎰偓瑙勬礃椤洨鍒掗弮鍫晪闁逞屽墮閻ｇ兘鎮滅粵瀣櫍濠电偞鍨剁湁濠㈣娲熷娲箰鎼达絿鐣垫俊銈囧У閹倿鎮伴鍢夌喓鎷嬪畷鍥╃暰?
 *
 * 婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佲枙闁绘帟濮ょ换娑㈠幢濡粯鍎庨梺杞扮鐎氫即寮诲☉銏╂晝闁绘ɑ褰冩慨鏇㈡⒑缁嬪潡顎楅柨鏇樺劦婵＄敻宕熼姘敤濡炪倖鍔﹀鈧繛宀婁邯濮婃椽骞栭悙娴嬪亾閺嶎厼鍨傞柛褎顨呴拑鐔哥箾閹存瑥鐏╃紒鐘崇洴閺屽秵娼幍顕呮М濡炪値鍓欓ˇ杈╂閹惧瓨濯村Δ锔藉椤庢姊洪幖鐐插闁绘牜鍘ч?
 * 1. 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄦ硾椤繈濡搁埡浣侯攨濠殿喗顭堥崺鏍磹閻㈠憡鐓熼柕蹇曞У閸熺偞绻涢崨顔剧煁缂佺粯绋戦悾婵囩節娓氣偓閸嬫姊洪崫鍕拱缂佸鍨块幃鎯р攽閸艾浜鹃柨婵嗙凹閹茬偓绻涘顔煎箻缂佽鲸鎸婚幏鍛存偡閺夋娼旈梻渚€娼уΛ娆撳Υ閹?闂傚倸鍊搁崐鎼佸磹瀹勬噴褰掑炊閵娧呭骄闂佸壊鍋嗛崰鍡樼閸儲鐓欓梻鍌氼嚟閸斿秹鏌涢妶鍛板閾绘牠鏌ㄥ┑鍡樺櫣闁哄棙鐟х槐鎺楁偐瀹曞洤鈷屽Δ鐘靛仦閻楁顭囪箛娑樼鐟滃繗鎽梻鍌欑閹芥粍鎱ㄩ弶鎳虫稑鈹戠€ｎ亣鎽曞┑鐐村灟閸ㄥ綊鎮炲ú顏呯厱闁归偊鍠栨禍楣冩煟閿濆妫戠紒?
 * 2. 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄥ煐缁岄亶鎮滃Ο璇插伎闂佽鍎兼慨銈夋偂閸愵喗鐓曟繝闈涙椤忊晠鏌￠崱妯荤缂佺粯绋戦悾婵囩節娓氣偓閸嬫姊洪崫鍕拱缂佸鍨块幃鎯р攽閸艾浜鹃柨婵嗙凹閹茬偓绻涘顔煎箻缂佽鲸鎸婚幏鍛存偡閺夋娼旀俊鐐€戦崝宀勬晝椤忓嫷鍤曞┑鐘崇閸嬪嫰鏌涘☉姗堝伐妞ゅ孩鎹囧娲川婵犲倸顫庨梺绋款儐閹稿墽妲愰悙鍝勯唶婵犲灚鍔栫€靛矂姊洪棃娑氬婵☆偅顨嗛幈銊ョ暆閸曨剛鍘遍柣搴秵閸嬪嫭鎱ㄥ鍡╂闁绘劘灏欑粻濠氭煙椤旇娅囩紒杈ㄥ笒铻ｇ紓浣诡焽瑜板棝姊婚崒娆愮グ濠殿喓鍊濋弫瀣渻閵堝繐鐦滈柛銊ㄤ含缁晠鎮㈤悡搴ㄥ敹闂侀潧顧€婵″洭宕㈡禒瀣拺缂備焦蓱閻撱儵鏌熼懞銉х煉妤犵偛锕畷姗€鍩￠埀顒傛崲閸℃ǜ浜滈柟鏉垮缁夊灚绻涢崼鐔割棃婵?
 * 3. 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄦ硾椤繈濡歌娑撳秹鏌￠崒娑崇穿鐟滅増甯楅弲鏌ユ煕椤愩倕鏋傞柍褜鍓欓崯顖滄崲濞戞鏆嗗┑鐘辩窔閸嬫姊洪崫鍕拱缂佸鍨块幃鎯р攽閸艾浜鹃柨婵嗙凹閹茬偓绻涘顔煎箻缂佽鲸鎸婚幏鍛存偡閺夋娼旀俊鐐€戦崝宀勬晝椤忓牊鍋樻い鏇楀亾鐎殿喕绮欓、鏇㈡偄閾氬倸顥氭繝鐢靛仦閸ㄥ爼顢旀导鏉戠闁绘浜崜銊ヮ渻閵堝棗濮傞柛濠冾殘婢规洟鎮剧仦绋夸壕妤犵偛鐏濋崝姘舵煙閸愯尙绠荤€规洜鏁诲畷鍫曞煘閹傚闁荤喐鐟ョ€氼厾绮氱捄銊х＜闂婎偒鍘鹃惌娆愩亜閵忊埄鎴犵紦閻ｅ瞼鐭欓柛顭戝枦閳ь剙鐏濋埞鎴︽倷閸欏妫炵紓浣虹帛閸旀妲愰幒妤€鐓涢柛娑卞枓閹锋椽姊洪悡搴綗闁稿﹥娲熷鎼佸箣閿旂晫鍘介梺缁樻⒐濞兼瑩宕濆鑸垫嚉闁绘劗绻濈换鍡涙煏閸繂鈧憡绂嶆ィ鍐┾拻?
 * 4. 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈡晝閳ь剛绮婚悩鍏呯箚妞ゆ牗绻傛禍鍦棯閹规劕袚闁逛究鍔岃灒闁圭娴烽妴鎰磽娴ｆ彃浜鹃梺绋挎湰缁嬫帡宕ｈ箛鏂剧箚闁绘劙顤傞崵娆徝瑰鍫㈢暫闁诡喗顨呴埢鎾诲垂椤旂晫褰梻浣侯焾椤戝懘骞婇幇鏉跨劦妞ゆ帒瀚☉褔鏌曢崼銏╃劸妞ゆ洩缍佸畷濂稿即閻愭鍚呴梻浣虹帛閸ㄩ潧螞閺冨牊鍋ㄥ┑鐘崇閳锋帒霉閿濆牊顥夐柛姘辨嚀閳规垿顢欓懖鈺嬬礊濠电偛妫庨崹鑺ヤ繆閸洖鐐婃い顓熷灦缂嶆姊绘担鍛婃儓婵炲眰鍨藉畷鐟懊洪鍕紱?
 * 5. 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴狅紱闂佸憡渚楅崣搴ㄦ偄閸℃ü绻嗘い鏍ㄦ皑濮ｇ偤鏌涚€ｎ偅灏い顐ｇ箞閹剝鎯旈敐鍕敇濠碉紕鍋戦崐鎴﹀垂濞差亗鈧啯绻濋崶褏鐣洪梺闈涚箞閸婃洜绮堥崒鐐寸厽闁规儳纾崙瑙勬叏鐟欏嫷娈滄慨濠勭帛閹峰懘宕崟顐＄帛闂備胶顢婃慨銈囧垝閹捐违闁稿本绋撻々鐑芥倵閿濆簼绨介柨娑欑洴濮婅櫣鎲撮崟顐㈠Б闂佸鏉垮闁告帒锕﹂埀顒勬涧閹芥粎澹曢崗鑲╃闁瑰鍎戞笟娑㈡煕鎼达紕绠婚柡宀€鍠栭、娆撳及韫囨挸甯紓?
 * 6. 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈡晜閽樺缃曢梻浣虹《閸撴繈鎽傜€ｎ喖鐐婇柕濞垮労閸ゃ倝姊洪崫鍕垫Ч闁搞劌缍婂鏄忣樄婵﹥妞藉Λ鍐ㄢ槈鏉堛剱銏ゆ⒑閸濆嫭鍣虹紒瀣崌閸┾偓妞ゆ帊娴囨竟妯汇亜閿旂偓鏆€殿喖顭烽弫鎰緞婵犲嫮娼夐梻浣侯焾鐞氼偊宕愬Δ鍛闁告縿鍎崇壕钘壝归敐鍫殐婵炲牊娲熼弻鐔兼偡閻楀牆鏋犻梺?
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class InventoryQueryServiceTest {

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private InventoryQueryService inventoryQueryService;

    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch testBatch1;
    private InventoryBatch testBatch2;

    @BeforeEach
    void setUp() {
        // 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫宥夊礋椤掍焦顔囨繝寰锋澘鈧洟宕导瀛樺剹婵炲棙鎸婚悡娆撴倵閻㈡鐒鹃崯鍝ョ磼閹冪稏缂侇喗鐟╁濠氭偄閻撳海顔夐梺褰掑亰閸熲晛顭ㄩ崼鐔哄幈闂佹寧妫侀褔鐛幇鐗堢厱閻庯綆鍋呭畷宀勬煛娴ｇ懓濮堥柟顖涙閸ㄦ儳鐣烽崶璺烘櫔婵犵數濮撮惀澶愬Χ閸涱垱顕楁俊鐐€ら崢濂告偋閹剧鑰垮〒姘ｅ亾婵﹨娅ｇ槐鎺戭潨閸℃瑥濮兼繝鐢靛仜閹冲繐煤濠婂嫮顩查柟闂寸贰閺佸洭鏌ｅΟ鍏兼毄闁?
        testWarehouse = Warehouse.builder()
            .id(1L)
            .code("WH01")
            .name("婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佲枙闁绘帟濮ょ换娑㈠幢濡粯鍎庨梺杞扮鐎氫即寮诲☉銏╂晝闁绘ɑ褰冩慨鏇㈡⒑缁嬪潡顎楅柨鏇樺劦婵＄敻宕熼姘敤闂侀潧臎娴ｆ彃浜鹃柟鐑樻尪娴滄粓鏌嶉崫鍕殭闁告ɑ鎸抽弻鈥崇暆閳ь剟宕伴弽顓犲祦闁糕剝鍑瑰Σ楣冩⒑閹稿海鈽夌紒澶屾暬楠?")
            .isActive(true)
            .build();

        // 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫宥夊礋椤掍焦顔囨繝寰锋澘鈧洟宕导瀛樺剹婵炲棙鎸婚悡娆撴倵閻㈡鐒鹃崯鍝ョ磼閹冪稏缂侇喗鐟╁濠氭偄閻撳海顔夐梺褰掑亰閸熲晛顭ㄩ崼鐔哄幈闂佹寧妫侀褔鐛幇鐗堢厱閻庯綆鍋呭畷宀勬煛娴ｇ懓濮堥柟顖涙閸ㄦ儳鐣烽崶璺烘櫔婵犵數濮烽。浠嬪焵椤掆偓閸熻法鐥閺岀喖顢欓悡搴樺亾閸ф鍋樻い鏇楀亾鐎殿喕绮欓、鏇㈡偄閾氬倸顥氭繝鐢靛仦閸ㄥ爼顢旀导鏉戠闁绘浜崜銊ヮ渻閵堝棗濮傞柛濠冾殘婢?
        testLocation = Location.builder()
            .id(1L)
            .warehouse(testWarehouse)
            .warehouseCode("WH01")
            .zone(Zone.ZONE_A)
            .shelfNumber("A-01")
            .positionNumber("001")
            .locationCode("WH01-ZONE_A-A-01-001")
            .enabled(true)
            .build();

        // 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫宥夊礋椤掍焦顔囨繝寰锋澘鈧洟宕导瀛樺剹婵炲棙鎸婚悡娆撴倵閻㈡鐒鹃崯鍝ョ磼閹冪稏缂侇喗鐟╁濠氭偄閻撳海顔夐梺褰掑亰閸熲晛顭ㄩ崼鐔哄幈闂佹寧妫侀褔鐛幇鐗堢厱閻庯綆鍋呭畷宀勬煛娴ｇ懓濮堥柟顖涙閸ㄦ儳鐣烽崶璺烘櫔婵犵數濮撮惀澶愬Χ閸涱垱顕楁俊鐐€ら崢濂告偋閹剧鑰垮ù锝囩《閺€浠嬫煟濡崵鎳冮柟鍐查閻☆參姊绘担鍛婃喐闁革絻鍎靛畷褰掑醇閺囩姴绁?
        testProduct = Product.builder()
            .id(1L)
            .barcode("6901234567890")
            .name("婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佲枙闁绘帟濮ょ换娑㈠幢濡粯鍎庨梺杞扮鐎氫即寮诲☉銏╂晝闁绘ɑ褰冩慨鏇㈡⒑缁嬪潡顎楅柨鏇樺劦婵＄敻宕熼姘敤濡炪倖鍔﹀鈧繛宀婁邯濮婃椽骞栭悙娴嬪亾閺嶎厼鍨傞柛褎顨呯粻鏍喐閻楀牆绗掑ù鑲╁█閺屾盯寮撮妸銉ヮ潽闂佺瀛╅幐缁樼┍?")
            .spu(ProductSpu.builder().id(1L).spuCode("SPU001").spuName("Test SPU").build())
            .packUnit("闂?")
            .conversionRate(12)  // 1缂?= 12闂?
            .safetyStock(50)
            .build();

        // 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫宥夊礋椤掍焦顔囨繝寰锋澘鈧洟宕导瀛樺剹婵炲棙鎸婚悡娆撴倵閻㈡鐒鹃崯鍝ョ磼閹冪稏缂侇喗鐟╁濠氭偄閻撳海顔夐梺褰掑亰閸熲晛顭ㄩ崼鐔哄幈闂佹寧妫侀褔鐛幇鐗堢厱閻庯綆鍋呭畷宀勬煛娴ｇ懓濮堥柟顖涙閸ㄦ儳鐣烽崶璺烘櫔婵犵數濮烽。浠嬪焵椤掆偓閸熻法鐥閺岀喖顢欓悡搴樺亾閸噮鍤曞┑鐘崇閸嬪嫰鏌涘☉姗堝伐妞ゅ孩鎹囧娲川婵犲倸顫庨梺绋款儐閹稿墽妲愰悙鍝勯唶婵犲灚鍔栫€?闂傚倸鍊搁崐鎼佸磹閻戣姤鍊块柨鏃堟暜閸嬫挾绮☉妯诲櫧闁活厽鐟╅弻鐔告綇妤ｅ啯顎嶉梺绋款儏椤戝懘鍩為幋锔藉亹闁告瑥顦伴幃娆忊攽椤旀娼愰柣鎿勭節瀵濡搁妷銏℃杸闂佺硶鍓濋敋缂佹劖绋掔换娑㈠箣閻愭潙纰嶇紓浣割槸缂嶅﹤顕ｆ繝姘亜闁绘挸娴烽宀勬⒑閸︻厼鍔嬬紒璇插€垮畷婊冪暦閸モ晝锛?4闂?= 2缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸ゅ嫰鏌ょ粙璺ㄤ粵闁告瑥绻橀弻锝夊閵忊晝鍔搁梺鑲╊焾缂嶅﹪寮婚妶鍡樺弿闁归偊鍏橀崑鎾诲冀椤撱劎绋?
        testBatch1 = InventoryBatch.builder()
            .id(1L)
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(24)
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build();

        // 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫宥夊礋椤掍焦顔囨繝寰锋澘鈧洟宕导瀛樺剹婵炲棙鎸婚悡娆撴倵閻㈡鐒鹃崯鍝ョ磼閹冪稏缂侇喗鐟╁濠氭偄閻撳海顔夐梺褰掑亰閸熲晛顭ㄩ崼鐔哄幈闂佹寧妫侀褔鐛幇鐗堢厱閻庯綆鍋呭畷宀勬煛娴ｇ懓濮堥柟顖涙閸ㄦ儳鐣烽崶璺烘櫔婵犵數濮烽。浠嬪焵椤掆偓閸熻法鐥閺岀喖顢欓悡搴樺亾閸噮鍤曞┑鐘崇閸嬪嫰鏌涘☉姗堝伐妞ゅ孩鎹囧娲川婵犲倸顫庨梺绋款儐閹稿墽妲愰悙鍝勯唶婵犲灚鍔栫€?闂傚倸鍊搁崐鎼佸磹閻戣姤鍊块柨鏃堟暜閸嬫挾绮☉妯诲櫧闁活厽鐟╅弻鐔告綇妤ｅ啯顎嶉梺绋款儏椤戝懘鍩為幋锔藉亹闁告瑥顦伴幃娆忊攽椤旀娼愰柣鎿勭節閻涱噣寮介妸锔剧Ф闂佸憡鎸嗛崥閿嬪灴閺岋絾鎯旈～顓濆閻庤娲滈弫濠氱嵁婢跺瞼鐭欓幖瀛樻尰閺傗偓闂備礁澹婇崑鍡涘闯閵夈儺鐒?闂傚倸鍊搁崐宄懊归崶褏鏆﹂柣銏㈩焾绾惧鏌ｉ幇顔煎妺闁搞倕鑻灃闁挎繂鎳庨弸銈夋煛娴ｇ顏柡宀€鍠撻埀顒傛暩椤牊鐗庢俊?
        testBatch2 = InventoryBatch.builder()
            .id(2L)
            .batchCode("BATCH002")
            .product(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(8)
            .initialQuantity(8)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .build();
    }

    // ========== 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄦ硾椤繈濡搁埡浣侯攨濠殿喗顭堥崺鏍磹閻㈠憡鐓熼柕蹇曞У閸熺偞绻涢崨顔剧煁缂佺粯绋戦悾婵囩節娓氣偓閸嬫姊洪崫鍕拱缂佸鍨块幃鎯р攽閸艾浜鹃柨婵嗙凹閹茬偓绻涘顔煎箻缂佽鲸鎸婚幏鍛存偡閺夋娼旈梻渚€娼уΛ娆撳Υ閹?闂傚倸鍊搁崐鎼佸磹瀹勬噴褰掑炊閵娧呭骄闂佸壊鍋嗛崰鍡樼閸儲鐓欓梻鍌氼嚟閸斿秹鏌涢妶鍛板閾绘牠鏌ㄥ┑鍡樺櫣闁哄棙鐟х槐鎺楁偐瀹曞洤鈷屽Δ鐘靛仦閻楁顭囪箛娑樼鐟滃繗鎽梻鍌欑閹芥粍鎱ㄩ弶鎳虫稑鈹戠€ｎ亣鎽曞┑鐐村灟閸ㄥ綊鎮炲ú顏呯厱闁归偊鍠栨禍楣冩煟閿濆妫戠紒杈ㄦ尰閹峰懐绮欐惔鎾充壕闁归棿鐒﹂崑瀣攽閻樻彃顏€规洖寮剁换娑㈠箣濞嗗繒浠肩紓浣哄珡閸ャ劎鍘甸梺鍦拡閸樺ジ鐛弽顓熺厱闁规崘鍩栭弳顒佹叏?==========

    @Test
    @DisplayName("case-2")
    void testGetSummary_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(pageable, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        InventorySummaryDto summary = result.getContent().get(0);
        assertThat(summary.getProductId()).isEqualTo(1L);
        assertThat(summary.getSkuInfo().getName()).isEqualTo("婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佲枙闁绘帟濮ょ换娑㈠幢濡粯鍎庨梺杞扮鐎氫即寮诲☉銏╂晝闁绘ɑ褰冩慨鏇㈡⒑缁嬪潡顎楅柨鏇樺劦婵＄敻宕熼姘敤濡炪倖鍔﹀鈧繛宀婁邯濮婃椽骞栭悙娴嬪亾閺嶎厼鍨傞柛褎顨呯粻鏍喐閻楀牆绗掑ù鑲╁█閺屾盯寮撮妸銉ヮ潽闂佺瀛╅幐缁樼┍?");
        assertThat(summary.getSkuInfo().getSkuCode()).isEqualTo("6901234567890");
        assertThat(summary.getDisplayQuantity()).matches("^2.*\\+\\s*8.*$");
        assertThat(summary.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);  // 32 < 50
        assertThat(summary.getFurthestExpiryDate()).isEqualTo(LocalDate.now().plusMonths(6));
    }

    @Test
    @DisplayName("case-3")
    void testGetSummary_WithSearch() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        String searchKeyword = "test";
        when(productRepository.findByNameContaining(searchKeyword))
            .thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(pageable, searchKeyword);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).findByNameContaining(searchKeyword);
    }

    @Test
    @DisplayName("case-4")
    void testDisplayQuantity_OnlyFullBoxes() {
        // Given
        InventoryBatch fullBoxBatch = InventoryBatch.builder()
            .quantity(36)  // 3缂?= 36闂?
            .product(testProduct)
            .active(true)
            .build();

        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(fullBoxBatch));
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(36);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).matches("^3.*\\+\\s*0.*$");
    }

    @Test
    @DisplayName("case-5")
    void testDisplayQuantity_OnlyLooseItems() {
        // Given
        InventoryBatch looseBatch = InventoryBatch.builder()
            .quantity(8)  // 8闂傚倸鍊搁崐宄懊归崶褏鏆﹂柣銏㈩焾绾惧鏌ｉ幇顔煎妺闁搞倕鑻灃闁挎繂鎳庨弸銈夋煛娴ｇ顏柡灞剧洴閸╁嫰宕橀浣诡潔婵犳鍠涢～澶愭偂閿熺姷宓侀柡宥冨妽缂嶅洭鏌涢幘妤€鎯欓幋锔界厽?
            .product(testProduct)
            .active(true)
            .build();

        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(8);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).matches("^0.*\\+\\s*8.*$");
    }

    @Test
    @DisplayName("case-6")
    void testDisplayQuantity_MixedBoxesAndLoose() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));  // 24闂?2缂? + 8闂?
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).matches("^2.*\\+\\s*8.*$");
    }

    @Test
    @DisplayName("case-7")
    void testStockStatus_Sufficient() {
        // Given
        testProduct.setSafetyStock(30);  // 闂傚倸鍊搁崐宄懊归崶顒夋晪鐟滃秹婀侀梺缁樺灱濡嫮绮婚悩缁樼厵闁诡垎鍐╊啈闂佹悶鍎洪崜锕傚极瀹ュ鐓熼柟閭﹀灠閻ㄦ椽鏌ｉ幘鏉戠伌婵☆偄鎳橀、鏇㈠閳ユ剚妲辨繝娈垮枛閿曘倗绱炴繝鍌滄殾闁哄洢鍨洪崑鍕煕韫囨艾浜归柛妯兼暬濮婅櫣鎷犻垾宕団偓濠氭煕濠婂啫鏆熺紒鈧繝鍥ㄢ拻?30
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);  // 闂傚倸鍊搁崐宄懊归崶顒夋晪鐟滃酣銆冮妷褏鐭欓柛鏌倐鍋撻崸妤佲拺妞ゆ巻鍋撶紒澶婎嚟婢规洘绻濆顓犲帾闂佸壊鍋呯换鍐╁垔婵傚憡鐓熼幖娣€曢悡鎰版煏閸パ冾伃鐎殿喕绮欐俊姝岊槷濠殿喖娲铏圭矙閹稿孩宕抽梺鍝ュУ閻楃姴顕ｆ繝姘亜闁绘挸娴烽悾楣冩偡濠婂啰绠婚柡浣哥Т椤撳吋寰勭€ｎ剙骞?32

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.SUFFICIENT);
    }

    @Test
    @DisplayName("case-8")
    void testStockStatus_LowStock() {
        // Given
        testProduct.setSafetyStock(50);  // 闂傚倸鍊搁崐宄懊归崶顒夋晪鐟滃秹婀侀梺缁樺灱濡嫮绮婚悩缁樼厵闁诡垎鍐╊啈闂佹悶鍎洪崜锕傚极瀹ュ鐓熼柟閭﹀灠閻ㄦ椽鏌ｉ幘鏉戠伌婵☆偄鎳橀、鏇㈠閳ユ剚妲辨繝娈垮枛閿曘倗绱炴繝鍌滄殾闁哄洢鍨洪崑鍕煕韫囨艾浜归柛妯兼暬濮婅櫣鎷犻垾宕団偓濠氭煕濠婂啫鏆熺紒鈧繝鍥ㄢ拻?50
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);  // 闂傚倸鍊搁崐宄懊归崶顒夋晪鐟滃酣銆冮妷褏鐭欓柛鏌倐鍋撻崸妤佲拺妞ゆ巻鍋撶紒澶婎嚟婢规洘绻濆顓犲帾闂佸壊鍋呯换鍐╁垔婵傚憡鐓熼幖娣€曢悡鎰版煏閸パ冾伃鐎殿喕绮欐俊姝岊槷濠殿喖娲铏圭矙閹稿孩宕抽梺鍝ュУ閻楃姴顕ｆ繝姘亜闁绘挸娴烽悾楣冩偡濠婂啰绠婚柡浣哥Т椤撳吋寰勭€ｎ剙骞?32

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
    }

    // ========== 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄥ煐缁岄亶鎮滃Ο璇插伎闂佽鍎兼慨銈夋偂閸愵喗鐓曟繝闈涙椤忊晠鏌￠崱妯荤缂佺粯绋戦悾婵囩節娓氣偓閸嬫姊洪崫鍕拱缂佸鍨块幃鎯р攽閸艾浜鹃柨婵嗙凹閹茬偓绻涘顔煎箻缂佽鲸鎸婚幏鍛存偡閺夋娼旀俊鐐€戦崝宀勬晝椤忓嫷鍤曞┑鐘崇閸嬪嫰鏌涘☉姗堝伐妞ゅ孩鎹囧娲川婵犲倸顫庨梺绋款儐閹稿墽妲愰悙鍝勯唶婵犲灚鍔栫€靛矂姊洪棃娑氬婵☆偅顨嗛幈銊ョ暆閸曨剛鍘遍柣搴秵閸嬪嫭鎱ㄥ鍡╂闁绘劘灏欑粻濠氭煙椤旇娅囩紒杈ㄥ笒铻ｇ紓浣诡焽瑜板棝姊婚崒娆愮グ濠殿喓鍊濋弫瀣渻閵堝繐鐦滈柛銊ㄤ含缁晠鎮㈤悡搴ㄥ敹闂侀潧顧€婵″洭宕㈡禒瀣拺缂備焦蓱閻撱儵鏌熼懞銉х煉妤犵偛锕畷姗€鍩￠埀顒傛崲閸℃ǜ浜滈柟鏉垮缁夊灚绻涢崼鐔割棃婵☆偂鐒﹀鍕偓锝傛櫇缁犳艾顪冮妶鍡欏闁告繂閰ｅ畷鎴﹀Ω閳哄倻鍘遍梺鎸庢椤曆囩嵁閹扮増鐓曢悗锝庡亝瀹曞矂鏌℃担鐟板闁诡垱妫冮崹鎯х暦閸ヨ泛鏅繝?==========

    @Test
    @DisplayName("case-9")
    void testGetDetailsBySkuId_Success() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result).hasSize(2);

        // 濠电姷鏁告慨鎾儉婢舵劕绾ч幖瀛樻尭娴滅偓淇婇妶鍕妽闁告瑥绻橀弻鐔虹磼閵忕姵鐏嶉梺绋垮椤ㄥ懘濡撮幒鎴僵闁挎繂鎳嶆竟鏇㈡⒒娴ｇ瓔鍤冮柛顭戝灦閹偤鏁冮埀顒勵敋閵夆晛绀嬫い鏍ㄦ皑閻も偓婵＄偑鍊栫敮鎺楀磻閸℃稑鑸归悹楦裤€€閺€浠嬫煟濮楀棗鏋涢柣蹇氶哺缁绘稒寰勭€ｎ剚鍒涘銈冨灪閻楃姴鐣烽崡鐐╂瀻闁瑰瓨绮庨悷婵嬫⒒娴ｅ憡璐￠柛搴涘€濋妴鍐幢濞戞瑥浜楀┑鐐村灦閳笺倛銇愰幒鎾存珳闂佸憡渚楅崰妤呭窗閹邦厾绡€闁冲皝鍋撻柛鏇ㄥ幐婵洨绱撴担铏瑰笡缂佽鐗撳畷娲焵椤掍降浜滈柟鐑樺灥椤忣亪鏌￠崨顔藉€愰柡宀嬬到铻ｇ紓浣诡焽娴煎洭姊洪崷顓х劸闁哥喐鎸抽獮鍐ㄎ旈崨顖氱ウ闂佸壊鐓堥崰鏍ㄦ叏鎼淬劍鈷戠痪顓炴噺閻濐亪鏌涢弮鈧〃濠傜暦閻楀牏绡€闁稿被鍊曢幃鎴炵節閵忥絾纭炬い鎴濇喘瀹曘垽鎮介崨濞炬嫼缂傚倷鐒﹂敋闁诲骏绠撻弻銊ヮ潩鏉堚晝锛滃┑掳鍊撶粈浣该归鈧弻锛勪沪閸撗勫垱闂佺偨鍎荤粻鎾荤嵁鐎ｎ亶鐓ラ柛顐ｇ箑缁綁姊婚崒娆愮グ婵℃ぜ鍔庣划鍫熸媴閾忓湱鐓嬮梺鑽ゅ枑婢瑰棝寮抽敃鈧埞鎴﹀磼濮橆厼鏆堥梺绋款儏鐎氭澘顫忔繝姘劦妞ゆ帒瀚粻娑欍亜閺冨洤浜规い鎰偢濮婂宕掑顑藉亾妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈡晝閳ь剛澹曡ぐ鎺撶厽闁硅揪绲鹃ˉ澶岀棯椤撴稑浜鹃梻鍌欐祰椤宕曢崗鍏煎弿闁靛牆顦壕鎸庝繆閵堝懏鍣洪柣鎾跺枛閺岀喖鏌囬敃鈧弸娑㈡煕閺傛鍎旈柡灞剧⊕閹棃鏁嶉崟鎳峰洦鐓涢悘鐐插⒔濞插鈧鍠楅幐铏繆閹间礁唯闁靛牆娲ら崵顒勬⒒閸屾艾鈧悂鈥﹂鍕闁挎洖鍊哥粈鍫ユ煟閺冨倸甯堕柦鍐枑缁绘盯骞嬪▎蹇曚患缂備胶濮惧畷鐢稿焵椤掑喚娼愭繛鍙夌墪鐓ら柣妯款嚙閸戠姷鈧厜鍋撻柍褜鍓涘Σ?
        assertThat(result.get(0).getBatchCode()).isEqualTo("BATCH001");  // 6濠电姷鏁告慨鐑藉极閹间礁纾婚柣鎰惈閸ㄥ倿鏌涢锝嗙缂佺姴缍婇弻宥夊传閸曨偀鍋撴繝姘モ偓鍛村矗婢跺瞼鐦堥梻鍌氱墛缁嬫帡鏁嶉弮鍫熺厾闁哄瀵ч崑銉╂煛鐏炵晫啸妞ぱ傜窔閺屾盯骞樼捄鐑樼亪閻庢鍠涢褔鍩ユ径濠庢建闁糕剝锚閸忓﹥淇婇悙顏勨偓鏍暜婵犲嫮鐭嗗〒姘ｅ亾鐎殿喓鍔戦、姗€鎮╅悽纰夌闯濠电偠鎻徊鍧楁偤閺冨牆鍚规繛鍡樻尰閻撴洟鏌ｉ弬鎸庡暈缂傚秵鍨堕妵?
        assertThat(result.get(0).getExpiryDate()).isEqualTo(LocalDate.now().plusMonths(6));
        assertThat(result.get(1).getBatchCode()).isEqualTo("BATCH002");  // 3濠电姷鏁告慨鐑藉极閹间礁纾婚柣鎰惈閸ㄥ倿鏌涢锝嗙缂佺姴缍婇弻宥夊传閸曨偀鍋撴繝姘モ偓鍛村矗婢跺瞼鐦堥梻鍌氱墛缁嬫帡鏁嶉弮鍫熺厾闁哄瀵ч崑銉╂煛鐏炵晫啸妞ぱ傜窔閺屾盯骞樼捄鐑樼亪閻庢鍠涢褔鍩ユ径濠庢建闁糕剝锚閸忓﹥淇婇悙顏勨偓鏍暜婵犲嫮鐭嗗〒姘ｅ亾鐎殿喓鍔戦、姗€鎮╅悽纰夌闯濠电偠鎻徊鍧楁偤閺冨牆鍚规繛鍡樻尰閻撴洟鏌ｉ弬鎸庡暈缂傚秵鍨堕妵?
        assertThat(result.get(1).getExpiryDate()).isEqualTo(LocalDate.now().plusMonths(3));
    }

    @Test
    @DisplayName("case-10")
    void testBatchDetail_FullBoxStatus() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(testBatch1));  // 24闂?= 2缂?

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result.get(0).getPackageStatus()).isEqualTo("\uD83D\uDCE6 \u6574\u7bb1");
    }

    @Test
    @DisplayName("case-11")
    void testBatchDetail_LooseStatus() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(testBatch2));  // 8闂傚倸鍊搁崐宄懊归崶褏鏆﹂柣銏㈩焾绾惧鏌ｉ幇顔煎妺闁搞倕鑻灃闁挎繂鎳庨弸銈夋煛娴ｇ顏柡灞剧洴閸╁嫰宕橀浣诡潔婵犳鍠涢～澶愭偂閿熺姷宓侀柡宥冨妽缂嶅洭鏌涢幘妤€鎯欓幋锔界厽?

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result.get(0).getPackageStatus()).isEqualTo("\uD83D\uDCED \u6563\u8d27");
    }

    @Test
    @DisplayName("case-12")
    void testGetDetailsBySkuId_EmptyList() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.emptyList());

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result).isEmpty();
    }

    // ========== 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄦ硾椤繈濡歌娑撳秹鏌￠崒娑崇穿鐟滅増甯楅弲鏌ユ煕椤愩倕鏋傞柍褜鍓欓崯顖滄崲濞戞鏆嗗┑鐘辩窔閸嬫姊洪崫鍕拱缂佸鍨块幃鎯р攽閸艾浜鹃柨婵嗙凹閹茬偓绻涘顔煎箻缂佽鲸鎸婚幏鍛存偡閺夋娼旀俊鐐€戦崝宀勬晝椤忓牊鍋樻い鏇楀亾鐎殿喕绮欓、鏇㈡偄閾氬倸顥氭繝鐢靛仦閸ㄥ爼顢旀导鏉戠闁绘浜崜銊ヮ渻閵堝棗濮傞柛濠冾殘婢规洟鎮剧仦绋夸壕妤犵偛鐏濋崝姘舵煙閸愯尙绠荤€规洜鏁诲畷鍫曞煘閹傚闁荤喐鐟ョ€氼厾绮氱捄銊х＜闂婎偒鍘鹃惌娆愩亜閵忊埄鎴犵紦閻ｅ瞼鐭欓柛顭戝枦閳ь剙鐏濋埞鎴︽倷閸欏妫炵紓浣虹帛閸旀妲愰幒妤€鐓涢柛娑卞枓閹锋椽姊洪悡搴綗闁稿﹥娲熷鎼佸箣閿旂晫鍘介梺缁樻⒐濞兼瑩宕濆鑸垫嚉闁绘劗绻濈换鍡涙煏閸繂鈧憡绂嶆ィ鍐┾拻闁搞儜灞拘ф繛锝呮搐閿曨亪銆佸☉姗嗘僵闁靛鍎扮紓鎾绘⒒娓氣偓濞艰崵鈧潧鐭傚畷銏ゅ箹娴ｅ摜锛涢梺鐟板⒔缁垶寮查弻銉ョ缂侇喖鍘滈崑鎾崇暦閸ヨ泛鏅繝?==========

    @Test
    @DisplayName("case-13")
    void testGetLocationView_Success() {
        // Given
        String locationCode = "WH01-ZONE_A-A-01-001";
        when(locationRepository.findByLocationCode(locationCode))
            .thenReturn(Optional.of(testLocation));
        when(inventoryBatchRepository.findByLocationCodeAndActive(locationCode, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));

        // When
        LocationViewDto result = inventoryQueryService.getLocationView(locationCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLocationCode()).isEqualTo(locationCode);
        assertThat(result.getWarehouseName()).isEqualTo("婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佲枙闁绘帟濮ょ换娑㈠幢濡粯鍎庨梺杞扮鐎氫即寮诲☉銏╂晝闁绘ɑ褰冩慨鏇㈡⒑缁嬪潡顎楅柨鏇樺劦婵＄敻宕熼姘敤闂侀潧臎娴ｆ彃浜鹃柟鐑樻尪娴滄粓鏌嶉崫鍕殭闁告ɑ鎸抽弻鈥崇暆閳ь剟宕伴弽顓犲祦闁糕剝鍑瑰Σ楣冩⒑閹稿海鈽夌紒澶屾暬楠?");
        assertThat(result.getBatches()).hasSize(2);
    }

    @Test
    @DisplayName("case-14")
    void testGetLocationView_LocationNotFound() {
        // Given
        String locationCode = "INVALID-CODE";
        when(locationRepository.findByLocationCode(locationCode))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inventoryQueryService.getLocationView(locationCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID-CODE");
    }

    @Test
    @DisplayName("case-15")
    void testGetLocationView_NoBatches() {
        // Given
        String locationCode = "WH01-ZONE_A-A-01-001";
        when(locationRepository.findByLocationCode(locationCode))
            .thenReturn(Optional.of(testLocation));
        when(inventoryBatchRepository.findByLocationCodeAndActive(locationCode, true))
            .thenReturn(Collections.emptyList());

        // When
        LocationViewDto result = inventoryQueryService.getLocationView(locationCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBatches()).isEmpty();
    }

    // ========== 闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曚綅閸ヮ剦鏁嶉柣鎰綑娴滈亶姊虹化鏇炲⒉妞ゃ劍鍔欓幊鎾诲锤濡や讲鎷哄銈嗗坊閸嬫挾绱掓径瀣唉闁诡喖娼￠幃娆擃敄閸欍儳鐩庨梻浣筋潐濠㈡ɑ鏅舵惔銊﹀仼閺夊牃鏅濈壕濂告煟濡搫鏆遍柛婵囨そ閺岋紕浠﹂崜褉妲堥梺瀹犳椤﹂潧鐣烽敓鐘冲€烽柤纰卞墻閸熷洭姊婚崒娆戭槮闁圭⒈鍋婇、鏍川閺夋垵鐝樺銈嗗笒閸婄懓鐣烽崣澶岀闁瑰瓨鐟ラ悘鈺冪磼閻欐瑥娲﹂悡娆撴煙绾板崬骞栨鐐寸墵閺屾盯骞橀懜鍨瘓濠?==========

    @Test
    @DisplayName("case-16")
    void testEmptyInventory() {
        // Given
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(null);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).matches("^0.*\\+\\s*0.*$");
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(result.getContent().get(0).getFurthestExpiryDate()).isNull();
    }

    @Test
    @DisplayName("case-17")
    void testBatchWithNullExpiryDate() {
        // Given
        testBatch1.setExpiryDate(null);
        testBatch2.setExpiryDate(null);

        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getExpiryDate()).isNull();
        assertThat(result.get(1).getExpiryDate()).isNull();
    }

    @Test
    @DisplayName("case-18")
    void testPagination() {
        // Given
        ProductSpu spu = ProductSpu.builder().id(1L).spuCode("SPU001").spuName("Test SPU").build();
        Product product1 = Product.builder().id(1L).barcode("001").name("Product 1").spu(spu).packUnit("pcs").conversionRate(12).safetyStock(50).build();
        Product product2 = Product.builder().id(2L).barcode("002").name("Product 2").spu(spu).packUnit("pcs").conversionRate(12).safetyStock(50).build();
        Product product3 = Product.builder().id(3L).barcode("003").name("Product 3").spu(spu).packUnit("pcs").conversionRate(12).safetyStock(50).build();

        when(productRepository.findAll()).thenReturn(Arrays.asList(product1, product2, product3));
        when(inventoryBatchRepository.findByProductIdAndActive(anyLong(), eq(true)))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.sumQuantityByProduct(anyLong())).thenReturn(0);

        // When - 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄦ硾椤繈濡搁埡浣侯攨濠殿喗顭堥崺鏍磹閸偆绠鹃柟瀵稿剱娴煎嫭绻濇繝鍌氼伀妞も晛寮剁换婵囩節閸屾稑娅у┑鈽嗗亽閸ㄥ爼寮婚埄鍐ㄧ窞閻庯綁娼ч崝灞解攽闄囩亸顏勨枍閿濆洦顫曢柟鐑橆殔閻掑灚銇勯幒宥囶槮缂佸墎鍋ら幃妤呮晲鎼粹€茬盎閻庢鍠栧鈥愁潖濞差亜绠伴幖杈剧悼閻ｇ敻姊虹涵鍛彧闁告梹顨堥崚鎺旂磼濡偐鐦堝┑顔斤供閸橀箖宕?闂?
        Page<InventorySummaryDto> page1 = inventoryQueryService.getSummary(PageRequest.of(0, 2), null);

        // Then
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(3);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        // When - 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍫曞焵椤掆偓閸熷磭绮诲☉妯锋婵☆垳鈷堝Σ顖涚節閻㈤潧浠﹂柛銊ㄥ煐缁岄亶鎮滃Ο璇插伎闂佽鍎兼慨銈夋偂閸愵喗鐓曟繝闈涙椤忊晠鏌嶈閸撴繈宕洪弽顐ょ焿?
        Page<InventorySummaryDto> page2 = inventoryQueryService.getSummary(PageRequest.of(1, 2), null);

        // Then
        assertThat(page2.getContent()).hasSize(1);
    }
}

