package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.Warehouse;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WarehouseController 闂傚倷娴囬褍霉閻戣棄绠犻柟鎹愵嚙鐎氬銇勯幒鎴濐仼婵鐓￠弻銊モ攽閸♀晜效闂佹娊鏀遍悧鐘诲蓟濞戙垹鐒洪柛蹇曞娴滎亪骞冨鈧畷鍫曞Ω瑜忛鏇㈡⒑閸濆嫷妲归柛鈺佺墦瀹曟洟骞樼紒妯煎幗?
 *
 * 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁稿骸锕ラ妵鍕冀椤愵澀娌梺鎼炲€栭崹鍧楀蓟濞戙垹绠涢柍杞扮椤绱撴担楦挎闁搞劌鐖煎?
 * 1. GET /api/warehouses - 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫╃紓浣哄О閸庣敻寮诲鍫闂佸憡鎸鹃崰搴敋閿濆鍨傛い鎰╁灮缁愮偞绻濋姀锝嗙【闁哄牜鍓涙禍绋库攽鐎ｎ偆鍘?
 * 2. GET /api/warehouses/active - 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫╃紓浣哄О閸庣敻寮诲鍫闂佸憡鎸鹃崰搴敋閿濆鍨傛い鎰╁灮缁愮偤鏌ｆ惔顖滅У闁告挻绻勭划鍫⑩偓锝庝簴閺€浠嬫煟閹邦垱纭鹃柦鍕亹閳ь剝顫夊ú鐔奉焽瑜旈崺銏ゅ箻鐠囪尙顓洪梺缁樺姈椤旀牕霉閸曨垱鈷戦悷娆忓閸斻倗鐥紒銏犲箹妞?
 * 3. GET /api/warehouses/{id} - 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢埥澶愬箼閸愨晜鍎為梻鍌氬€风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ閿濆懎顬嬪銈傛櫇閸忔﹢寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿?
 * 4. GET /api/warehouses/code/{code} - 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢湁闁挎繂鐗婇鐘电棯閸欍儳鐭欓柡灞糕偓鎰佸悑閹肩补鈧尙鏁栭梻浣规偠閸斿秵绻涙繝鍥ц摕闁靛牆顦粻姘辨喐瀹ュ憘锝夋嚒閵堝棗鏋戦梺鍝勵槹鐎笛勫緞閸曨厾纾奸柛灞剧☉濞搭喗顨ラ悙鐤殿亪鎮鹃悜钘夌倞闁靛ě鍕靛悋
 * 5. POST /api/warehouses - 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箚瑜滈崵鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈?
 * 6. PUT /api/warehouses/{id} - 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷戦悢灏佹斀闁绘ɑ褰冮弳鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈?
 * 7. PUT /api/warehouses/{id}/activate - 婵犵數濮烽弫鍛婃叏閻戝鈧倹绂掔€ｎ亞锛涢梺瑙勫劤椤曨厾绮绘ィ鍐╃厾濠靛倸澹婇弶娲煕閻愯埖顏犵紒杈ㄥ浮椤㈡瑩宕崟顐€撮梻?
 * 8. PUT /api/warehouses/{id}/deactivate - 闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁糕剝鐟ラ崹婵嬪箹濞ｎ剙濡奸柡瀣╃窔閺屾洟宕煎┑鍥舵￥濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿?
 * 9. GET /api/warehouses/{id}/location-count - 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ閿濆懎顬夐梺绋挎捣閸犳牠寮婚悢鐓庣闁归偊鍓涘В銏㈢磽娴ｅ壊妲归柟鍛婂▕瀵鍩勯崘鈺侇€撻悗鐟板濠㈡绮婇鈧?
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
class WarehouseControllerCompleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    private Warehouse testWarehouse1;
    private Warehouse testWarehouse2;
    private Warehouse inactiveWarehouse;

    @BeforeEach
    void setUp() {
        // 婵犵數濮烽弫鎼佸磻閻愬搫绠伴柟闂寸缁犵娀鏌熼悧鍫熺凡缂佺姵鐗曢埞鎴︽偐閸欏鎮欓梺鍝勵儎缁舵岸寮婚敐鍛傛棃鍩€椤掑嫭鍋嬮柛鈩冪懅閻?
        warehouseRepository.deleteAll();

        // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箲閹邦啩婊堟煕閿旇骞愰柛瀣崌閺佹劖鎯旈垾宕囶啋婵犵鈧啿绾ч柟顔煎€垮濠氬焺閸愨晛顎撻梺鍛婄箓鐎氱兘寮抽銏″€?
        testWarehouse1 = Warehouse.builder()
            .code("WH01")
            .name("濠电姷鏁搁崑鐐哄垂閸洖绠插〒姘ｅ亾妞ゃ垺鐟︾换婵嬪礋椤愩垻浜伴梻渚€娼ч…顓㈡⒔閸曨垰纾?")
            .address("濠电姷鏁搁崑鐐哄垂閸洖绠伴柟闂寸劍閺呮繈鏌曟径鍡樻珔缂佺姵鐓￠弻銊モ攽閸℃ê顦╅梺鍝勬缁捇寮诲☉姘勃闁告挆鍕灡闂備胶顭堢换鎰崲閹版澘鐓″鑸靛姇缁犲鎮楅棃娑欐喐闁绘挷绶氬娲传閸曨喖顏梺鎸庢处娴滄粓鎮鹃悜鑺ュ亹閻犲洩寮撶槐鍫曟⒑閸涘﹥澶勯柛姗€绠栧畷鎰版倷濞村鏂€濡炪倖鏌ㄩ崥瀣嫻閿熺姵鐓曢柣妯虹－婢х敻鏌熼鐐毈妞ゃ垺鐟╁畷婊嗩槾闁绘挸顑夊娲偡闁箑娈堕梺绋款儐缁嬫牠鎮橀崶顒佲拻濞达綀顫夐崑鐘绘煕鎼淬垺銇濈€规洏鍨介幊鏍煛閳ь剟鎮?")
            .contact("闂備浇顕х€涒晠顢欓弽顓炵獥婵﹩鍘介崣蹇曗偓瑙勬礀濞层劎绮?13800138000")
            .isActive(true)
            .build();
        testWarehouse1 = warehouseRepository.save(testWarehouse1);

        testWarehouse2 = Warehouse.builder()
            .code("WH02")
            .name("闂傚倸鍊风粈渚€骞夐敍鍕殰闁圭儤鍤﹀☉銏犵睄闁稿本鑹鹃悘濠囨⒑闂堟侗鐓梻鍕瀹?")
            .address("闂傚倸鍊风粈渚€骞夐敓鐘冲亱婵°倕鎳庢闂佺粯鍔曢幖顐ゆ喆閿斿浜滈柟鎵虫櫅閳ь剚鐗犲鍐差煥閸喓鍘遍柣蹇曞仜婢т粙鍩婇弴鐘电＜闁炽儱鍟块埀顒€鐏濋～蹇旂節濮橆剛顦ㄩ梺闈涱焾閸庢彃袙閹扮増鐓?")
            .contact("闂傚倸鍊风粈渚€骞栭位鍥敍濞戣鲸顔旂紓浣割儐閿?13900139000")
            .isActive(true)
            .build();
        testWarehouse2 = warehouseRepository.save(testWarehouse2);

        inactiveWarehouse = Warehouse.builder()
            .code("WH03")
            .name("闂備浇顕уù鐑藉箠閹捐绠熼梽鍥Φ閹版澘绀冮柍鍝勫枤濞村嫰姊虹紒姗嗙劷缂侇噮鍨跺畷鎴﹀Ω閳哄倻鍘繝鐢靛仜閻忔繈鍩€椤掍緡娈旈柍缁樻婵偓闁靛牆鎳愰?")
            .address("婵犵數濮烽弫鎼佸磿閹寸姴绶ら柦妯侯槺閺嗭附銇勯幒鍡椾壕闂佹寧娲忛崹娲囬悧鍫熷劅闁靛繆鍓濋鍥⒒娴ｄ警鐒鹃柡鍫墴閹虫繈宕滆閸忔粓鏌嶈閸撶喖寮婚敐鍫㈢杸闁哄洨鍋樼划鍫曟倵鐟欏嫭澶勯柛鎾寸⊕缁?")
            .contact("闂傚倸鍊烽懗鍓佸垝椤栨粌鍨濈€光偓閸曞灚鏅為梺鍛婃处閸欌偓濠?13700137000")
            .isActive(false)
            .build();
        inactiveWarehouse = warehouseRepository.save(inactiveWarehouse);
    }

    // ========== 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崨顔兼缂備胶濮伴崕鐢稿蓟瀹ュ牜妾ㄩ梺鍛婃尵閸犲酣顢氶敐澶婂瀭妞ゆ劑鍨荤粣鐐寸節閵忥絾纭鹃柡鍫墰娴滅鈹戠€ｎ偆鍘甸梺缁樻尭濞寸兘骞楅悩缁樼厵婵繂鑻崥鍦磼椤旂》鍔熼柍褜鍓熷褔骞夐敍鍕ㄥ亾?==========

    @Test
    @DisplayName("case-2")
    void getAllWarehouses_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].code").value("WH01"))
            .andExpect(jsonPath("$[1].code").value("WH02"))
            .andExpect(jsonPath("$[2].code").value("WH03"));
    }

    @Test
    @DisplayName("case-3")
    void getAllActiveWarehouses_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/active")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].code").value("WH01"))
            .andExpect(jsonPath("$[0].isActive").value(true))
            .andExpect(jsonPath("$[1].code").value("WH02"))
            .andExpect(jsonPath("$[1].isActive").value(true));
    }

    @Test
    @DisplayName("case-4")
    void getAllWarehouses_EmptyDatabase() throws Exception {
        // 婵犵數濮烽弫鎼佸磻閻愬搫绠伴柟闂寸缁犵娀鏌熼悧鍫熺凡闁绘挻锕㈤弻鈥愁吋鎼粹€崇闂佸搫顑勭欢姘跺蓟閿濆憘鏃堝焵椤掑嫭鍋嬮柛鈩冪懅閻?
        warehouseRepository.deleteAll();

        mockMvc.perform(get("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ========== 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢埥澶愬箼閸愨晜鍎為梻鍌氬€风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿婵☆垳顭堟俊鐣岀磼缂佹娲寸€殿喖鐖奸獮瀣敇閻愭彃顥掗梻?==========

    @Test
    @DisplayName("case-5")
    void getWarehouseById_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testWarehouse1.getId()))
            .andExpect(jsonPath("$.code").value("WH01"))
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.address").value(notNullValue()))
            .andExpect(jsonPath("$.contact").value(notNullValue()))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("case-6")
    void getWarehouseById_NotFound() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("case-7")
    void getWarehouseById_InvalidId() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", "invalid")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // ========== 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢湁闁挎繂鐗婇鐘电棯閸欍儳鐭欓柡灞糕偓鎰佸悑閹肩补鈧尙鏁栭梻浣规偠閸斿秵绻涙繝鍥ц摕闁挎稑瀚▽顏堟偣閸ャ劌绲诲┑顔哄€曢—鍐Χ閸℃顦ㄥ銈冨妼濡稓鍒掔€ｎ喖绠虫俊銈傚亾缂佺姾宕甸埀顒冾潐濞叉牕煤閿曞倹鍎嶉柟瀛樼妇閺€浠嬫煟閹邦剛鎽犵紓宥嗗灦閵囧嫰骞嬪┑鍫濃叺闂?==========

    @Test
    @DisplayName("case-8")
    void getWarehouseByCode_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/code/{code}", "WH01")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("WH01"))
            .andExpect(jsonPath("$.name").value(notNullValue()));
    }

    @Test
    @DisplayName("case-9")
    void getWarehouseByCode_NotFound() throws Exception {
        mockMvc.perform(get("/api/warehouses/code/{code}", "INVALID")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箚瑜滈崵鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈銊ノ熺粙鍨瀴缂備胶绮惄顖氼嚕閸洖绠ｆい鎾跺仜婢瑰秹姊?==========

    @Test
    @DisplayName("case-10")
    void createWarehouse_AllFields() throws Exception {
        String requestBody = """
            {
                "code": "WH04",
                "name": "新建仓库",
                "address": "北京市海淀区仓储路1号",
                "contact": "闂傚倷娴囧畷鍨叏瀹ュ鐐婇柕濠忕畵閺侇亪姊?13600136000"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("WH04"))
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.address").value(notNullValue()))
            .andExpect(jsonPath("$.contact").value(notNullValue()))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("case-11")
    void createWarehouse_RequiredFieldsOnly() throws Exception {
        String requestBody = """
            {
                "code": "WH05",
                "name": "仅必填仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("WH05"))
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("case-12")
    void createWarehouse_CodeAlreadyExists() throws Exception {
        String requestBody = """
            {
                "code": "WH01",
                "name": "重复编码仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("case-13")
    void createWarehouse_MissingCode() throws Exception {
        String requestBody = """
            {
                "name": "缺少编码仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("case-14")
    void createWarehouse_MissingName() throws Exception {
        String requestBody = """
            {
                "code": "WH06"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("case-15")
    void createWarehouse_InvalidCodeFormat() throws Exception {
        String requestBody = """
            {
                "code": "wh01",
                "name": "无效编码仓库"
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // ========== 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷戦悢灏佹斀闁绘ɑ褰冮弳鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈銊ノ熺粙鍨瀴缂備胶绮惄顖氼嚕閸洖绠ｆい鎾跺仜婢瑰秹姊?==========

    @Test
    @DisplayName("case-16")
    void updateWarehouse_AllFields() throws Exception {
        String requestBody = """
            {
                "name": "更新后的仓库",
                "address": "上海市静安区仓储路2号",
                "contact": "李四 13600136000"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testWarehouse1.getId()))
            .andExpect(jsonPath("$.code").value("WH01"))  // 缂傚倸鍊搁崐鎼佸磹閹间礁纾圭憸鐗堝笚閸嬪绻濇繝鍌滃闁稿顦扮换婵囩節閸屾凹浼€闂佸磭绮濠氬焵椤掆偓缁犲秹宕曢柆宥呯疇閹艰揪绲跨粈?
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.address").value(notNullValue()))
            .andExpect(jsonPath("$.contact").value(notNullValue()));
    }

    @Test
    @DisplayName("case-17")
    void updateWarehouse_OnlyName() throws Exception {
        String requestBody = """
            {
                "name": "只更新名称仓库"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.address").value(notNullValue()))
            .andExpect(jsonPath("$.contact").value(notNullValue()));
    }

    @Test
    @DisplayName("case-18")
    void updateWarehouse_NotFound() throws Exception {
        String requestBody = """
            {
                "name": "不存在仓库"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", 99999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 婵犵數濮烽弫鍛婃叏閻戝鈧倹绂掔€ｎ亞锛涢梺瑙勫劤椤曨厾绮?闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁糕剝鐟ラ崹婵嬪箹濞ｎ剙濡奸柡瀣╃窔閺屾洟宕煎┑鍥舵￥濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿婵☆垳顭堟俊鐣岀磼缂佹娲寸€殿喖鐖奸獮瀣敇閻愭彃顥掗梻?==========

    @Test
    @DisplayName("case-19")
    void activateWarehouse_Success() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/activate", inactiveWarehouse.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(inactiveWarehouse.getId()))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("case-20")
    void activateWarehouse_NotFound() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/activate", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("case-21")
    void deactivateWarehouse_Success() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testWarehouse1.getId()))
            .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("case-22")
    void deactivateWarehouse_NotFound() throws Exception {
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ閿濆懎顬夐梺绋挎捣閸犳牠寮婚悢鐓庣闁归偊鍓涘В銏㈢磽娴ｅ壊妲归柟鍛婂▕瀵鍩勯崘鈺侇€撻悗鐟板濠㈡绮婇鈧娲閳哄啫鍩岄梺鍦拡閸嬪﹤锕㈡笟鈧弻锝嗘償椤栨粎校闂佸憡蓱閸庡啿宓?==========

    @Test
    @DisplayName("case-23")
    void getLocationCount_Success() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}/location-count", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isNumber());
    }

    @Test
    @DisplayName("case-24")
    void getLocationCount_WarehouseNotFound() throws Exception {
        // 婵犵數濮烽弫鎼佸磻濞戔懞鍥敇閵忕姷顦悗骞垮劚椤︻垳绮堥崼婢濆綊鎮℃惔锝嗘喖闂佸搫鎷嬮崜姘跺箞閵娿儙鐔兼惞閻у摜绀婄紓鍌欒兌婵兘骞婇幘璇茬叀濠㈣埖鍔曠粻濂告煕閹板苯鎳庨崵顒勬⒑濮瑰洤鐒洪柛銊╀憾楠炴劙宕橀懠顒佹闂佹寧绻傞ˇ浼村疾閺屻儲鐓曢柡鍥ュ妼楠炴﹢鏌熼懠棰濇綈缂佺粯鐩鍫曞箣濠靛洤鈧垳绱掗悙顒佺凡缂佸鏁婚獮鍫ュΩ閵娧呮澑濠电偞鍨堕…鍥囬埡鍛拺闁告稑锕︾粻鎾绘倵濮樼厧鏋ゅ瑙勬礃缁傛帞鈧綆鍋勬禒鍝勵渻閵堝棛澧遍柛瀣仦缁傚秹濡堕崶鈺冿紲闁哄鐗勯崝搴ｇ不閻愮鍋撶憴鍕┛缂傚秳绶氶妴渚€寮崼婵嗙獩濡炪倖鏋鍥╂殼閻庢鍠栭悥濂稿春閿熺姴纾兼繛鎴炶壘瀵櫕绻?0
        mockMvc.perform(get("/api/warehouses/{id}/location-count", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(0));
    }

    // ========== 濠电姷鏁搁崑鐐哄垂閸洖绠板┑鐘崇閸ゅ矂鏌ｉ幇闈涘闁告瑥绻橀弻锝夊籍閸ヨ泛鏅遍柣鐔哥懃鐎氼厽鍒婇幘顔藉仩婵炴垶甯掓晶鏌ユ煟閹捐泛孝闁宠鍨块幃娆戔偓娑欘焽缁嬪洤顪冮妶鍡楃仴闁绘鎹囬獮?==========

    @Test
    @DisplayName("case-25")
    void completeWorkflow() throws Exception {
        // 1. 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箚瑜滈崵鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈?
        String createRequest = """
            {
                "code": "WH99",
                "name": "流程测试仓库",
                "address": "广州市天河区流程路9号",
                "contact": "王五 13500135000"
            }
            """;

        String createResponse = mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long warehouseId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿?
        mockMvc.perform(get("/api/warehouses/{id}", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("WH99"));

        // 3. 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷戦悢灏佹斀闁绘ɑ褰冮弳鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈?
        String updateRequest = """
            {
                "name": "流程更新仓库"
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", warehouseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(notNullValue()));

        // 4. 闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁糕剝鐟ラ崹婵嬪箹濞ｎ剙濡奸柡瀣╃窔閺屾洟宕煎┑鍥舵￥濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿?
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));

        // 5. 婵犵數濮烽弫鍛婃叏閻戝鈧倹绂掔€ｎ亞锛涢梺瑙勫劤椤曨厾绮绘ィ鍐╃厾濠靛倸澹婇弶娲煕閻愯埖顏犵紒杈ㄥ浮椤㈡瑩宕崟顐€撮梻?
        mockMvc.perform(put("/api/warehouses/{id}/activate", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true));
    }
}
