package com.wms.system.repository;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.StocktakeCycleType;
import com.wms.system.entity.enums.StocktakeStatus;
import com.wms.system.entity.enums.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StocktakeItemRepository 闂傚倸鍊风粈渚€骞夐敓鐘偓鍐幢濡炴洘妞藉浠嬵敇閻愭彃浜堕梻浣筋潐瀹曟绮旈鈧畷鐑筋敇濞戞ü澹曢梺鎸庣箓妤犳悂鐛Ο璁崇箚?
 *
 * V3.8 闂傚倸鍊风粈渚€骞栭锔绘晞闁割偁鍎遍弰銉╂煛瀹ュ骸骞楅柛濠傜仛閵囧嫰寮介顫勃闂佸搫鎷嬮崜姘跺箞閵娿儺娼ㄩ柛鈩冾殔缁犲搫顪冮妶鍛寸崪缂佺粯绻堝濠氬Χ閸涱垱娈鹃梺鎼炲劘閸斿秹鎮甸鍕ㄦ斀闁绘劖娼欑徊鑽ょ磼缂佹﹫鑰挎鐐插暙閳诲酣骞橀搹顐㈢ザ婵＄偑鍊栭幐鍫曞垂瑜版帒鍌ㄦ繛鍡樺灩绾?
 *
 * 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁?StocktakeItemRepository 闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯圭矙瀵尙鎹勬笟顖氭倯婵犮垼娉涢敃銈夋偟濡崵绡€闁靛骏绲剧涵鎯旈悩娈跨劸闁崇粯鎸荤€佃偐鈧稒锚閳ь剛鏁婚幃宄扳枎韫囨搩浼€闂佺粯鎼╅崹宕囨閹烘鍋愰梻鍫熺⊕閻忓牓鎮楀▓鍨灈妞ゎ參鏀辨穱濠囨倻鐟欙絺鍋撻敃鍌氱婵犻潧娲ㄩ弳鐘绘⒒閸屾瑧顦﹂柟纰卞亰瀹曟劖绂掔€ｅ墎绋忔繝銏ｆ硾閺堫剟宕楀鍫熺厱妞ゆ劧绲剧粈鍐煛?
 * 1. findByTaskId() - 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢湁闁挎繂鎳忕拹鈩冦亜閳哄啫鍘撮柟顔肩秺瀹曞爼濡搁妷褏銈锋俊鐐€ら崑鎺懳涚粙銈夋⒒閸屾瑧顦﹂柟纰卞亞閹噣顢曢敃鈧壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崨顔兼闂佺楠哥€涒晜绌辨繝鍥舵晬婵犲﹤鍟禒妯荤箾鐎电顎岄柛銊ョ埣瀵鏁撻悩鑼槹濡炪倖鍔х徊鎯х暦瀹曞洨纾藉〒姘搐閺嬫稓绱掔紒妯忣亪鎮惧畡閭︽僵妞ゆ垟鏅滃Λ鍐ㄧ暦閻旂厧鐓橀柟顖嗗倸顥?
 * 2. findByTaskIdAndIsCounted() - 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢湁闁挎繂鎳忕拹鈩冦亜閳哄啫鍘撮柟顔肩秺瀹曞爼濡搁妷褏銈锋俊鐐€ら崑鎺懳涚粙銈夋⒒閸屾瑧顦﹂柟璇х磿閸掓帒鐣濋崟顒€鍓堕梺缁樻尭缁″啯銈︾捄銊ф澑濠电偞鍨堕悷銉╁焵椤掑倸鍘撮柡灞剧洴閺佸倻鎷犻幓鎺旑啇缂備焦顨嗛崹鍨潖閾忚瀚氶柍銉ュ暱楠炴劙姊虹紒妯洪嚋缂佺姵鎹囬獮鍐晸閻樿櫕娅栭梺鍛婃处閸撴瑩鎮垫导瀛樷拺闂傚牊绋撶粻姘舵煕閹惧绠為柨婵堝仱瀵粙顢曢妶鍥风闯濠电偠鎻徊浠嬪箟閿熺姄澶婎煥閸喓鍘卞銈庡幗閸ㄥ磭鑺辨禒瀣厵?
 * 3. countByTaskIdAndIsCounted() - 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢湁闁挎繂鎳忕拹鈩冦亜閳哄啫鍘撮柟顔肩秺瀹曞爼濡搁妷褏銈锋俊鐐€ら崑鎺懳涚粙銈夋⒒閸屾瑧顦﹂柟璇х磿閸掓帒鐣濋崟顒€鍓堕梺缁樻尭缁″啯銈︾捄銊ф澑濠电偞鍨堕悷銉╁焵椤掑倸鍘撮柡灞剧洴閺佸倻鎷犻幓鎺旑啇缂備焦顨嗛崹鍨潖閾忚瀚氶柍銉ュ暱楠炴劙姊洪悡搴℃毐濠⒀冮叄瀹曟岸骞掑Δ浣瑰劒闁荤喐鐟ョ€氼剟顢撳☉銏♀拺闁哄倶鍎插▍鍛存煕閻曚礁鐏﹂柨婵堝仱瀵粙顢曢妶鍥风闯濠电偠鎻徊浠嬪箟閿熺姄澶愭倷閻戞鍘搁梺绯曞墲閻熴儵寮稿☉銏＄厸?
 * 4. countByTaskIdAndDifferenceQtyNot() - 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢湁闁挎繂鎳忕拹鈩冦亜閳哄啫鍘撮柟顔肩秺瀹曞爼濡搁妷褏銈锋俊鐐€ら崑鎺懳涚粙銈囩磽閸屾艾鈧悂宕愰悜鑺ュ殑闁割偅娲栫粣妤呮煛閸ャ儱鐒洪柡浣告喘閺岀喖姊荤€靛壊妲柣搴㈣壘椤︻垶鈥︾捄銊﹀磯闁绘艾鐡ㄩ弫楣冩⒑瀹曞洨甯涙繛鍙夌矒閸┾偓妞ゆ帊绶￠崯蹇涙煕閻樺磭澧电€规洘鍔楅幏鐘裁圭€ｎ偆浜栭梻浣侯攰閹活亞绮婚幋锕€鐭楀┑鐘叉搐缁犲綊寮堕崼婵嗏挃闁诡喖銈搁弻锝夘敇閻愬搫寮伴梺鍝勫閸撴繈骞忛崨顓犳殝闁哄娉曢妶顕€姊?
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@DisplayName("case-1")
class StocktakeItemRepositoryTest {

