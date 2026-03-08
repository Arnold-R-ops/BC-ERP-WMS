package com.wms.system.repository;

import com.wms.system.entity.Customer;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.enums.SalesOrderStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SalesOrderRepository 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * V3.7 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥΟ鍨厫闁轰焦锕㈤弻娑㈡倻閸℃鐏曞銈嗘尭缂嶅﹤鐣烽敓鐘插嵆闁绘瑢鍋撻柛姘ｅ亾闂?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎?SalesOrderRepository 闂備焦鐪归崝宀€鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾妤犵鈻撴导瀛樺€甸悷娆忓閻擃垳绱掗悩闈涙灈鐎殿喖顭锋俊鐑解€﹂幋婵囩暠闂備礁鎼崐浠嬶綖婢跺本鍏滈柛顐ｆ礃閺?
 * 1. findByOrderNo() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝囧劋婵ジ鏌曡箛鏇炐㈢紒鈧€ｎ剛纾介柛灞剧〒婢ь亞鎲搁弶鎸庡仴鐎殿喖顭锋俊鐑解€﹂幋婵囩暠闂佽崵濮抽梽宥夊垂閻熸壆鏆?
 * 2. existsByOrderNo() - 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑Г濡炪倖鎸哥紞濠傜暦閿熺姴鍗抽柣妯虹仛閵囨繈姊洪崨濠勬噰闁衡偓閸楃倣锝夊箹娴ｆ瓕袝闂佹寧娲嶉崑鎾绘煟鎺抽崝鎴濈暦?
 * 3. findByStatus() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁诡喒鏅犻幊婊呭枈濡桨澹曟繛杈剧到濠€閬嶅矗閳ь剟鏌ｉ悩鍙夊偍闁搞劌銈搁、妯裤亹閹烘垹顢呴梺鍝勬川閸嬫盯鎮樺▎鎾村仩?
 * 4. findByCustomerId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝堛€€閸嬫捇鐛崹顔句痪闂佺硶鏅滈弶鐤嶉梻浣告惈鐞氼偊宕曢幘顕呮晪婵°倕鍟刊濂告煏韫囨洖孝缂佲偓鐎ｎ喗鐓曢柟鐑樻惄濞堟瑩鏌?
 * 5. findByApplicantId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁诡喚鏅埀顒€婀辨刊顓㈠吹閻愯鐟邦煥閸涱垱袨D闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧€ｎ喗鐓曢柟鐑樻惄濞堟瑩鏌?
 * 6. findByReviewedBy() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝堛€€閸嬫捇宕楁径濠傗拤婵犮垼顫夌敮妤冪矉瀹ュ洤濮柟鍨殜閺岋紕浠︾拠鎻掑Г濡炪倖娲橀〃鍫ュ箯鐎ｎ喖顫呴柣妯垮皺椤︿即姊洪崨濠傜瑲妞ゃ劌顦垫俊?
 * 7. findByCreatedAtBetween() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾鐎殿噮鍣ｅ畷濂稿即閻曞倻鍚归梻浣藉亹閸樠囧疮椤愨挌娲锤濡も偓閽冪喖鏌曟径妯煎帥闁搞倕瀚伴幃瑙勬媴闂堟稓浠奸悗瑙勬礈閸犳牕鐣峰顑╂棃宕熼埞鎯т壕?
 * 8. findByCustomerIdAndStatus() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝堛€€閸嬫捇鐛崹顔句痪闂佺硶鏅滈弶鐤嶉梻浣告啞缁矂鎯夐崗鍏煎弿闁归棿绀佺粻鎴澝归敐鍛础闁告瑢鍋撻梺鑽ゅТ濞层垽宕规總鍓叉晣鐟滅増甯掔涵鈧梺鍝勬川閸嬫盯鎮樺▎鎾村仩?
 * 9. countPendingApprovalOrders() - 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敇瑜嶉幃渚€姊洪崷顓熸毄妞ゃ垹锕、妯裤亹閹烘垹顢呴梺鍝勬川閸犳劕鈻撻崼鏇熲拺?
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
class SalesOrderRepositoryTest {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Customer testCustomer1;
    private Customer testCustomer2;
    private SalesOrder draftOrder;
    private SalesOrder pendingApprovalOrder;
    private SalesOrder approvedOrder;
    private SalesOrder shippedOrder;

