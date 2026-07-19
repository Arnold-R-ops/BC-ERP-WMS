package com.wms.system.repository;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboundTaskRepository 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * V3.7 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥ鍡楀婵絽顦甸獮鏍偓娑櫳戠亸顐︽煙楠炲灝鐏茬€规洘绻堥弫宥夊礋闂堟稒鍊庨梻?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎?OutboundTaskRepository 闂備焦鐪归崝宀€鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾妤犵鈻撴导瀛樺€甸悷娆忓閻擃垳绱掗悩闈涙灈鐎殿喖顭锋俊鐑解€﹂幋婵囩暠闂備礁鎼崐浠嬶綖婢跺本鍏滈柛顐ｆ礃閺?
 * 1. findBySalesOrderId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁哄被鍔戦崺鈧い鎺戝閼歌銇勯弮鈧娆撳触閸岀偞鐓曟俊銈勭劍椤︾泴闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊濋弻娑㈠箳閹垮啯鐣介梺?
 * 2. findBySalesOrderItemId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁哄被鍔戦崺鈧い鎺戝閼歌銇勯弮鈧娆撳触閸岀偞鐓曟俊銈勭劍缁€鍐╀繆閸欏鐏╃紒杈ㄥ浮瀹曨亞寮х€ｎ喗鐓涢悘鐐额嚙閸斻儲銇勯弴鐕佹畷缂佸倹甯為幏鐘诲箵閹烘繃鍖犻梻浣告啞鐢銆冩径鎰?
 * 3. findByAssignedBatchId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾妤犵偛绉堕幉鎾礋闂堟稐杩業D闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊濋弻娑㈠箳閹垮啯鐣介梺?
 * 4. findByStatus() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁诡喒鏅犻幊婊呭枈濡桨澹曟繛杈剧到濠€閬嶅矗閳ь剟鏌ｉ悩鍙夌ォ婵犫懇鍋撻梺鐟扮畭閸ㄨ棄鐣峰┑瀣伋闁告劘灏欐禒姘舵煟?
 * 5. findByPickedBy() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾妤犵偛绻橀弫鎰板醇閻旈绋夊┑鐐茬摠缁酣鎯侀垾鏇㈡⒑閸濆嫯顫﹂柛搴㈡尦椤㈡艾螖娴ｄ警娴勯柣鐘叉处瑜板啴锝為妶澶嬬厱闁圭儤鎼╁▓娆撴煏?
 * 6. findByLocationId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝堫潐閸庣喖鏌熼幑鎰毢缂佸顣笵闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊濋弻娑㈠箳閹垮啯鐣介梺?
 * 7. findBySalesOrderIdAndStatus() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁哄被鍔戦崺鈧い鎺戝閼歌銇勯弮鈧娆撳触閸岀偞鐓曟俊銈勭劍椤︾泴闂備礁鎲＄划宀勬儔閸忓吋鍙忛柟闂寸缁犳垵霉閿濆懏璐￠柛娆屽亾闂佽崵濮村ù鍌氼熆閳ь剟鏌熼獮鍨伈鐎规洘绻堥崹楣冨礃閼碱兛绮撮梺?
 * 8. deleteBySalesOrderId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁哄被鍔戦崺鈧い鎺戝閼歌銇勯弮鈧娆撳触閸岀偞鐓曟俊銈勭劍椤︾泴闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓㈠弰妤犵偛绉归崺鈧い鎺戝鐎氬顭跨捄渚Ч鐎规洘鐓￠弻?
 * 9. countPendingTasks() - 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敊閼测晛娴囬梺鑽ゅУ閸擃剟宕掑☉妯虹哎闂備礁鎲￠弻锟犲礈濠靛姹查柣鏂垮悑閻?
 * 10. countCompletedTasksBySalesOrderId() - 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犱即鏌涢妷鎴濇噺濮ｅ酣鏌ｉ悩閬嶆闁搞劎鎳撻悾鐑芥偄閸忕厧鍓梺鍛婃处閸嬪嫰宕甸幒妤佸€甸柣鐔哄濠€浼存煕閵婏妇顣茬紒鍌涘笧閹风娀骞撻幒婵囧尃闂備浇妗ㄥ鎺楀础閹惰棄闂?
 * 11. countTasksBySalesOrderId() - 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犱即鏌涢妷鎴濇噺濮ｅ酣鏌ｉ悩閬嶆闁搞劎鎳撻悾鐑芥偄閸忕厧鍓梺鍛婃处閸忔﹢宕戦幘鑸靛闁惧浚鍋勬惔濠囨⒑閸涘﹥鐓涢柛鎾寸箞瀵娊鎮㈤崗鑲╁帓?
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("case-1")
class OutboundTaskRepositoryTest {

