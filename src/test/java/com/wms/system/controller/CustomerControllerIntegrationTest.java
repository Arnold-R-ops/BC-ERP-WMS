package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.customer.CustomerResponse;
import com.wms.system.entity.Customer;
import com.wms.system.entity.User;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.UserRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CustomerController 闂傚倸鍊稿Λ妤€螞濞嗘挸鍨傛慨姗嗗劒閸︻厸鍋撻敐搴″箻婵?
 *
 * V4.1 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥ鍡楀⒒闁告艾鍊块弻鐔煎箻椤曞懏顥栧銈嗘尰閹倿骞冮崼鏇炲耿婵°倕鍟▓顕€姊烘潪鎵妽闁圭鍟块埢鏃堟晝閸屾氨顓洪梺褰掑亰閸橀箖濡堕幘顔藉仯闁搞儯鍔嶇粚璺ㄧ磼鏉堛劎鎳囩€规洜濞€瀹曘劑顢橀悢宄板闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簼閳锋洟鏌￠崘銊ヤ簽闁诲孩鐓￠弻娑橆煥閸愨晜鎷遍梺鍝勬閸犳牠鐛幋锔绘晩闁割煈鍠栭～鎾绘⒑鏉炴壆顦﹀Δ鐘茬箳濡?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ矋閸熸椽鏌涢埄鍐噭缁惧彞鍗抽弻?
 * 1. 闂佽娴烽幊鎾诲嫉椤掑嫬姹查柨婵嗩槹閸庡秹鏌涢弴銊ヤ航闁告艾鍊块弻?CRUD 婵犵數鍋熺换婵嬫嚄閸洖鐓?
 * 2. 闂佽崵鍋炵粙鎴︽儔閸忕⒈娈介柛銉墯閳锋洟鏌￠崘銊ヤ簽闁诲孩褰冭灃闁绘灏欓悞鐑芥煟閿曗偓椤︾敻寮澶婃嵍闁绘稓鐔丒S 闂備礁鎲￠悷顖涚閿濆绀傛慨妞诲亾闁诡垰娲ㄩ埀顒婄秵閸樼厧鈻撻幍顔瑰亾鐟欏嫮鎽冨ù婊庡墮閳绘捇骞嬮悩顐壕妤犵偛鐏濋悘顏堟煕閳哄倻娲撮柡?
 * 3. 闂備礁鎲￠弻锝夊礉瀹ュ鐒垫い鎴炲缁佷即鏌涢弮鈧畝绋款嚕椤愩儳鐤€婵炴垶鑹鹃惌妤呮煟閻樺弶鐭楃紒韬插€楀Σ鎰潨閻看ES 闂備焦妞挎禍婊堫敄閸涙潙鏄ョ€光偓閸曨剙娈楅柡澶婄墑閸斿秴鈻嶅☉銏＄厸濞达絽鎼。鑲┾偓瑙勬尫缁舵岸寮?
 * 4. 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柛蹇氬亹閳瑰秵绻濋棃娑欐悙鐞氭ɑ淇婇妶鍥㈤柣顓濈窔閹焦寰勯幇顓熸珫闂佸壊鍋嗛崰搴ｆ嫻閻斿吋鐓熸い鏃囧吹椤︼箓鏌ｉ埊娆忓暊閺嬫棃鏌涜箛鎾存喐闁荤喐绻堥弻锟犲炊瑜忛幗鐘绘煛鐏炴枻韬柡?
 * 5. 闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簻缁犲磭鎲稿澶婃槬婵°倓绶″▓鐣屸偓鍏夊亾闁告劦浜濋～?
 * 6. 闂備浇妗ㄩ懗鑸垫櫠濡も偓閻ｅ灚鎷呯憴鍕妳闂佸湱鍋撳娆撴儊椤斿皷妲堥柡鍌涘閸ｅ綊鎮楅棃娑樼骇妞ゃ劊鍎遍悾婵嬪礃椤忓拋娼?
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Customer Data Security)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@DisplayName("case-1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User salesUser1;
    private User salesUser2;
    private User adminUser;

    @BeforeEach
    void setUp() {
        // 婵犵數鍋為幐鎼佸箠閹版澘绠栧┑鐘叉搐閺嬩線鏌ｅΔ鈧悧鍡欑矈?
        customerRepository.deleteAll();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愮М閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锝夛綖椤掆偓婵′粙鏌?
        salesUser1 = createUser("sales1", "SALESPERSON");
        salesUser2 = createUser("sales2", "SALESPERSON");
        adminUser = createUser("admin", "SUPER_ADMIN");
    }

    private User createUser(String username, String role) {
        User user = User.builder()
            .username(username)
            .password(passwordEncoder.encode("password"))
            .enabled(true)
            .build();
        return userRepository.save(user);
    }

    // ========== 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯х亪閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎴︽箒闁诲函缍嗛崢鎯?==========

    @Test
    @Order(1)
    @WithMockUser(username = "sales1", authorities = {"customer:create", "SALESPERSON"})
    @DisplayName("case-2")
    void testCreateCustomer_AutoSetOwner() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("Customer A")
            .contact("Alice")
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address("1234567890Address")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .build();

        // When
        MvcResult result = mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CUST001"))
            .andExpect(jsonPath("$.name").value("Customer A"))
            .andReturn();

        // Then - 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒佺€梺缁橆殔閻楀棛绮婇敃鍌氱?
        String responseBody = result.getResponse().getContentAsString();
        CustomerResponse response = objectMapper.readValue(responseBody, CustomerResponse.class);

        Customer savedCustomer = customerRepository.findById(response.getId()).orElseThrow();
        assertThat(savedCustomer.getOwnerId()).isNotNull();
        assertThat(savedCustomer.getCode()).isEqualTo("CUST001");
    }

    // ========== 闂佽崵鍋炵粙鎴︽儔閸忕⒈娈介柛銉墯閳锋洟鏌￠崘銊ヤ簽闁诲骸寮剁换娑㈠级閹搭厼鍓甸梺?==========

    @Test
    @Order(2)
    @WithMockUser(username = "sales1", authorities = {"customer:view", "SALESPERSON"})
    @DisplayName("case-3")
    void testRowLevelSecurity_SalesOnlySeesOwnCustomers() throws Exception {
        // Given - 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘棃鏌曢崼婵嗩伃闁搞倕顑夊鐑樻償閹惧厖澹曢梻浣告憸閸庢劙宕伴弽顓炵疅闁规儼濮ら崕宥夋煕閺囥劌浜介柛姘€块弻?
        Customer customer1 = createCustomer("CUST001", "Customer A", salesUser1.getId());
        Customer customer2 = createCustomer("CUST002", "Customer B", salesUser2.getId());
        customerRepository.saveAll(java.util.Arrays.asList(customer1, customer2));

        // When - sales1 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂佺硶鏅滈惄顖氱暦濮橆儵鏃堝礋閳规儳浜?
        mockMvc.perform(get("/api/customers")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))  // 闂備礁鎲￠悷顖涚閿濆绀傛慨妞诲亾闁诡垰娲ㄩ埀顒婄秵娴滄粓鎮￠崒鐐寸厾濠靛倸顦花鑽ょ磼鐠佸磭绐旈柟?濠电偞鍨堕幖鈺傜濠婂牜鏁囩憸鐗堝笒缁?
            .andExpect(jsonPath("$[0].code").value("CUST001"));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "admin", authorities = {"customer:view", "SUPER_ADMIN"})
    @DisplayName("case-4")
    void testRowLevelSecurity_AdminSeesAllCustomers() throws Exception {
        // Given - 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟閳绘棃鏌曢崼婵嗩伃闁搞倕顑夊鐑樻償閹惧厖澹曢梻浣告憸閸庢劙宕伴弽顓炵疅闁规儼濮ら崕宥夋煕閺囥劌浜介柛姘€块弻?
        Customer customer1 = createCustomer("CUST001", "Customer A", salesUser1.getId());
        Customer customer2 = createCustomer("CUST002", "Customer B", salesUser2.getId());
        customerRepository.saveAll(java.util.Arrays.asList(customer1, customer2));

        // When - admin 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂佺硶鏅滈惄顖氱暦濮橆儵鏃堝礋閳规儳浜?
        mockMvc.perform(get("/api/customers")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));  // 闂備礁鎲￠悷顖炲垂椤栨稓顩查柟鐑橆殕閸庢垿鎮楅敐搴濈盎闁绘挸鍊块弻鐔哄枈濡桨澹曢梻?濠电偞鍨堕幖鈺傜濠婂牜鏁囩憸鐗堝笒缁?
    }

    // ========== 闂備礁鎲￠弻锝夊礉瀹ュ鐒垫い鎴炲缁佷即鏌涢弮鈧畝绋款嚕椤愩儳鐤€闁哄啫鍊堕崑鐐烘煟?==========

    @Test
    @Order(4)
    @WithMockUser(username = "sales1", authorities = {"customer:view", "SALESPERSON"})
    @DisplayName("case-5")
    void testDynamicMasking_SalesSeeMaskedData() throws Exception {
        // Given
        Customer customer = createCustomer("CUST001", "Customer A", salesUser1.getId());
        customer.setPhone("13912345678");
        customer.setEmail("zhangsan@example.com");
        customer.setAddress("1234567890Address");
        customerRepository.save(customer);

        // When - sales1 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎾诲箯閻樼粯鏅滈柦妯侯槸婢?
        mockMvc.perform(get("/api/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", containsString("*")))  // 闂備浇鍋愰ˉ鎰仈閹间礁鐤?
            .andExpect(jsonPath("$.phone").value("139****5678"))  // 闂備浇鍋愰ˉ鎰仈閹间礁鐤?
            .andExpect(jsonPath("$.email").value("z***@example.com"))  // 闂備浇鍋愰ˉ鎰仈閹间礁鐤?
            .andExpect(jsonPath("$.address").value("123456789***"));  // 闂備浇鍋愰ˉ鎰仈閹间礁鐤?
    }

    @Test
    @Order(5)
    @WithMockUser(username = "admin", authorities = {"customer:view", "SUPER_ADMIN"})
    @DisplayName("case-6")
    void testDynamicMasking_AdminSeesOriginalData() throws Exception {
        // Given
        Customer customer = createCustomer("CUST001", "Customer A", salesUser1.getId());
        customer.setPhone("13912345678");
        customer.setEmail("zhangsan@example.com");
        customer.setAddress("1234567890Address");
        customerRepository.save(customer);

        // When - admin 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎾诲箯閻樼粯鏅滈柦妯侯槸婢?
        mockMvc.perform(get("/api/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Customer A"))  // 闂備礁鎲￠…鍥窗鎼搭煉缍栭柟鐗堟緲閺嬩線鏌ｅΔ鈧悧鍡欑矈?
            .andExpect(jsonPath("$.phone").value("13912345678"))  // 闂備礁鎲￠…鍥窗鎼搭煉缍栭柟鐗堟緲閺嬩線鏌ｅΔ鈧悧鍡欑矈?
            .andExpect(jsonPath("$.email").value("zhangsan@example.com"))  // 闂備礁鎲￠…鍥窗鎼搭煉缍栭柟鐗堟緲閺嬩線鏌ｅΔ鈧悧鍡欑矈?
            .andExpect(jsonPath("$.address").value("1234567890Address"));  // 闂備礁鎲￠…鍥窗鎼搭煉缍栭柟鐗堟緲閺嬩線鏌ｅΔ鈧悧鍡欑矈?
    }

    // ========== 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柛蹇氬亹閳瑰秵绻濋棃娑欐悙鐞氭ê鈹戦悙瀛樼稇妞ゆ垵鐗撻幆?==========

    @Test
    @Order(6)
    @WithMockUser(username = "sales1", authorities = {"customer:edit", "SALESPERSON"})
    @DisplayName("case-7")
    void testEditProtection_MaskedFieldsNotUpdated() throws Exception {
        // Given - 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯х亪閸嬫捇鐛崹顔句痪闂?
        Customer customer = createCustomer("CUST001", "Customer A", salesUser1.getId());
        customer.setPhone("13912345678");
        customer.setEmail("zhangsan@example.com");
        customer.setAddress("1234567890Address");
        customer = customerRepository.save(customer);

        // When - 闂備礁婀辩划顖炲礉閹烘埈娼╅柨鏇炲€哥粈宀勬煕濞戝崬骞楅柛搴㈡崌閺岀喓鎮伴埄鍐╃彇闂佺粯鐗楃划鎾诲箚閸愵喖绀嬫い鎺嶈兌閸戜粙姊洪崫鍕偓瑙勫垔娴犲鏁嬫い鎺嗗亾閺?
        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠斿〒姘ｅ亾鐎殿喖顕埀顒佺⊕钃遍柣鎾亾")
            .contact("闂備礁鎼ˇ顖涱殽缁嬫５娲箻鐠囨彃宓嗛柣搴㈢⊕钃遍柣鎾亾")
            .phone("139****5678")  // 闂備胶顢婇妴鈧柡鍛箞閹儱顭ㄩ崨顏勪壕婵炴垶鐟▓鏇熴亜?
            .email("z***@example.com")  // 闂備胶顢婇妴鈧柡鍛箞閹儱顭ㄩ崨顏勪壕婵炴垶鐟▓鏇熴亜?
            .address("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣?**")  // 闂備胶顢婇妴鈧柡鍛箞閹儱顭ㄩ崨顏勪壕婵炴垶鐟▓鏇熴亜?
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .build();

        mockMvc.perform(put("/api/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andDo(print())
            .andExpect(status().isOk());

        // Then - 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒佺€梺缁橆殔閻楀棛绮婇敃鍌氱閻庢稒蓱閹牏绱掗崣妯哄祮妤犵偞甯￠崺锟犲礃椤忓嫮浠梺璇插缁嬫帡銆冮崱娑辨晩閻忕偛澧介埞宥嗙節闂堟稒鎼愰柣锔诲櫍閺屾稑螣閻撳孩鐎婚梺?
        Customer updatedCustomer = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(updatedCustomer.getName()).isEqualTo("闁诲孩顔栭崰姘叏瀹曞洨绠斿〒姘ｅ亾鐎殿喖顕埀顒佺⊕钃遍柣鎾亾");  // 闁诲骸婀遍…鍫濐嚕鐠哄ソ娲锤濡も偓濡?
        assertThat(updatedCustomer.getContact()).isEqualTo("闂備礁鎼ˇ顖涱殽缁嬫５娲箻鐠囨彃宓嗛柣搴㈢⊕钃遍柣鎾亾");  // 闁诲骸婀遍…鍫濐嚕鐠哄ソ娲锤濡も偓濡?
        assertThat(updatedCustomer.getPhone()).isEqualTo("13912345678");  // 濠电儑绲藉ú锔炬崲閸愵亖鍋撻崹顐ｎ棃鐎规洏鍨介幃銏焊娴ｉ晲澹?
        assertThat(updatedCustomer.getEmail()).isEqualTo("zhangsan@example.com");  // 濠电儑绲藉ú锔炬崲閸愵亖鍋撻崹顐ｎ棃鐎规洏鍨介幃銏焊娴ｉ晲澹?
        assertThat(updatedCustomer.getAddress()).isEqualTo("1234567890Address");  // 濠电儑绲藉ú锔炬崲閸愵亖鍋撻崹顐ｎ棃鐎规洏鍨介幃銏焊娴ｉ晲澹?
    }

    // ========== 闂佽娴烽幊鎾诲嫉椤掑嫬姹?CRUD 婵犵數鍋熺换婵嬫嚄閸洖鐓濋柣鎾崇瘍閸︻厸鍋撻敐搴″箻婵?==========

    @Test
    @Order(7)
    @WithMockUser(username = "sales1", authorities = {"customer:create", "customer:view", "customer:edit", "customer:delete", "SALESPERSON"})
    @DisplayName("case-8")
    void testCompleteCRUDFlow() throws Exception {
        // 1. 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯х亪閸嬫捇鐛崹顔句痪闂?
        CreateCustomerRequest createRequest = CreateCustomerRequest.builder()
            .code("CUST999")
            .name("CRUD Customer")
            .contact("CRUD Contact")
            .phone("13900000000")
            .email("test@example.com")
            .address("CRUD Address 123")
            .creditLimit(new BigDecimal("50000.00"))
            .isActive(true)
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        CustomerResponse createdCustomer = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            CustomerResponse.class
        );
        Long customerId = createdCustomer.getId();

        // 2. 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎾诲箯閻樼粯鏅滈柦妯侯槸婢?
        mockMvc.perform(get("/api/customers/" + customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CUST999"));

        // 3. 闂備礁鎼ú銈夋偤閵娾晛钃熷┑鐘插閸嬫捇鐛崹顔句痪闂?
        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .code("CUST999")
            .name("CRUD Customer Updated")
            .contact("CRUD Contact Updated")
            .phone("13900000001")
            .email("test_updated@example.com")
            .address("CRUD Address 456")
            .creditLimit(new BigDecimal("60000.00"))
            .isActive(true)
            .build();

        mockMvc.perform(put("/api/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", containsString("*")));  // 闂備浇鍋愰ˉ鎰仈閹间礁鐤鹃梺顒€绉寸憴锕傚箹鏉堝墽绋婚柣锝変憾閺屾稑顫濋鍌傘倗鎮?

        // 4. 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁逞屽墮缁犲秹宕瑰ú顏勬槬婵炴垯鍨洪弲顒勬煕椤愵剛绉柟濂夊亰閺屾盯骞掗弬搴撴瀰濠电偛鎳庨柊锝夊极?
        mockMvc.perform(delete("/api/customers/" + customerId))
            .andExpect(status().isNoContent());

        // 5. 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ偄顫″銈嗙墬閼归箖鎮橀埡鍛拻?
        Customer deletedCustomer = customerRepository.findById(customerId).orElseThrow();
        assertThat(deletedCustomer.getIsActive()).isFalse();
    }

    // ========== 闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簻缁犲磭鎲稿澶婃槬婵°倐鍋撻棁澶愭倵閿濆骸骞樻俊?==========

    @Test
    @Order(8)
    @WithMockUser(username = "sales1", authorities = {"SALESPERSON"})
    @DisplayName("case-9")
    void testPermissionControl_AccessDenied() throws Exception {
        // Given
        Customer customer = createCustomer("CUST001", "Customer A", salesUser1.getId());
        customerRepository.save(customer);

        // When & Then - 婵犵數鍋涙径鍥礈濠靛棴鑰?customer:view 闂備礁鎼ˇ顖炲疮閺夋埈鐎?
        mockMvc.perform(get("/api/customers/" + customer.getId()))
            .andDo(print())
            .andExpect(status().isForbidden());
    }

    // ========== 闂佸搫顦悧鍡涘箠鎼淬垺鍙忔い蹇撶墕濡﹢鎮峰▎蹇擃伀闁?==========

    private Customer createCustomer(String code, String name, Long ownerId) {
        return Customer.builder()
            .code(code)
            .name(name)
            .contact("Default Contact")
            .phone("13900000000")
            .email("test@example.com")
            .address("Default Address")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .ownerId(ownerId)
            .build();
    }
}