    /**
     * 婵犳鍣徊鐣屾崲鐎ｎ喗鐓傛繝濠傚幘閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锟犲磼濠垫劖缍堢紒缁㈠幖閻栧ジ鐛鍫▉濡炪們鍨洪崹鍨暦濠婂喚鍚嬮柛娑卞幘娴犲瓨绻濆▓鍨灈妞ゆ垵鎳愰埀顒€鐏氳ぐ鍐箒闁诲函缍嗛崢鎯ｉ幖浣圭厸濞达絽鎼。鑲┾偓?
     */
    @BeforeEach
    void setUp() {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞幃妤呯嵁閸喚浠鹃梺?
        testCustomer1 = Customer.builder()
                .code("CUST001")
                .name("Customer A")
                .isActive(true)
                .build();

        testCustomer2 = Customer.builder()
                .code("CUST002")
                .name("Customer B")
                .isActive(true)
                .build();

        entityManager.persist(testCustomer1);
        entityManager.persist(testCustomer2);
        entityManager.flush();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鍓х帛閸ゆ帒顭跨捄铏圭劸闁绘帡绠栭幃瑙勬媴闂堟稓浠奸悗?
        draftOrder = SalesOrder.builder()
                .orderNo("SO20260129001")
                .customerId(testCustomer1.getId())
                .totalAmount(new BigDecimal("30000.00"))
                .status(SalesOrderStatus.DRAFT)
                .applicantId(1L)
                .applicantName("Alice")
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鐐墯閸ゆ鏌涘☉鍗炲季闁告埃鍋撻梻浣告贡閺佹悂銆佹繝鍥舵晣鐟滅増甯掔涵鈧?
        pendingApprovalOrder = SalesOrder.builder()
                .orderNo("SO20260129002")
                .customerId(testCustomer1.getId())
                .totalAmount(new BigDecimal("60000.00"))
                .status(SalesOrderStatus.PENDING_APPROVAL)
                .reviewReason("Needs manager approval")
                .applicantId(1L)
                .applicantName("Alice")
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鐐墯閸熷懘鏌涘▎蹇ｆЦ妞ゃ儲顨婇弻娑㈠箣濠靛浂妫炵紓浣稿閸犳牕鐣烽敐澶婄闁挎繂妫楃紞鎾绘煟閻橀亶妾烽柛銊ф嚀閻?
        approvedOrder = SalesOrder.builder()
                .orderNo("SO20260129003")
                .customerId(testCustomer2.getId())
                .totalAmount(new BigDecimal("40000.00"))
                .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
                .applicantId(2L)
                .applicantName("Bob")
                .reviewedBy(3L)
                .reviewedAt(LocalDateTime.now())
                .reviewComment("Approved by reviewer")
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鐐墯閸熷懘鏌熺紒妯虹瑨缂佸弶妞介幃褰掑箛鐏炶姤锛嶉柛姘€块弻?
        shippedOrder = SalesOrder.builder()
                .orderNo("SO20260129004")
                .customerId(testCustomer2.getId())
                .totalAmount(new BigDecimal("25000.00"))
                .status(SalesOrderStatus.SHIPPED)
                .applicantId(2L)
                .applicantName("Bob")
                .reviewedBy(3L)
                .reviewedAt(LocalDateTime.now().minusDays(1))
                .build();

        // 闂備礁缍婇弲鎻掝渻閹烘梻涓嶆繛鍡樻尭缁€宀勬煛瀹ュ啫濡块柕鍫熸尦閹綊宕堕妸锔绢槰闂佸搫妫涢崰鏍嵁?
        entityManager.persist(draftOrder);
        entityManager.persist(pendingApprovalOrder);
        entityManager.persist(approvedOrder);
        entityManager.persist(shippedOrder);
        entityManager.flush();
    }

