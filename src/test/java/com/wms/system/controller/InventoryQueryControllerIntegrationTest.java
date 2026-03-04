package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSpuRepository;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InventoryQueryController 闂傚倸鍊稿Λ妤€螞濞嗘挸鍨傛慨姗嗗劒閸︻厸鍋撻敐搴″箻婵?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閸ゆ垿鏌涢幇鈺佸缂佺虎鍨堕弻?
 * 1. GET /api/inventory/summary - SKU 闂備浇澹堟ご鎼佹嚌妤ｅ啫绠栭幖杈剧稻閸犲棝鏌涢埄鍐炬畷缂?
 * 2. GET /api/inventory/details/{skuId} - 闂備線鈧稓绁锋い顐㈩樀椤㈡洟鎳栭埡鍌氱彴闂佹寧妫佸Λ鍕礈娴煎瓨鍋℃繛鍡楃箲椤ユ粍绻?
 * 3. GET /api/inventory/location/{locationCode} - 闂佸湱鍘ч悺銊╁箰妞嬪海绀婇柛娑欐綑閻鈧箍鍎卞Λ娑㈠矗閳ь剟鏌ｉ悢鍝ユ噧婵☆偅顨呴湁?
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class InventoryQueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch testBatch1;
    private InventoryBatch testBatch2;

    @BeforeEach
    void setUp() {
        // 婵犵數鍋為幐鎼佸箠閹版澘绠栧┑鐘叉搐閺嬩線鏌ｅΔ鈧悧鍡欑矈?
        inventoryBatchRepository.deleteAll();
        locationRepository.deleteAll();
        productRepository.deleteAll();
        productSpuRepository.deleteAll();
        warehouseRepository.deleteAll();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵¤尙顭堥湁闁绘娅曠亸顓犵磼?
        testWarehouse = Warehouse.builder()
            .code("WH01")
            .name("Main Warehouse")
            .address("Test Address 1")
            .isActive(true)
            .build();
        testWarehouse = warehouseRepository.save(testWarehouse);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞獮鏍偓娑櫳戠亸顐ょ磽?
        testLocation = Location.builder()
            .warehouse(testWarehouse)
            .warehouseCode("WH01")
            .zone(Zone.ZONE_A)
            .shelfNumber("A-01")
            .positionNumber("001")
            .enabled(true)
            .build();
        testLocation = locationRepository.save(testLocation);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵?SPU
        ProductSpu testSpu = ProductSpu.builder()
            .spuCode("SPU-TEA-001")
            .spuName("Test Tea SPU")
            .category("Beverage")
            .description("Test product spu description")
            .build();
        testSpu = productSpuRepository.save(testSpu);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵¤尙顭堥湁婵犙呭Т閸燁垶骞?
        testProduct = Product.builder()
            .spu(testSpu)  // 闂備胶顭堢换鎰版偪閸ャ劎顩?SPU
            .barcode("6901234567890")
            .name("Test Green Tea")
            .skuName("Test Green Tea SKU")  // V3.3 闂傚鍋勫ú銈夊箠鎼淪劍鏅查柣鎰暯閸嬫挸鈽夊▎妯荤暦濡?
            .packUnit("BOX")
            .conversionRate(12)  // 1缂?= 12闂?
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new java.math.BigDecimal("99.99"))
            .enabled(true)
            .build();
        testProduct = productRepository.save(testProduct);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻鐔告媴閸愮偓缍堝?闂備焦瀵х粙鎴︽偋婵犲洤姹查柨婵嗘川娑撳秹鏌熼幓鎺濆剳缂?4闂?= 2缂傚倷鑳舵慨鐢稿船閼姐倖顫?
        testBatch1 = InventoryBatch.builder()
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(24)
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build();
        testBatch1 = inventoryBatchRepository.save(testBatch1);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻鐔告媴閸愮偓缍堝?闂備焦瀵х粙鎴︽偋婵犲洤姹查幖杈剧稻鐎氳崵鎲稿┑鍫燁潟?闂佽崵鍋為懝楣冾敄閹寸姵顫?
        testBatch2 = InventoryBatch.builder()
            .batchCode("BATCH002")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(8)
            .initialQuantity(8)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .build();
        testBatch2 = inventoryBatchRepository.save(testBatch2);
    }

    // ========== 缂傚倷鐒﹂〃蹇涘礂濞戞氨鍗氶悗鐢告櫜濞岊亪鏌涘畝瀣洭缂佹劖銆桲U 闂備浇澹堟ご鎼佹嚌妤ｅ啫绠栭幖杈剧稻閸犲棝鏌涢埄鍐炬畷缂佸倸鐗婄换娑㈠级閹搭厼鍓甸梺?==========

    @Test
    @DisplayName("case-2")
    void testGetSummary_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].productId").value(testProduct.getId()))
            .andExpect(jsonPath("$.content[0].skuInfo.name").value("Test Green Tea"))
            .andExpect(jsonPath("$.content[0].skuInfo.skuCode").value("6901234567890"))
            .andExpect(jsonPath("$.content[0].displayQuantity", notNullValue()))
            .andExpect(jsonPath("$.content[0].stockStatus").value("LOW_STOCK"))  // 32 < 50
            .andExpect(jsonPath("$.content[0].warehouseNames", hasSize(1)))
            .andExpect(jsonPath("$.content[0].warehouseNames[0]").value("Main Warehouse"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("case-3")
    void testGetSummary_WithSearch() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("search", "Tea")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].skuInfo.name").value("Test Green Tea"));
    }

    @Test
    @DisplayName("case-4")
    void testGetSummary_SearchNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("search", "NOT-FOUND-KEYWORD")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("case-5")
    void testGetSummary_Pagination() throws Exception {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯у缁犳梹銇勯幋锝嗙《闁谎冭嫰閳?SPU
        ProductSpu spu2 = ProductSpu.builder()
            .spuCode("SPU-COFFEE-001")
            .spuName("Test Coffee SPU")
            .category("Beverage")
            .description("Coffee product spu")
            .build();
        spu2 = productSpuRepository.save(spu2);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯у缁犳梹銇勯幋锝嗙《闁谎冭嫰閳藉骞欓崘褏鑳烘俊銈囧Т濞差厼鐣?
        Product product2 = Product.builder()
            .spu(spu2)  // 闂備胶顭堢换鎰版偪閸ャ劎顩?SPU
            .barcode("6901234567891")
            .name("Test Coffee")
            .skuName("Test Coffee SKU")  // V3.3 闂傚鍋勫ú銈夊箠鎼淪劍鏅查柣鎰暯閸嬫挸鈽夊▎妯荤暦濡?
            .packUnit("BOX")
            .conversionRate(10)
            .safetyStock(30)
            .minStock(20)
            .leadTime(5)
            .unitPrice(new java.math.BigDecimal("79.99"))
            .enabled(true)
            .build();
        productRepository.save(product2);

        // 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ矌缁犳梹銇勯幋锝嗙《妞ゅ繐宕—鍐Χ閸ヨ埖娈扮紓浣介哺閻熴儴顣鹃棅顐㈡处缁嬪繘鍩€?闂備礁鎼ˇ顐⑽ｉ崟顓燁潟?
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "0")
                .param("size", "1")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));

        // 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ矌缁犳梹銇勯幋锝嗙《闁谎冭嫰椤?
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "1")
                .param("size", "1")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("case-6")
    void testGetSummary_SufficientStock() throws Exception {
        // 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柤娴嬫杹閸嬫捇鎮烽柇锔叫﹂梺鍛婄懃缁绘垿骞嗛弮鍫濈鐎规洖娲﹂幉鍏肩箾?30闂備焦瀵х粙鎴︽偋閸℃瑧绀婇悗锝庡枛缁€鍫⑩偓骞垮劚閹虫劕顫濋妸鈺傚€?32 > 30闂?
        testProduct.setSafetyStock(30);
        productRepository.save(testProduct);

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].stockStatus").value("SUFFICIENT"));
    }

    @Test
    @DisplayName("case-7")
    void testGetSummary_OnlyFullBoxes() throws Exception {
        // 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓㈠弰鐎殿噮鍋婇弫鎰板醇閻旈绋夐梻渚€鈧稓绁锋い顐㈩樀椤?
        inventoryBatchRepository.delete(testBatch2);

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].displayQuantity", notNullValue()));
    }

    @Test
    @DisplayName("case-8")
    void testGetSummary_OnlyLooseItems() throws Exception {
        // 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓㈠弰鐎殿噮鍋嗛埀顒勬涧婢瑰﹪宕幖浣圭厵濞达絽鍠氬鎰亜?
        inventoryBatchRepository.delete(testBatch1);

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].displayQuantity", notNullValue()));
    }

    // ========== 缂傚倷鐒﹂〃蹇涘礂濞戞俺濮抽柍鍝勫暕濞岊亪鏌涘畝瀣洭缂佹劖顨婇弻鐔告媴閸愮偓缍堝銈嗗姌閸嬫劙骞忛悩缁樻櫆闁芥ê顦竟鍫ユ煟閻斿摜鎳冩俊顐ｎ殔闇夋俊顖氥偨閸︻厸鍋撻敐搴″箻婵?==========

    @Test
    @DisplayName("case-9")
    void testGetDetails_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/details/{skuId}", testProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤唶婵犮垼娉涢鍡欑不閹烘鐓涘ù锝呮憸婢ф稒绻濋埀顒勬晸閻樻枼鎸冮悗骞垮劚閹虫劗鍠婂澶嬬厵闁绘垶锚缁楁帡鏌涢…鎴滈偗闁轰礁绉瑰畷濂告偄闂€鎰笡闂備礁鎼崐鐑藉础閹惰姤鍎楅柣鎰劋閸庡秹鏌涢弴銊ヤ簼闂婎剦鍓熼弻娑㈠箻瀹曞洠鏋岀紓?
            .andExpect(jsonPath("$[0].batchCode").value("BATCH001"))  // 6濠电偞鍨堕幖鈺傜濠靛棴鑰块柍褜鍓熼弻娑橆潩椤掍礁鏀紓浣筋嚙閸熸潙顕?
            .andExpect(jsonPath("$[0].quantity").value(24))
            .andExpect(jsonPath("$[0].packageStatus", notNullValue()))
            .andExpect(jsonPath("$[0].warehouseName").value("Main Warehouse"))
            .andExpect(jsonPath("$[0].locationCode").value(testLocation.getLocationCode()))
            .andExpect(jsonPath("$[1].batchCode").value("BATCH002"))  // 3濠电偞鍨堕幖鈺傜濠靛棴鑰块柍褜鍓熼弻娑橆潩椤掍礁鏀紓浣筋嚙閸熸潙顕?
            .andExpect(jsonPath("$[1].quantity").value(8))
            .andExpect(jsonPath("$[1].packageStatus", notNullValue()));
    }

    @Test
    @DisplayName("case-10")
    void testGetDetails_NoBatches() throws Exception {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘棃鏌嶈閸撴氨绮欐径鎰垫晜闁糕€崇箲閺?SPU
        ProductSpu emptySpu = ProductSpu.builder()
            .spuCode("SPU-EMPTY-001")
            .spuName("Empty Product SPU")
            .category("General")
            .description("Empty product for detail query")
            .build();
        emptySpu = productSpuRepository.save(emptySpu);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘棃鏌嶈閸撴氨绮欐径鎰垫晜闁告侗鍨遍埛鏇㈡⒑閸濆嫮澧曟い锔惧缁旂喖骞掗幋鏃€鐎婚梺鎸庣☉鐎氼參鎮鹃崡鐏荤懓鈹冮悩鎻掓殲闁?
        Product emptyProduct = Product.builder()
            .spu(emptySpu)  // 闂備胶顭堢换鎰版偪閸ャ劎顩?SPU
            .barcode("6901234567892")
            .name("Empty Stock Product")
            .skuName("缂傚倷绀侀惌浣割浖閵婏富娈介柛銉墮娴?SKU")  // V3.3 闂傚鍋勫ú銈夊箠鎼淬劍鏅查柣鎰暯閸嬫挸鈽夊▎妯荤暦濡?
            .packUnit("BOX")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new java.math.BigDecimal("99.99"))
            .enabled(true)
            .build();
        emptyProduct = productRepository.save(emptyProduct);

        mockMvc.perform(get("/api/inventory/details/{skuId}", emptyProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("case-11")
    void testGetDetails_ProductNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/details/{skuId}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("case-12")
    void testGetDetails_ExpiryDateSorting() throws Exception {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯у缁犳梹銇勯幋锝嗙《闁荤喎绻愰埥澶愬箼閸愌呭嚬婵犮垼顫夌敮鎺楀煝鎼淬劌鐓￠柛娑卞灣椤︹晠姊洪崫鍕闁稿鎹囧鍫曞煛閸屾粎鐓佸┑鐘亾濞撴埃鍋撶€殿噮鍋婂畷濂告偄缁嬭法鍔梻?
        InventoryBatch batch3 = InventoryBatch.builder()
            .batchCode("BATCH003")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(12)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().plusMonths(12))  // 闂備礁鎼悧鍐磻閹捐绾ч柍鍝勫€婚惌瀣節閳ь剚绗熼埀顒€顕ｉ锕€閱囬柣鏃傤焾閻?
            .active(true)
            .build();
        inventoryBatchRepository.save(batch3);

        mockMvc.perform(get("/api/inventory/details/{skuId}", testProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顓涙寖閻庡箍鍎遍幊鎰枈瀹ュ鐓欓柣鎴灻粭鎺楁煕椤垳閭柡?2濠电偞鍨堕幖鈺傜濠靛棴鑰块柍?> 6濠电偞鍨堕幖鈺傜濠靛棴鑰块柍?> 3濠电偞鍨堕幖鈺傜濠靛棴鑰块柍?
            .andExpect(jsonPath("$[0].batchCode").value("BATCH003"))
            .andExpect(jsonPath("$[1].batchCode").value("BATCH001"))
            .andExpect(jsonPath("$[2].batchCode").value("BATCH002"));
    }

    // ========== 缂傚倷鐒﹂〃蹇涘礂濞戞氨绠斿ù锝堟〃濞岊亪鏌涘畝瀣洭缂佹劖顨婇獮鏍偓娑櫳戠亸顐ょ磽瀹ュ懏鍤囩€规洩绲介濂稿幢濞嗗繐缁╅梺鑽ゅ枑閻熴儱螞濡も偓闇夋俊顖氥偨閸︻厸鍋撻敐搴″箻婵?==========

    @Test
    @DisplayName("case-13")
    void testGetLocationView_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/location/{locationCode}", testLocation.getLocationCode())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationCode").value(testLocation.getLocationCode()))
            .andExpect(jsonPath("$.warehouseName").value("Main Warehouse"))
            .andExpect(jsonPath("$.batches", hasSize(2)))
            .andExpect(jsonPath("$.batches[0].batchCode").value("BATCH001"))
            .andExpect(jsonPath("$.batches[1].batchCode").value("BATCH002"));
    }

    @Test
    @DisplayName("case-14")
    void testGetLocationView_LocationNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/location/{locationCode}", "INVALID-CODE")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isInternalServerError());  // 濠电偞娼欓崥瀣枈瀹ュ棙鍙忛煫鍥ㄧ☉缁€?IllegalArgumentException
    }

    @Test
    @DisplayName("case-15")
    void testGetLocationView_NoBatches() throws Exception {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘棃鏌嶈閸撴氨绮欐径鎰垫晜闁糕€崇箲閺呯偤鏌熼悡搴ｆ憼闁规悂顥撶槐?
        Location emptyLocation = Location.builder()
            .warehouse(testWarehouse)
            .warehouseCode("WH01")
            .zone(Zone.ZONE_B)
            .shelfNumber("B-01")
            .positionNumber("001")
            .enabled(true)
            .build();
        emptyLocation = locationRepository.save(emptyLocation);

        mockMvc.perform(get("/api/inventory/location/{locationCode}", emptyLocation.getLocationCode())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationCode").value(emptyLocation.getLocationCode()))
            .andExpect(jsonPath("$.warehouseName").value("Main Warehouse"))
            .andExpect(jsonPath("$.batches", hasSize(0)));
    }

    @Test
    @DisplayName("case-16")
    void testGetLocationView_MultipleProducts() throws Exception {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯у缁犳梹銇勯幋锝嗙《闁谎冭嫰閳藉骞欓崘褏鑳烘俊銈囧Т濞差厼鐣?
        Product product2 = Product.builder()
            .spu(testProduct.getSpu())
            .barcode("6901234567893")
            .name("Test Coffee")
            .skuName("闂備礁鎲＄划宥夋偋閺囥垹姹?闂佽崵鍋為懝楣冾敄閸儖?")  // V3.3 闂傚鍋勫ú銈夊箠鎼淬劍鏅查柣鎰暯閸嬫挸鈽夊▎妯荤暦濡?
            .packUnit("BOX")
            .conversionRate(10)
            .safetyStock(30)
            .minStock(20)
            .leadTime(5)
            .unitPrice(new java.math.BigDecimal("79.99"))
            .enabled(true)
            .build();
        product2 = productRepository.save(product2);

        // 闂備線娼荤拹鐔煎礉瀹€鍕畺闁规儳顕埢鏃堟煃瑜滈崜姘跺箚閺冨牆绠婚柤纰卞墰瀛濋梻浣告啞鐢绮欓幋鐘电＝闁规儳澧庣粻鏃€銇勯幋锝嗙《闁谎冭嫰閳藉骞欓崘褏鑳烘俊銈囧Т濞差厼鐣烽锝嗗闁绘垶顭囬弳鐘绘⒑闁稓绁锋い顐㈩樀椤?
        InventoryBatch batch3 = InventoryBatch.builder()
            .batchCode("BATCH003")
            .product(product2)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(20)
            .initialQuantity(20)
            .expiryDate(LocalDate.now().plusMonths(4))
            .active(true)
            .build();
        inventoryBatchRepository.save(batch3);

        mockMvc.perform(get("/api/inventory/location/{locationCode}", testLocation.getLocationCode())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batches", hasSize(3)));  // 3濠电偞鍨堕幖鈺傜濠靛洨鐝堕柟鐑樺灍閺嬪酣鏌嶉挊澶嬪櫧缂佲偓?濠电偞鍨堕幖鈺傜閿濆洣鐒婃い蹇撶墕閻?+ 1濠电偞鍨堕幖鈺傜濠婂牆绠伴柛娑欐綑閻ゎ噣鏌嶉挊澶嬪櫧缂佲偓?
    }

    // ========== 闂佸搫顦悧鍡楋耿闁秴鐤炬い鎰剁畱缁犳岸鏌涘☉鍗炲箹闁哄鏈换娑㈠级閹搭厼鍓甸梺?==========

    @Test
    @DisplayName("case-17")
    void testGetSummary_EmptyDatabase() throws Exception {
        // 婵犵數鍋為幐鎼佸箠閹版澘鐓橀柡宥庡幖缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閻庯綆鍋呴宥夋⒑?
        inventoryBatchRepository.deleteAll();
        productRepository.deleteAll();

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("case-18")
    void testGetSummary_InvalidPagination() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "-1")
                .param("size", "0")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk());  // Spring 濠电偞娼欓崥瀣┍濞差亜鍚规繝濠傜墕缁€澶愭煏婵炲灝鈧牠宕ｈ箛鏃€鍙忔繝鍨尵鐎靛ジ鎮归幇顔兼灈鐎殿喖鐏氬鍕偓锝庡亝閻濓繝姊?
    }

    @Test
    @DisplayName("case-19")
    void testGetDetails_InvalidSkuId() throws Exception {
        mockMvc.perform(get("/api/inventory/details/{skuId}", "invalid")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());  // 缂傚倷绶￠崑澶愵敋瑜旈幃妤呮煥鐎ｎ偄顫″銈嗗笂缁€浣规償婵犲洦鈷戦柟绋挎捣閹冲棙銇?
    }
}
