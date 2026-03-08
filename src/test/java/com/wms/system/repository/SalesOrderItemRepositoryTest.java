package com.wms.system.repository;

import com.wms.system.entity.Customer;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSpu;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesOrderItem;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SalesOrderItemRepository 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * V3.7 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥΟ鍨厫闁轰焦锕㈤弻娑㈡倻閸℃鐏曞銈嗘尭缂嶅﹤鐣烽敓鐘插嵆闁绘柨澹婂鐘电磽閸屾瑧顦︽俊顐ｇ洴椤㈡﹢骞栨担鐟颁哗?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎?SalesOrderItemRepository 闂備焦鐪归崝宀€鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾妤犵鈻撴导瀛樺€甸悷娆忓閻擃垳绱掗悩闈涙灈鐎殿喖顭锋俊鐑解€﹂幋婵囩暠闂備礁鎼崐浠嬶綖婢跺本鍏滈柛顐ｆ礃閺?
 * 1. findBySalesOrderId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁哄被鍔戦崺鈧い鎺戝閼歌銇勯弮鈧娆撳触閸岀偞鐓曟俊銈勭劍椤︾泴闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹閸欏﹪骞栨潏鍓ф偧闁活厼閰ｉ弻娑㈠箳閹垮啯鐣介梺?
 * 2. findByProductId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝呮贡椤╄尙鎲稿澶婄妞ゎ亜绮氶梻浣告惈鐞氼偊宕曢幘顕呮晪婵°倕鎳庨崣濠囧箹鏉堝墽鎮奸柣顓為叄閺屾盯骞掗幙鍐╃暯闂?
 * 3. deleteBySalesOrderId() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ〒姘ｅ亾闁哄被鍔戦崺鈧い鎺戝閼歌銇勯弮鈧娆撳触閸岀偞鐓曟俊銈勭劍椤︾泴闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓㈠弰妤犵偛绉归崺鈧い鎺戝鐎氬顭跨捄渚剱妞ゎ偅鐗滅槐?
 * 4. findBySpecifiedBatchIdsContaining() - 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犱即鏌涢妷鎴濇噺濮ｅ酣姊洪柅娑氱シ妞ゎ偄顦甸、鏇熺附閸涘﹤鍓梺鍛婃处閸ｎ噣宕ラ崒鐐寸厱婵°倓鐒︾粈鍐╀繆閸欏鐏╃紒?
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
class SalesOrderItemRepositoryTest {

    @Autowired
    private SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Customer testCustomer;
    private ProductSpu testSpu;
    private Product testProduct1;
    private Product testProduct2;
    private SalesOrder testOrder1;
    private SalesOrder testOrder2;
    private SalesOrderItem orderItem1;
    private SalesOrderItem orderItem2;
    private SalesOrderItem orderItem3;

    /**
     * 婵犳鍣徊鐣屾崲鐎ｎ喗鐓傛繝濠傚幘閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锟犲磼濠垫劖缍堢紒缁㈠幖閻栧ジ鐛鍫▉濡炪們鍨洪崹鍨暦濠婂喚鍚嬮柛娑卞幘娴犲瓨绻濆▓鍨灈妞ゆ垵鎳愰埀顒€鐏氳ぐ鍐箒闁诲函缍嗛崢鎯ｉ幖浣圭厸濞达絽鎼。鑲┾偓?
     */
    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .code("CUST001")
                .name("Test Customer")
                .isActive(true)
                .build();
        entityManager.persist(testCustomer);

        testSpu = ProductSpu.builder()
                .spuCode("SPU-TEST-001")
                .spuName("Test SPU")
                .build();
        entityManager.persist(testSpu);

        testProduct1 = Product.builder()
                .spu(testSpu)
                .skuName("PROD001-SKU")
                .barcode("PROD001-BARCODE")
                .name("Test Product A")
                .unitPrice(new BigDecimal("120.00"))
                .minSalesPrice(new BigDecimal("100.00"))
                .nearExpiryDays(90)
                .build();

        testProduct2 = Product.builder()
                .spu(testSpu)
                .skuName("PROD002-SKU")
                .barcode("PROD002-BARCODE")
                .name("Test Product B")
                .unitPrice(new BigDecimal("60.00"))
                .minSalesPrice(new BigDecimal("50.00"))
                .nearExpiryDays(60)
                .build();

        entityManager.persist(testProduct1);
        entityManager.persist(testProduct2);