    @Test
    @DisplayName("case-2")
    void testFindByOrderNo_Success() {
        // When: 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝囧劋婵ジ鏌曡箛鏇炐㈢紒鈧€ｎ剛纾介柛灞剧〒婢ь亞鎲搁弶鎸庡仴鐎殿喖顭锋俊鐑解€﹂幋婵囩暠
        Optional<SalesOrder> found = salesOrderRepository.findByOrderNo("SO20260129001");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧壕濂告煙閹屽殶濞?
        assertThat(found).isPresent();
        assertThat(found.get().getOrderNo()).isEqualTo("SO20260129001");
        assertThat(found.get().getStatus()).isEqualTo(SalesOrderStatus.DRAFT);
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("case-3")
    void testFindByOrderNo_NotFound() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏂挎惈婵℃寧绻涢崼鐔风仼闁瑰嘲顑夋慨鈧柣妯垮皺椤︽壆绱撻崒姘偓褰掓偋閺嚶颁汗?
        Optional<SalesOrder> found = salesOrderRepository.findByOrderNo("SO99999999999");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱?
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("case-4")
    void testExistsByOrderNo_Exists() {
        // When: 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑闂佸憡鍩婄槐鏇㈠焵椤掍胶鈯曢柨姘節閳ь剟顢旈崼鐔峰壄闂佸憡娲﹂崳顕€宕ラ崒鐐寸厱婵°倓鐒︾粈鍫㈢磼妲屾牔閭€?
        boolean exists = salesOrderRepository.existsByOrderNo("SO20260129001");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳?true
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("case-5")
    void testExistsByOrderNo_NotExists() {
        // When: 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑缂備焦顨呴ˇ閬嶅焵椤掍胶鈯曢柨姘節閳ь剟顢旈崼鐔峰壄闂佸憡娲﹂崳顕€宕ラ崒鐐寸厱婵°倓鐒︾粈鍫㈢磼妲屾牔閭€?
        boolean exists = salesOrderRepository.existsByOrderNo("SO99999999999");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳?false
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("case-6")
    void testFindByStatus_PendingApproval() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敇瑜嶉幃渚€姊洪崷顓熸毄妞ゃ垹锕、妯裤亹閹烘垹顢?
        List<SalesOrder> orders = salesOrderRepository.findByStatus(SalesOrderStatus.PENDING_APPROVAL);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠婂懏顐介柣鏃囥€€閸嬫捇宕楁径濠傗拤婵犮垼顫夌敮鐐哄箯鐎ｎ喖顫呴柣妯垮皺椤?
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getOrderNo()).isEqualTo("SO20260129002");
        assertThat(orders.get(0).getStatus()).isEqualTo(SalesOrderStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("case-7")
    void testFindByStatus_ApprovedAwaitingShipment() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ柨绻樺畷锝嗗緞婵犲孩顥滈梻浣告啞閸ㄩ潧螞濡ゅ啯顐介柣鏃傚帶閻鏌熸潏鍓хɑ鐟滅増鐩幃瑙勬媴闂堟稓浠奸悗?
        List<SalesOrder> orders = salesOrderRepository.findByStatus(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠婂牆绀勯柨鐔哄Т缁犮儵鏌ｉ悢鍛婄凡婵炲懏鐟ч埀顒€鐏氬姗€骞婃惔銈冧汗闁稿本绮嶇€氳崵鎲搁幋锔绘晣鐟滅増甯掔涵鈧?
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getOrderNo()).isEqualTo("SO20260129003");
    }

    @Test
    @DisplayName("case-8")
    void testFindByStatus_NoMatch() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ柨绻樺畷锝嗗緞婵犲嫬绲剧紓鍌氬€烽悞锔炬崲閸℃侗鏁囩憸鐗堝笒绾偓?
        List<SalesOrder> orders = salesOrderRepository.findByStatus(SalesOrderStatus.REJECTED);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担姝屽闁圭⒈鍋婂畷褰掝敂閸℃ê浠?
        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("case-9")
    void testFindByCustomerId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂?闂備焦鐪归崝宀€鈧凹鍨堕、妯裤亹閹烘垹顢?
        List<SalesOrder> orders = salesOrderRepository.findByCustomerId(testCustomer1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓?
        assertThat(orders).hasSize(2);
        assertThat(orders)
                .extracting(SalesOrder::getOrderNo)
                .containsExactlyInAnyOrder("SO20260129001", "SO20260129002");
    }

    @Test
    @DisplayName("case-10")
    void testFindByApplicantId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噺閸嬨劑鎮楅崷顓烆€岄柛銈囧仜闇?闂備焦鐪归崝宀€鈧凹鍨堕、妯裤亹閹烘垹顢?
        List<SalesOrder> orders = salesOrderRepository.findByApplicantId(1L);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓?
        assertThat(orders).hasSize(2);
        assertThat(orders)
                .extracting(SalesOrder::getOrderNo)
                .containsExactlyInAnyOrder("SO20260129001", "SO20260129002");
        assertThat(orders)
                .allMatch(order -> order.getApplicantName().equals("Alice"));
    }

    @Test
    @DisplayName("case-11")
    void testFindByReviewedBy() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇宕楁径濠傗拤婵犮垼顫夌敮妤冪矉?闂備焦鐪归崝宀€鈧凹鍨堕、妯裤亹閹烘垹顢?
        List<SalesOrder> orders = salesOrderRepository.findByReviewedBy(3L);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓?
        assertThat(orders).hasSize(2);
        assertThat(orders)
                .extracting(SalesOrder::getOrderNo)
                .containsExactlyInAnyOrder("SO20260129003", "SO20260129004");
        assertThat(orders)
                .allMatch(order -> order.getReviewedBy().equals(3L));
    }

