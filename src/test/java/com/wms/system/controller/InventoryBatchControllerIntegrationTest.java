package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.*;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InventoryBatchController 闂傚倸鍊稿Λ妤€螞濞嗘挸鍨傛慨姗嗗劒閸︻厸鍋撻敐搴″箻婵?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍仦閸ゆ垿鏌涢幇鈺佸缂佺虎鍨堕弻?
 * 1. POST /api/inventory/batches/outbound - FIFO 闂備礁鎲￠崹鐢稿箹椤愩倛濮?
 * 2. GET /api/inventory/batches/total-stock/{productId} - 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墮缁犳垿鎮归崶顏勭毢婵ǜ鍔戦幃?
 * 3. 闂傚倸鍊稿Λ婊冣枖濞戙垹桅闁哄啠鍋撻悗鐢靛帶铻ｉ柛婵嗗瀵绱撴担鍦姇闁绘绮撳鎶藉箛椤撗勵啍閻庡厜鍋撻柛鎰典簼椤?
 * 4. 闂佸湱鍘ч悺銊╁箰閹间焦鍋ら柕濠忓閳绘梻鈧箍鍎遍悧鍡涘窗閺囩姭鍋撳▓鍨灈闁稿﹥鎮傞幃銉╂嚋闂堟稓绐為梺鍛婃处閸樹粙宕?
 * 5. 闂佸搫顦弲娑樏洪敃鈧敃銏ゆ晸閻樿尙顓奸柣鐔哥懃鐎氱兘宕戣娣囧﹪顢曢悢铚傚婵?
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
class InventoryBatchControllerIntegrationTest {

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

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch looseBatch;
    private InventoryBatch fullPackBatch;

    @BeforeEach
    void setUp() {
        // 婵犵數鍋為幐鎼佸箠閹版澘绠栧┑鐘叉搐閺嬩線鏌ｅΔ鈧悧鍡欑矈?
        stockTransactionRepository.deleteAll();
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

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵¤尙顭堥湁婵犙呭Т閸燁垶骞嗛崒鐐寸叆?缂?= 12闂佽崵鍋為懝楣冾敄閹寸姵顫?
        testProduct = Product.builder()
            .spu(testSpu)  // 闂備胶顭堢换鎰版偪閸ャ劎顩?SPU
            .barcode("6901234567890")
            .name("Test Green Tea")
            .skuName("Test Green Tea SKU")  // V3.3 闂傚鍋勫ú銈夊箠鎼淪劍鏅查柣鎰暯閸嬫挸鈽夊▎妯荤暦濡?
            .packUnit("BOX")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build();
        testProduct = productRepository.save(testProduct);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙閺嬩線鏌ㄥ┑鍡欏鐟滅増鐩弻鐔告媴閸愮偓缍堝銈嗗姇閵堟悂寮?闂佽崵鍋為懝楣冾敄閹寸姵顫?
        looseBatch = InventoryBatch.builder()
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(8)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .build();
        looseBatch = inventoryBatchRepository.save(looseBatch);

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙閺嬩線鎮楅棃娑橆棌闁哥偞鎮傞弻鐔告媴閸愮偓缍堝銈嗗姇閵堟悂寮?4闂?= 2缂傚倷鑳舵慨鐢稿船閼姐倖顫?
        fullPackBatch = InventoryBatch.builder()
            .batchCode("BATCH002")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(24)
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build();
        fullPackBatch = inventoryBatchRepository.save(fullPackBatch);
    }

    // ========== FIFO 闂備礁鎲￠崹鐢稿箹椤愩倛濮抽柛妤冧紳閸︻厸鍋撻敐搴″箻婵?==========