    @Autowired
    private OutboundTaskRepository outboundTaskRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    private Customer testCustomer;
    private ProductSku testProduct;
    private Warehouse testWarehouse;
    private Location testLocation1;
    private Location testLocation2;
    private InventoryBatch testBatch1;
    private InventoryBatch testBatch2;
    private SalesOrder testOrder1;
    private SalesOrder testOrder2;
    private SalesOrderItem testOrderItem1;
    private SalesOrderItem testOrderItem2;
    private OutboundTask pendingTask1;
    private OutboundTask pickingTask;
    private OutboundTask completedTask;

    /**
     * 婵犳鍣徊鐣屾崲鐎ｎ喗鐓傛繝濠傚幘閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锟犲磼濠垫劖缍堢紒缁㈠幖閻栧ジ鐛鍫▉濡炪們鍨洪崹鍨暦濠婂喚鍚嬮柛娑卞幘娴犲瓨绻濆▓鍨灈妞ゆ垵鎳愰埀顒€鐏氳ぐ鍐箒闁诲函缍嗛崢鎯ｉ幖浣圭厸濞达絽鎼。鑲┾偓?
     */
    @BeforeEach
    void setUp() {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞幃妤呯嵁閸喚浠鹃梺?
        testCustomer = Customer.builder()
                .code("CUST001")
                .name("Test Customer")
                .isActive(true)
                .build();
        entityManager.persist(testCustomer);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵¤尙顭堥湁婵犙呭Т閸燁垶骞?
        Product testSpu = Product.builder()
                .category(com.wms.system.support.TestCatalogFactory.persistLeafCategory(entityManager))
                .productCode("SPU001")
                .productName("Test SPU")
                .enabled(true)
                .build();
        testSpu = productRepository.save(testSpu);
        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .product(testSpu)
                .skuName("PROD001")
                .name("Test ProductSku")
                .barcode("6900000000001")
                .unitPrice(new BigDecimal("100.00"))
                .nearExpiryDays(90)
                .build();
        entityManager.persist(testProduct);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵¤尙顭堥湁闁绘娅曠亸顓犵磼?
        testWarehouse = Warehouse.builder()
                .code("WH001")
                .name("Test Warehouse")
                .build();
        entityManager.persist(testWarehouse);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞獮鏍偓娑櫳戠亸顐ょ磽?
        testLocation1 = Location.builder()
                .warehouse(testWarehouse)
                .shelfNumber("A-01")
                .positionNumber("001")
                .zone(Zone.ZONE_A)
                .build();

        testLocation2 = Location.builder()
                .warehouse(testWarehouse)
                .shelfNumber("A-01")
                .positionNumber("002")
                .zone(Zone.ZONE_A)
                .build();

        entityManager.persist(testLocation1);
        entityManager.persist(testLocation2);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻鐔告媴閸愮偓缍堝?
        testBatch1 = InventoryBatch.builder()
                .productSku(testProduct)
                .batchCode("BATCH001")
                .location(testLocation1)
                .locationCode(testLocation1.getLocationCode())
                .quantity(100)
                .initialQuantity(100)
                .expiryDate(LocalDate.now().plusMonths(6))
                .active(true)
                .build();

        testBatch2 = InventoryBatch.builder()
                .productSku(testProduct)
                .batchCode("BATCH002")
                .location(testLocation2)
                .locationCode(testLocation2.getLocationCode())
                .quantity(100)
                .initialQuantity(100)
                .expiryDate(LocalDate.now().plusMonths(12))
                .active(true)
                .build();

        entityManager.persist(testBatch1);
        entityManager.persist(testBatch2);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞幃瑙勬媴闂堟稓浠奸悗?
        testOrder1 = SalesOrder.builder()
                .orderNo("SO20260129001")
                .customerId(testCustomer.getId())
                .totalAmount(new BigDecimal("5000.00"))
                .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
                .applicantId(1L)
                .applicantName("user1")
                .build();

        testOrder2 = SalesOrder.builder()
                .orderNo("SO20260129002")
                .customerId(testCustomer.getId())
                .totalAmount(new BigDecimal("3000.00"))
                .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
                .applicantId(1L)
                .applicantName("user1")
                .build();

        entityManager.persist(testOrder1);
        entityManager.persist(testOrder2);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞幃瑙勬媴闂堟稓浠奸悗瑙勬礈閸犳牕顕ｉ悧鍫熷劅闁挎繂娲ㄩ悡?
        testOrderItem1 = SalesOrderItem.builder()
                .salesOrderId(testOrder1.getId())
                .productSku(testProduct)
                .productSkuId(testProduct.getId())
                .quantity(10)
                .unitPrice(new BigDecimal("120.00"))
                .subtotal(new BigDecimal("1200.00"))
                .rejectNearExpiry(false)
                .build();

        testOrderItem2 = SalesOrderItem.builder()
                .salesOrderId(testOrder2.getId())
                .productSku(testProduct)
                .productSkuId(testProduct.getId())
                .quantity(15)
                .unitPrice(new BigDecimal("110.00"))
                .subtotal(new BigDecimal("1650.00"))
                .rejectNearExpiry(false)
                .build();

        entityManager.persist(testOrderItem1);
        entityManager.persist(testOrderItem2);
        entityManager.flush();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鐐墯閸ゆ鏌涘☉鍗炴灈闁绘繄鏁婚幃褰掑箛鐏炶棄鈧懓鐣烽弻銉︾厱?
        pendingTask1 = OutboundTask.builder()
                .salesOrderId(testOrder1.getId())
                .salesOrderItemId(testOrderItem1.getId())
                .assignedBatchId(testBatch1.getId())
                .locationId(testLocation1.getId())
                .planQty(10)
                .actualQty(0)
                .status(OutboundTaskStatus.PENDING)
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙缁狀垶鏌ㄥ┑鍡欏鐟滅増绋戦埥澶愬箼閸愩劌绠抽梺鐟扮畭閸ㄨ棄鐣?
        pickingTask = OutboundTask.builder()
                .salesOrderId(testOrder1.getId())
                .salesOrderItemId(testOrderItem1.getId())
                .assignedBatchId(testBatch2.getId())
                .locationId(testLocation2.getId())
                .planQty(5)
                .actualQty(0)
                .status(OutboundTaskStatus.PICKING)
                .pickedBy(2L)
                .pickedAt(LocalDateTime.now())
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鐐墯閸熷懘鏌熺紒妯虹闁哄棙宀搁弻鐔衡偓娑櫭慨鍐煙楠炲灝鐏茬€?
        completedTask = OutboundTask.builder()
                .salesOrderId(testOrder2.getId())
                .salesOrderItemId(testOrderItem2.getId())
                .assignedBatchId(testBatch1.getId())
                .locationId(testLocation1.getId())
                .planQty(15)
                .actualQty(15)
                .status(OutboundTaskStatus.COMPLETED)
                .pickedBy(2L)
                .pickedAt(LocalDateTime.now().minusHours(1))
                .build();

        // 闂備礁缍婇弲鎻掝渻閹烘梻涓嶆繛鍡樻尭缁€宀勬煛瀹ュ啫濡块柕鍫熸尦閹綊宕堕妸锔绢槰闂佸搫妫涢崰鏍嵁?
        entityManager.persist(pendingTask1);
        entityManager.persist(pickingTask);
        entityManager.persist(completedTask);
        entityManager.flush();
    }