    @Test
    @DisplayName("case-12")
    void testFindByCreatedAtBetween() {
        // Given: 闂佽崵濮崇粈浣规櫠娴犲鍋柛鈩冪☉缁秹鏌涢锝嗙闁挎稓鍠栭弻銈夋偂鎼粹剝娈┑鐐村絻濞硷繝寮澶婇唶婵犲﹤鎳愰悡蹇旂節閵忕姷浜柡鍛箓閳绘捇骞嬪┑鎰闂侀€炲苯澧柟渚垮妿閳ь剨缍嗘禍婵堚偓姘冲皺缁辨挻鎷呯憴鍕瀺濠电偟鈷堥崑濠囧极?
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now().plusDays(1);

        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁秹鏌涢锝嗙闁挎稓鍠栭弻銈夋偂鎼粹剝娈┑鐐村絻濞尖€崇暦濮椻偓瀹曘劑顢涘☉姘闂佽崵濮抽梽宥夊垂閻熸壆鏆?
        List<SalesOrder> orders = salesOrderRepository.findByCreatedAtBetween(startTime, endTime);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顓奸梺璇″瀻閸愵亜甯撻梻浣告贡椤ｄ粙宕戦幘缁樼厸闁割偅绻嶅Σ鍏笺亜閹惧磭绉虹€?
        assertThat(orders).hasSize(4);
    }