    @Autowired
    private StocktakeItemRepository stocktakeItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Warehouse testWarehouse;
    private Location testLocation1;
    private Location testLocation2;
    private Product testProduct1;
    private Product testProduct2;
    private InventoryBatch testBatch1;
    private InventoryBatch testBatch2;
    private StocktakeTask testTask1;
    private StocktakeTask testTask2;
    private StocktakeItem countedItem1;
    private StocktakeItem countedItem2;
    private StocktakeItem uncountedItem;
    private StocktakeItem itemWithDifference;

    /**
     * 婵犵數濮甸鏍闯椤栨粌绶ら柣锝呮湰瀹曟煡鎮楅敐搴℃灍闁绘挸鍊圭换婵囩節閸屾艾绠婚梺闈╁瘜閸樻悂宕戦幘缁樻櫜閹肩补鈧磭顔掓繝纰樷偓鍐茬骇闁诡喖鍊垮濠氭晸閻樿尙锛滃┑鐘茬仛閸旀牜绱為崼銏㈢＜缂備降鍨归獮鏍煟閺嵮佸仮闁绘侗鍠栭濂稿醇椤愶絺鏋嗗┑锛勫亼閸婃垿宕瑰ú顏勫瀭闁割煈鍠氶弳锔界節婵犲倸鏋ら柛姘儔閺屾稑鈽夐崡鐐茬婵炲濮撮悺銊ф崲濠靛棌鏋旈柛顭戝枟閻忓牆顪冮妶鍡楃仴闁硅櫕鍔欓崺鈧い鎺嗗亾闁诲繑淇洪妵鎰板礃椤旂晫鐣抽梻浣筋嚙閸戠晫绱為崱娑樺偍闁诡垼鐏涢敐澶婄濞达絽婀遍崢鍛婄箾鏉堝墽鍒伴柟纰卞亰閵嗗倿鎳栭埞鎯т壕?
     */
    @BeforeEach
    void setUp() {
        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹邦啩婊堟煕閿旇骞愰柛瀣崌閺佹劖鎯旈垾宕囶啋婵犲痉銈呯毢妞ゎ厼鐗撳﹢渚€姊虹紒姗嗙劸婵炲懏娲滄禍鎼侇敇閻樼數锛?
        testWarehouse = Warehouse.builder()
                .code("WH001")
                .name("Test Warehouse")
                .build();
        entityManager.persist(testWarehouse);

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹邦啩婊堟煕閿旇骞愰柛瀣崌閺佹劖鎯旈垾宕囶啋婵犵鈧啿绾ч柟顔煎€块悰顕€寮介鐔蜂壕婵炴垶顏伴幋鐘辩剨妞ゆ劑鍊楃壕?
                testLocation1 = Location.builder()
                .warehouse(testWarehouse)
                .shelfNumber("A-01")
                .positionNumber("001")
                .zone(Zone.ZONE_A)
                .enabled(true)
                .build();

                testLocation2 = Location.builder()
                .warehouse(testWarehouse)
                .shelfNumber("A-01")
                .positionNumber("002")
                .zone(Zone.ZONE_A)
                .enabled(true)
                .build();

        entityManager.persist(testLocation1);
        entityManager.persist(testLocation2);

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹邦啩婊堟煕閿旇骞愰柛瀣崌閺佹劖鎯旈垾宕囶啋婵犲痉銈呯毢妞ゎ厼鐗撳﹢浣糕攽閻樻瑥鎳庡瓭闂佸摜鍣ラ崹鍫曠嵁?
                ProductSpu testSpu = ProductSpu.builder()
                .spuCode("SPU001")
                .spuName("Test SPU")
                .enabled(true)
                .build();
        entityManager.persist(testSpu);

        testProduct1 = Product.builder()
                .spu(testSpu)
                .skuName("PROD001")
                .name("Test Product A")
                .barcode("6900000000001")
                .unitPrice(new BigDecimal("100.00"))
                .nearExpiryDays(90)
                .enabled(true)
                .build();

        testProduct2 = Product.builder()
                .spu(testSpu)
                .skuName("PROD002")
                .name("Test Product B")
                .barcode("6900000000002")
                .unitPrice(new BigDecimal("50.00"))
                .nearExpiryDays(60)
                .enabled(true)
                .build();

        entityManager.persist(testProduct1);
        entityManager.persist(testProduct2);

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹邦啩婊堟煕閿旇骞愰柛瀣崌閺佹劖鎯旈垾宕囶啋婵犵鈧啿绾ч柟顔煎€垮濠氭偄閸涘﹤顎撻梺鍛婂姉閸嬫挾绱為崼婵愭富?
                testBatch1 = InventoryBatch.builder()
                .product(testProduct1)
                .batchCode("BATCH001")
                .location(testLocation1)
                .locationCode(testLocation1.getLocationCode())
                .quantity(100)
                .initialQuantity(100)
                .expiryDate(LocalDate.now().plusMonths(6))
                .active(true)
                .build();

        testBatch2 = InventoryBatch.builder()
                .product(testProduct2)
                .batchCode("BATCH002")
                .location(testLocation2)
                .locationCode(testLocation2.getLocationCode())
                .quantity(50)
                .initialQuantity(50)
                .expiryDate(LocalDate.now().plusMonths(12))
                .active(true)
                .build();

        entityManager.persist(testBatch1);
        entityManager.persist(testBatch2);

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹邦啩婊堟煕閿旇骞愰柛瀣崌閺佹劖鎯旈垾宕囶啋婵犵鈧啿绾ч柟顔煎€垮濠氭晲婢跺﹦鍘搁梺绋胯閸婃宕濋悜鑺モ拺缂佸顑欓崕婊呯磼閹绘帒鈷旂紒顔碱儔楠炴帡寮埀顒傗偓姘哺閺岀喓绱掑Ο杞板垔濠?
        testTask1 = StocktakeTask.builder()
                .taskNo("TK-202601-M01")
                .warehouseId(testWarehouse.getId())
                .cycleType(StocktakeCycleType.MONTHLY)
                .status(StocktakeStatus.COUNTING)
                .snapshotTime(LocalDateTime.now())
                .totalItems(4)
                .countedItems(2)
                .differenceItems(1)
                .createdBy(1L)
                .createdByName("user1")
                .build();

        testTask2 = StocktakeTask.builder()
                .taskNo("TK-202601-Q01")
                .warehouseId(testWarehouse.getId())
                .cycleType(StocktakeCycleType.QUARTERLY)
                .status(StocktakeStatus.CREATED)
                .snapshotTime(LocalDateTime.now())
                .totalItems(1)
                .countedItems(0)
                .differenceItems(0)
                .createdBy(1L)
                .createdByName("user1")
                .build();

        entityManager.persist(testTask1);
        entityManager.persist(testTask2);
        entityManager.flush();

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔兼倷椤掆偓婢ь垶鏌涢悢閿嬪殗闁哄被鍊楅崰濠囧础閻愬樊娼界紓浣稿⒔閸嬫捇骞冮崒鐐茶摕闁挎繂顦粻娑欍亜閺嶇數绋荤紓宥呯墕閳规垿鍨鹃崘鑼獓闂佹悶鍔屽﹢鍗炍? - 闂傚倷娴囧畷鍨叏閻㈢绀夌憸蹇曞垝婵犳艾绠ｉ柨鏃囨娴犻箖姊洪崨濠冨闁搞劑浜跺畷鏇熸償閵婏妇鍘卞┑鐘绘涧鐎氼剟宕濋幘顔界厱?
        countedItem1 = StocktakeItem.builder()
                .taskId(testTask1.getId())
                .productId(testProduct1.getId())
                .batchId(testBatch1.getId())
                .locationId(testLocation1.getId())
                .snapshotQty(100)
                .countedQty(100)
                .differenceQty(0)
                .isCounted(true)
                .countedBy(2L)
                .countedByName("user2")
                .countedAt(LocalDateTime.now())
                .build();

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔兼倷椤掆偓婢ь垶鏌涢悢閿嬪殗闁哄被鍊楅崰濠囧础閻愬樊娼界紓浣稿⒔閸嬫捇骞冮崒鐐茶摕闁挎繂顦粻娑欍亜閺嶇數绋荤紓宥呯墕閳规垿鍨鹃崘鑼獓闂佹悶鍔屽﹢鍗炍? - 闂傚倸鍊风粈渚€骞栭锔藉亱闁糕剝铔嬮崶顒夋晬婵綀鍋愰崰鎰崲濠靛纾奸柕鍫濇噺椤撳潡姊绘担渚劸闁活厼顦版穱濠囧炊閳哄啰鐒奸梺鍛婃处閸ㄩ亶鎮￠悢鍏肩厓闁告繂瀚禍鐐烘煟閹烘洖袚缂佺粯绋掑鍕幢濡崵褰嬮梻?
                countedItem2 = StocktakeItem.builder()
                .taskId(testTask1.getId())
                .productId(testProduct2.getId())
                .batchId(testBatch2.getId())
                .locationId(testLocation2.getId())
                .snapshotQty(50)
                .countedQty(55)
                .differenceQty(5)
                .isCounted(true)
                .countedBy(2L)
                .countedByName("user2")
                .countedAt(LocalDateTime.now())
                .remark("surplus 5")
                .build();

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹伴潧娈柣搴㈣壘椤︿即濡甸崟顔剧杸闁圭偓鎯屽Λ锛勭磼閸撗冧壕闁诡喖鍊垮濠氭晲婢跺﹦鐤€濡炪倖鐗徊鍓х礊閸績鏀介柍銉ュ暱缁狙囨煕閵娿儲璐℃俊?
        uncountedItem = StocktakeItem.builder()
                .taskId(testTask1.getId())
                .productId(testProduct1.getId())
                .batchId(testBatch1.getId())
                .locationId(testLocation2.getId())
                .snapshotQty(80)
                .countedQty(null)
                .differenceQty(0)
                .isCounted(false)
                .build();

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹伴潧娈柣搴㈣壘椤︻垶鈥︾捄銊﹀磯闁绘艾鐡ㄩ弫楣冩⒑瀹曞洨甯涙繛鍙夌矒閸┾偓妞ゆ帊绶￠崯蹇涙煕閻樺磭澧电€规洘鍔欓幃鐑藉箯閺冩挾绉鐐差儔閹晠鎮界喊澶屽簥闂傚倷鑳剁涵璺何ｉ崨鏉戝偍闁告挆鍛劶闂佸憡娲﹂崹閬嶆偂閻斿吋鐓冮柛婵嗗缁辨牠鏌ゅù瀣珗濠殿喛娅曢妵鍕箻閸楃偟浠鹃梺?
                itemWithDifference = StocktakeItem.builder()
                .taskId(testTask1.getId())
                .productId(testProduct2.getId())
                .batchId(testBatch2.getId())
                .locationId(testLocation1.getId())
                .snapshotQty(30)
                .countedQty(25)
                .differenceQty(-5)
                .isCounted(true)
                .countedBy(2L)
                .countedByName("user2")
                .countedAt(LocalDateTime.now())
                .remark("shortage 5")
                .build();

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箚瑜滈崵鐔搞亜閳哄啫鍘撮柟顔肩秺瀹曞爼濡搁妷褏銈锋俊?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯煎帶瀹撳嫰姊洪悷閭﹀殶濠殿噣顥撴竟?
        StocktakeItem task2Item = StocktakeItem.builder()
                .taskId(testTask2.getId())
                .productId(testProduct1.getId())
                .batchId(testBatch1.getId())
                .locationId(testLocation1.getId())
                .snapshotQty(120)
                .countedQty(null)
                .differenceQty(0)
                .isCounted(false)
                .build();

        // 闂傚倸鍊风粈浣虹礊婵犲洤鐤鹃柟缁樺俯濞撳鏌熼悜妯烩拻濞戞挸绉电换娑㈠幢濡纰嶇紓浣插亾鐎光偓閸曨剛鍘搁悗骞垮劚閸燁偅淇婇崸妤佺厪闁割偆鍠愮亸锕傛煙椤旇偐绉虹€规洖鐖兼俊鎼佹晜缂併垺袨闂傚倷绀侀幖顐⑽涘☉銏犵獥闁哄稁鍘惧畵?
        entityManager.persist(countedItem1);
        entityManager.persist(countedItem2);
        entityManager.persist(uncountedItem);
        entityManager.persist(itemWithDifference);
        entityManager.persist(task2Item);
        entityManager.flush();
    }