        testOrder1 = SalesOrder.builder()
                .orderNo("SO20260129001")
                .customerId(testCustomer.getId())
                .totalAmount(new BigDecimal("5000.00"))
                .status(SalesOrderStatus.DRAFT)
                .applicantId(1L)
                .applicantName("Test User")
                .build();

        testOrder2 = SalesOrder.builder()
                .orderNo("SO20260129002")
                .customerId(testCustomer.getId())
                .totalAmount(new BigDecimal("3000.00"))
                .status(SalesOrderStatus.PENDING_APPROVAL)
                .applicantId(1L)
                .applicantName("Test User")
                .build();

        entityManager.persist(testOrder1);
        entityManager.persist(testOrder2);
        entityManager.flush();

        orderItem1 = SalesOrderItem.builder()
                .salesOrderId(testOrder1.getId())
                .productId(testProduct1.getId())
                .quantity(10)
                .unitPrice(new BigDecimal("120.00"))
                .subtotal(new BigDecimal("1200.00"))
                .rejectNearExpiry(false)
                .specifiedBatchIds(null)
                .build();

        orderItem2 = SalesOrderItem.builder()
                .salesOrderId(testOrder1.getId())
                .productId(testProduct2.getId())
                .quantity(20)
                .unitPrice(new BigDecimal("60.00"))
                .subtotal(new BigDecimal("1200.00"))
                .rejectNearExpiry(true)
                .specifiedBatchIds("[123, 456]")
                .build();

        orderItem3 = SalesOrderItem.builder()
                .salesOrderId(testOrder2.getId())
                .productId(testProduct1.getId())
                .quantity(15)
                .unitPrice(new BigDecimal("110.00"))
                .subtotal(new BigDecimal("1650.00"))
                .rejectNearExpiry(false)
                .specifiedBatchIds("[789]")
                .build();