    @Test
    @DisplayName("case-13")
    void testFindByCreatedAtBetween_NoMatch() {
        // Given: 闂佽崵濮崇粈浣规櫠娴犲鍋柛鈩冪☉鐎氬銇勮箛鎾村櫤闂傚嫬绉归弻锝夊Ω閵夈儺浠惧┑锛勫仜閸婂灝顫忛懡銈咁棜閻庯綆浜炲Σ娲⒑?
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = LocalDateTime.now().plusDays(2);

        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁秹鏌涢锝嗙闁挎稓鍠栭弻銈夋偂鎼粹剝娈┑鐐村絻濞尖€崇暦濮椻偓瀹曘劑顢涘☉姘闂佽崵濮抽梽宥夊垂閻熸壆鏆?
        List<SalesOrder> orders = salesOrderRepository.findByCreatedAtBetween(startTime, endTime);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担姝屽闁圭⒈鍋婂畷褰掝敂閸℃ê浠?
        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("case-14")
    void testFindByCustomerIdAndStatus() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂?闂備焦鐪归崝宀€鈧凹鍓涘▎銏ゆ偄鐞涒€充壕闁稿繐顦晶鎻掝熆鐟欏嫬绗ч柟宄邦儔婵偓闁绘灏欓ˇ?
        List<SalesOrder> orders = salesOrderRepository.findByCustomerIdAndStatus(
                testCustomer1.getId(),
                SalesOrderStatus.PENDING_APPROVAL
        );

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓?
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getOrderNo()).isEqualTo("SO20260129002");
        assertThat(orders.get(0).getCustomerId()).isEqualTo(testCustomer1.getId());
        assertThat(orders.get(0).getStatus()).isEqualTo(SalesOrderStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("case-15")
    void testCountPendingApprovalOrders() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敇瑜嶉幃渚€姊洪崷顓熸毄妞ゃ垹锕、妯裤亹閹烘垹顢呴梺鍝勬川閸犳劕鈻撻崼鏇熲拺?
        long count = salesOrderRepository.countPendingApprovalOrders();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠婂懏顐介柣鏃囥€€閸嬫捇宕楁径濠傗拤婵犮垼顫夌敮鐐哄箯鐎ｎ喖顫呴柣妯垮皺椤?
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("case-16")
    void testCountPendingApprovalOrders_Multiple() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙閻鏌ㄩ弮鍥ㄧ《妞ゅ繐宕埥澶愬箼閸愌呯泿缂備礁澧庨崰鎾诲焵椤掆偓閸樻粓宕滃┑鍥╃彾闁圭儤姊规刊濂告煏韫囨洖孝缂佲偓?
        SalesOrder anotherPendingOrder = SalesOrder.builder()
                .orderNo("SO20260129005")
                .customerId(testCustomer2.getId())
                .totalAmount(new BigDecimal("70000.00"))
                .status(SalesOrderStatus.PENDING_APPROVAL)
                .reviewReason("Needs manager approval")
                .applicantId(2L)
                .applicantName("Bob")
                .build();
        entityManager.persist(anotherPendingOrder);
        entityManager.flush();

        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾妞ゆ洏鍎靛畷銊╊敇瑜嶉幃渚€姊洪崷顓熸毄妞ゃ垹锕、妯裤亹閹烘垹顢呴梺鍝勬川閸犳劕鈻撻崼鏇熲拺?
        long count = salesOrderRepository.countPendingApprovalOrders();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濠婂懏顐介柣鏃囥€€閸嬫捇宕楁径濠傗拤婵犮垼顫夌敮鐐哄箯鐎ｎ喖顫呴柣妯垮皺椤?
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("case-17")
    void testSaveNewOrder() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙濡﹢鏌熷畡鎵劸闁告艾鍊块弻?
        SalesOrder newOrder = SalesOrder.builder()
                .orderNo("SO20260129006")
                .customerId(testCustomer1.getId())
                .totalAmount(new BigDecimal("35000.00"))
                .status(SalesOrderStatus.DRAFT)
                .applicantId(1L)
                .applicantName("Alice")
                .build();

        // When: 濠电儑绲藉ú锔炬崲閸岀偞鍋ら柕濞у嫬顎涢梺闈涚箳婵绮?
        SalesOrder saved = salesOrderRepository.save(newOrder);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ兘鏀冲┑鐘绘涧濡盯骞楅悩缁樼厵閻庢稒锚婵洤鈹?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂傚倷鐒﹁ぐ鍐矓閻㈢钃熷┑鐘叉搐閽冪喖鏌曟径妯煎帥闁?
        Optional<SalesOrder> found = salesOrderRepository.findByOrderNo("SO20260129006");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩閼搁潧娈戦梺鍏间航閸庨亶藟濠靛鐓?
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo("35000.00");
    }

