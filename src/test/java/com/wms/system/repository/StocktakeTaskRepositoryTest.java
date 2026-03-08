package com.wms.system.repository;

import com.wms.system.entity.StocktakeTask;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.StocktakeCycleType;
import com.wms.system.entity.enums.StocktakeStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StocktakeTaskRepository 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * V3.8 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥ鍡楀箺妞ゅ骏绻濋弻銈呯暦閸ャ劍鐝┑鐐村絻缁绘﹢骞冨▎鎾虫嵍妞ゆ挶鍨归崢娆戠磽?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎?StocktakeTaskRepository 闂備焦鐪归崝宀€鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾妤犵鈻撴导瀛樺€甸悷娆忓閻擃垳绱掗悩闈涙灈鐎殿喖顭锋俊鐑解€﹂幋婵囩暠闂備礁鎼崐浠嬶綖婢跺本鍏滈柛顐ｆ礃閺?
 * 1. findByTaskNo() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝呮贡椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊楃槐鎾诲磼濞戞艾鈪归悷婊勬緲濞尖€愁嚕婵犳艾唯妞ゎ厽鍨靛▓鎴︽⒑閻戔晜娅嗘い顓炴喘瀹曟垿鏁愰崱娆樻祫闁荤姴娲﹁ぐ鍐綖?
 * 2. findByWarehouseId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝呮贡椤╃兘鏌熼幆褏锛嶆慨妯峰晱D闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噺閸庡孩淇婇婊冨付濞寸媭鍨伴湁闁绘ü璀﹂崵娆忊攽椤旀儳鏋涚€规洘鑹捐灃闁告洍鏂侀崑?
 * 3. findByStatus() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁诡喒鏅犻幊婊呭枈濡桨澹曟繛杈剧到濠€閬嶅矗閳ь剟鏌ｉ悩鍙夊偍闁搞劏顕х叅闊洦绋掗崐鐑芥⒑椤愶絿鈯曠€规洘鐓￠弻娑㈠棘鐞涒€充壕闁告劘灏欐禒姘舵煟?
 * 4. findByCycleType() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾鐎规洏鍔戞俊鎼佹晜閽樺鍔紓鍌欑贰閸嬪顢氳閹鎮滈挊澶庢憰闂侀潧顦介悡鍫ュ吹閸曨垱鐓熼柍鍝勫亞閻掍粙鏌涚€ｅ墎绋荤紒鍌涘笧閹风娀骞撻幒婵囧尃闂備礁鎲＄敮妤呫€冩径鎰?
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("case-1")
class StocktakeTaskRepositoryTest {

    @Autowired
    private StocktakeTaskRepository stocktakeTaskRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Warehouse testWarehouse1;
    private Warehouse testWarehouse2;
    private StocktakeTask monthlyTask;
    private StocktakeTask quarterlyTask;
    private StocktakeTask annualTask;
    private StocktakeTask adhocTask;

