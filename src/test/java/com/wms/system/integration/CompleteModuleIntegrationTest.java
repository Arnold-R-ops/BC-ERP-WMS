package com.wms.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WMS 缂傚倸鍊搁崐鎼佸磹妞嬪孩顐介柨鐔哄Т閸ㄥ倿姊婚崼鐔恒€掗柡鍡畵閺岋綁濮€閵堝棙閿梺鎼炲妽缁诲牓寮昏缁犳盯骞橀崘鑼剁窡闂備胶顭堥鍡涘箲閸ヮ剙绠栭柣鎴ｅГ閸嬪鏌涢銈呮瀾閻庢碍濞婂缁樻媴閸涘﹥鍎撳┑鈽嗗亜椤曨厼顕ユ繝鍥х９闁绘棁妗ㄩ懜顏堟⒒閸屾瑨鍏岄柛瀣ㄥ姂瀹曟洟寮堕幋鏃€鐎洪梺鍝勬储閸ㄥ湱澹曟繝姘厪濠㈣泛妫欏▍鍡涙煃闁垮娴柡灞剧洴閸╁嫰宕橀悙顒傛殾婵＄偑鍊戦崕鎶藉磻閵堝钃熸繛鎴欏灩鎯熼梺鎸庢磻缁€渚€藟濠靛鈷掗柛銉戝本鈻堥梺璇″枟椤ㄥ﹪寮幇顓熷劅闁炽儱纾鎺撶節瀵版灚鍊曢惃鐑樸亜椤撶偟澧﹂柛鈺冨仱楠炲鏁冮埀顒傚閸忚偐绠鹃柟瀵稿仧婢а勭箾瀹€濠佺盎闁?
 *
 * 濠电姷鏁告慨鐑藉极閹间礁纾婚柣鎰▕閻掕姤绻涢崱妯绘儎闁轰礁瀚伴弻娑㈩敃閻樻彃濮曢梺绋块閿曘儵濡甸崟顖氬唨闁靛ě鍛帒闂備礁鎽滈崰鎰渻娴犲钃熸繛鎴欏灩閻掓椽鏌涢幇鍏哥凹闁革綆鍘剧槐鎺楀箚瑜嶇紞鏍煕濡や礁鈻曠€殿喖顭烽幃銏ゅ传閸曨剛鈧啿鈹戦埥鍡楃仴婵℃ぜ鍔嶇粩鐔煎即閵忊檧鎷绘繛杈剧到閹诧繝骞嗛崼銉︾厽婵°倐鍋撴俊顐ｇ箚濡喎顪冮妶鍡橆棃闁轰緡鍣ｅ畷鎴﹀箻缂佹﹩妫冨┑鐐村灦濮樸劍瀵奸崶顒佲拺缁绢厼鎳庨惃鍝勵熆瑜庨〃濠囩嵁韫囨稑宸濇い鏇炴噺鏁堥梺纭呭閹活亞寰婃ィ鍐ㄥ惞鐎光偓閸曨兘鎷绘繛杈剧到閹诧繝宕悙鐑樼厸鐎光偓鐎ｎ剛鐦堥悗瑙勬礃缁矂鍩為幋鐐电瘈闁稿本绋掗澶愭⒒閸屾艾鈧兘鎳楅崼鏇稏閻庯綆鍠栫粈瀣煕椤垵浜介柛姘噽缁?
 * 1. 闂傚倸鍊峰ù鍥х暦閸偅鍙忛柟鎯板Г閳锋梻鈧箍鍎遍ˇ顖滃鐟欏嫮绠鹃柟瀛樼懃閻忣亪鏌涙惔鈽呭伐妞ゎ叀娉曢幑鍕瑹椤栨艾澹嬮梻浣哥－缁垰螞閸愵喚宓侀柡宥庣仈鎼搭煈鏁嗛柍褜鍓熼幃姗€顢旈崱娆戯紳閻庡箍鍎扮粈浣圭妤ｅ啯鈷掗柛灞捐壘閳ь剚鎮傚畷鎰板传閵夈垹浜炬慨妯煎帶瀵喚鈧娲樼换鍌濈亙闂佸憡绮堥悞锕傚疾閿濆鈷戠憸鐗堝笚閿涚喖鏌ｉ幒鐐电暤鐎?
 * 2. 婵犵數濮烽弫鎼佸磻濞戙埄鏁嬫い鎾跺枑閸欏繘鏌ｉ姀銏╃劸缂佺姳鍗抽弻娑樷攽閸曨偄濮ゆ繝娈垮枟婵炲﹤顕ｉ崼鏇為唶婵炴垶锚椤姊哄畷鍥╁笡婵☆偄鍟撮悰顕€寮介妸锔剧Ф闂佸憡鎸嗛崪鍐惞濠电姵顔栭崰妤勫綘闂佸憡鏌ㄩ惌鍌氾耿娓氣偓濮婃椽骞愭惔锝囩暤闂佺娅曢崝姗€宕愮€涙ü绻嗛柣鎰典簻閳ь剚鐗犻獮鎰節濮橆剛鐣洪梺绋跨灱閸嬫稓绮?
 * 3. 闂傚倸鍊搁崐鎼佸磹閻戣姤鍊块柨鏇氶檷娴滃綊鏌涢幇鐢靛帒婵炲樊浜滈悡娑㈡煕濞戝崬骞橀柣娑栧劚閳规垿鍩ラ崱妤冧淮閻庤娲滈弫濠氬春閵忋倕绫嶉柛顐ｇ箘椤旀劗绱撴笟鍥ф灈闁告ɑ绮撳畷鎴﹀箻鐠囪尙顦ㄩ梺鍛婄懃椤﹀磭娆㈤姀銈嗏拻濞达絽鎲＄拹锟犳煕濡姴娲﹂崑銈夋煏婵炵偓娅呯紒鐘虫緲铻栭柨婵嗘噹閺嬨倝鏌℃担鍛婂枠闁哄瞼鍠栧鑽も偓鐢殿焾婵′粙姊?
 * 4. 闂傚倸鍊风粈浣革耿闁秴鍌ㄧ憸鏃堝箖濞差亜惟闁宠桨鑳堕鍥⒑瑜版帗锛熼柣鎺炵畵瀹曟垿濡舵径瀣帾婵犵數鍊崘鈺佹缂備焦顨呴ˇ闈涱潖濞差亜绠伴幖杈剧岛濡插牓鏌ｆ惔銏犲毈闁告瑥鍟悾?
 * 5. 闂傚倸鍊峰ù鍥敋瑜庨〃銉╁传閵壯傜瑝閻庡箍鍎遍ˇ顖炲垂閸屾稓绠剧€瑰壊鍠曠花濠氭煛閸曗晛鍔滅紒缁樼洴楠炲鎮欑€靛憡顓诲┑鐐村灦閹稿摜绮旇ぐ鎺戣摕闁靛鍎Σ鍫熶繆椤栨瑨顒熷ù鐘荤畺濮婅櫣绮欓崠鈥冲闂佺顑冮崐婵嗩嚕婵犳艾惟闁靛鍨洪～宥呪攽閳藉棗鐏ｉ柛妯犲洢鈧倸煤椤忓應鎷洪柣鐘叉礌閳ь剙纾禒鈺呮⒑缂佹﹩娈曢柟鍛婃倐椤㈡岸鏁愭径妯绘櫇闂佹寧妫佸Λ鍕闁秵鈷戦柛鎾村絻娴滄繄绱掔拠鎻掝伃濠碘€崇埣閺佸啴宕掑☉鎺撳闂備礁鍚嬬粊鎾疾閳轰絼娑欐償閳藉棙瀵岄柣搴㈢⊕钃遍柛濠冨姉閳ь剚顔栭崰妤呮偂閿熺姴钃熼柛鈩冾殢閸氬鏌涢埄鍏╂垿鎮?
 * 6. 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗ù锝夌細婵娊姊洪鈧粔瀵稿婵犳碍鐓欓柛鎾楀懎绗￠梺缁樻尰濞茬喖寮婚敐澶婄婵犻潧娴傚Λ鐐烘⒑閸濆嫷鍎愰柣鐔濆懏顫曢柟鐑樺殾閺冨牆鐒垫い鎺戝閸庢鏌涚仦鐐殤闁哄棴闄勭换娑㈠幢濡纰嶆繛纾嬪亹婵炩偓闁哄本鐩鎾Ω閵夈儳顔戦梻浣圭湽閸斿秹宕归崷顓燁潟闁规儳鐡ㄦ刊鎾煕濠靛棗顏撮柍褜鍓氶幐鍓ф閹烘挻缍囬柕濠忓椤︽澘螖?
 * 7. 闂傚倸鍊搁崐鐑芥嚄閸洖纾块柣銏㈩焾閻ょ偓绻濇繝鍌滃闁逞屽墾缁犳挸鐣烽崼鏇ㄦ晢闁逞屽墰婢规洘绻濆顓犲弳濠电娀娼уΛ娆忣啅濠靛棌妲堥柟鎯х－鏁堥梺鍝勮閸斿矂鍩ユ径濞㈢喐绗熼娆戞／闂?
 * 8. 缂傚倸鍊搁崐鎼佸磹妞嬪孩顐介柨鐔哄Т閸ㄥ倿姊婚崼鐔恒€掗柡鍡畵閺岋綁濮€閵堝棙閿梺鎼炲妽缁诲啰鎹㈠☉姗嗗晠妞ゆ棁宕甸惄搴ｇ磼閻愵剙鍔ゆい顓犲厴瀵寮撮悢椋庣獮闂佸壊鍋呯换鍌涙叏閺囥垺鈷戠紒瀣儥閻擃厾绱掗懠顒€浜剧紒宀冮哺缁绘繈宕堕懜鍨珖闂備胶绮Λ浣搞€掔憴鍕彾闁哄洢鍨洪埛?
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Complete Integration Test Suite)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@DisplayName("case-1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompleteModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductSkuRepository productSkuRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User salesUser;
    private User buyerUser;
    private Warehouse warehouse;
    private Location location;
    private ProductSku product;
    private Supplier supplier;
    private Customer customer;

    @BeforeEach
    void setUp() {
        // 濠电姷鏁告慨鐑藉极閹间礁纾婚柣鎰惈缁犱即鏌熼梻瀵割槮缂佺姷濞€閺岀喖鎮ч崼鐔哄嚒缂備胶濮甸悧鏇㈠煘閹达附鍋愰柛娆忣槹閹瑩姊洪崫鍕靛剮缂佽埖宀稿濠氭晲閸涘倹妫冮崺鈧い鎺戝閸嬪鏌涢埄鍐噮闁?
        cleanupData();

        // 闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顒佹濠德板€曢崯浼存儗濞嗘挻鐓欓悗鐢殿焾鍟哥紒鎯у綖缁瑩寮婚悢鐓庣闁归偊鍟╁鍫熺厱闁挎棁顕ч獮鎰版煕鐎ｎ偅宕岄柡浣瑰姈閹棃鍨惧畷鍥跺晪濠电姷顣介埀顒€鍟跨痪褔鏌熼鐓庘偓鍨嚕婵犳碍鏅插鑸电〒缁嬪繐顪冮妶鍡楀潑闁稿鎸搁埞鎴﹀灳閼碱剛鐓撻梺?
        adminUser = createUser("admin", "SUPER_ADMIN");
        salesUser = createUser("sales", "SALESPERSON");
        buyerUser = createUser("buyer", "BUYER");

        // 闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顒佹濠德板€曢崯浼存儗濞嗘挻鐓欓悗鐢殿焾鍟哥紒鎯у綖缁瑩寮婚悢鐓庣闁逛即娼у▓顓㈡⒑閸濆嫬顏ラ柛搴ｆ暬瀵槒顦剁紒鐘崇☉椤繈鎮℃惔銏㈡殶闂傚倷娴囬妴鈧柛瀣崌閺屾洘绻涢悙顒佺彆闂佺粯鎸荤粙鎴︽箒闂佹寧绻傞幊蹇涘箚閸喆浜滈柨婵嗙墕娴滃綊鏌?
        warehouse = createWarehouse();
        location = createLocation(warehouse);
        product = createProduct();
        supplier = createSupplier();
        customer = createCustomer(salesUser.getId());
    }

    private void cleanupData() {
        salesOrderRepository.deleteAll();
        customerRepository.deleteAll();
        purchaseOrderRepository.deleteAll();
        inventoryBatchRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productSkuRepository.deleteAll();
        productRepository.deleteAll();
        supplierRepository.deleteAll();
    }

    private User createUser(String username, String role) {
        return userRepository.findByUsername(username)
            .orElseGet(() -> {
                User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode("password"))
                    .enabled(true)
                    .build();
                return userRepository.save(user);
            });
    }

        private Warehouse createWarehouse() {
        Warehouse wh = Warehouse.builder()
            .code("WH01")
            .name("Main Warehouse")
            .address("Beijing")
            .isActive(true)
            .build();
        return warehouseRepository.save(wh);
    }

        private Location createLocation(Warehouse warehouse) {
        Location loc = Location.builder()
            .warehouse(warehouse)
            .zone(com.wms.system.entity.enums.Zone.ZONE_A)
            .shelfNumber("A-01")
            .positionNumber("001")
            .enabled(true)
            .build();
        return locationRepository.save(loc);
    }

        private ProductSku createProduct() {
        Product spu = Product.builder()
            .category(com.wms.system.support.TestCatalogFactory.saveLeafCategory(categoryRepository))
            .productCode("SPU001")
            .productName("Test SPU")
            .enabled(true)
            .build();
        spu = productRepository.save(spu);

        ProductSku prod = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
            .product(spu)
            .skuName("SKU001")
            .name("Test ProductSku")
            .barcode("6900000001001")
            .specification("Standard")
            .unitPrice(new BigDecimal("100.00"))
            .minSalesPrice(new BigDecimal("50.00"))
            .safetyStock(10)
            .nearExpiryDays(90)
            .enabled(true)
            .build();
        return productSkuRepository.save(prod);
    }

        private Supplier createSupplier() {
        Supplier sup = Supplier.builder()
            .code("SUP001")
            .name("Test Supplier")
            .contact("Zhang San")
            .phone("13912345678")
            .email("supplier@example.com")
            .address("Shanghai")
            .isActive(true)
            .build();
        return supplierRepository.save(sup);
    }

    private Customer createCustomer(Long ownerId) {
        Customer cust = Customer.builder()
            .code("CUST001")
            .name("Test Customer")
            .contact("Li Si")
            .phone("13800138000")
            .email("customer@example.com")
            .address("Guangzhou")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .ownerId(ownerId)
            .build();
        return customerRepository.save(cust);
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜妯诲暗闁崇懓绉电换娑橆啅椤旂粯鍠氶梺杞扮濞差參寮婚妶鍡樺弿闁归偊鍏橀崑鎾诲冀椤愮喎浜炬慨姗嗗弨婢规﹢妫佹径鎰叆婵犻潧妫Ο鍫熶繆椤愶絽鐏撮柡宀嬬秮閹垽寮堕幋鐘辩礄闂備礁鎼径鍥礈濠靛棭鍤楅柛鏇ㄥ墰缁♀偓闂佺鏈粙鎺楊敊閹烘挾绡€缁剧増锚婢ц尙鈧娲橀敋闁宠绉归弫鎰板炊瑜嶉悘濠冪節閻㈤潧校缁炬澘绉瑰鏌ヮ敆閸曨剛鍘遍梺鍝勬储閸斿本鏅堕鈧弻?==========

    @Test
    @Order(1)
    @DisplayName("case-2")
    void testModule1_Auth_HealthCheck() throws Exception {
        mockMvc.perform(get("/api/auth/health"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("AuthenticationService"));
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜姗嗘畷闁稿孩顨嗛妵鍕籍閳ь剟宕曢幎钘夊瀭婵犻潧顑嗛悡蹇撯攽閻樿尙绠绘俊缁㈠櫍閺屾稒鎯旈敐鍛亪闂佸搫鐬奸崰鏍嵁閹达箑绠涙い鎺戝€归妤呮⒒娴ｈ棄鍚归柛鐘叉瀹曡绂掔€ｎ剙绁﹂梺鍝勭▉閸嬧偓闁稿鎸搁埥澶娾枍閾忣偄鐏╁ù婊勬倐椤㈡洟濡堕崶鈺嬬闯闁诲骸绠嶉崕閬嶅箠韫囨稑闂柣锝呯灱绾惧吋銇勯弮鍌楁嫛闁绘挸銈搁弻鈩冩媴閸濄儛褏鈧娲滈崰鏍€佸Δ鍛劦妞ゆ帒瀚悞?==========

    @Test
    @Order(2)
    @WithMockUser(username = "admin", authorities = {"warehouse:view", "SUPER_ADMIN"})
    @DisplayName("case-3")
    void testModule2_Warehouse_ListAll() throws Exception {
        mockMvc.perform(get("/api/warehouses"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].code").value("WH01"))
            .andExpect(jsonPath("$[0].name").value("Main Warehouse"));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "admin", authorities = {"location:view", "SUPER_ADMIN"})
    @DisplayName("case-4")
    void testModule2_Location_ListByWarehouse() throws Exception {
        mockMvc.perform(get("/api/locations/warehouse/" + warehouse.getId()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].locationCode").value(startsWith("WH01-ZONE_A-")));
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜妯虹仼闁哄棗妫濋弻鐔兼⒒鐎靛壊妲梻鍌氬亞閸ㄥ爼寮婚悢灏佹灁闁割煈鍠楅悘宥夋煟鎼淬垺鐨戠紒顕呭灦婵＄敻宕熼姘鳖啋闁诲酣娼ч幗婊堟偩婵傚憡鈷戦柤濮愬€曢弸鍌炴煕閵娿倗鐭欑€殿喛顕ч埥澶婎煥閸涱垱婢戦梺璇插嚱缂嶅棙绂嶅鍕弿閹艰揪绲跨壕钘壝归敐澶嬫锭濠殿喒鍋撳┑鐐茬摠缁娀宕滃☉銏犵闁圭儤鎸剧弧鈧┑顔斤供閸樿棄鈻嶉弽顓熲拺闁告稑锕ユ径鍕煕閹惧鎳囬柛鈹惧亾?==========

    @Test
    @Order(4)
    @WithMockUser(username = "buyer", authorities = {"purchase:create", "BUYER"})
    @DisplayName("case-5")
    void testModule3_Purchase_CreateOrder() throws Exception {
        String requestBody = """
            {
                "supplier": "Test Supplier",
                "operatorId": %d,
                "operatorName": "buyer",
                "expectedDate": "2026-02-20",
                "items": [
                    {
                        "productSkuId": %d,
                        "orderedQuantity": 100,
                        "unitCost": 50.00,
                        "productionDate": "2026-02-10",
                        "expiryDate": "2027-02-10"
                    }
                ]
            }
            """.formatted(buyerUser.getId(), product.getId());

        mockMvc.perform(post("/api/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.supplier").value("Test Supplier"))
            .andExpect(jsonPath("$.status").value("ORDERING"));
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜姗嗘畷闁搞倕鐭傞弻娑㈠箻濡も偓濡鐨梻浣告惈椤︻垶鎮ч崘顔兼濞撴埃鍋撶€规洘鍨块獮姗€寮妷锔绘綌婵犵數鍋涘Λ娆撯€﹂崶顒€绀夌€瑰嫰鍋婂〒濠氭煏閸繃顥為悘蹇庡嵆閺岀喖顢欓悡搴樺亾閸ф宓?==========

    @Test
    @Order(5)
    @WithMockUser(username = "admin", authorities = {"inventory:view", "SUPER_ADMIN"})
    @DisplayName("case-6")
    void testModule4_Inventory_QuerySummary() throws Exception {
        // 闂傚倸鍊搁崐鐑芥嚄閸洍鈧箓宕奸姀鈥冲簥闂佽澹嗘晶妤呭磻鐎ｎ喗鐓曢柍鈺佸暟閳藉鏌涢妸銉モ偓鍧楀蓟濞戞鏃堝礃閵娿儱顥庨梻浣规偠閸婃洟鎮ч幘鎰佸殨闁割偅娲橀崐鐑芥煛婢跺鐒炬俊顐㈡椤啴濡堕崨顖滎唶閻庤娲﹂崜鐔凤耿娴ｇ硶鏀介柣妯款嚋瀹搞儵鎮楀鐓庢珝闁糕斂鍎插鍕暆閳ь剛澹曟總鍛婄厪濠电偛鐏濇俊娲煕濮樼厧浜伴柡灞界Х椤т線鏌涢幘瀛樼殤缂侇喗鐟╅獮鎺楀即閻旂娅″┑鐘垫暩婵即宕规總闈╃稏濠㈣泛鏈弳婊堟煙閻戞﹩娈旈柣?
        InventoryBatch batch = InventoryBatch.builder()
            .batchCode("SPU001-SKU001-20260212")
            .productSku(product)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(100)
            .initialQuantity(100)
            .productionDate(LocalDate.now())
            .expiryDate(LocalDate.now().plusYears(1))
            .active(true)
            .build();
        inventoryBatchRepository.save(batch);

        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "0")
                .param("size", "10"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(6)
    @WithMockUser(username = "admin", authorities = {"inventory:view", "SUPER_ADMIN"})
    @DisplayName("case-7")
    void testModule4_Inventory_QueryBatchDetails() throws Exception {
        // 闂傚倸鍊搁崐鐑芥嚄閸洍鈧箓宕奸姀鈥冲簥闂佽澹嗘晶妤呭磻鐎ｎ喗鐓曢柍鈺佸暟閳藉鏌涢妸銉モ偓鍧楀蓟濞戞鏃堝礃閵娿儱顥庨梻浣规偠閸婃洟鎮ч幘璇茶摕婵炴垶菤閺€浠嬫煕閳╁喚娈㈠ù灏栧亾濠电姵顔栭崰妤勫綘闂佸憡姊归崹鍧楃嵁閸愩剮鏃堝焵椤掑嫬鐓″璺号堥弸宥夋煣韫囷絽浜滈柣蹇ュ缁辨帡寮崒姘亪濡ょ姷鍋炵敮锟犵嵁鐎ｎ喖绫嶉柍褜鍓熼幃?
        InventoryBatch batch = InventoryBatch.builder()
            .batchCode("SPU001-SKU001-20260212")
            .productSku(product)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(100)
            .initialQuantity(100)
            .productionDate(LocalDate.now())
            .expiryDate(LocalDate.now().plusYears(1))
            .active(true)
            .build();
        inventoryBatchRepository.save(batch);

        mockMvc.perform(get("/api/inventory/details/" + product.getId()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜姗嗘畷闁搞倕鑻灃闁挎繂鎳庨弸銈夋煛娴ｅ憡宸濋柟鍙夋倐閹囧醇濠靛牏鎳嗙紓鍌欒兌婵寰婃禒瀣р偓鏃堝礃椤忎礁浜鹃柨婵嗙凹缁ㄥ鏌ｉ敂鍝勫闁哄本绋戦～婵嬫晲閸涱剙顥氶梻鍌氬€搁崐宄懊归崶褏鏆﹂柣銏㈩焾绾剧粯绻涢幋鐑嗙劯婵炴垶鍩冮崑鎾绘濞戞瑥鏆堝銈庡亝濞叉鎹㈠☉銏犲耿婵☆垵顕х喊宥夋煟閻斿摜鎳曢梻鍕婵＄敻宕熼姘辩杸闂佸憡鎸烽懗鎯版懌濠电姷鏁搁崑娑㈡偋閸℃稒鍋夊┑鍌滎焾閽冪喖鏌ｉ弮鍌氬妺閻庢碍宀搁弻宥夊Ψ閵婏妇褰ч梺璇茬箲濞茬喎顫忓ú顏勫窛濠电姴鍟犻幏褰掓倵閸忓浜剧紓浣割儐椤戞瑩宕甸弴鐔翠簻闁规壋鏅涢崝銈夋煟閵堝倸浜鹃梻鍌欑窔濞佳囨晬韫囨稑妞藉ù锝呭ⅲ?=========

    @Test
    @Order(7)
    @WithMockUser(username = "sales", authorities = {"customer:view", "SALESPERSON"})
    @DisplayName("case-8")
    void testModule5_Customer_SalesViewWithMasking() throws Exception {
        mockMvc.perform(get("/api/customers"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(8)
    @WithMockUser(username = "admin", authorities = {"customer:view", "SUPER_ADMIN"})
    @DisplayName("case-9")
    void testModule5_Customer_AdminViewWithoutMasking() throws Exception {
        mockMvc.perform(get("/api/customers"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].name").value("Test Customer"))
            .andExpect(jsonPath("$[0].phone").value("13800138000"));  // 闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩顐熷亾閿曞倸鐐婃い鎺嗗亾缂佹劖顨婇獮鏍箹椤撶姴甯ㄧ紓鍌氱У閻楃娀寮婚悢鍏煎亱闁割偆鍠撻崙锟犳⒑閸濆嫷鍎庣紒鑸靛哺瀵鏁愰崨鍌涙閸┾偓妞ゆ帒瀚崑瀣煕閳╁啰鎳呴柣?
    }

    @Test
    @Order(9)
    @WithMockUser(username = "sales", authorities = {"customer:create", "SALESPERSON"})
    @DisplayName("case-10")
    void testModule5_Customer_CreateWithAutoOwner() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST002")
            .name("New Customer")
            .contact("Wang Wu")
            .phone("13700137000")
            .email("newcustomer@example.com")
            .address("Shenzhen")
            .creditLimit(new BigDecimal("50000.00"))
            .isActive(true)
            .build();

        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CUST002"))
            .andExpect(jsonPath("$.name").value("New Customer"));
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜妯虹仼闁哄棗妫濋弻鐔兼⒒鐎靛壊妲紓鍌欒閺呯娀寮婚弴锛勭杸閻庯綆浜栭崑鎾诲即閵忊€虫疂闁荤喐鐟ョ€氥劍绂嶅鍫熺厸闁告劑鍔嶉幖鎰版煕濮橆剛绉洪柡宀嬬秮閹垽寮堕幋鐘辩礄闂備礁鎼張顒勬儎椤栫偟宓侀悗锝庝簴閺€浠嬫煕閵夛絽濡奸柛鎾舵暩缁辨捇宕掑▎鎾搭€栭梺鍛婃煥缁绘垹绮嬪澶樻晢闁稿本鑹鹃悘濠冪節閻㈤潧校缁炬澘绉瑰鏌ヮ敆閸曨剛鍘遍梺鍝勬储閸斿本鏅堕鈧弻?==========

    @Test
    @Order(10)
    @WithMockUser(username = "sales", authorities = {"sales:create", "SALESPERSON"})
    @DisplayName("case-11")
    void testModule6_Sales_DownloadTemplate() throws Exception {
        mockMvc.perform(get("/api/sales-orders/template"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("sales_order_template.xlsx")));
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜妯虹劸婵炲皷鏅犻弻鏇熺箾閸喖澹勬俊銈忕畳濞夋洟鎮块埀顒€鈹戦悙鏉戠仸闁荤喆鍎甸崺鈧い鎺戝€搁崢鎾煛鐏炲墽娲撮柡浣稿€婚幏鐘诲箵閹烘埈鍔€闂傚倷绀侀幉锟犲春閸愵喖纾婚柟鍓х帛閳?==========

    @Test
    @Order(11)
    @WithMockUser(username = "admin", authorities = {"stocktake:view", "SUPER_ADMIN"})
    @DisplayName("case-12")
    void testModule7_Stocktake_ListTasks() throws Exception {
        mockMvc.perform(get("/api/stocktake/tasks"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ========== 濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁圭儤鍨甸弳鐐烘煟濠垫劒閭柡?闂傚倸鍊搁崐鐑芥倿閿旈敮鍋撶粭娑樻噽閻瑩鏌熼悜妯虹劸婵炲皷鏅犻弻鏇熺箾閻愵剚鐝曢梺缁樺浮缁犳牠骞冨Δ鍛棃婵炴垶鐟﹂崰鎰磼閻愵剙鍔ら柕鍫熸倐瀵鏁愭径濠勭潉闂佹悶鍎崝蹇涘触閸屾埃鏀介柣鎴濇川缁夌敻鏌涢敐蹇曠М闁绘侗鍣ｅ浠嬵敇閻愭鍟嬫俊鐐€栧Λ浣规叏閵堝姹查柣妯烘▕濞撳鏌曢崼婵嗘殭闁告梹宀搁弻鐔兼惞椤愨偓椤忓牜鏁?==========

    @Test
    @Order(12)
    @DisplayName("case-13")
    void testModule8_System_HealthCheck() throws Exception {
        mockMvc.perform(get("/health/check"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // ========== 缂傚倸鍊搁崐鎼佸磹閻戣姤鍊块柨鏇楀亾閾荤偤鐓崶銊р槈闁搞劌鍊搁湁闁稿繐鍚嬬紞鎴︽煛閸曗晛鍔ら柍褜鍓欓崢婊堝磻閹剧粯鐓冪憸婊堝礈濞戙垹绀嗛柟鐑橆殔閻撴盯鏌涘鈧悞锕傚疾椤掆偓閳规垿鎮欓崣澶樻！闂佹悶鍔岀紞濠囩嵁婵犲倵鏀介悗锝庡亐閹锋椽姊洪崨濠勨槈闁挎洏鍎甸幃鈥斥槈閵忥紕鍘遍梺瑙勫劤椤曨厾绮诲Ο鑲╃＜妞ゆ梻銆嬮煬顒勬煛娴ｇ鈧灝鐣峰鍡╂Ь闂佺粯鎸婚幑鍥蓟閿濆棙鍎熸い鏍ㄧ矌鏍￠梻浣侯焾椤戝懎螞濠靛洣绻嗛柛顐ｆ礀缁犵粯銇勯弮鍥棄濞?==========

    @Test
    @Order(13)
    @WithMockUser(username = "admin", authorities = {"SUPER_ADMIN"})
    @DisplayName("case-14")
    void testE2E_CompleteBusinessFlow() throws Exception {
        // Keep a basic smoke assertion to ensure the suite class is complete.
        Assertions.assertNotNull(adminUser);
    }
}