    @Test
    @DisplayName("case-18")
    void testUpdateOrderStatus() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸ゆ帒顭跨捄铏圭劸闁绘帡绠栭幃瑙勬媴闂堟稓浠奸悗?
        SalesOrder order = salesOrderRepository.findByOrderNo("SO20260129001").orElseThrow();

        // When: 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柤娴嬫櫆婵ジ鏌曡箛鏇炐㈢紒鈧€ｎ喗鐓熸俊顖氱仢閻撴劙鏌嶈閸忔稑顪冮幒鏃€瀚婚柣鏂款殠閸ゆ鏌涘☉鍗炲季闁告埃鍋撻梻?
        order.setStatus(SalesOrderStatus.PENDING_APPROVAL);
        order.setReviewReason("Updated review reason");
        salesOrderRepository.save(order);

        // 婵犵數鍋為幐鎼佸箠濡　鏋嶉幖娣妼缁犳澘霉閿濆妫戦柣锝勭矙閺屾盯寮介妸褍鈪剁紓浣诡殔閸婂湱绮欐径灞稿亾閿濆骸浜濋柣搴櫍閺屻劌鈽夊Ο鍨伃閻庤鎮傛禍璺虹暦濮樿泛閱囬柡鍥╁仦椤忕喖姊洪崫鍕偓缁樻櫠濡ゅ懏鍋傞柨娑樺鐎?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒€宓嗛柣搴㈢⊕钃遍柣鎾亾闂備胶鎳撻悺銊╁礉閺囩喐鍙?
        SalesOrder reloaded = salesOrderRepository.findByOrderNo("SO20260129001").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SalesOrderStatus.PENDING_APPROVAL);
        assertThat(reloaded.getReviewReason()).isEqualTo("Updated review reason");
    }

    @Test
    @DisplayName("case-19")
    void testDeleteOrder() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬫繈鏌ｅΔ鈧悧濠勭不閹烘鍋ｅù锝夋涧閻忊晝鈧?
        SalesOrder order = salesOrderRepository.findByOrderNo("SO20260129001").orElseThrow();
        Long orderId = order.getId();

        // When: 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁瑰嘲顑夋慨鈧柣妯垮皺椤?
        salesOrderRepository.delete(order);
        entityManager.flush();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤槴闂佺粯顭囩划顖炴偟閻斿吋鐓欓悗娑櫭慨鍥р攽?
        Optional<SalesOrder> found = salesOrderRepository.findById(orderId);
        assertThat(found).isEmpty();

        // When: 闂備礁鎲￠崝鏇犵矓閻㈠壊鏁冮柤娴嬫櫃閻掑﹥绻濋棃娑冲姛婵″弶鎮傞弻锛勪沪鐠囨彃濮ゅ?
        Optional<SalesOrder> foundByOrderNo = salesOrderRepository.findByOrderNo("SO20260129001");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠洪缚鎽曢梺闈涱樈閻撳牓宕甸崟顐熸闁规儳纾瓭闂?
        assertThat(foundByOrderNo).isEmpty();
    }

    @Test
    @DisplayName("case-20")
    void testCountOrders() {
        // When: 缂傚倸鍊烽懗鍫曞窗閺囥埄鏁囬柟闂寸缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閹兼惌鍠栭幃鍛存⒑?
        long count = salesOrderRepository.count();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?4 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓?
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("case-21")
    void testFindAll() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閹兼惌鍠栭幃鍛存⒑?
        List<SalesOrder> allOrders = salesOrderRepository.findAll();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?4 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓?
        assertThat(allOrders).hasSize(4);
        assertThat(allOrders)
                .extracting(SalesOrder::getOrderNo)
                .containsExactlyInAnyOrder(
                        "SO20260129001",
                        "SO20260129002",
                        "SO20260129003",
                        "SO20260129004"
                );
    }
}