    /**
     * 婵犳鍣徊鐣屾崲鐎ｎ喗鐓傛繝濠傚幘閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锟犲磼濠垫劖缍堢紒缁㈠幖閻栧ジ鐛鍫▉濡炪們鍨洪崹鍨暦濠婂喚鍚嬮柛娑卞幘娴犲瓨绻濆▓鍨灈妞ゆ垵鎳愰埀顒€鐏氳ぐ鍐箒闁诲函缍嗛崢鎯ｉ幖浣圭厸濞达絽鎼。鑲┾偓?
     */
    @BeforeEach
    void setUp() {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵¤尙顭堥湁闁绘娅曠亸顓犵磼?
        testWarehouse1 = Warehouse.builder()
                .code("WH001")
                .name("婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ〒椤╃兘鏌熼幆褏锛嶆慨?")
                .build();

        testWarehouse2 = Warehouse.builder()
                .code("WH002")
                .name("婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ〒椤╃兘鏌熼幆褏锛嶆慨?")
                .build();

        entityManager.persist(testWarehouse1);
        entityManager.persist(testWarehouse2);
        entityManager.flush();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙鐎氬鏌涢锝囩畵妞ゆ柨锕弻锝夊煛閸屻倗鍔烽梺绋款儜缁插墽绮嬮幒鏃€瀚氶柟缁樺俯濞兼娊姊洪幐搴ｂ槈闁绘妫濆畷娆撴晸閻樿尙顦┑鐐村灦椤洨妲愰敃鍌涚厽婵☆垰鐏濋悡鎰版煃瑜滈崗娑氭閵堝洦顫?
        monthlyTask = StocktakeTask.builder()
                .taskNo("TK-202601-M01")
                .warehouseId(testWarehouse1.getId())
                .cycleType(StocktakeCycleType.MONTHLY)
                .status(StocktakeStatus.CREATED)
                .snapshotTime(LocalDateTime.now())
                .totalItems(100)
                .countedItems(0)
                .differenceItems(0)
                .createdBy(1L)
                .createdByName("Creator A")
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯х亪閸嬫挸鈽夊畷鍥у煂闁诲氦顫夋繛濠囧箚閸曨倠鐔兼偂鎼存繂瀵插┑鐐差嚟婵箖顢氳閹便劑鎮㈤崗鍏兼珫闂佸壊鍋侀崹铏瑰閹间焦鐓熼柟鐗堝閸嬫挸鈽夊鍐ㄧ畱闂備胶绮…鍫ュ春閺嶎厼鐒垫い鎴ｆ硶缁涘繒绱?
        quarterlyTask = StocktakeTask.builder()
                .taskNo("TK-202601-Q01")
                .warehouseId(testWarehouse1.getId())
                .cycleType(StocktakeCycleType.QUARTERLY)
                .status(StocktakeStatus.COUNTING)
                .snapshotTime(LocalDateTime.now().minusDays(1))
                .totalItems(500)
                .countedItems(250)
                .differenceItems(10)
                .createdBy(1L)
                .createdByName("Creator A")
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愬吹瀹撲線鏌℃径瀣仴妞ゆ柨锕弻锝夊煛閸屻倗鍔烽梺绋款儜缁插墽绮嬮幒鏃€瀚氶柟缁樺俯濞兼娊姊洪幐搴ｂ槈闁绘妫濋、姗€骞栨担绋垮敤闂傚倵鍋撻柟閭﹀灠楠炩偓闂備胶绮…鍫ュ春閺嶎厼鐒垫い鎴ｆ硶缁涘繒绱?
        annualTask = StocktakeTask.builder()
                .taskNo("TK-202601-A01")
                .warehouseId(testWarehouse2.getId())
                .cycleType(StocktakeCycleType.ANNUAL)
                .status(StocktakeStatus.REVIEWING)
                .snapshotTime(LocalDateTime.now().minusDays(7))
                .totalItems(1000)
                .countedItems(1000)
                .differenceItems(50)
                .createdBy(2L)
                .createdByName("Creator B")
                .reviewedBy(3L)
                .reviewedByName("Reviewer A")
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘棃鎮楀☉娅虫垿藝瑜旈弻锝夊煛閸屻倗鍔烽梺绋款儜缁插墽绮嬮幒鏃€瀚氶柟缁樺俯濞兼娊姊洪幐搴ｂ槈闁绘妫濆畷娆撴晬閸曘劌浜鹃柣鐔哄濠€浼存煕閵婏箑鍝洪柟顔规櫊閹虫粎鍠婂Ο杞板婵炴挻鑹鹃敃锔剧矆?
        adhocTask = StocktakeTask.builder()
                .taskNo("TK-202601-AD01")
                .warehouseId(testWarehouse2.getId())
                .cycleType(StocktakeCycleType.ADHOC)
                .status(StocktakeStatus.COMPLETED)
                .snapshotTime(LocalDateTime.now().minusDays(14))
                .totalItems(50)
                .countedItems(50)
                .differenceItems(5)
                .createdBy(2L)
                .createdByName("Creator B")
                .reviewedBy(3L)
                .reviewedByName("Reviewer A")
                .reviewedAt(LocalDateTime.now().minusDays(13))
                .reviewComment("Review completed successfully")
                .build();

        // 闂備礁缍婇弲鎻掝渻閹烘梻涓嶆繛鍡樻尭缁€宀勬煛瀹ュ啫濡块柕鍫熸尦閹綊宕堕妸锔绢槰闂佸搫妫涢崰鏍嵁?
        entityManager.persist(monthlyTask);
        entityManager.persist(quarterlyTask);
        entityManager.persist(annualTask);
        entityManager.persist(adhocTask);
        entityManager.flush();
    }

