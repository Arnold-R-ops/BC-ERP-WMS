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
 * InventoryQueryService 闂傚倷绀侀幉锟犮€冮崱妞曟椽寮介鐐插亶闂佽宕樼粔顕€宕烽娑樹壕闁挎繂楠搁獮妯讳繆?
 *
 * 濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倖鍨块弻娑㈠箛閳轰礁顬夌紓浣鸿檸閸ㄥ爼寮?
 * 1. 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶姘ㄩ崡姘舵倵閻㈠憡娅滄繛宀婁邯閺屾稑鐣濈€ｎ亶娲紓浣瑰姈閵嗘〔U 闂傚倷娴囨竟鍫熴仈閹间焦鍤屽Δ锝呭暙缁犳牠骞栨潏鍓хɑ闁哥姴妫濋弻娑㈠焺閸愮偓鐣风紓?
 * 2. 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶淇烘慨鎶芥煃閸濆嫬鏆曟繛宀婁邯閺屾稑鐣濈€ｎ亶娲紓浣瑰姈椤ㄥ﹪寮婚悢鍛婂闁告劗鍋撶紞鍫濐渻閵堝棗濮岄柛瀣姍楠炲繘鎮╃紒妯绘珕闂佽姤锚椤︻垱绔熼崼銉︾厽闁绘柨鎽滈幊鍐╀繆椤愶綆娈旈棁?
 * 3. 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶姘ㄧ粻鏂棵归敐鍫熴€冩繛宀婁邯閺屾稑鐣濈€ｎ亶娲紓浣瑰姈椤ㄥ﹪鐛弽顐熷亾濞戞鎴犱焊椤愩倗纾界€广儱鎳忛崵鍥┾偓瑙勬穿缁蹭粙顢樻總绋垮耿婵炲棗绻愮紒鈺呮⒑閼姐倕鏋戦柣鐔村劚铻炴俊銈傚亾闂?
 * 4. 闂傚倷绀侀幖顐﹀箠濡偐纾芥慨妯挎硾缁€鍌涙叏濡炶浜鹃悗娈垮櫘閸嬪懐鈧潧銈稿鍫曞箣閻戝棔鐢婚梻浣藉吹婵磭娆㈤妶鍥潟闁哄洢鍨洪崑?
 * 5. 婵犵妲呴崑鍛熆濡皷鍋撳鐓庣仸妞ゃ垺顨婇幃銏ゅ礂閻撳簼姹楅梻浣哄帶閹芥粓銆傛禒瀣；闁规儳鐡ㄦ刊瀵哥磼濞戞﹩鍎忛柟顔昏兌缁?
 * 6. 闂傚倷绀侀幖顐︽偋閸℃蛋鍥ㄥ閺夋垹鏌ч梺闈涱槴閺呮稓鈧艾顦甸弻锝夊籍閸屻倗鍔哥紓浣诡殕閹瑰洭鐛?
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

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠德ゅ皺椤牓婀佸┑鐘欏懎孝闁哥噥鍨堕獮?
        testProduct = Product.builder()
            .id(1L)
            .barcode("6901234567890")
            .name("濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倖鍨块幃褰掑传閸曨剚鍎撴俊?")
            .spu(ProductSpu.builder().id(1L).spuCode("SPU001").spuName("Test SPU").build())
            .packUnit("闂?")
            .conversionRate(12)  // 1缂?= 12闂?
            .safetyStock(50)
            .build();

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠碘€冲级閹倿寮婚悢鍛婂闁告劗鍋撶紞鍫濐渻?闂傚倷鐒︾€笛呯矙閹达附鍋嬪┑鐘叉搐濮规煡鏌ㄥ┑鍡樺窛濞戞挸绉归弻鐔煎箵閹烘繂鍓崇紓?4闂?= 2缂傚倸鍊烽懗鑸垫叏閻㈢鑸归柤濮愬€栭～?
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

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠碘€冲级閹倿寮婚悢鍛婂闁告劗鍋撶紞鍫濐渻?闂傚倷鐒︾€笛呯矙閹达附鍋嬪┑鐘叉搐濮规煡骞栨潏鍓хɑ閻庢俺宕甸幉绋库攽閸噥娼?闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柟瀵稿У椤?
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

    // ========== 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶姘ㄩ崡姘舵倵閻㈠憡娅滄繛宀婁邯閺屾稑鐣濈€ｎ亶娲紓浣瑰姈閵嗘〔U 闂傚倷娴囨竟鍫熴仈閹间焦鍤屽Δ锝呭暙缁犳牠骞栨潏鍓хɑ闁哥姴妫濋弻娑㈠焺閸愮偓鐣风紓浣稿€搁悧濠勬崲濞戙垹绾ч柟鎼幖閸撶敻姊?==========

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
        assertThat(summary.getSkuInfo().getName()).isEqualTo("濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倖鍨块幃褰掑传閸曨剚鍎撴俊?");
        assertThat(summary.getSkuInfo().getSkuCode()).isEqualTo("6901234567890");
        assertThat(summary.getDisplayQuantity()).isEqualTo("2缂?+ 8闂?");  // 24/12=2缂? 8闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柛鈩冪☉濮规煡骞栨潏鍓хɑ閻?
        assertThat(summary.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);  // 32 < 50
        assertThat(summary.getFurthestExpiryDate()).isEqualTo(LocalDate.now().plusMonths(6));
    }

    @Test
    @DisplayName("case-3")
    void testGetSummary_WithSearch() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findByNameContaining("闂傚倷娴囨竟鍫熺珶閸績鏋栨繛鎴炲殠娴?)"))
            .thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(pageable, "闂傚倷娴囨竟鍫熺珶閸績鏋栨繛鎴炲殠娴?");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).findByNameContaining("闂傚倷娴囨竟鍫熺珶閸績鏋栨繛鎴炲殠娴?");
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
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("3缂?+ 0闂?");
    }

    @Test
    @DisplayName("case-5")
    void testDisplayQuantity_OnlyLooseItems() {
        // Given
        InventoryBatch looseBatch = InventoryBatch.builder()
            .quantity(8)  // 8闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柛鈩冪☉濮规煡骞栨潏鍓хɑ閻?
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
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("0缂?+ 8闂?");
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
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("2缂?+ 8闂?");
    }

    @Test
    @DisplayName("case-7")
    void testStockStatus_Sufficient() {
        // Given
        testProduct.setSafetyStock(30);  // 闂備浇顕уù鐑藉箠閹惧嚢鍥敍閻愯尙鐓戦棅顐㈡处濮婂綊宕曢悢鍏肩厵闁诡垎鍜冪礊闂?30
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);  // 闂備浇顕ф绋匡耿闁秴纾婚柕鍫濇媼閻庤埖銇勯弽銊р姇闁稿海鍠栭弻鐔煎箚瑜忛敍宥夋煙?32

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.SUFFICIENT);
    }

    @Test
    @DisplayName("case-8")
    void testStockStatus_LowStock() {
        // Given
        testProduct.setSafetyStock(50);  // 闂備浇顕уù鐑藉箠閹惧嚢鍥敍閻愯尙鐓戦棅顐㈡处濮婂綊宕曢悢鍏肩厵闁诡垎鍜冪礊闂?50
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);  // 闂備浇顕ф绋匡耿闁秴纾婚柕鍫濇媼閻庤埖銇勯弽銊р姇闁稿海鍠栭弻鐔煎箚瑜忛敍宥夋煙?32

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
    }

    // ========== 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶淇烘慨鎶芥煃閸濆嫬鏆曟繛宀婁邯閺屾稑鐣濈€ｎ亶娲紓浣瑰姈椤ㄥ﹪寮婚悢鍛婂闁告劗鍋撶紞鍫濐渻閵堝棗濮岄柛瀣姍楠炲繘鎮╃紒妯绘珕闂佽姤锚椤︻垱绔熼崼銉︾厽闁绘柨鎽滈幊鍐╀繆椤愶綆娈旈棁澶嬩繆椤栨哎鍋ㄩ柛锔诲幐閸嬫捇鏁愭惔鈥崇濠?==========

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

        // 婵犲痉鏉库偓妤佹叏閹绢喗鍎楀〒姘ｅ亾闁诡垯鐒﹀鍕箛椤掑偆鍞跺┑鐘灱濞夋盯顢栭崱娆戜笉闁圭儤顨嗛悡娑樏归敐鍛喐濠⒀勭⊕缁绘繈鍩€椤掑嫭鏅搁柣妯绘灱閹稿啴鎮楅獮鍨姎闁硅櫕鍔楅崰濠傤吋婢跺鍘甸梺缁樺灦閿氱紒妤佸浮閺屾盯鈥﹂幋婊堝仐闂佽桨绀佺粔鐟扮暦婵傚憡鍋勯梻鈧幇顔肩闂傚倷绀侀幖顐﹀磹閻戣棄纭€闁规儼濮ら崕妤呮煟閹邦喖鍔嬮柛搴＄Ч閺屾盯寮撮妸銉ょ凹闂傚鍓﹂崜鐔煎蓟濞戙垹绠荤€规洖娲犻弸宀€绱?
        assertThat(result.get(0).getBatchCode()).isEqualTo("BATCH001");  // 6婵犵數鍋為崹鍫曞箹閳哄倻顩叉繝闈涙４閼板潡鏌嶈閸撶喖寮诲☉姗嗘僵妞ゆ帊绀侀弨顓犵磽娴ｇ瓔鍤欓柛鐔告綑椤?
        assertThat(result.get(0).getExpiryDate()).isEqualTo(LocalDate.now().plusMonths(6));
        assertThat(result.get(1).getBatchCode()).isEqualTo("BATCH002");  // 3婵犵數鍋為崹鍫曞箹閳哄倻顩叉繝闈涙４閼板潡鏌嶈閸撶喖寮诲☉姗嗘僵妞ゆ帊绀侀弨顓犵磽娴ｇ瓔鍤欓柛鐔告綑椤?
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
        assertThat(result.get(0).getPackageStatus()).isEqualTo("濠碘槅鍋撶徊浠嬪疮椤栫偛绠?闂傚倷娴囧銊х矆娓氣偓椤㈡岸顢橀姀鈺傛櫓?");
    }

    @Test
    @DisplayName("case-11")
    void testBatchDetail_LooseStatus() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(testBatch2));  // 8闂備浇宕甸崑鐐烘嚌妤ｅ喚鏁勯柛鈩冪☉濮规煡骞栨潏鍓хɑ閻?

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result.get(0).getPackageStatus()).isEqualTo("濠碘槅鍋撶徊浠嬪疮椤栫偛绠?闂傚倷娴囧銊ф閵堝洨绀婇柍褜鍓熼弻?");
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

    // ========== 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶姘ㄧ粻鏂棵归敐鍫熴€冩繛宀婁邯閺屾稑鐣濈€ｎ亶娲紓浣瑰姈椤ㄥ﹪鐛弽顐熷亾濞戞鎴犱焊椤愩倗纾界€广儱鎳忛崵鍥┾偓瑙勬穿缁蹭粙顢樻總绋垮耿婵炲棗绻愮紒鈺呮⒑閼姐倕鏋戦柣鐔村劚铻炴俊銈傚亾闂囧淇婇姘ュ仺闁革富鍘搁崑鎾绘晲鎼粹€崇濠?==========

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
        assertThat(result.getWarehouseName()).isEqualTo("濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽銊с€掓い鈺冨厴閺岀喖骞嗚閿涘秵鎱?");
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
            .hasMessageContaining("闂備礁婀遍崢褔鎮洪妸鈺佺濡炲娴风粈濠囨煕濞戝崬鏋ら柍缁樻⒒閳ь剙绠嶉崕閬嶅箠閹版澘绠洪柣妯肩帛閻?");
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

    // ========== 闂備礁鎼ˇ顖炴偋閸℃鑰块梺顒€绉撮悿鐐亜閹板墎鐣辩紒鐘冲哺閺屾稑鈽夐崡鐐茬闂佸搫顑呴張顒傛崲濞戙垹绾ч柟鎼幖閸撶敻姊?==========

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
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("0缂?+ 0闂?");
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

        // When - 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶姘ㄩ崡姘舵倵濞戞婊勭濠婂懐妫柡澶嬵儥濡茶櫣绱掗埀顒佸緞鐎ｎ偄寮块梺鎸庣箓閹虫劗绮绘繝姘厪?闂?
        Page<InventorySummaryDto> page1 = inventoryQueryService.getSummary(PageRequest.of(0, 2), null);

        // Then
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(3);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        // When - 缂傚倸鍊烽悞锕傘€冭箛娑樼婵炴垶淇烘慨鎶芥煃閸濆嫬鈧埖绂?
        Page<InventorySummaryDto> page2 = inventoryQueryService.getSummary(PageRequest.of(1, 2), null);

        // Then
        assertThat(page2.getContent()).hasSize(1);
    }
}