        entityManager.persist(orderItem1);
        entityManager.persist(orderItem2);
        entityManager.persist(orderItem3);
        entityManager.flush();
    }
    @Test
    @DisplayName("case-2")
    void testFindBySalesOrderId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(testOrder1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        assertThat(items).hasSize(2);
        assertThat(items)
                .extracting(SalesOrderItem::getProductId)
                .containsExactlyInAnyOrder(testProduct1.getId(), testProduct2.getId());
    }

    @Test
    @DisplayName("case-3")
    void testFindBySalesOrderId_SingleItem() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(testOrder2.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductId()).isEqualTo(testProduct1.getId());
        assertThat(items.get(0).getQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("case-4")
    void testFindBySalesOrderId_NoItems() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏂挎惈婵℃寧绻涢崼鐔风仼闁瑰嘲顑夋慨鈧柣妯垮皺椤︾檺D
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(99999L);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担姝屽闁圭⒈鍋婂畷褰掝敂閸℃ê浠?
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("case-5")
    void testFindByProductId() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌椤╄尙鎲稿澶婄?闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findByProductId(testProduct1.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛滈梺绯曟閸樺墽绮堥崟顖涚厸濠㈣泛鐗嗛崝銉╂煕閵堝棛鐭嬬紒瀣槸椤撳ジ宕ㄩ灏栧亾闁秵鍋ｅù锝夋涧閻忊晝鈧娲滈崰鏍极?
        assertThat(items).hasSize(2);
        assertThat(items)
                .extracting(SalesOrderItem::getSalesOrderId)
                .containsExactlyInAnyOrder(testOrder1.getId(), testOrder2.getId());
    }

    @Test
    @DisplayName("case-6")
    void testFindByProductId_SingleItem() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌椤╄尙鎲稿澶婄?闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findByProductId(testProduct2.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?1 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSalesOrderId()).isEqualTo(testOrder1.getId());
        assertThat(items.get(0).getQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("case-7")
    void testFindByProductId_NoItems() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏂挎惈婵℃寧绻涢崼鐔风仸缂佸倸绉烽ˇ鏌ユ煙绾拌鲸绠烡
        List<SalesOrderItem> items = salesOrderItemRepository.findByProductId(99999L);

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担姝屽闁圭⒈鍋婂畷褰掝敂閸℃ê浠?
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("case-8")
    void testDeleteBySalesOrderId() {
        // Given: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭仦鎯ь€涢梺闈涚箳婵绮?闂?2 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        List<SalesOrderItem> itemsBefore = salesOrderItemRepository.findBySalesOrderId(testOrder1.getId());
        assertThat(itemsBefore).hasSize(2);

        // When: 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁瑰嘲顑夋慨鈧柣妯垮皺椤?闂備焦鐪归崝宀€鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾椤戝棝锝為弽顐ょ＝?
        salesOrderItemRepository.deleteBySalesOrderId(testOrder1.getId());
        entityManager.flush();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤槴闂佺粯顭囩划顖炴偟閻斿吋鐓欓悗娑櫭慨鍥р攽?
        List<SalesOrderItem> itemsAfter = salesOrderItemRepository.findBySalesOrderId(testOrder1.getId());
        assertThat(itemsAfter).isEmpty();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭仦鎯ь€涢梺闈涚箳婵绮?闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕濂告煕閳╁叐鎴濐嚕閹剧粯鐓曟繛鍡樺姦濞堟柨鈹戦崒娑氬⒌鐎?
        List<SalesOrderItem> order2Items = salesOrderItemRepository.findBySalesOrderId(testOrder2.getId());
        assertThat(order2Items).hasSize(1);
    }

    @Test
    @DisplayName("case-9")
    void testFindBySpecifiedBatchIdsContaining_Batch123() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁€宀勬煕濞戝崬骞楅柛搴㈡崌閺岀喐鎷呴崘鐐秷濡?23闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySpecifiedBatchIdsContaining("123");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顓奸梺璇″瀻閸愵亜甯?1 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSalesOrderId()).isEqualTo(testOrder1.getId());
        assertThat(items.get(0).getProductId()).isEqualTo(testProduct2.getId());
        assertThat(items.get(0).getSpecifiedBatchIds()).contains("123");
    }

    @Test
    @DisplayName("case-10")
    void testFindBySpecifiedBatchIdsContaining_Batch456() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁€宀勬煕濞戝崬骞楅柛搴㈡崌閺岀喐鎷呴崘鐐秷濡?56闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySpecifiedBatchIdsContaining("456");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顓奸梺璇″瀻閸愵亜甯?1 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSpecifiedBatchIds()).contains("456");
    }

    @Test
    @DisplayName("case-11")
    void testFindBySpecifiedBatchIdsContaining_Batch789() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁€宀勬煕濞戝崬骞楅柛搴㈡崌閺岀喐鎷呴崘鐐秷濡?89闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySpecifiedBatchIdsContaining("789");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顓奸梺璇″瀻閸愵亜甯?1 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSalesOrderId()).isEqualTo(testOrder2.getId());
        assertThat(items.get(0).getSpecifiedBatchIds()).contains("789");
    }

    @Test
    @DisplayName("case-12")
    void testFindBySpecifiedBatchIdsContaining_NoMatch() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏂挎惈婵℃寧绻涢崼鐔风伌妤犵偛绉堕幉鎾礋闂堟稐杩?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySpecifiedBatchIdsContaining("999");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担姝屽闁圭⒈鍋婂畷褰掝敂閸℃ê浠?
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("case-13")
    void testSaveNewItem() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙濡﹢鏌熷畡鎵劸闁告艾鍊块弻娑樜旀担鍦槰濠碘€冲级閸ㄥ湱妲?
        SalesOrderItem newItem = SalesOrderItem.builder()
                .salesOrderId(testOrder2.getId())
                .productId(testProduct2.getId())
                .quantity(25)
                .unitPrice(new BigDecimal("55.00"))
                .subtotal(new BigDecimal("1375.00"))
                .rejectNearExpiry(false)
                .build();

        // When: 濠电儑绲藉ú锔炬崲閸岀偞鍋ら柕濞у嫬顎涢梺闈涚箳婵绮堢€ｎ喗鐓涢柛鏇ㄥ亝閹癸絿绱?
        SalesOrderItem saved = salesOrderItemRepository.save(newItem);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ兘鏀冲┑鐘绘涧濡盯骞楅悩缁樼厵閻庢稒锚婵洤鈹?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂傚倷鐒﹁ぐ鍐矓閻㈢钃熷┑鐘叉搐閽冪喖鏌曟径妯煎帥闁搞倕瀚伴幃瑙勬媴闂堟稓浠奸悗?闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(testOrder2.getId());

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        assertThat(items).hasSize(2);
    }

    @Test
    @DisplayName("case-14")
    void testUpdateItem() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬫繈鏌ｅΔ鈧悧濠勭不閹烘鍋ｅù锝夋涧閻忊晝鈧娲滈崰鏍ь嚕閻楀牊鍎熼柨婵嗘川閻?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(testOrder1.getId());
        SalesOrderItem item = items.get(0);

        // When: 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柤娴嬫櫆婵ジ鏌曡箛鏇炐㈢紒鈧€ｎ喗鐓涢柛鏇ㄥ亝閹癸絿绱?
        item.setQuantity(30);
        item.setSubtotal(new BigDecimal("3600.00"));
        salesOrderItemRepository.save(item);

        // 婵犵數鍋為幐鎼佸箠濡　鏋嶉幖娣妼缁犳澘霉閿濆妫戦柣锝勭矙閺屾盯寮介妸褍鈪剁紓浣诡殔閸婂湱绮欐径灞稿亾閿濆骸浜濋柣搴櫍閺屻劌鈽夊Ο鍨伃閻庤鎮傛禍璺虹暦濮樿泛閱囬柡鍥╁仦椤忕喖姊洪崫鍕偓缁樻櫠濡ゅ懏鍋傞柨娑樺鐎?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒€宓嗛柣搴㈢⊕钃遍柣鎾亾闂備胶鎳撻悺銊╁礉閺囩喐鍙?
        SalesOrderItem reloaded = salesOrderItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(30);
        assertThat(reloaded.getSubtotal()).isEqualByComparingTo("3600.00");
    }

    @Test
    @DisplayName("case-15")
    void testDeleteItem() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬫繈鏌ｅΔ鈧悧濠勭不閹烘鍋ｅù锝夋涧閻忊晝鈧娲滈崰鏍ь嚕閻楀牊鍎熼柨婵嗘川閻?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(testOrder1.getId());
        SalesOrderItem item = items.get(0);
        Long itemId = item.getId();

        // When: 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁瑰嘲顑夋慨鈧柣妯垮皺椤︿即姊洪崫鍕仼婵炴挳顥撻崚?
        salesOrderItemRepository.delete(item);
        entityManager.flush();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤槴闂佺粯顭囩划顖炴偟閻斿吋鐓欓悗娑櫭慨鍥р攽?
        assertThat(salesOrderItemRepository.findById(itemId)).isEmpty();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭仦鎯ь€涢梺闈涚箳婵绮?闂備礁鎲￠悷顖涚濠婂嫭娅?1 濠电偞鍨堕幖鈺傜濠靛柈锝夊箣閻樼數锛?
        List<SalesOrderItem> remainingItems = salesOrderItemRepository.findBySalesOrderId(testOrder1.getId());
        assertThat(remainingItems).hasSize(1);
    }

    @Test
    @DisplayName("case-16")
    void testCountItems() {
        // When: 缂傚倸鍊烽懗鍫曞窗閺囥埄鏁囬柟闂寸缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閹兼惌鍠栭幃鍛存⒑閸涘娈曟繛鍙壝嵄闁瑰濮风壕?
        long count = salesOrderItemRepository.count();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?3 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓闂佸搫娲ㄩ崰鎰帮綖閺嶎偆纾?
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("case-17")
    void testFindAll() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閹兼惌鍠栭幃鍛存⒑閸涘娈曟繛鍙壝嵄闁瑰濮风壕?
        List<SalesOrderItem> allItems = salesOrderItemRepository.findAll();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?3 濠电偞鍨堕幖鈺傜閿濆鏁囩憸鐗堝笒绾偓闂佸搫娲ㄩ崰鎰帮綖閺嶎偆纾?
        assertThat(allItems).hasSize(3);
    }

    @Test
    @DisplayName("case-18")
    void testRejectNearExpiryFlag() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暞婵ジ鏌曡箛鏇炐㈢紒鈧?闂備焦鐪归崝宀€鈧凹鍘煎嵄闁瑰濮风壕?
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(testOrder1.getId());

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤唵闂佸湱鍎ら幐濠氬汲椤忓嫧妲堥柟鎯х－閹界姵绻濋埀顒勬晸閻樿弓绱舵繛杈剧到濠€閬嶅垂婵傚摜鍙?
        SalesOrderItem item1 = items.stream()
                .filter(i -> i.getProductId().equals(testProduct1.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(item1.getRejectNearExpiry()).isFalse();

        SalesOrderItem item2 = items.stream()
                .filter(i -> i.getProductId().equals(testProduct2.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(item2.getRejectNearExpiry()).isTrue();
    }
}
