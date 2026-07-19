package com.wms.system.service;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Product;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.Zone;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * InventoryBatchService 闂傚倷绀侀幉锟犮€冮崱妞曟椽寮介鐐插亶闂佽宕樼粔顕€宕烽娑樹壕闁挎繂楠搁獮妯讳繆?
 *
 * 濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倖鍨块弻娑㈠箛閳轰礁顬夌紓浣鸿檸閸ㄥ爼寮?
 * 1. FIFO 闂傚倷绀侀幉锟犲垂閻㈢绠规い鎰╁€涙慨鎶芥⒑椤掆偓缁夌敻鎮炴繝姘厸闁告洦鍋嗙粻鎶芥偨椤栨碍顥㈤柡灞诲妼閳藉螣閻撳寒鏆梻浣告啞钃辩紒瀣笒椤洨鎷犲ù瀣潔濠碘槅鍨伴幖顐㈩嚕濡ゅ懏鈷戦柛娑橈工閻忊晠鎮樿箛瀣婵?
 * 2. 闂傚倸鍊搁崐绋课涘鍐ｆ灃婵炴垯鍨规闂佸搫鍟犻崑鎾绘倵閻㈤潧甯堕摶锝夋煕濠靛棗顏╃€殿喗顨堢槐鎾存媴閸︻厸濮囬梺缁橆殕缁挸顕ｉ幎钘夌疀闁绘鐗婂▍鏍⒑闂堚晛顩柛銊︽▓se Item First闂?
 * 3. 闂備礁婀遍崢褔鎮洪妸鈺佺闁归棿鐒﹂崑銈夋煏婵犲繐顩柍缁樻⒒閳ь剙绠嶉崕閬嶆偋閸℃稑绐楅柡鍥╁Л閸嬫挸鈻撻崹顔界亪闂佺锕ラ幃鍌炲箖閵夆晜鍤嬮梻鍫熺〒缁愮偤姊洪崨濠冨闁告ü绮欏畷?
 * 4. 闂備礁鎼ˇ顐﹀疾濞戞◤娲晝閳ь剟鏁冮姀銈嗘櫢闁绘灏欓濂告煟閻斿摜鎳冮悗姘卞厴瀹曟垼顦冲ǎ鍥э躬椤㈡洟鎮㈤摎鍌氼棜濠?
 * 5. 闂傚倷绶氶埀顒佺〒缁侀攱銇勯銏╂█妞ゃ垺娲樼缓鐣岀矙閼愁垱鎲伴梻渚€娼чˇ浠嬫偂閸儱鍚归柛鏇ㄥ灡閻撴洟鏌￠崘銊モ偓鎼佸几濞嗘挻鐓?
 * 6. 闂備礁婀遍崢褔鎮洪妸鈺佺闁归棿鐒﹂崑銈夋煏婵炲灝鍓剧憸鏂跨暦閸洖惟闁靛繒濮虫竟鏇㈡⒑閸濆嫬顏╂繛瀛樺哺瀹曘儵鍩€椤掑倻纾?
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class InventoryBatchServiceTest {

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private ProductSkuRepository productSkuRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @Mock
    private LocationOccupancyService locationOccupancyService;

    @InjectMocks
    private InventoryBatchService inventoryBatchService;

    private ProductSku testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch looseBatch;
    private InventoryBatch fullPackBatch;
    private InventoryBatch expiredBatch;

    private void mockActiveBatches(List<InventoryBatch> batches) {
        when(inventoryBatchRepository.findByProductSkuIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(batches);
    }

    @BeforeEach
    void setUp() {
        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠德ゅ皺椤牓婀侀梺缁橆焾濞呮洜浜搁鐘电＜?
        testWarehouse = Warehouse.builder()
            .id(1L)
            .code("WH01")
            .name("濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽銊с€掓い鈺冨厴閺岀喖骞嗚閿涘秵鎱?")
            .isActive(true)
            .build();

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠碘€冲级閹倿鐛弽顐熷亾濞戞鎴犱焊椤愩倗纾?
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

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠德ゅ皺椤牓婀佸┑鐘欏懎孝闁哥噥鍨堕獮鍡涘磼閻愬鍙?缂?= 12闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤?
        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
            .id(1L)
            .barcode("6901234567890")
            .name("濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倖鍨块幃褰掑传閸曨剚鍎撴俊?")
            .product(Product.builder().id(1L).productCode("SPU001").productName("Test SPU").build())
            .packUnit("闂?")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build();

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭闁哄绶氶弻銊モ攽閸℃瑥顤€閻熸粎澧楅惄顖炲蓟閻斿憡濯撮柛鎰亾缂嶅牆顪冮妶鍡楀闁靛牊鎮傚?闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑欙紞闁哥喎鎳橀弻鐔虹磼濡櫣顑傜紒鐐礃瀹曠數妲愰幒妤佸亹闁告劕寮堕弳鎵磽?
        looseBatch = InventoryBatch.builder()
            .id(1L)
            .batchCode("BATCH001")
            .productSku(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(8)  // 8闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柛鈩冪☉濮规煡骞栨潏鍓хɑ閻?
            .initialQuantity(12)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .version(0)
            .build();

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭闁哄绶氶幃妤呮濞戞﹩妫岄梺鍝ュ仦閹倿寮婚悢鍛婂闁告劗鍋撶紞鍫濐渻閵堝棗濮囬柕鍫熸倐瀵?4闂?= 2缂傚倸鍊烽懗鑸垫叏閻㈢鑸归柤濮愬€栭～?
        fullPackBatch = InventoryBatch.builder()
            .id(2L)
            .batchCode("BATCH002")
            .productSku(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(24)  // 24闂?= 2缂?
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .version(0)
            .build();

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褏鎽犲ù婧垮€濋弻娑㈠Ψ閿濆懎顬夐梺绯曟櫅閸婂潡寮婚悢鍛婂闁告劗鍋撶紞鍫濐渻?
        expiredBatch = InventoryBatch.builder()
            .id(3L)
            .batchCode("BATCH003")
            .productSku(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(12)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().minusDays(1))  // 闂佽姘﹂～澶愭偤閺囩姳鐒婃い蹇撴瀹曟煡鏌涢幇闈涙灈閻?
            .active(true)
            .version(0)
            .build();
    }

    // ========== FIFO 闂傚倷绀侀幉锟犲垂閻㈢绠规い鎰╁€涙慨鎶芥煕濡ゅ啩绱抽柛锔诲幐閸嬫捇鏁愭惔鈥崇濠?==========

    @Test
    @DisplayName("case-2")
    void fifoOutbound_OnlyLooseBatch() {
        // Given - 闂備浇宕垫慨鏉懨洪銏犵哗闂侇剙绉甸崕?5闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑卞剰闁哄绶氶弻銊モ攽閸℃瑥顤€閻熸粎澧楅惄顖炲蓟閻斿憡濯撮柛鎰亾缂嶅牆顪冮妶鍡楀闁靛牏顭堥?8闂?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        mockActiveBatches(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(looseBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 5, SourceType.SALE_OUT, "SO-001", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        );

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(looseBatch.getQuantity()).isEqualTo(3);  // 8 - 5 = 3
        verify(inventoryBatchRepository).save(looseBatch);
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("case-3")
    void fifoOutbound_LooseInsufficientNeedUnpack() {
        // Given - 闂備浇宕垫慨鏉懨洪銏犵哗闂侇剙绉甸崕?20闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑卞剰闁哄绶氶弻銊モ攽閸℃瑥顤€閻?8闂?+ 闂傚倸鍊搁崐绋棵洪悩璇茬；闁瑰墽绮崑锟犳煛閸ャ劍鐨戦柛鏂跨Т閳?1缂傚倸鍊烽懗鑸垫叏閻㈢鑸归柤濮愬€栭～?2闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.findFullPackBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));
        mockActiveBatches(Arrays.asList(looseBatch, fullPackBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 20, SourceType.SALE_OUT, "SO-002", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        );

        // Then
        assertThat(transactions).hasSize(2);  // 2婵犵數鍋為崹鍫曞箹閳哄倻顩叉繝闈涙川閻濆爼鏌熼悜妯虹亶闁?
        assertThat(looseBatch.getQuantity()).isEqualTo(0);  // 闂傚倷娴囧銊ф閵堝洨绀婇柍褜鍓熼弻鐔稿濞嗘垹袦闂佽鍠曠划娆愪繆閹间礁唯鐟滃骸鈻?
        assertThat(fullPackBatch.getQuantity()).isEqualTo(12);  // 24 - 12 = 12
        verify(inventoryBatchRepository, times(2)).save(any(InventoryBatch.class));
    }

    @Test
    @DisplayName("case-4")
    void fifoOutbound_OnlyFullPackBatch() {
        // Given - 闂備浇宕垫慨鏉懨洪銏犵哗闂侇剙绉甸崕?12闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑欘棏闁哄矉绠撻弻宥夊煛娴ｅ憡娈茬紓浣哄У鐢繝寮婚埄鍐懝濠电姴瀚ⅲ闂備線鈧稓涓茬紓宥勭窔瀵偄顓奸崶锔藉媰闂佽姤锚椤︿即鏌ㄩ銏♀拺闁告繂瀚晶鏇熴亜閿斿灝宓嗙€殿喗濞婇弫鎰板炊閳衡偓缁?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.findFullPackBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));
        mockActiveBatches(Collections.singletonList(fullPackBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(fullPackBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 12, SourceType.SALE_OUT, "SO-003", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        );

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(fullPackBatch.getQuantity()).isEqualTo(12);  // 24 - 12 = 12
    }

    @Test
    @DisplayName("case-5")
    void fifoOutbound_InsufficientStock() {
        // Given - 闂備浇宕垫慨鏉懨洪銏犵哗闂侇剙绉甸崕?100闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑欏濠殿垱鎸抽弻娑㈠焺閸忥附宀稿畷鎴﹀箻閼搁潧鐝伴梺鑲┾拡閸撴岸鎯冮幋锔界厽閹兼番鍨婚埊鏇㈡煥濮樿鲸鍠愰柟璺猴工濞层倗鈧?32闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤? + 24闂?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.findFullPackBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 100, SourceType.SALE_OUT, "SO-004", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.INSUFFICIENT_STOCK);

        // Verify no save operations
        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verify(stockTransactionRepository, never()).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("case-6")
    void fifoOutbound_ProductNotFound() {
        // Given
        when(productSkuRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            999L, 10, SourceType.SALE_OUT, "SO-005", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PRODUCT_SKU_NOT_FOUND);
    }

    @Test
    @DisplayName("case-7")
    void fifoOutbound_ExpiredBatchDetected() {
        // Given - 闂傚倷绀侀幖顐︽偋閸℃蛋鍥х暦閸ャ劌搴婇梺鍛婂姦閸犳牜鈧艾顦甸弻锝夊籍閸屻倗鍔稿銈冨劜椤ㄥ棛鎹?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(expiredBatch));
        mockActiveBatches(Collections.singletonList(expiredBatch));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 5, SourceType.SALE_OUT, "SO-006", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.EXPIRED_BATCH_FOUND);
    }

    // ========== 闂傚倸鍊搁崐绋课涘鍐ｆ灃婵炴垯鍨规闂佸搫鍟犻崑鎾绘倵閻㈤潧甯堕摶锝夋煕濠靛棗顏╃€殿喗顨堢槐鎾存媴閸︻厸濮囬梺缁橆殕缁挸顕ｉ幎钘夌疀妞ゆ垹鍋涘﹢閬嶅焵椤掑﹦绉甸柛妯诲劤鍗?==========

    @Test
    @DisplayName("case-8")
    void looseItemFirst_PreferLooseBatch() {
        // Given - 闂傚倷绀侀幖顐︽偋閸℃蛋鍥ㄥ閺夋垶杈堥柟鑹版彧缁插墽鈧俺宕甸幉绋款吋婢跺﹦顔嗘繛鏉戝悑濞兼瑩寮告笟鈧幃妤呮濞戞﹩妫岄梺鍝ュ仦閹倿寮婚妸銉㈡婵☆垵鍋愰崝顔碱渻閵堝棙澶勯柛鎾寸懄閺呫儵姊虹粙璺ㄧ伇闁稿绋戦埢宥夊Χ婢跺鍘卞┑鐐叉閿氶柣蹇曞枎闇夋繝濠傚濞搭噣鏌熼鍨撴繛鐓庣箻婵℃悂鏁傞悾灞剧€梻浣藉吹婵敻宕滈鍕妞ゆ劏鎳ｅ鑸电厽?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        mockActiveBatches(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(looseBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        inventoryBatchService.outboundWithFifo(
            1L, 5, SourceType.SALE_OUT, "SO-007", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        );

        // Then - 闂傚倷绀侀幉锟犳偡椤栨稓顩叉繝闈涱儐閸嬪倿鏌ㄥ☉妯侯仾閻庢碍纰嶉妵鍕敃椤愩垹绠婚梺鍝ュ枎閿曨亪寮婚埄鍐懝濠电姴瀚ⅲ闂備線鈧稓涓茬紓宥勭窔閻涱喖顓奸崶銊ユ瀭闂佸憡娲熷褎绂掕ぐ鎺撯拺闁圭娴烽埥澶愭煛閸偄澧撮柟顖楀亾闂佸憡绋掑娆戔偓姘槸椤法鎹勬笟顖氬壋闂佸憡鐟㈤崑鎾绘⒑閼姐倕孝婵炲眰鍨藉畷鐟懊洪鍕緢闂佹寧绻傚ú銊︾▔瀹ュ绾ч柛顐ｇ☉婵″吋銇勯妷锔绘畷缂?
        verify(inventoryBatchRepository).findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true);
        verify(inventoryBatchRepository, never()).findFullPackBatchesByProductSkuOrderByExpiryDateAsc(anyLong(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("case-9")
    void looseItemFirst_UnpackOnlyWhenNecessary() {
        // Given - 闂傚倷娴囧銊ф閵堝洨绀婇柍褜鍓熼弻?8闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑氬閻庢碍宀稿娲垂椤曞懎鍓冲┑鐘亾?10闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑卞剱妞ゃ儱妫濋弻宥堫檨闁告挻鐩獮澶愭偋閸喎顎撻梺鑽ゅ枛閸嬪﹪鍩€?1闂?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.findFullPackBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));
        mockActiveBatches(Arrays.asList(looseBatch, fullPackBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 10, SourceType.SALE_OUT, "SO-008", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        );

        // Then
        assertThat(transactions).hasSize(2);
        assertThat(looseBatch.getQuantity()).isEqualTo(0);  // 闂傚倷娴囧銊ф閵堝洨绀婇柍褜鍓熼弻鐔稿濞嗘垹袦闂佽鍠曠划娆愪繆閹间礁唯鐟滃骸鈻?
        assertThat(fullPackBatch.getQuantity()).isEqualTo(22);  // 24 - 2 = 22
    }

    // ========== 婵犵數濮伴崹濂稿春閺嶎偄鏋堢€广儱妫涢悵鍫曟煙閻戞ê鐏嶉柡瀣叄閺屽秹濡烽妷銉︽瘣濠殿噯绲介ˇ鐢哥嵁閺嶎偀鍋撳☉娅虫垹浜搁幍顔剧＜濞达絽澹婇崵鐔兼煙?==========

    @Test
    @DisplayName("case-10")
    void multipleBatches_SortedByExpiryDate() {
        // Given - 2婵犵數鍋為崹鍫曞箹閳哄倻顩叉繝闈涱儏濮规煡骞栨潏鍓хɑ閻庢俺宕甸幉鍛婃償閵忋垻褰鹃梺鍦劋閸ㄧ喖寮告惔銊︾厓闁芥ê顦伴崳鐣岀磼閳ь剚寰勬繛鐐潔闂佽鍎崇壕顓炵摥闂備浇顕栭崰妤呭箲閸パ屽殨闁割煈鍋勭欢鐐烘倵閿濆骸浜濋柣婵撶節濮?
        InventoryBatch looseBatch1 = InventoryBatch.builder()
            .id(1L)
            .batchCode("BATCH001")
            .productSku(testProduct)
            .quantity(5)
            .expiryDate(LocalDate.now().plusMonths(2))  // 闂備礁鎼ˇ顖炴偋閸℃稑鐤い鏍嚤濞戙埄鏁囬柣鏃堟櫜濮橈箓姊洪幖鐐插姌闁告柨绉瑰畷?
            .active(true)
            .build();

        InventoryBatch looseBatch2 = InventoryBatch.builder()
            .id(2L)
            .batchCode("BATCH002")
            .productSku(testProduct)
            .quantity(5)
            .expiryDate(LocalDate.now().plusMonths(4))  // 闂備礁鎼ˇ顖炴偋閸℃稑鐤い鏍ㄥ焹閺嬪秹鏌涢弴銊ュ濞存嚎鍊濋弻娑㈠Ψ閿濆懎顬夐梺?
            .active(true)
            .build();

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Arrays.asList(looseBatch1, looseBatch2));  // 闂佽楠稿﹢閬嶁€﹂崼婵愬殨闁告挷璁查崑鎾诲垂椤愩倗鐓夐悗娈垮枛閻忔艾顕ラ崟顓涘亾閿濆骸浜濋柣婵撶節濮婃椽宕崟顓炩叡闂佸摜濮甸崝妤呭极椤曗偓楠炴鈧稒锚閸?
        mockActiveBatches(Arrays.asList(looseBatch1, looseBatch2));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When - 闂備浇宕垫慨鏉懨洪銏犵哗闂侇剙绉甸崕?8闂?
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 8, SourceType.SALE_OUT, "SO-009", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        );

        // Then - 闂傚倷鑳堕…鍫㈡崲閹扮増鍋嬮煫鍥ㄧ☉閺嬩線鏌曢崼婵囩闁稿鎹囬幃鐑藉箥椤斿彞绱樻俊鐐€栭崹闈浢洪敂鍓х煓濠㈣埖鍔曢悞鍨亜閹哄秷鍏岄柍缁樻⒐閵囧嫯绠涢幘鏉戞濡炪們鍎查〃鍡欐崲濞戙垹骞㈤煫鍥ㄦ濞兼劗绱撴担浠嬪摵缂佽鐗嗛悾宄拔熸笟顖氭倯闂佹悶鍎崝宥呪枔閵忋垻纾藉ù锝堫嚃濞堛垽鏌涜箛鏃傘€掗柤娲憾閸╋繝宕掑┃鐐亙婵＄偑鍊曠换鎰板箠鎼淬剫澶娢旀担铏诡啎?
        assertThat(transactions).hasSize(2);
        assertThat(looseBatch1.getQuantity()).isEqualTo(0);  // 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶姘ㄩ崡姘舵倵濞戞瑯鐒介柍缁樻⒐閵囧嫯绠涢幘鏉戞濡炪們鍎查〃鍡欐崲濞戙垹骞㈡い鎾跺Х閿涚喖姊烘潪浼存闁稿﹥顨婇崺鈧?
        assertThat(looseBatch2.getQuantity()).isEqualTo(2);  // 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶淇烘慨鎶芥煃閸濆嫬鈧鍩㈤弮鈧妵鍕疀閹炬潙娅ｅ銈冨劜椤ㄥ棛鎹㈠☉銏犲耿闊洤锕ゆ禍楣冩煕閹邦厾銈撮柟绋挎噺缁?2闂?
    }

    // ========== 闂備礁鎼ˇ顖炴偋閸℃鑰块梺顒€绉撮悿鐐亜閹板墎鐣辩紒鐘冲哺閺屾稑鈽夐崡鐐茬闂佸搫顑呴張顒傛崲濞戙垹绾ч柟鎼幖閸撶敻姊?==========

    @Test
    @DisplayName("case-11")
    void outbound_ZeroQuantity() {
        // Given
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 0, SourceType.SALE_OUT, "SO-010", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("case-12")
    void outbound_NegativeQuantity() {
        // Given
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, -10, SourceType.SALE_OUT, "SO-011", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("case-13")
    void outbound_NoBatches() {
        // Given
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.findFullPackBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.emptyList());
        mockActiveBatches(Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 10, SourceType.SALE_OUT, "SO-012", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("case-14")
    void outbound_ExactMatch() {
        // Given - 闂傚倷娴囧銊ф閵堝洨绀婇柍褜鍓熼弻?8闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤洘绻濋棃娑氬閻庢碍宀稿娲垂椤曞懎鍓冲┑鐘亾?8闂?
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductSkuOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        mockActiveBatches(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(looseBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 8, SourceType.SALE_OUT, "SO-013", 1L, "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶鐎瑰憡绻冩穱濠囶敍濠靛洢鈧啰绱?"
        );

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(looseBatch.getQuantity()).isEqualTo(0);  // 闂傚倷绀侀幉锛勬暜濡ゅ懏鍤屽Δ锝呭暙鍥存繛瀵稿Т椤戝棝宕戦妸鈺傜厪濠电偛鐏濋崝銈夋煛?
    }
}