    @Test
    @DisplayName("case-2")
    void testFindBySalesOrderId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹浜獮鎴﹀閵堝懐顦?
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(testOrder1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .extracting(OutboundTask::getStatus)
                .containsExactlyInAnyOrder(OutboundTaskStatus.PENDING, OutboundTaskStatus.PICKING);
    }

    @Test
    @DisplayName("case-3")
    void testFindBySalesOrderItemId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧€ｎ喗鐓涢柛鏇ㄥ亝閹癸絿绱?闂備焦鐪归崝宀€鈧凹浜獮鎴﹀閵堝懐顦?
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderItemId(testOrderItem1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .allMatch(task -> task.getSalesOrderItemId().equals(testOrderItem1.getId()));
    }

    @Test
    @DisplayName("case-4")
    void testFindByAssignedBatchId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鎮峰▎蹇擃伌闁?闂備焦鐪归崝宀€鈧凹浜獮鎴﹀閵堝懐顦?
        List<OutboundTask> tasks = outboundTaskRepository.findByAssignedBatchId(testBatch1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .extracting(OutboundTask::getStatus)
                .containsExactlyInAnyOrder(OutboundTaskStatus.PENDING, OutboundTaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("case-5")
    void testFindByStatus_Pending() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敊閼测晛娴囬梺鑽ゅУ閸擃剟宕掑☉妯虹哎闂?
        List<OutboundTask> tasks = outboundTaskRepository.findByStatus(OutboundTaskStatus.PENDING);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠婂懏顐介柣鏃傚帶缁狀垶鏌ㄥ┑鍡欏鐟滅増绋戦湁闁绘ü璀﹂崵娆忊攽?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo(OutboundTaskStatus.PENDING);
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(10);
    }

    @Test
    @DisplayName("case-6")
    void testFindByStatus_Picking() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁狀垶鏌ㄥ┑鍡欏鐟滅増绋戦埥澶愬箼閸愩劌绠抽梺鐟扮畭閸ㄨ棄鐣?
        List<OutboundTask> tasks = outboundTaskRepository.findByStatus(OutboundTaskStatus.PICKING);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠靛牃鍋撳鍗炰户闁归顢婇ˇ閬嶆煠閸偄鐏︾紒鍌涘笧閹风娀骞撻幒婵囧尃
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo(OutboundTaskStatus.PICKING);
        assertThat(tasks.get(0).getPickedBy()).isEqualTo(2L);
    }