    @Test
    @DisplayName("case-2")
    void testFindByTaskNo_Success() {
        // When: 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝呮贡椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊楃槐鎾诲磼濞戞艾鈪归悷婊勬緲濞尖€愁嚕婵犳艾唯妞ゎ厽鍨靛▓?
        Optional<StocktakeTask> found = stocktakeTaskRepository.findByTaskNo("TK-202601-M01");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧壕濂告煙閹屽殶濞?
        assertThat(found).isPresent();
        assertThat(found.get().getTaskNo()).isEqualTo("TK-202601-M01");
        assertThat(found.get().getCycleType()).isEqualTo(StocktakeCycleType.MONTHLY);
        assertThat(found.get().getStatus()).isEqualTo(StocktakeStatus.CREATED);
        assertThat(found.get().getTotalItems()).isEqualTo(100);
    }

    @Test
    @DisplayName("case-3")
    void testFindByTaskNo_NotFound() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏂挎惈婵℃寧绻涢崼鐔风仸缂佸倹甯為幏鐘诲箵閹烘繃鍖犵紓鍌氬€搁崐褰掓偋閺嚶颁汗?
        Optional<StocktakeTask> found = stocktakeTaskRepository.findByTaskNo("TK-999999-M99");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱?
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("case-4")
    void testFindByWarehouseId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌椤╃兘鏌熼幆褏锛嶆慨?闂備焦鐪归崝宀€鈧凹鍠栫叅闊洦绋掗崐鐑芥⒑椤愶絿鈯曠€规洘鐓￠弻?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByWarehouseId(testWarehouse1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .extracting(StocktakeTask::getTaskNo)
                .containsExactlyInAnyOrder("TK-202601-M01", "TK-202601-Q01");
    }

    @Test
    @DisplayName("case-5")
    void testFindByWarehouseId_Warehouse2() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌椤╃兘鏌熼幆褏锛嶆慨?闂備焦鐪归崝宀€鈧凹鍠栫叅闊洦绋掗崐鐑芥⒑椤愶絿鈯曠€规洘鐓￠弻?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByWarehouseId(testWarehouse2.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .extracting(StocktakeTask::getTaskNo)
                .containsExactlyInAnyOrder("TK-202601-A01", "TK-202601-AD01");
    }

