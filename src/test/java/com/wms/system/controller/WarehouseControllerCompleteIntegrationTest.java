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
 * WarehouseController 闂備浇顕уù鐑藉箠閹捐瀚夋い鎺戝濮规煡鏌ㄥ┑鍡╂Ч闁抽攱鐗犻弻娑㈠焺閸忕媭浜幃姗€宕堕妸褏顔曢梺鍝勵槹閸╁牓宕曢幘缁樼厽?
 *
 * 濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倖鍨块弻娑㈠箛閳轰礁顬夌紓浣鸿檸閸ㄥ爼寮?
 * 1. GET /api/warehouses - 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷顔煎缂佺姰鍎甸弻宥堫檨闁告挾鍠庨锝夊垂椤愩垻绐炴繝銏ｆ硾閺堫剛浜稿┑瀣厵?
 * 2. GET /api/warehouses/active - 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷顔煎缂佺姰鍎甸弻宥堫檨闁告挾鍠庨锝夊垂椤愩垻绐為柣搴秵閸撴繄绮堢€ｎ亖鏀介柣鎰硾閽勮偐鈧娲熷褔鍩㈤幘璇茬闁绘劖顔栧ù鍕⒑鐟欏嫬鍔ょ痪缁㈠幖椤?
 * 3. GET /api/warehouses/{id} - 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕鈽夐幙鍐╂儞闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷锝呭妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏?
 * 4. GET /api/warehouses/code/{code} - 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕霉閿濆牊顏犵痪鍙ョ矙閺屸€愁吋鎼粹€茬敖闂佹悶鍔嶆繛濠囧蓟閵堝绠氱憸宥呂ｉ懡銈嗗枑闁哄瀵ф径鍕磼閸屾稑娴鐐疵悾鐑藉炊閵娧勵吇
 * 5. POST /api/warehouses - 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褜鍤熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱?
 * 6. PUT /api/warehouses/{id} - 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃鏆熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱?
 * 7. PUT /api/warehouses/{id}/activate - 濠电姷鏁告慨鐑姐€傛禒瀣；闁规儳顕粻楣冩煠婵傚壊鏉洪柛鐐舵缁辨帡顢欓崫鍕瀴闂?
 * 8. PUT /api/warehouses/{id}/deactivate - 闂傚倷鑳堕…鍫ヮ敄閸℃瑥鍨濋幖娣妼閺嬩線鏌曢崼婵囶棤妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏?
 * 9. GET /api/warehouses/{id}/location-count - 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷锝呭闁稿海鍠栭弻鐔煎箲閹邦剛姣㈢紓浣割槹閹告娊寮婚埄鍐╁鐎瑰嫭婢樼粊顕€姊?
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
        // 濠电姷鏁搁崑鐐哄箰閹间礁绠犻柟鐗堟緲缁犳牕鈹戦悩鍙夋悙闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭?
        warehouseRepository.deleteAll();

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠碘€冲级閹倿寮婚埄鍐╁闁告繂瀚烽弳顓㈡倵?
        testWarehouse1 = Warehouse.builder()
            .code("WH01")
            .name("婵犵數鍋為崹鍫曞箲娓氣偓椤㈡瑦绻濋崟顐㈢亰闂侀潧顭梽鍕磿?")
            .address("婵犵數鍋為崹鍫曞箰閹间焦鏅濋柕澶嗘櫅缁犳煡鏌ㄥ┑鍡樺闁哄棙绮撻弻娑氫沪閸撗勫垱闂佺绻愮换鎰板煡婢舵劕绠婚悗闈涙憸閻撲線姊洪崫鍕闁挎洦浜滈悾鐑芥偐鐠囪弓绱堕梺鍛婃处閸橀箖宕愰悙娴嬫斀妞ゆ柨鍚嬬拹锟犳煕閻樺磭澧甸柟顔炬暬椤㈡瑩宕滆閻撳姊洪悷閭﹀殶闁稿绋栭悘鍥⒒娴ｈ鍋犻柛搴㈡そ瀹曘垽鎳栭埡鈧悞?")
            .contact("闂佽瀛╅鏍窗濮橆厽鍙忕€规洖娲ㄧ粻?13800138000")
            .isActive(true)
            .build();
        testWarehouse1 = warehouseRepository.save(testWarehouse1);

        testWarehouse2 = Warehouse.builder()
            .code("WH02")
            .name("闂傚倷绀侀幉锛勬暜閹烘嚦娑㈠籍閸屾艾鐏婇梺闈涱煭闂勫嫰宕?")
            .address("闂傚倷绀侀幉锟犳偋濡ゅ懎桅闁绘劕鎼憴锔姐亜閹扳晛鈧牠寮冲鍫熺厱閻忕偛澧介埊鏇犵磼閳ュ啿鈧灝顫忔繝姘闁靛鍎插В鎰版煟?")
            .contact("闂傚倷绀侀幖顐λ囬娑辨缂佸锛?13900139000")
            .isActive(true)
            .build();
        testWarehouse2 = warehouseRepository.save(testWarehouse2);

        inactiveWarehouse = Warehouse.builder()
            .code("WH03")
            .name("闂佽娴烽幊鎾诲箟闄囬妵鎰板礃閳哄喚娴勯梺缁橆焽缁垶宕戦妸鈺傜厪濠电偛鐏濋埀顒侇殔閳绘棃濮€閵堝懐顔?")
            .address("濠电姷鏁搁崕鎴犲緤閽樺鏆︽い鎺嗗亾闁挎洏鍨洪ˇ鐗堟償閵忊剝顔囬梻浣侯焾閺堫剟鎳濋崜褎鍏滈柍褜鍓熼弻锝堢疀閺囩偘绮堕悗瑙勬处閸撴稒绂?")
            .contact("闂傚倷鑳剁划顖滃垝瀹€鍕垫晞闁告洦鍙€婵?13700137000")
            .isActive(false)
            .build();
        inactiveWarehouse = warehouseRepository.save(inactiveWarehouse);
    }

    // ========== 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛櫣缂佺姰鍎甸弻宥堫檨闁告挾鍠庨锝夊垂椤愩垻绐炴繝銏ｆ硾閺堫剛浜稿┑瀣厵闁绘挸娴烽幗鐘绘煙濮濆苯鍚圭紒顔硷功閳ь剟娼ч幉锛勨偓?==========

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
        // 濠电姷鏁搁崑鐐哄箰閹间礁绠犻柟鐗堟緲閻撴﹢鏌″搴″箹闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭?
        warehouseRepository.deleteAll();

        mockMvc.perform(get("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ========== 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕鈽夐幙鍐╂儞闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍕厡妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏濡濡界紒缁樼洴瀵爼骞嬮鐐插闂?==========

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

    // ========== 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕霉閿濆牊顏犵痪鍙ョ矙閺屸€愁吋鎼粹€茬敖闂佹悶鍔嶆繛濠囧蓟閿涘嫪娌悹鍥ㄥ絻婵倕顪冮妶鍡樼妞ゃ劌妫涚划瀣箳濡も偓缁犺崵鈧娲栧ú锕傛儍閹存績鏀介柣鎰摠缂嶆垶銇勯幋婵堝ⅵ闁?==========

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

    // ========== 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褜鍤熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱ㄥΟ绋垮缂佺粯鐩鍫曞箣椤撶偛澹嶉梻?==========

    @Test
    @DisplayName("case-10")
    void createWarehouse_AllFields() throws Exception {
        String requestBody = """
            {
                "code": "WH04",
                "name": "闂傚倷绀侀幖顐﹀磹閸фぜ鈧倿鏁傞幆褍鐏婇梺闈涱煭闂勫嫰宕?,
                "address": "濠德板€楁慨鐑藉磻濞戙垺鍊舵繝闈涚墢閻滃鏌ｉ幇顒備粵闁哄棙绮撻弻娑氫沪閹勬儧婵炲瓨绮犻崹杈╂崲濞戞矮娌柦妯侯槹濮ｆ劙鏌?,
                "contact": "闂備浇宕垫慨宥夊炊閵婏箓鏁梻?13600136000"
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
                "name": "缂傚倸鍊烽懗鑸垫叏椤撱垹纾婚柟鍓х帛閻撴洘淇婇妶鍕妽缁炬崘宕电槐鎺楊敊閸濆嫬顬夐梺?
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
                "name": "闂傚倸鍊烽悞锕併亹閸愵亞鐭撻柟缁㈠暉閸ヮ剙鐭楀璺虹焾濞村嫰姊虹憴鍕姢缁剧虎鍘奸～?
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
                "name": "闂傚倷绀侀幖顐﹀疮閻楀牊鍙忓瀣椤洘銇勮箛鎾跺闁稿孩锚闇夐柨婵嗩樈濡垶绻涙径鍫濈伈妤?
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
                "name": "闂備浇顕х换鎰崲閹邦喗宕查柟鐗堟緲閻ゎ噣鏌熼幆褍顣崇痪鍙ョ矙閺屸€愁吋鎼粹€茬敖闂佹悶鍔岄崯鍧楁箒闂佺粯顭堝▍鏇犱焊椤撶姷纾?
            }
            """;

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // ========== 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃鏆熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱ㄥΟ绋垮缂佺粯鐩鍫曞箣椤撶偛澹嶉梻?==========

    @Test
    @DisplayName("case-16")
    void updateWarehouse_AllFields() throws Exception {
        String requestBody = """
            {
                "name": "闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樺弶鎼愰悷娆欑畵楠炴牗娼忛崜褏蓱闂佷紮缍佹禍鍫曞蓟濞戞﹩娼╂い鎺戝€愰妶鍥ㄥ仏?,
                "address": "闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樺弶鎼愰悷娆欑畵楠炴牗娼忛崜褏蓱闂佷紮缍佹禍鍫曞蓟濞戙垺鏅滈柤鎭掑劤閸戔€斥攽?,
                "contact": "闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樺弶鎼愰悷娆欑畵楠炴牗娼忛崜褏蓱闂佷紮缍佹禍鍫曞蓟閵堝棙缍囬柕濠忚吂閹峰姊绘笟鍥ф灍闁圭鍟块锝夘敆閸曨剙浠梺瑙勵問閸犳岸銆?
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", testWarehouse1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testWarehouse1.getId()))
            .andExpect(jsonPath("$.code").value("WH01"))  // 缂傚倸鍊搁崐鎼佸磹瑜版帗鍋嬫繝濠傜墛閸嬪绻濇繝鍌氼伀闁崇粯姊婚埀顒€绠嶉崕閬嶅箠鎼达絿绀?
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.address").value(notNullValue()))
            .andExpect(jsonPath("$.contact").value(notNullValue()));
    }

    @Test
    @DisplayName("case-17")
    void updateWarehouse_OnlyName() throws Exception {
        String requestBody = """
            {
                "name": "闂傚倷绀侀幖顐﹀磹閻熼偊鐔嗘慨妞诲亾妤犵偛鍟村鎾偐閻㈢數鍘?
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
                "name": "闂傚倷绀侀幖顐﹀磹閻熼偊鐔嗘慨妞诲亾妤犵偛鍟村鎾偐閻㈢數鍘?
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", 99999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 濠电姷鏁告慨鐑姐€傛禒瀣；闁规儳顕粻?闂傚倷鑳堕…鍫ヮ敄閸℃瑥鍨濋幖娣妼閺嬩線鏌曢崼婵囶棤妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏濡濡界紒缁樼洴瀵爼骞嬮鐐插闂?==========

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

    // ========== 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷锝呭闁稿海鍠栭弻鐔煎箲閹邦剛姣㈢紓浣割槹閹告娊寮婚埄鍐╁鐎瑰嫭婢樼粊顕€姊洪棃鈺冨埌闁圭⒈鍋婂﹢渚€鏌ｆ惔顖滅У闁告ɑ鍎冲嵄?==========

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
        // 濠电姷鏁搁崑娑⑺囬銏犵鐎广儱顦粈鍫澝归悡搴ｆ憼闁哄拋鍓氶幈銊ノ熼搹鐧哥礊缂備胶濮烽幊鎾诲煡婢舵劕绠奸柛鎰屽懎鍤梺姹囧焺閸ㄩ亶骞愰崘鑼殾闁挎繂顦伴弲鏌ユ煕閺囥劌骞橀柟鑼额潐缁绘盯寮堕幋婵囧€紒鐐緲缁夌敻骞堥妸銊х杸婵炴垶顭囬ˇ鈺呮⒑閸涘﹦绠撻悗姘煎枤婢规洘绂掔€ｎ偄浠哄銈嗙墱閸嬬偞绂嶉妶鍥╃＜閺夊牄鍔庣粻鐐碘偓瑙勬穿缂嶄線銆侀弮鍫濆窛妞ゆ枮宥囩暤鐎殿喖鐖奸崺锟犲磼濞戞艾寮虫繝?0
        mockMvc.perform(get("/api/warehouses/{id}/location-count", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(0));
    }

    // ========== 婵犵數鍋為崹鍫曞箰婵犳碍鍤岄柣鎰靛墯閸欏繘鏌ｉ弮鍥跺晱閻熸瑥瀚刊鎾偠濞戞帒澧查柣鎾跺Т閳规垿鎮欑€涙绋囧銈嗗灥閻楁捇骞?==========

    @Test
    @DisplayName("case-25")
    void completeWorkflow() throws Exception {
        // 1. 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褜鍤熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱?
        String createRequest = """
            {
                "code": "WH99",
                "name": "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽銊с€掓い鈺冨厴閺岀喖骞嗚閿涘秵鎱?,
                "address": "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐粶闁圭懓鐖奸弻鈩冨緞鐎ｎ亶鍤嬬紓?,
                "contact": "濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倕鍊块弻锟犲礃閵娿儮鍋撻崸妤€绀傞柛銉墯閻撴盯鏌涚仦鍓х煁闁搞倐鍋撶紓?
            }
            """;

        String createResponse = mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long warehouseId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍕厡妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏?
        mockMvc.perform(get("/api/warehouses/{id}", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("WH99"));

        // 3. 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃鏆熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱?
        String updateRequest = """
            {
                "name": "闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樺弶鎼愰悷娆欑畵楠炴牗娼忛崜褏蓱闂佷紮缍€妞村摜鎹㈠☉銏犵骇闁规惌鍘奸崜鐢告⒑娴兼瑥鐦滈柛銊ㄤ含缁骞掑Δ鈧粻鑽も偓瑙勬礀濞诧箓鎯?
            }
            """;

        mockMvc.perform(put("/api/warehouses/{id}", warehouseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(notNullValue()));

        // 4. 闂傚倷鑳堕…鍫ヮ敄閸℃瑥鍨濋幖娣妼閺嬩線鏌曢崼婵囶棤妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏?
        mockMvc.perform(put("/api/warehouses/{id}/deactivate", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));

        // 5. 濠电姷鏁告慨鐑姐€傛禒瀣；闁规儳顕粻楣冩煠婵傚壊鏉洪柛鐐舵缁辨帡顢欓崫鍕瀴闂?
        mockMvc.perform(put("/api/warehouses/{id}/activate", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true));
    }
}