    @Test
    @DisplayName("case-7")
    void testFindByStatus_Completed() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ柨绻橀獮鎾诲箳閺冣偓濞堫噣姊洪悷鎵憼闁告梹甯￠獮鎴﹀閵堝懐顦?
        List<OutboundTask> tasks = outboundTaskRepository.findByStatus(OutboundTaskStatus.COMPLETED);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠婂牆绀勯柨娑樺閸嬫捇鎮烽悧鍫熸嫳闂佹悶鍔嶇喊宥囩矉閹烘梹瀚氶柟缁樺俯濞?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo(OutboundTaskStatus.COMPLETED);
        assertThat(tasks.get(0).getActualQty()).isEqualTo(15);
    }

    @Test
    @DisplayName("case-8")
    void testFindByPickedBy() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁狀垶鏌ㄥ┑鍡欏鐟滅増绋戦湁?闂備焦鐪归崝宀€鈧凹浜獮鎴﹀閵堝懐顦?
        List<OutboundTask> tasks = outboundTaskRepository.findByPickedBy(2L);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .allMatch(task -> task.getPickedBy().equals(2L));
    }

    @Test
    @DisplayName("case-9")
    void testFindByLocationId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勭劍閸庣喖鏌熼幑鎰毢缂?闂備焦鐪归崝宀€鈧凹浜獮鎴﹀閵堝懐顦?
        List<OutboundTask> tasks = outboundTaskRepository.findByLocationId(testLocation1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .allMatch(task -> task.getLocationId().equals(testLocation1.getId()));
    }

    @Test
    @DisplayName("case-10")
    void testFindBySalesOrderIdAndStatus() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹鍓涘▎銏ゆ偄閻撳海顔嗛梺鎸庣箓閻楀﹨銇愬☉妯忓綊鎮╂笟顖氭婵?
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderIdAndStatus(
                testOrder1.getId(),
                OutboundTaskStatus.PENDING
        );

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getSalesOrderId()).isEqualTo(testOrder1.getId());
        assertThat(tasks.get(0).getStatus()).isEqualTo(OutboundTaskStatus.PENDING);
    }

    @Test
    @DisplayName("case-11")
    void testDeleteBySalesOrderId() {
        // Given: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭仦鎯ь€涢梺闈涚箳婵绮?闂?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        List<OutboundTask> tasksBefore = outboundTaskRepository.findBySalesOrderId(testOrder1.getId());
        assertThat(tasksBefore).hasSize(2);

        // When: 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁瑰嘲顑夋慨鈧柣妯垮皺椤?闂備焦鐪归崝宀€鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾椤︽澘鐣烽弻銉︾厱?
        outboundTaskRepository.deleteBySalesOrderId(testOrder1.getId());
        entityManager.flush();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤槴闂佺粯顭囩划顖炴偟閻斿吋鐓欓悗娑櫭慨鍥р攽?
        List<OutboundTask> tasksAfter = outboundTaskRepository.findBySalesOrderId(testOrder1.getId());
        assertThat(tasksAfter).isEmpty();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭仦鎯ь€涢梺闈涚箳婵绮?闂備焦鐪归崝宀€鈧凹浜獮鎴﹀閵堝懐顦梺鏂ユ櫅閸燁垰顕ｉ幘缁樼厱婵炲棙鍔﹀▓鏂库攽閸屾稓澧电€?
        List<OutboundTask> order2Tasks = outboundTaskRepository.findBySalesOrderId(testOrder2.getId());
        assertThat(order2Tasks).hasSize(1);
    }

    @Test
    @DisplayName("case-12")
    void testCountPendingTasks() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敊閼测晛娴囬梺鑽ゅУ閸擃剟宕掑☉妯虹哎闂備礁鎲￠弻锟犲礈濠靛姹查柣鏂垮悑閻?
        long count = outboundTaskRepository.countPendingTasks();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠婂懏顐介柣鏃傚帶缁狀垶鏌ㄥ┑鍡欏鐟滅増绋戦湁闁绘ü璀﹂崵娆忊攽?
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("case-13")
    void testCountPendingTasks_Multiple() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙閻鏌ㄩ弮鍥ㄧ《妞ゅ繐宕埥澶愬箼閸愌呯泿缂備礁澧庨崰鏍嵁韫囨稒鏅查柛顐ゅ枎缂嶆挻绻涚€电鞋妞ゆ泦鍕弿?
        OutboundTask anotherPendingTask = OutboundTask.builder()
                .salesOrderId(testOrder2.getId())
                .salesOrderItemId(testOrderItem2.getId())
                .assignedBatchId(testBatch2.getId())
                .locationId(testLocation2.getId())
                .planQty(20)
                .actualQty(0)
                .status(OutboundTaskStatus.PENDING)
                .build();
        entityManager.persist(anotherPendingTask);
        entityManager.flush();

        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敊閼测晛娴囬梺鑽ゅУ閸擃剟宕掑☉妯虹哎闂備礁鎲￠弻锟犲礈濠靛姹查柣鏂垮悑閻?
        long count = outboundTaskRepository.countPendingTasks();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濠婂懏顐介柣鏃傚帶缁狀垶鏌ㄥ┑鍡欏鐟滅増绋戦湁闁绘ü璀﹂崵娆忊攽?
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("case-14")
    void testCountCompletedTasksBySalesOrderId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹鍓熷畷娆撴晬閸曘劌浜鹃柣鐔哄濠€浼存煕閵婏妇顣茬紒鍌涘笧閹风娀骞撻幒婵囧尃闂備浇妗ㄥ鎺楀础閹惰棄闂?
        long count = outboundTaskRepository.countCompletedTasksBySalesOrderId(testOrder2.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠婂牆绀勯柨娑樺閸嬫捇鎮烽悧鍫熸嫳闂佹悶鍔嶇喊宥囩矉閹烘梹瀚氶柟缁樺俯濞?
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("case-15")
    void testCountCompletedTasksBySalesOrderId_NoCompleted() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹鍓熷畷娆撴晬閸曘劌浜鹃柣鐔哄濠€浼存煕閵婏妇顣茬紒鍌涘笧閹风娀骞撻幒婵囧尃闂備浇妗ㄥ鎺楀础閹惰棄闂?
        long count = outboundTaskRepository.countCompletedTasksBySalesOrderId(testOrder1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?0 濠电偞鍨堕幖鈺傜濠婂牆绀勯柨娑樺閸嬫捇鎮烽悧鍫熸嫳闂佹悶鍔嶇喊宥囩矉閹烘梹瀚氶柟缁樺俯濞?
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("case-16")
    void testCountTasksBySalesOrderId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹鍙冮崺鈧い鎺戝枤閸炴椽鏌熼獮鍨伈鐎规洘绻堥崺鍕礃閳哄偆鍟€闂?
        long count = outboundTaskRepository.countTasksBySalesOrderId(testOrder1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("case-17")
    void testSaveNewTask() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙濡﹢鏌℃径濠勪虎婵絽顦甸獮鏍偓娑櫳戠亸顐︽煙楠炲灝鐏茬€?
        OutboundTask newTask = OutboundTask.builder()
                .salesOrderId(testOrder2.getId())
                .salesOrderItemId(testOrderItem2.getId())
                .assignedBatchId(testBatch2.getId())
                .locationId(testLocation2.getId())
                .planQty(25)
                .actualQty(0)
                .status(OutboundTaskStatus.PENDING)
                .build();

        // When: 濠电儑绲藉ú锔炬崲閸岀偞鍋ら柕濞炬櫅缁€鍕煕濠靛棗顏慨妯稿妼闇夐柣妯硅閸ゆ瑥鈹?
        OutboundTask saved = outboundTaskRepository.save(newTask);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ兘鏀冲┑鐘绘涧濡盯骞楅悩缁樼厵閻庢稒锚婵洤鈹?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂傚倷鐒﹁ぐ鍐矓閻㈢钃熷┑鐘叉搐閽冪喖鏌曟径妯煎帥闁搞倕瀚伴幃瑙勬媴闂堟稓浠奸悗?闂備焦鐪归崝宀€鈧凹浜獮鎴﹀閵堝懐顦?
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(testOrder2.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濞嗘挸绠查柕蹇嬪€曠粈?
        assertThat(tasks).hasSize(2);
    }

    @Test
    @DisplayName("case-18")
    void testUpdateTaskStatus() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉戝苯娈梺鍛婁緱閸犳牠鎮甸悽鍛婂仯闁规澘鑻崐鐟扮暦閺屻儲鐓?
        List<OutboundTask> tasks = outboundTaskRepository.findByStatus(OutboundTaskStatus.PENDING);
        OutboundTask task = tasks.get(0);

        // When: 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柛蹇氬亹椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊濋弻锝呂熼崹顔惧帿闂侀€炲苯鍘告俊鐐村笧閹峰綊鎮㈤悡搴ｎ唵闂佹寧绻傞悧濠呫亹濞戞ǚ妲?
        task.setStatus(OutboundTaskStatus.PICKING);
        task.setPickedBy(3L);
        task.setPickedAt(LocalDateTime.now());
        outboundTaskRepository.save(task);

        // 婵犵數鍋為幐鎼佸箠濡　鏋嶉幖娣妼缁犳澘霉閿濆妫戦柣锝勭矙閺屾盯寮介妸褍鈪剁紓浣诡殔閸婂湱绮欐径灞稿亾閿濆骸浜濋柣搴櫍閺屻劌鈽夊Ο鍨伃閻庤鎮傛禍璺虹暦濮樿泛閱囬柡鍥╁仦椤忕喖姊洪崫鍕偓缁樻櫠濡ゅ懏鍋傞柨娑樺鐎?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒€宓嗛柣搴㈢⊕钃遍柣鎾亾闂備胶鎳撻悺銊╁礉閺囩喐鍙?
        OutboundTask reloaded = outboundTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboundTaskStatus.PICKING);
        assertThat(reloaded.getPickedBy()).isEqualTo(3L);
        assertThat(reloaded.getPickedAt()).isNotNull();
    }

    @Test
    @DisplayName("case-19")
    void testCompleteTask() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墮缁狀垶鏌ㄥ┑鍡欏鐟滅増绋戦埥澶愬箼閸愩劌绠抽梺鐟扮畭閸ㄨ棄鐣?
        List<OutboundTask> tasks = outboundTaskRepository.findByStatus(OutboundTaskStatus.PICKING);
        OutboundTask task = tasks.get(0);

        // When: 闂佽娴烽幊鎾诲嫉椤掑嫬鍨傛慨姗嗗幘椤╃兘鎮归崶銊ョ祷妞?
        task.setStatus(OutboundTaskStatus.COMPLETED);
        task.setActualQty(5);
        outboundTaskRepository.save(task);

        // 婵犵數鍋為幐鎼佸箠濡　鏋嶉幖娣妼缁犳澘霉閿濆妫戦柣锝勭矙閺屾盯寮介妸褍鈪剁紓浣诡殔閸婂湱绮欐径灞稿亾閿濆骸浜濋柣搴櫍閺屻劌鈽夊Ο鍨伃閻庤鎮傛禍璺虹暦濮樿泛閱囬柡鍥╁仦椤忕喖姊洪崫鍕偓缁樻櫠濡ゅ懏鍋傞柨娑樺鐎?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒€宓嗛柣搴㈢⊕钃遍柣鎾亾闂備胶鎳撻悺銊╁礉閺囩喐鍙?
        OutboundTask reloaded = outboundTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboundTaskStatus.COMPLETED);
        assertThat(reloaded.getActualQty()).isEqualTo(5);
    }

    @Test
    @DisplayName("case-20")
    void testCountTasks() {
        // When: 缂傚倸鍊烽懗鍫曞窗閺囥埄鏁囬柟闂寸缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞濠电姴鍊归惁鏃堟煙閻撳海鎽犻柟鍝ュ厴楠炴垿濮€閵堝懐顦?
        long count = outboundTaskRepository.count();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?3 濠电偞鍨堕幖鈺傜濠婂牆鍚规い鎾跺枑閸庣喖鏌熼幆褍绾х€规洘鐓￠弻?
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("case-21")
    void testFindAll() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞濠电姴鍊归惁鏃堟煙閻撳海鎽犻柟鍝ュ厴楠炴垿濮€閵堝懐顦?
        List<OutboundTask> allTasks = outboundTaskRepository.findAll();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?3 濠电偞鍨堕幖鈺傜濠婂牆鍚规い鎾跺枑閸庣喖鏌熼幆褍绾х€规洘鐓￠弻?
        assertThat(allTasks).hasSize(3);
        assertThat(allTasks)
                .extracting(OutboundTask::getStatus)
                .containsExactlyInAnyOrder(
                        OutboundTaskStatus.PENDING,
                        OutboundTaskStatus.PICKING,
                        OutboundTaskStatus.COMPLETED
                );
    }
}