    @Test
    @DisplayName("case-6")
    void testFindByWarehouseId_NoTasks() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏂挎惈婵℃寧绻涢崼鐔风仸缂佸倹甯￠獮妯虹暦閸ャ劎娈D
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByWarehouseId(99999L);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担姝屽闁圭⒈鍋婂畷褰掝敂閸℃ê浠?
        assertThat(tasks).isEmpty();
    }

    @Test
    @DisplayName("case-7")
    void testFindByStatus_Created() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ柨绻橀獮鎾诲箳閹寸姳鐢婚柣搴ｅ仯閸婃稑鈻嶉敐鍡樺弿闁归棿绀佺粻鎴澝归敐鍥у妺闁伙絽宕湁闁绘ü璀﹂崵娆忊攽?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByStatus(StocktakeStatus.CREATED);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-M01");
        assertThat(tasks.get(0).getStatus()).isEqualTo(StocktakeStatus.CREATED);
    }

    @Test
    @DisplayName("case-8")
    void testFindByStatus_Counting() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噺閸庡孩淇婇婊冨付濞寸媭鍨伴埥澶愬箼閸愩劌绠绘繝娈垮枟閹倿鐛埀顒€霉閿濆洤鍔嬮柣锝呭船闇夐柣妯硅閸ゆ瑥鈹?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByStatus(StocktakeStatus.COUNTING);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-Q01");
        assertThat(tasks.get(0).getStatus()).isEqualTo(StocktakeStatus.COUNTING);
        assertThat(tasks.get(0).getCountedItems()).isEqualTo(250);
    }

    @Test
    @DisplayName("case-9")
    void testFindByStatus_Reviewing() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇宕楁径濠傗拤闂佺粯顨夊▍鏇犵矙婢舵劦鏁囬柣妯荤暙閿曞倹鐓欐い鎴ｅ劵閸忓本绻涢崼鐔风仸缂佸倹甯為幏鐘诲箵閹烘繃鍖?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByStatus(StocktakeStatus.REVIEWING);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-A01");
        assertThat(tasks.get(0).getStatus()).isEqualTo(StocktakeStatus.REVIEWING);
    }

    @Test
    @DisplayName("case-10")
    void testFindByStatus_Completed() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ柨绻橀獮鎾诲箳閺冣偓濞堫噣姊洪悷鎵憼闁告梹顨嗛幈銊╁箹娴ｅ摜鐣辨繛杈剧悼閸庛倝鎮鹃崡鐏诲綊鎮╂笟顖氭婵?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByStatus(StocktakeStatus.COMPLETED);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-AD01");
        assertThat(tasks.get(0).getStatus()).isEqualTo(StocktakeStatus.COMPLETED);
        assertThat(tasks.get(0).getReviewComment()).isEqualTo("Review completed successfully");
    }

    @Test
    @DisplayName("case-11")
    void testFindByCycleType_Monthly() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹鐎氬鏌涢锝囩畵妞ゆ柨锕弻锝夊煛閸屻倗鍔烽梺绋款儜缁插墽绮嬮幒鏃€瀚氶柟缁樺俯濞?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByCycleType(StocktakeCycleType.MONTHLY);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-M01");
        assertThat(tasks.get(0).getCycleType()).isEqualTo(StocktakeCycleType.MONTHLY);
    }

    @Test
    @DisplayName("case-12")
    void testFindByCycleType_Quarterly() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫挸鈽夊畷鍥у煂闁诲氦顫夋繛濠囧箚閸曨倠鐔兼偂鎼存繂瀵插┑鐐差嚟婵箖顢氳閹?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByCycleType(StocktakeCycleType.QUARTERLY);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-Q01");
        assertThat(tasks.get(0).getCycleType()).isEqualTo(StocktakeCycleType.QUARTERLY);
    }

    @Test
    @DisplayName("case-13")
    void testFindByCycleType_Annual() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噽瀹撲線鏌℃径瀣仴妞ゆ柨锕弻锝夊煛閸屻倗鍔烽梺绋款儜缁插墽绮嬮幒鏃€瀚氶柟缁樺俯濞?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByCycleType(StocktakeCycleType.ANNUAL);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-A01");
        assertThat(tasks.get(0).getCycleType()).isEqualTo(StocktakeCycleType.ANNUAL);
    }

    @Test
    @DisplayName("case-14")
    void testFindByCycleType_Adhoc() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘棃鎮楀☉娅虫垿藝瑜旈弻锝夊煛閸屻倗鍔烽梺绋款儜缁插墽绮嬮幒鏃€瀚氶柟缁樺俯濞?
        List<StocktakeTask> tasks = stocktakeTaskRepository.findByCycleType(StocktakeCycleType.ADHOC);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskNo()).isEqualTo("TK-202601-AD01");
        assertThat(tasks.get(0).getCycleType()).isEqualTo(StocktakeCycleType.ADHOC);
    }

    @Test
    @DisplayName("case-15")
    void testSaveNewTask() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙濡﹢鏌熷▓鍨灍缁剧偓鎮傞弻锝夊箛閹靛啿浜炬繛鎴炵懃鎼村﹪姊?
        StocktakeTask newTask = StocktakeTask.builder()
                .taskNo("TK-202601-M02")
                .warehouseId(testWarehouse1.getId())
                .cycleType(StocktakeCycleType.MONTHLY)
                .status(StocktakeStatus.CREATED)
                .snapshotTime(LocalDateTime.now())
                .totalItems(80)
                .countedItems(0)
                .differenceItems(0)
                .createdBy(1L)
                .createdByName("Creator A")
                .build();

        // When: 濠电儑绲藉ú锔炬崲閸岀偞鍋ら柕濞炬櫆閸庡孩淇婇婊冨付濞寸媭鍨伴湁闁绘ü璀﹂崵娆忊攽?
        StocktakeTask saved = stocktakeTaskRepository.save(newTask);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ兘鏀冲┑鐘绘涧濡盯骞楅悩缁樼厵閻庢稒锚婵洤鈹?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂傚倷鐒﹁ぐ鍐矓閻㈢钃熷┑鐘叉搐閽冪喖鏌曟径妯煎帥闁?
        Optional<StocktakeTask> found = stocktakeTaskRepository.findByTaskNo("TK-202601-M02");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩閼搁潧娈戦梺鍏间航閸庨亶藟濠靛鐓?
        assertThat(found).isPresent();
        assertThat(found.get().getTotalItems()).isEqualTo(80);
    }

    @Test
    @DisplayName("case-16")
    void testUpdateTaskStatus() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉戝苯鏅犻梺鍦帛鐢帡鎮橀敍鍕ㄥ亾閻愮懓鈧稑鈻嶉敐鍛灁闁瑰鍋熼々鐑芥偣閸ャ劌绲绘い?
        StocktakeTask task = stocktakeTaskRepository.findByTaskNo("TK-202601-M01").orElseThrow();

        // When: 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柛蹇氬亹椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊濋弻锝呂熼崹顔惧帿闂侀€炲苯鍘告俊鐐村笧閹峰綊鎮㈤崗鐓庡壆濠碘槅鍨抽崢褎绂掗姘ｆ?
        task.setStatus(StocktakeStatus.COUNTING);
        task.setCountedItems(50);
        stocktakeTaskRepository.save(task);

        // 婵犵數鍋為幐鎼佸箠濡　鏋嶉幖娣妼缁犳澘霉閿濆妫戦柣锝勭矙閺屾盯寮介妸褍鈪剁紓浣诡殔閸婂湱绮欐径灞稿亾閿濆骸浜濋柣搴櫍閺屻劌鈽夊Ο鍨伃閻庤鎮傛禍璺虹暦濮樿泛閱囬柡鍥╁仦椤忕喖姊洪崫鍕偓缁樻櫠濡ゅ懏鍋傞柨娑樺鐎?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒€宓嗛柣搴㈢⊕钃遍柣鎾亾闂備胶鎳撻悺銊╁礉閺囩喐鍙?
        StocktakeTask reloaded = stocktakeTaskRepository.findByTaskNo("TK-202601-M01").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StocktakeStatus.COUNTING);
        assertThat(reloaded.getCountedItems()).isEqualTo(50);
    }

    @Test
    @DisplayName("case-17")
    void testCompleteTask() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉㈡杹閸嬫捇宕楁径濠傗拤闂佺粯顨夊▍鏇犵矙婢舵劦鏁囬柣妯虹－閺嗙姵绻涚€电鞋妞ゆ泦鍕弿?
        StocktakeTask task = stocktakeTaskRepository.findByTaskNo("TK-202601-A01").orElseThrow();

        // When: 闂佽娴烽幊鎾诲嫉椤掑嫬鍨傛慨姗嗗幘椤╃兘鎮归崶銊ョ祷妞?
        task.setStatus(StocktakeStatus.COMPLETED);
        task.setReviewedAt(LocalDateTime.now());
        task.setReviewComment("Review completed with manual override");
        stocktakeTaskRepository.save(task);

        // 婵犵數鍋為幐鎼佸箠濡　鏋嶉幖娣妼缁犳澘霉閿濆妫戦柣锝勭矙閺屾盯寮介妸褍鈪剁紓浣诡殔閸婂湱绮欐径灞稿亾閿濆骸浜濋柣搴櫍閺屻劌鈽夊Ο鍨伃閻庤鎮傛禍璺虹暦濮樿泛閱囬柡鍥╁仦椤忕喖姊洪崫鍕偓缁樻櫠濡ゅ懏鍋傞柨娑樺鐎?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒€宓嗛柣搴㈢⊕钃遍柣鎾亾闂備胶鎳撻悺銊╁礉閺囩喐鍙?
        StocktakeTask reloaded = stocktakeTaskRepository.findByTaskNo("TK-202601-A01").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StocktakeStatus.COMPLETED);
        assertThat(reloaded.getReviewedAt()).isNotNull();
        assertThat(reloaded.getReviewComment()).isEqualTo("Review completed with manual override");
    }

    @Test
    @DisplayName("case-18")
    void testDeleteTask() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬫繈鏌ｅΔ鈧悧濠勭不閹烘嚚褰掓偐娓氼垰娈繝?
        StocktakeTask task = stocktakeTaskRepository.findByTaskNo("TK-202601-AD01").orElseThrow();
        Long taskId = task.getId();

        // When: 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓烆劉缂佸倹甯為幏鐘诲箵閹烘繃鍖?
        stocktakeTaskRepository.delete(task);
        entityManager.flush();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤槴闂佺粯顭囩划顖炴偟閻斿吋鐓欓悗娑櫭慨鍥р攽?
        assertThat(stocktakeTaskRepository.findById(taskId)).isEmpty();

        // When: 闂備礁鎲￠崝鏇犵矓閻㈠壊鏁冮柤娴嬫櫃閻掑﹥绻濋棃娑冲姛婵″弶鎮傞弻锛勪沪鐠囨彃濮ゅ?
        Optional<StocktakeTask> foundByTaskNo = stocktakeTaskRepository.findByTaskNo("TK-202601-AD01");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠洪缚鎽曢梺闈涱樈閻撳牓宕甸崟顐熸闁规儳纾瓭闂?
        assertThat(foundByTaskNo).isEmpty();
    }

    @Test
    @DisplayName("case-19")
    void testCountTasks() {
        // When: 缂傚倸鍊烽懗鍫曞窗閺囥埄鏁囬柟闂寸缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閻忕偛澧介敍姗€姊虹紒妯哄闁逞屽墯缁嬫帒鐣烽弻銉︾厱?
        long count = stocktakeTaskRepository.count();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?4 濠电偞鍨堕幖鈺傜閻愯楦跨疀濞戞瑥鈧兘姊洪锝団姇鐎规洘鐓￠弻?
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("case-20")
    void testFindAll() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閻忕偛澧介敍姗€姊虹紒妯哄闁逞屽墯缁嬫帒鐣烽弻銉︾厱?
        List<StocktakeTask> allTasks = stocktakeTaskRepository.findAll();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?4 濠电偞鍨堕幖鈺傜閻愯楦跨疀濞戞瑥鈧兘姊洪锝団姇鐎规洘鐓￠弻?
        assertThat(allTasks).hasSize(4);
        assertThat(allTasks)
                .extracting(StocktakeTask::getTaskNo)
                .containsExactlyInAnyOrder(
                        "TK-202601-M01",
                        "TK-202601-Q01",
                        "TK-202601-A01",
                        "TK-202601-AD01"
                );
    }

    @Test
    @DisplayName("case-21")
    void testProgressCalculation() {
        // When: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉㈡杹閸嬫挸鈽夊畷鍥у煂闁诲氦顫夋繛濠囧箚閸曨倠鐔兼偂鎼存繂瀵插┑鐐差嚟婵箖顢氳閹?
        StocktakeTask task = stocktakeTaskRepository.findByTaskNo("TK-202601-Q01").orElseThrow();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偂姘﹀┑鐐村灦閿氭い鏂匡躬閹绗熸繝鍕紵闂?
        int progress = task.getProgress();
        assertThat(progress).isEqualTo(50); // 250/500 = 50%
    }

    @Test
    @DisplayName("case-22")
    void testStatistics() {
        // When: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墰瀹撲線鏌℃径瀣仴妞ゆ柨锕弻锝夊煛閸屻倗鍔烽梺绋款儜缁插墽绮嬮幒鏃€瀚氶柟缁樺俯濞?
        StocktakeTask task = stocktakeTaskRepository.findByTaskNo("TK-202601-A01").orElseThrow();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧壕濂告煟閺冣偓濡炲潡宕ラ埀顒佺箾閿濆懏绀岄柛鎾寸箖鐎?
        assertThat(task.getTotalItems()).isEqualTo(1000);
        assertThat(task.getCountedItems()).isEqualTo(1000);
        assertThat(task.getDifferenceItems()).isEqualTo(50);
        assertThat(task.getProgress()).isEqualTo(100); // 1000/1000 = 100%
    }
}