    @Test
    @DisplayName("case-2")
    void testFindByTaskId() {
        // When: 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯煎帶瀹撳嫰姊洪悷閭﹀殶濠殿噣顥撴竟?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(testTask1.getId());

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?4 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒鐒鹃柨婵嗩槸缁狅綁鏌ｅΟ鍏兼毄闁?
        assertThat(items).hasSize(4);
        assertThat(items)
                .allMatch(item -> item.getTaskId().equals(testTask1.getId()));
    }

    @Test
    @DisplayName("case-3")
    void testFindByTaskId_Task2() {
        // When: 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯煎帶瀹撳嫰姊洪悷閭﹀殶濠殿噣顥撴竟?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(testTask2.getId());

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?1 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒鐒鹃柨婵嗩槸缁狅綁鏌ｅΟ鍏兼毄闁?
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTaskId()).isEqualTo(testTask2.getId());
        assertThat(items.get(0).getIsCounted()).isFalse();
    }

    @Test
    @DisplayName("case-4")
    void testFindByTaskId_NoItems() {
        // When: 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯闂佸磭绮濠氬焵椤掆偓缁犲秹宕曢柆宥呯疇闁圭増婢樼粻娲煟濡偐甯涢柣鎾存礋閺屻劑寮幐搴㈠創婵犵鍓濈€笛呮崲濞戙垹閱囬柣鏃堫棑娴犲摜绱撴担绋库偓鍦暜閻愬搫绠柣妯款嚙缁犵敻鏌熼悜妯肩畺闁告牜鍤怐
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(99999L);

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊濡炪倖鎸鹃崰宥咁潩閿曞倹鈷戦柛婵嗗閳ь剙婀遍埀顒傜懗閸涱垳鐒奸梺閫炲苯澧扮紒杈ㄦ尰閹峰懎顫㈢仦绛嬩純闂備礁婀遍埛鍫ュ磻婵犲倻鏆︾憸鐗堝俯閺佸倿鏌涢埄鍏狀亝绂?
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("case-5")
    void testFindByTaskIdAndIsCounted_Counted() {
        // When: 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛鎾跺枎閻ｅ嘲鈻庨幘瀛樻闂佺粯锚閸熷潡宕滈灏栨斀闁宠棄妫楅悘锝夋煕濡姴瀚々閿嬨亜閺嶎偄浠﹂柣鎾寸洴閺屾盯寮撮妸銉ょ凹闂佸湱娅㈢徊璺ㄦ?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskIdAndIsCounted(testTask1.getId(), true);

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?3 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節婵犲倻澧曠紒鈧崟顖涚叆闁绘柨鎼牎闂佺楠哥€涒晜绌辨繝鍥舵晬婵犲﹤鍟禒妯荤箾鐎电顎岄柛銊ョ埣瀵鏁撻悩鑼槹濡炪倖鍔х徊鎯х暦瀹曞洨纾?
        assertThat(items).hasSize(3);
        assertThat(items)
                .allMatch(StocktakeItem::getIsCounted);
        assertThat(items)
                .allMatch(item -> item.getCountedQty() != null);
    }

    @Test
    @DisplayName("case-6")
    void testFindByTaskIdAndIsCounted_Uncounted() {
        // When: 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯恒偢閺佸啴濮€閵堝孩鏅ｉ梺闈涚箚閳ь剙鍟挎竟鍡樼節绾版ɑ顫婇柛銊﹀▕瀹曘垼顦崇紒鍌涘浮椤㈡盯鎮欑€电骞愰梻浣告啞濞诧箓宕戦崨鏉戠闁挎繂娲ㄧ壕?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskIdAndIsCounted(testTask1.getId(), false);

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?1 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒锛旈柤鐗堝閵囧嫰骞橀崡鐐典痪闂佺楠哥€涒晜绌辨繝鍥舵晬婵犲﹤鍟禒妯荤箾鐎电顎岄柛銊ョ埣瀵鏁撻悩鑼槹濡炪倖鍔х徊鎯х暦瀹曞洨纾?
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getIsCounted()).isFalse();
        assertThat(items.get(0).getCountedQty()).isNull();
    }

    @Test
    @DisplayName("case-7")
    void testCountByTaskIdAndIsCounted_Counted() {
        // When: 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁愭鏌￠崶銉ョ労闁轰礁娲弻娑樷槈濮楀牆濮涘銈傛櫇閸忔﹢骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛鎾跺枎閻ｅ嘲鈻庨幘瀛樻闂佺粯锚閸熷潡宕滈灏栨斀闁宠棄妫楅悘锝夋煕濡姴瀚々閿嬨亜閺嶎偄浠﹂柣鎾寸洴閺屾盯寮撮妸銉ょ凹闂佸湱娅㈢徊璺ㄦ閹烘鏁婇柤娴嬫櫇椤旀帡鎮楀▓鍨珮闁稿鎳愰埀顒勬涧閵堢顕ｉ崼鏇炵闁绘垵妫旈悽?
        long count = stocktakeItemRepository.countByTaskIdAndIsCounted(testTask1.getId(), true);

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?3 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節婵犲倻澧曠紒鈧崟顖涚叆闁绘柨鎼牎闂佺楠哥€涒晜绌辨繝鍥舵晬婵犲﹤鍟禒妯荤箾鐎电顎岄柛銊ョ埣瀵鏁撻悩鑼槹濡炪倖鍔х徊鎯х暦瀹曞洨纾?
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("case-8")
    void testCountByTaskIdAndIsCounted_Uncounted() {
        // When: 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁愭鏌￠崶銉ョ労闁轰礁娲弻娑樷槈濮楀牆濮涘銈傛櫇閸忔﹢骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯恒偢閺佸啴濮€閵堝孩鏅ｉ梺闈涚箚閳ь剙鍟挎竟鍡樼節绾版ɑ顫婇柛銊﹀▕瀹曘垼顦崇紒鍌涘浮椤㈡盯鎮欑€电骞愰梻浣告啞濞诧箓宕戦崨鏉戠闁挎繂娲ㄧ壕鍏笺亜閺冨洤浜圭紒鐘哄吹閳ь剚顔栭崳顕€宕戦崨顖楀亾闂堟稏鍋㈢€殿喖鐖奸獮瀣倻閸℃洜鏁?
        long count = stocktakeItemRepository.countByTaskIdAndIsCounted(testTask1.getId(), false);

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?1 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒锛旈柤鐗堝閵囧嫰骞橀崡鐐典痪闂佺楠哥€涒晜绌辨繝鍥舵晬婵犲﹤鍟禒妯荤箾鐎电顎岄柛銊ョ埣瀵鏁撻悩鑼槹濡炪倖鍔х徊鎯х暦瀹曞洨纾?
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("case-9")
    void testCountByTaskIdAndDifferenceQtyNot() {
        // When: 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁愭鏌￠崶銉ョ労闁轰礁娲弻娑樷槈濮楀牆濮涘銈傛櫇閸忔﹢骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯恒偢閺佸啴濮€閳╁啫顎撻梺鍛婃尰瑜板啴寮查悩宸富闁靛牆妫欓ˉ鍡涙煕鐎ｎ偄濮夊瑙勬礃缁傛帞鈧綆鍋嗛崢鎼佹⒑閸涘﹥澶勯柛瀣嚇楠炴垿鏁愰崶鈺冿紲濡炪倖妫侀崑鎰不閼姐倐鍋撳▓鍨珮闁稿鎳愰埀顒勬涧閵堢顕ｉ崼鏇炵闁绘垵妫旈悽?
        long count = stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(testTask1.getId(), 0);

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?2 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒锛旈柤鏉跨仢闇夐柨婵嗘噹椤ュ繘鏌涢悢閿嬪櫤闁靛洤瀚板顕€宕惰濮规绱掗幆褍缍栫紒顔界懇瀵鏁愭径濞⑩晠鏌曟径鍫濆姶濞寸姵鍎抽埞鎴﹀灳閸愯尙楠囬梺鎼炲妼濠€鍗炍ｉ幇鏉跨婵°倐鍋撴鐐灪閵囧嫰骞樼捄鐩掋儲銇?濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鏌ユ煟閹邦垼姊垮Δ锕佹硶閻も偓濠电偞鍨堕悷銉╁礈椤曗偓濮婃椽宕滈幓鎺嶈埅闂佸憡鎸荤粙鎾跺垝?濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鏌ユ煟閹邦垼姊垮Δ锕佹硶閻も偓闂佸搫鍟崑鍡椕洪幖浣诡棅妞ゆ劑鍨洪崒銊╂煕鎼达紕锛嶇紒?
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("case-10")
    void testCountByTaskIdAndDifferenceQtyNot_NoDifference() {
        // When: 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁愭鏌￠崶銉ョ労闁轰礁娲弻娑樷槈濮楀牆濮涘銈傛櫇閸忔﹢骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯恒偢閺佸啴濮€閳╁啫顎撻梺鍛婃尰瑜板啴寮查悩宸富闁靛牆妫欓ˉ鍡涙煕鐎ｎ偄濮夊瑙勬礃缁傛帞鈧綆鍋嗛崢鎼佹⒑閸涘﹥澶勯柛瀣嚇楠炴垿鏁愰崶鈺冿紲濡炪倖妫侀崑鎰不閼姐倐鍋撳▓鍨珮闁稿鎳愰埀顒勬涧閵堢顕ｉ崼鏇炵闁绘垵妫旈悽?
        long count = stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(testTask2.getId(), 0);

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?0 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒锛旈柤鏉跨仢闇夐柨婵嗘噹椤ュ繘鏌涢悢閿嬪櫤闁靛洤瀚板顕€宕惰濮规绱掗幆褍缍栫紒顔界懇瀵鏁愭径濞⑩晠鏌曟径鍫濆姶濞寸姵鍎抽埞鎴﹀灳閸愯尙楠囬梺鎼炲妼濠€鍗炍?
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("case-11")
    void testSaveNewItem() {
        // Given: 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹伴潧娈┑鈽嗗亝閿曘垽寮婚悢灏佹灁闁割煈鍠楅悘宥囩磼閸撗冧壕闁诡喖鍊垮濠氭晲婢跺﹦鐤€濡炪倖鐗徊鍓х礊閸績鏀介柍銉ュ暱缁狙囨煕閵娿儲璐℃俊?
        StocktakeItem newItem = StocktakeItem.builder()
                .taskId(testTask2.getId())
                .productId(testProduct2.getId())
                .batchId(testBatch2.getId())
                .locationId(testLocation2.getId())
                .snapshotQty(60)
                .countedQty(null)
                .differenceQty(0)
                .isCounted(false)
                .build();

        // When: 濠电姷鏁搁崕鎴犲緤閽樺娲晜閻愵剙搴婇梺绋跨灱閸嬬偤宕戦妶澶嬬厪濠电偟鍋撳▍鍡涙煕鎼粹€愁劉濞ｅ洤锕、娑橆煥閸愩劋绮┑鐐差嚟婵參宕归崼鏇炶摕闁跨喓濮寸粈瀣亜閹板墎绋荤€规洖纾槐?
        StocktakeItem saved = stocktakeItemRepository.save(newItem);

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顓涘亾閿濆骸鍘撮柡鈧崘娴嬫斀闁绘绮☉褎淇婇锝囨创妤犵偞顨婇幃鈺冪磼濡厧骞嶉梺璇插缁嬫帡鏁嬫繝娈垮枛濞层倝鍩?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂傚倸鍊搁崐鐑芥倿閿曚降浜归柛鎰典簽閻捇鏌ｉ姀銏╃劸闁藉啰鍠庨埞鎴︽偐閸欏鎮欓梺钘夊暟閸犳牠寮婚弴鐔风窞婵☆垳鍘х敮銉╂⒑閹肩偛鈧洜鈧凹鍓熷﹢渚€姊虹紒妯忚偐鎷冮敃鍌氬惞婵炲棗绻嗛弨?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛妯煎帶瀹撳嫰姊洪悷閭﹀殶濠殿噣顥撴竟?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(testTask2.getId());

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?2 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒鐒鹃柨婵嗩槸缁狅綁鏌ｅΟ鍏兼毄闁?
        assertThat(items).hasSize(2);
    }

    @Test
    @DisplayName("case-12")
    void testCountItem() {
        // Given: 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫╅柣搴㈣壘椤︿即濡甸崟顔剧杸闁圭偓鎯屽Λ锛勭磼閸撗冧壕闁诡喖鍊垮濠氭晲婢跺﹦鐤€濡炪倖鐗徊鍓х礊閸績鏀介柍銉ュ暱缁狙囨煕閵娿儲璐℃俊?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskIdAndIsCounted(testTask1.getId(), false);
        StocktakeItem item = items.get(0);

        // When: 闂備浇宕甸崰鎰垝鎼淬垺娅犳俊銈傚亾閻撱倝鏌″搴″箹闁活厽顨婇弻娑㈠焺閸愵亖濮囬梺绋块鐎涒晜绌辨繝鍥舵晬婵犲﹤鍟禒妯荤箾鐎电顎岄柛銊ョ埣瀵鍩勯崘鈺侇€撻悗鐟板濠㈡绮婇鈧?
        item.setCountedQty(82);
        item.setDifferenceQty(item.calculateDifference());
        item.setIsCounted(true);
        item.setCountedBy(3L);
        item.setCountedByName("user3");
        item.setCountedAt(LocalDateTime.now());
        stocktakeItemRepository.save(item);

        // 婵犵數濮烽弫鎼佸磻閻愬搫绠伴柟闂寸缁犵姵淇婇婵勨偓鈧柡瀣Ч楠炴牕菐椤掆偓婵¤偐绱掗悩铏凡闂囧鏌ㄥ┑鍡╂▓婵☆偅鍨块弻锝夋晲閸曨厾鐓撻梺鍝勬湰閻╊垰顕ｆ禒瀣╃憸宥夊焻閸撲胶纾藉ù锝堫嚃濞堟棃鏌涙繝鍌涜础缂侇喗鐟﹀鍕沪缁嬪じ澹曢梺鎸庣箓妤犲憡绂嶅┑瀣厽閹兼惌鍠栧▍宥夋煛鐏炶濡奸柍钘夘槸铻ｉ柛顭戝櫘娴煎啴鏌ｆ惔銈庢綈闁诡喖鍊圭粋宥囨崉閾忚娈惧┑顔筋焾濞夋盯鏌嬮崶顒佺厸闁搞儮鏅涙禒锔姐亜韫囨洖鏋涙慨濠冩そ瀹曨偊宕熼鐔蜂壕缂備焦蓱濞呯姵淇婇妶鍛櫤闁稿鍊块弻銊モ槈濡警浠鹃柣?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ巻鍋撶€规挸妫濋弻锝嗘償閵忊懇濮囬柦鍐憾閺岋綁骞橀姘闂傚倸鍊烽懗鍫曞箠閹剧粯鍋ら柕濞炬櫅缁€澶愭煛閸モ晛鏋戦柛?
        StocktakeItem reloaded = stocktakeItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getCountedQty()).isEqualTo(82);
        assertThat(reloaded.getDifferenceQty()).isEqualTo(2); // 82 - 80 = 2
        assertThat(reloaded.getIsCounted()).isTrue();
        assertThat(reloaded.getCountedBy()).isEqualTo(3L);
    }

    @Test
    @DisplayName("case-13")
    void testUpdateItem() {
        // Given: 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ閹存繆瀚伴柡鍛█濮婃椽鎳栭埞鐐珱闂佸憡鎸诲銊︾珶閺囥垹绠瑰ù锝呮贡閸樹粙姊鸿ぐ鎺戜喊闁搞劎鍘ч…鍥箛椤撴繄绠氶梺鍛婄懃椤︻垶鎮橀埡鍐＜?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskIdAndIsCounted(testTask1.getId(), true);
        StocktakeItem item = items.get(0);

        // When: 濠电姷鏁搁崕鎴犲緤閽樺褰掑磼閻愯尙鐛ュ┑掳鍊曢幊搴ㄥ几娴ｇ硶鏀介柛灞剧閸熺偤鏌涙惔鈥愁劉濞ｅ洤锕、娑橆煥閸愩劋绮┑鐐差嚟婵參宕归崼鏇炶摕闁斥晛鍟刊瀵糕偓鐟板濠㈡绮婇鈧?
        item.setCountedQty(105);
        item.setDifferenceQty(item.calculateDifference());
        item.setRemark("adjusted");
        stocktakeItemRepository.save(item);

        // 婵犵數濮烽弫鎼佸磻閻愬搫绠伴柟闂寸缁犵姵淇婇婵勨偓鈧柡瀣Ч楠炴牕菐椤掆偓婵¤偐绱掗悩铏凡闂囧鏌ㄥ┑鍡╂▓婵☆偅鍨块弻锝夋晲閸曨厾鐓撻梺鍝勬湰閻╊垰顕ｆ禒瀣╃憸宥夊焻閸撲胶纾藉ù锝堫嚃濞堟棃鏌涙繝鍌涜础缂侇喗鐟﹀鍕沪缁嬪じ澹曢梺鎸庣箓妤犲憡绂嶅┑瀣厽閹兼惌鍠栧▍宥夋煛鐏炶濡奸柍钘夘槸铻ｉ柛顭戝櫘娴煎啴鏌ｆ惔銈庢綈闁诡喖鍊圭粋宥囨崉閾忚娈惧┑顔筋焾濞夋盯鏌嬮崶顒佺厸闁搞儮鏅涙禒锔姐亜韫囨洖鏋涙慨濠冩そ瀹曨偊宕熼鐔蜂壕缂備焦蓱濞呯姵淇婇妶鍛櫤闁稿鍊块弻銊モ槈濡警浠鹃柣?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ巻鍋撶€规挸妫濋弻锝嗘償閵忊懇濮囬柦鍐憾閺岋綁骞橀姘闂傚倸鍊烽懗鍫曞箠閹剧粯鍋ら柕濞炬櫅缁€澶愭煛閸モ晛鏋戦柛?
        StocktakeItem reloaded = stocktakeItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getCountedQty()).isEqualTo(105);
        assertThat(reloaded.getDifferenceQty()).isEqualTo(5);
        assertThat(reloaded.getRemark()).isEqualTo("adjusted");
    }

    @Test
    @DisplayName("case-14")
    void testDeleteItem() {
        // Given: 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫梺绋款儐缁诲牓寮婚敐鍛傛棃鍩€椤掑嫭鍋嬪┑鐘插娑撳秹鏌熼悜姗嗘畷闁绘挾鍠栭弻宥夊传閸曨偂绨介梺缁樺笂缁瑩寮诲☉姘ｅ亾閿濆懎顣崇紒澶樺枤閳ь剚顔栭崰姘櫠濡ゅ懎绠氶柡鍐ㄧ墛閺呮煡鏌涢妷銏℃珖闁?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(testTask1.getId());
        StocktakeItem item = items.get(0);
        Long itemId = item.getId();

        // When: 闂傚倸鍊风粈渚€骞夐敍鍕殰闁绘劕顕粻楣冩煃瑜滈崜姘辨崲濞戙垹宸濇い鎾卞灩瀵即姊虹拠鈥崇仩閻庢凹鍓濋悘鎺楁⒑缂佹ɑ灏紒銊ャ偢瀹曠娀寮介鐔叉嫼闂佸憡绻傜€氼剟顢旈埡鍌滅闁圭虎鍣Λ鎴︽煕?
        stocktakeItemRepository.delete(item);
        entityManager.flush();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ帒鍋嗗Σ鎾⒒娴ｈ櫣甯涙い顓炴川閸掓帡顢涢悙鏉戜户闂佺粯鏌ㄩ崥瀣偂濞嗘挻鍊垫繛鎴烆仾椤忓懏鍙忛柛銉戔偓閺€?
        assertThat(stocktakeItemRepository.findById(itemId)).isEmpty();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顓涘亾閿濆骸澧绘繛鏉戝閺岋綁鎮╅崣澶婎槱閻熸粍婢橀崯鎾晲?闂傚倸鍊风粈渚€骞夐敓鐘冲仭妞ゆ牗绋撻々鍙夌節婵犲倸顏繛?3 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鍙夌節闂堟稒鐒鹃柨婵嗩槸缁狅綁鏌ｅΟ鍏兼毄闁?
        List<StocktakeItem> remainingItems = stocktakeItemRepository.findByTaskId(testTask1.getId());
        assertThat(remainingItems).hasSize(3);
    }

    @Test
    @DisplayName("case-15")
    void testCountItems() {
        // When: 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁愭鏌￠崶銉ョ労闁轰礁娲弻鐔兼⒒鐎靛壊妲紓浣哄О閸庣敻寮诲鍫闂佸憡鎸鹃崰搴敋閿濆鍨傛い鎰╁灮缁愮偤鏌ｈ箛鏇炰粶濠⒀傜矙閺佸秴顫滈埀顒€顫忛搹鍦＜婵☆垰鎼～鎴濐渻閵堝骸浜滄い锔诲灠瀹撳嫰姊洪悷閭﹀殶濠殿噣顥撴竟?
        long count = stocktakeItemRepository.count();

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?5 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鏌ユ煟閹邦垼姊垮Δ锕佹硶閻も偓濠电偞鍨堕悷銉╁焵椤掑倸鍘撮柟顔煎槻閳诲氦绠涢幙鍐х棯婵＄偑鍊ら崑鍛存偋濠婂懏顫?
        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("case-16")
    void testFindAll() {
        // When: 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崨顔兼缂備胶濮伴崕鐢稿蓟瀹ュ牜妾ㄩ梺鍛婃尵閸犲酣顢氶敐澶婂瀭妞ゆ劑鍨荤粣鐐烘煟韫囨洖浠滃褌绮欓弫宥咁潨閳ь剙顫忛搹鍦＜婵☆垰鎼～鎴濐渻閵堝骸浜滄い锔诲灠瀹撳嫰姊洪悷閭﹀殶濠殿噣顥撴竟?
        List<StocktakeItem> allItems = stocktakeItemRepository.findAll();

        // Then: 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閵夊孩绋掔粭鐔煎焵椤掑嫨鈧礁顫濋澶嬪兊闁荤姴鎼幖顐︽偟?5 濠电姷鏁搁崑鐐哄垂閸洖绠归柍鍝勫€婚々鏌ユ煟閹邦垼姊垮Δ锕佹硶閻も偓濠电偞鍨堕悷銉╁焵椤掑倸鍘撮柟顔煎槻閳诲氦绠涢幙鍐х棯婵＄偑鍊ら崑鍛存偋濠婂懏顫?
        assertThat(allItems).hasSize(5);
    }

    @Test
    @DisplayName("case-17")
    void testDifferenceCalculation_Surplus() {
        // When: 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫梺绋块鐎涒晜绌辨繝鍥舵晬婵﹩鍓氶崐顖炴⒑娴兼瑧鍒伴柟铏耿瀵鏁撻悩鑼槹濡炪倖鍔х徊鎯х暦瀹曞洨纾?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(testTask1.getId());
        StocktakeItem surplusItem = items.stream()
                .filter(item -> item.getDifferenceQty() > 0)
                .findFirst()
                .orElseThrow();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ挾鍋涙竟鍡樼節绾版ɑ顫婇柛銊ゅ嵆瀹曘儳鈧綆鍠楅崑?
        assertThat(surplusItem.getDifferenceQty()).isEqualTo(5);
        assertThat(surplusItem.isSurplus()).isTrue();
        assertThat(surplusItem.isShortage()).isFalse();
        assertThat(surplusItem.hasDifference()).isTrue();
    }

    @Test
    @DisplayName("case-18")
    void testDifferenceCalculation_Shortage() {
        // When: 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫梺绋块鐎涒晜绌辨繝鍥舵晬婵﹩鍙€绾偓缂備胶鍋撳妯肩矓瑜版帒钃熼柨鐔哄Т缁€瀣亜閹板墎绋荤€规洖纾槐?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(testTask1.getId());
        StocktakeItem shortageItem = items.stream()
                .filter(item -> item.getDifferenceQty() < 0)
                .findFirst()
                .orElseThrow();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ挾鍋涙竟鍡樼節绾版ɑ顫婇柛銊︽緲閿曘垺娼忛埡浣圭€?
        assertThat(shortageItem.getDifferenceQty()).isEqualTo(-5);
        assertThat(shortageItem.isSurplus()).isFalse();
        assertThat(shortageItem.isShortage()).isTrue();
        assertThat(shortageItem.hasDifference()).isTrue();
    }

    @Test
    @DisplayName("case-19")
    void testDifferenceCalculation_NoDifference() {
        // When: 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ閵忊剝鐝曢柣搴㈣壘椤兘寮婚妸鈺佸嵆婵°倐鍋撳ù婊堢畺濮婂搫效閸パ€鍋撻弴鐘亾濮橆偄宓嗘鐐叉瀹曠喖顢涘顐ょ倞闂備線娼чˇ浠嬪窗閺嶎厼绀堥柕濞炬櫆閳锋垿鏌涘┑鍡楊仼妞ゅ繆鏅滅换娑㈠箻椤曞懏顥栭梺?
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(testTask1.getId());
        StocktakeItem noDiffItem = items.stream()
                .filter(item -> item.getDifferenceQty() == 0 && item.getIsCounted())
                .findFirst()
                .orElseThrow();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顓濇勃闁诡垎灞肩穿闂傚倷鐒︾€笛兠哄澶婄；闁瑰墽绮悡鐔兼煏閸繃鍣规い蹇ｅ弮閺岀喖顢涘顒変純閻庤娲﹂崑濠冧繆閻戠瓔鏁婇柣鎾抽閳?
        assertThat(noDiffItem.getDifferenceQty()).isEqualTo(0);
        assertThat(noDiffItem.isSurplus()).isFalse();
        assertThat(noDiffItem.isShortage()).isFalse();
        assertThat(noDiffItem.hasDifference()).isFalse();
    }

    @Test
    @DisplayName("case-20")
    void testCountingProgress() {
        // When: 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁愭鏌￠崶銉ョ労闁轰礁娲弻娑樷槈濮楀牆濮涘銈傛櫇閸忔﹢骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛鐘崇墱閸欏懘妫呴銏″缂佸甯″畷鎰槹鎼达絾锛忓銈嗘尵閸嬬偤宕抽悾宀€纾奸柣妯烘▕閻撳吋顨?
        long totalItems = stocktakeItemRepository.findByTaskId(testTask1.getId()).size();
        long countedItems = stocktakeItemRepository.countByTaskIdAndIsCounted(testTask1.getId(), true);
        long uncountedItems = stocktakeItemRepository.countByTaskIdAndIsCounted(testTask1.getId(), false);

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顐ゆ殕闁逞屽墰婢规洘绺介崨濠勫幗闂佸搫鍟犻崑鎾翠繆閻愬弶鍋ョ€规洏鍎甸崺鈧い鎺戝€荤壕浠嬫煕鐏炲墽鎳呴柛鏂跨Ч閺岀喓绮欏▎鍓у悑闂?
        assertThat(totalItems).isEqualTo(4);
        assertThat(countedItems).isEqualTo(3);
        assertThat(uncountedItems).isEqualTo(1);
        assertThat(countedItems + uncountedItems).isEqualTo(totalItems);
    }

    @Test
    @DisplayName("case-21")
    void testDifferenceStatistics() {
        // When: 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀缁愭鏌￠崶銉ョ労闁轰礁娲弻娑樷槈濮楀牆濮涘銈傛櫇閸忔﹢骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻?闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛鎾寸懆閻忔帗绻濋悽闈浶㈤柛瀣瀹曠敻寮撮姀锛勫幈闁诲繒鍋涙晶浠嬪煡婢舵劖鐓曢柟鐐絻瀹撳棝鏌?
        long itemsWithDifference = stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(testTask1.getId(), 0);
        List<StocktakeItem> allItems = stocktakeItemRepository.findByTaskId(testTask1.getId());
        long surplusCount = allItems.stream().filter(StocktakeItem::isSurplus).count();
        long shortageCount = allItems.stream().filter(StocktakeItem::isShortage).count();

        // Then: 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顐ゆ殕闁逞屽墰婢规洘绺介崨濠勫幗闂佸搫鍟犻崑鎾翠繆閻愬弶鍋ョ€规洏鍎甸崺鈧い鎺戝€荤壕浠嬫煕鐏炲墽鎳呴柛鏂跨Ч閺岀喓绮欏▎鍓у悑闂?
        assertThat(itemsWithDifference).isEqualTo(2);
        assertThat(surplusCount).isEqualTo(1);
        assertThat(shortageCount).isEqualTo(1);
    }
}