    @Test
    @DisplayName("case-2")
    void fifoOutbound_OnlyLooseBatch() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-001")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionCount").value(1))
            .andExpect(jsonPath("$.transactionIds", hasSize(1)));

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偄鍔呴梺瑙勫劤绾绢參骞楅悩缁樼厱婵炲棙鍏庨鍡忓亾?
        InventoryBatch updatedBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        assertThat(updatedBatch.getQuantity()).isEqualTo(3);  // 8 - 5 = 3
    }

    @Test
    @DisplayName("case-3")
    void fifoOutbound_LooseInsufficientNeedUnpack() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "20")  // 闂傚倸鍊稿ú鐘诲磻閹剧粯鍋?8闂佽崵鍋為懝楣冾敄閸℃稑姹查幖杈剧稻鐎?+ 12闂佽崵鍋為懝楣冾敄閸℃稑姹查柨婵嗘川娑?
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-002")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionCount").value(2));  // 2濠电偞鍨堕幖鈺傜濠靛洨鐝堕柟鐑樺灍閺?

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偄鍔呴梺瑙勫劤绾绢參骞楅悩缁樼厱婵炲棙鍏庨鍡忓亾?
        InventoryBatch updatedLooseBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        InventoryBatch updatedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(updatedLooseBatch.getQuantity()).isEqualTo(0);  // 闂備浇妗ㄧ欢銈囩礊閳ь剟鏌熸导娆戠М闁诡喕绮欐俊鎼佸Ψ瑜庡▓?
        assertThat(updatedFullPackBatch.getQuantity()).isEqualTo(12);  // 24 - 12 = 12
    }

    @Test
    @DisplayName("case-4")
    void fifoOutbound_InsufficientStock() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "100")  // 闂備浇顕栭崜婵嬵敋瑜忛懞杈ㄦ綇閳规儳浜炬繛鎴烆仾椤忓洢浜圭憸鏂款嚕?32闂?
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-003")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偄鍔呴梺瑙勫劤绾绢參骞楅悩缁樼厸闁割偒鍋傜花鑽ゆ喐閺夋妯€鐎?
        InventoryBatch unchangedLooseBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        InventoryBatch unchangedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(unchangedLooseBatch.getQuantity()).isEqualTo(8);  // 闂備礁鎼悧婊勭濠婂棎浜瑰鑸靛姇缁€?
        assertThat(unchangedFullPackBatch.getQuantity()).isEqualTo(24);  // 闂備礁鎼悧婊勭濠婂棎浜瑰鑸靛姇缁€?
    }

    @Test
    @DisplayName("case-5")
    void fifoOutbound_ProductNotFound() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", "99999")
                .param("quantity", "10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-004")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("case-6")
    void fifoOutbound_MissingRequiredParams() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                // 缂傚倸鍊搁崐鎼佸箹椤愶附鍎?quantity 闂備礁鎲￠悷銉╁磹瑜版帒姹?
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-005")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("case-7")
    void fifoOutbound_NegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "-10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-006")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("case-8")
    void fifoOutbound_ExpiredBatchDetected() throws Exception {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯х摠娴溿倝鏌涢妷锝呭闁糕晛鍊块弻鐔告媴閸愮偓缍堝?
        InventoryBatch expiredBatch = InventoryBatch.builder()
            .batchCode("BATCH003")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(12)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().minusDays(1))  // 闁诲氦顫夐悺鏇犱焊椤忓棙宕查柛鎰靛枛鐎?
            .active(true)
            .build();
        inventoryBatchRepository.save(expiredBatch);

        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-007")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // ========== 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墮缁犳垿鎮归崶顏勭毢婵ǜ鍔戦幃妤€鈽夊▍铏灩缁﹦鈧潧鎲＄€?==========

    @Test
    @DisplayName("case-9")
    void getTotalStock_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", testProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(testProduct.getId()))
            .andExpect(jsonPath("$.totalStock").value(32));  // 8 + 24 = 32
    }

    @Test
    @DisplayName("case-10")
    void getTotalStock_NoStock() throws Exception {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘棃鏌嶈閸撴氨绮欐径鎰垫晜闁告侗鍨遍埛鏇㈡⒑閸濆嫮澧曟い锔诲灣閼鸿鲸娼忛埞鎯т壕婵炴垶鐟悞鑺ョ箾閸喎鐏︾紒鍌氱Х椤︽煡鏌?
        Product emptyProduct = Product.builder()
            .spu(testProduct.getSpu())
            .barcode("6901234567891")
            .name("Empty Stock Product")
            .skuName("缂傚倷绀侀惌浣割浖閵婏富娈介柛銉墮娴?SKU")  // V3.3 闂傚鍋勫ú銈夊箠鎼淬劍鏅查柣鎰暯閸嬫挸鈽夊▎妯荤暦濡?
            .packUnit("BOX")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build();
        emptyProduct = productRepository.save(emptyProduct);

        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", emptyProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(emptyProduct.getId()))
            .andExpect(jsonPath("$.totalStock").value(0));
    }

    @Test
    @DisplayName("case-11")
    void getTotalStock_ProductNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 闂傚倸鍊稿Λ婊冣枖濞戙垹桅闁哄啠鍋撻悗鐢靛帶铻ｉ柛婵嗗瀵绱撴担鍦姇闁绘绮撳鎶藉箛椤撗勵啍閻庡厜鍋撻柛鎰典簼椤?==========

    @Test
    @DisplayName("case-12")
    void looseItemFirst_PreferLooseBatch() throws Exception {
        // 闂佽崵濮村ú顓㈠绩闁秵鍎?5闂佽崵鍋為懝楣冾敄閹寸姵顫曟繝闈涙处閸庣喖鏌￠崘銊︾ォ闁搞倕顦甸弻娑樷枎濡湱鑳烘繝鐢靛仜濞差參骞冩禒瀣╅柨鏃囧Г閻ゅ洭鏌ｉ悩鍐插闁告洦鍋掑Λ妤€鈹?
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-008")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionCount").value(1));  // 闂備礁鎲￠悷顖涚閻愬搫鏋侀柕鍫濇椤?濠电偞鍨堕幖鈺傜濠靛洨鐝堕柟鐑樺灍閺?

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒佺€柣搴ㄦ涧婢瑰﹪宕幖浣圭厵濞达絽鍠氬鎰亜閹邦兙鍋㈢€殿喚鏁婚、鏃堝礋椤?
        InventoryBatch unchangedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(unchangedFullPackBatch.getQuantity()).isEqualTo(24);  // 闂備礁鎼悧婊勭濠婂棎浜瑰鑸靛姇缁€?
    }

    @Test
    @DisplayName("case-13")
    void looseItemFirst_UnpackOnlyWhenNecessary() throws Exception {
        // 闂佽崵濮村ú顓㈠绩闁秵鍎?10闂佽崵鍋為懝楣冾敄閹寸姵顫曟繝闈涱儏閺嬩線鏌ㄥ┑鍡欏鐟?8闂佽崵鍋為懝楣冾敄閸曨厾绠斿璺烘湰閹儳绱掑☉妯昏础缂佲偓婢舵劖鈷掗柛銉到娴滈箖鏌ｉ悢鍛婄；闁绘帪闄勯崚?2闂?
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-009")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionCount").value(2));  // 闂備焦妞垮鍧楀礉鐎ｎ剝濮?濠电偞鍨堕幖鈺傜濠靛洨鐝堕柟鐑樺灍閺?

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偄鍔呴梺瑙勫劤绾绢參骞楅悩缁樼厱婵炲棙鍏庨鍡忓亾?
        InventoryBatch updatedLooseBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        InventoryBatch updatedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(updatedLooseBatch.getQuantity()).isEqualTo(0);  // 闂備浇妗ㄧ欢銈囩礊閳ь剟鏌熸导娆戠М闁诡喕绮欐俊鎼佸Ψ瑜庡▓?
        assertThat(updatedFullPackBatch.getQuantity()).isEqualTo(22);  // 24 - 2 = 22
    }

    // ========== 濠电偞鍨堕幐濠氭嚌閻愵剚鍙忛柣鏃囶問鐟欏嫭濯撮悶娑掑墲閻撶姴鈹戦悙瀛樼稇妞ゆ垵鐗撻幆?==========

    @Test
    @DisplayName("case-14")
    void completeWorkflow_MultipleOutboundsUntilEmpty() throws Exception {
        // 缂傚倷鐒﹂〃蹇涘礂濞戞氨鍗氶柡澶嬪灍閺嬪酣鏌嶉妷銉ユ毐婵絽顦甸獮鏍偓娑櫳戦幆鍫㈢磼?闂佽崵鍋為懝楣冾敄閹寸姵顫曟繛鍡樺姈婵挳鎮归幁鎺戝闁哄棗绻橀弻鈩冪瑹婵犲嫮袣闂侀€涚串缂嶄線寮?
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-010")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator"))
            .andExpect(status().isOk());

        // 缂傚倷鐒﹂〃蹇涘礂濞戞俺濮抽柍杞拌閺嬪酣鏌嶉妷銉ユ毐婵絽顦甸獮鏍偓娑櫳戦幆鍫㈢磼?0闂佽崵鍋為懝楣冾敄閹寸姵顫曟繛鍡樺姈婵挳鎮归幁鎺戝闁哄棗绻橀弻娑㈠箻椤栨稒鐝掔紓鍌氱У閸ㄥ灝顕ｉ锔芥櫜闁割偆鍠庣紞?+ 闂傚倷绶￠崰鎾诲礉瀹€鍕瀭閹兼番鍔岄弸渚€鎮楅棃娑橆棌闁哥偞鎮傞弻?
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-011")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator"))
            .andExpect(status().isOk());

        // 缂傚倷鐒﹂〃蹇涘礂濞戞氨绠斿ù锝囩《閺嬪酣鏌嶉妷銉ユ毐婵絽顦甸獮鏍偓娑櫳戦幆鍫㈢磼?7闂佽崵鍋為懝楣冾敄閹寸姵顫曟繛鍡樺姈婵挳鎮归幁鎺戝闁哄棗绻橀弻娑㈠箻椤栨稒鐝掔紓鍌氱У閸ㄥ灝顕ｉ銈傚亾闂堟稑顥忛柛鐐存倐閺?
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "17")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-012")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator"))
            .andExpect(status().isOk());

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傜暠闁荤姴娲╃亸娆忣潩閵娾晜鍊垫繛鎴烆仾閼测晜瀚?0
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", testProduct.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalStock").value(0));

        // 缂傚倷鐒﹂〃蹇涘礈濞嗗緷娲箻閺傘儲鐎婚梺鐐藉劚閸熷灝袙婢舵劕绠归悗娑櫳戦幆鍫㈢磼鏉堚晛顣奸柟顖涙瀵噣宕剁捄鐑樼暯濠电姰鍨洪崕鑲╁垝閸撗勫枂闁挎洖鍊归弲顒勬煕椤愶絿绠樻慨妯稿姂閹鈽夊▍顓″亹缁厽寰勭仦鐐劚缂備焦绋戝﹢杈╃矆?
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "1")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-013")
                .param("operatorId", "1")
                .param("operatorName", "Test Operator"))
            .andExpect(status().isBadRequest());
    }
}
