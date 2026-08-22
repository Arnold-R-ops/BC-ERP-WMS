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
 * CustomerController 闂傚倸鍊搁崐绋课涘Δ鈧灋婵炲棙鎸搁崹鍌涙叏濮楀棗鍔掗柛锔诲幐閸嬫捇鏁愭惔鈥崇濠?
 *
 * V4.1 闂傚倷绀侀幖顐︻敄閸儱鏄ラ柡宥庡幗閸婂灚銇勯弽顐沪闁哄拋鍓氶幈銊ヮ潨閸℃鈷掗梺鍛婅壘閸婂潡寮婚悢鐓庣妞ゆ洖鎳忛ˉ鏍ь渻閵堝棙灏伴柟顔煎€块獮鍐醇閺囩偛鑰垮┑掳鍊曢崯顖氣枔椤曗偓濮婄儤娼幍顔煎闂佸湱顭堥崯鍧楀煝閺冨牊鏅濋柛灞炬皑椤撴椽姊鸿ぐ鎺戜喊闁告﹢绠栨俊鍫曞箻椤旇棄浠梺鎼炲劘閸斿秶绮氱捄銊х＜閺夊牄鍔庨幊鍥┾偓瑙勬礈婵炩偓鐎规洏鍔戦、姗€鎮㈠畡鏉款棐闂傚倷绀侀幖顐λ囬鐐茬柈闁哄鍩堥悗鑸电箾瀹割喕绨奸柍閿嬫礋閺岋繝宕橀妸銉ょ敖闂佽瀛╅悡锟犲蓟濞戞﹩鐓ラ柛鎰ㄦ櫆閹烽亶姊洪崫鍕棤闁哥姵鐗犻悰顕€骞嬮敂缁樻櫓闂佸壊鐓堥崰鏍綖閹剧粯鈷戦弶鐐村椤︼箑螖閻樿尙绠虫俊?
 *
 * 濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽銊х煁闁哥喐妞介弻娑㈠焺閸愵亝鍣紒鎯у綖閸楁娊寮?
 * 1. 闂備浇顕уù鐑藉箠閹捐瀚夋い鎺戝濮规煡鏌ㄥ┑鍡╂Ч闁稿骸绉归弻娑㈠即閵娿儰鑸梺鍛婅壘閸婂潡寮?CRUD 濠电姷鏁搁崑鐔烘崲濠靛鍤勯柛顐ｆ礀閻?
 * 2. 闂備浇宕甸崑鐐电矙閹达附鍎旈柛蹇曗拡濞堜粙鏌涢妷顔煎闁抽攱娲熼弻锟犲礃閵娿儰绨介梺璇插瑜板啳鐏冮梺缁橆焾鐏忔瑩鎮為悜鑺ョ厽闁挎洍鍋撴い锔炬暬瀵偄顓兼径濠冨祶闂佺粯绋撻悢涓扴 闂傚倷绀侀幉锟犳偡椤栨稓顩查柨婵嗩槸缁€鍌涙叏濡炶浜鹃梺璇″灠濞层劑鍩€椤掑﹦绉甸柛妯煎帶閳绘捇骞嶉鐟颁壕閻熸瑥瀚幗鍐瑰搴″闁崇粯鎹囬獮瀣偐椤愵澀澹曞Δ鐘靛仜閻忔繈鎮橀鍫熺厱闁冲搫鍊诲ú鎾煛?
 * 3. 闂傚倷绀侀幉锟犲蓟閿濆绀夌€广儱顦悞鍨亜閹寸偛顕滅紒浣峰嵆閺屾盯寮埀顒€鐣濈粙娆惧殨妞ゆ劑鍎抽悿鈧┑鐐村灦閼归箖鎯屽Δ鍛厽闁绘ê寮堕惌妤冪磼闊彃鈧危閹邦剦娼ㄩ柣顐ｇ湅ES 闂傚倷鐒﹀鎸庣濠婂牜鏁勯柛娑欐綑閺勩儳鈧厜鍋撻柛鏇ㄥ墮濞堟鏌℃径濠勫闁告柨绉撮埢宥呪槈閵忥紕鍘告繛杈剧到閹碱偊銆傞懖鈹惧亾鐟欏嫭灏紒鑸靛哺瀵?
 * 4. 婵犵數鍎戠徊钘壝归崒鐐茬獥婵°倕鎳庨弸渚€鏌涜箛姘汗闁崇懓绉电换婵嬫濞戞瑦鎮欓悶姘戞穱濠囧Χ閸ヮ灝銏ゆ煟椤撴繄绐旈柟顖欑劍瀵板嫰骞囬鐔哥彨闂備礁澹婇崑鍡涘窗鎼达絾瀚婚柣鏂垮悑閻撶喐銇勯弮鍥у惞妞わ讣绠撻弻锝夊煀濞嗗繐鏆婇柡瀣閺屾稖绠涢幘瀛樺枑闂佽崵鍠愮换鍫ュ蓟閿熺姴鐐婄憸蹇涘箺閻樼粯鐓涢悘鐐存灮闊剟鏌?
 * 5. 闂傚倷绀侀幖顐λ囬鐐茬柈闁哄鍩堥悗鑸电箾瀹割喕绨荤紒鐘茬－閹茬顓兼径濠冩К濠德板€撶欢鈥斥枔閻ｅ备鍋撻崗澶婁壕闂佸憡鍔︽禍婵嬶綖?
 * 6. 闂傚倷娴囧銊╂嚄閼稿灚娅犳俊銈傚亾闁伙絽鐏氶幏鍛喆閸曨偄濡抽梻浣告贡閸嬫挸顭囧▎鎾村剨妞ゆ柨鐨峰Σ鍫ユ煛閸屾稑顕滈柛锝呯秺閹妫冨☉妯奸獓濡炪們鍔婇崕閬嶆偩濠靛绀冩い蹇撴媼濞?
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
        // 濠电姷鏁搁崑鐐哄箰閹间礁绠犻柟鐗堟緲缁犳牕鈹戦悩鍙夋悙闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭?
        customerRepository.deleteAll();

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰滈柛锔诲幐閸嬫捇鏁愭惔鈥崇濠碘€冲级閹倿寮婚敐澶涚稏妞ゆ巻鍋撳┑鈥茬矙閺?
        salesUser1 = createUser("sales1", "SALESPERSON");
        salesUser2 = createUser("sales2", "SALESPERSON");
        adminUser = createUser("admin", "TENANT_ADMIN");
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

    // ========== 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褏浜柛瀣崌閻涱噣宕归鍙ョ棯闂備胶纭堕弲婊呯矙閹达附绠掗梺璇插嚱缂嶅棝宕㈤幆顬?==========

    @Test
    @Order(1)
    @WithMockUser(username = "sales1", authorities = {"customer:create", "SALESPERSON"})
    @DisplayName("case-2")
    void testCreateCustomer_AutoSetOwner() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
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
            .andExpect(jsonPath("$.code", matchesPattern("\\d+")))
            .andExpect(jsonPath("$.name").value("Customer A"))
            .andReturn();

        // Then - 婵犲痉鏉库偓妤佹叏閹绢喗鍎楀〒姘ｅ亾闁诡垯鐒﹀鍕箛椤掍胶鈧剟姊虹紒姗嗘當闁绘妫涚划濠囨晝閸屾氨顔?
        String responseBody = result.getResponse().getContentAsString();
        CustomerResponse response = objectMapper.readValue(responseBody, CustomerResponse.class);

        Customer savedCustomer = customerRepository.findById(response.getId()).orElseThrow();
        assertThat(savedCustomer.getOwnerId()).isNotNull();
        assertThat(savedCustomer.getCode()).isEqualTo(response.getCode());
    }

    // ========== 闂備浇宕甸崑鐐电矙閹达附鍎旈柛蹇曗拡濞堜粙鏌涢妷顔煎闁抽攱娲熼弻锟犲礃閵娿儰绨介梺璇查瀵墎鎹㈠☉銏犵骇闁规惌鍘奸崜鐢告⒑?==========

    @Test
    @Order(2)
    @WithMockUser(username = "sales1", authorities = {"customer:view", "SALESPERSON"})
    @DisplayName("case-3")
    void testRowLevelSecurity_SalesOnlySeesOwnCustomers() throws Exception {
        // Given - 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褜鍤熼柍缁樻閺屾洟宕煎┑鍡╀純闂佹悶鍊曢澶婎潖閻戞ɑ鍎熼柟鎯у帠婢规洟姊绘担鍛婃喐闁稿孩鍔欏畷浼村冀椤撶偟鐤呴梺瑙勫劶婵倝宕曞澶嬬厱闁哄洢鍔屾禍浠嬫煕濮橆剙鈧潡寮?
        Customer customer1 = createCustomer("CUST001", "Customer A", salesUser1.getId());
        Customer customer2 = createCustomer("CUST002", "Customer B", salesUser2.getId());
        customerRepository.saveAll(java.util.Arrays.asList(customer1, customer2));

        // When - sales1 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛殜闁稿鎹囬悰顕€宕归鍙ョ棯闂備胶纭堕弲婊堟儎椤栨氨鏆︽慨姗嗗劦閺冨牆绀嬮柍瑙勫劤娴?
        mockMvc.perform(get("/api/customers")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))  // 闂傚倷绀侀幉锟犳偡椤栨稓顩查柨婵嗩槸缁€鍌涙叏濡炶浜鹃梺璇″灠濞层劑鍩€椤掑﹦绉靛ù婊勭矒閹繝宕掗悙瀵稿幘婵犻潧鍊搁ˇ顔捐姳閼姐倗纾奸悹浣哥－缁愭棃鏌?婵犵數鍋為崹鍫曞箹閳哄倻顩叉繝濠傜墱閺佸洨鎲搁悧鍫濈瑨缂?
            .andExpect(jsonPath("$[0].code").value("CUST001"));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "admin", authorities = {"customer:view", "TENANT_ADMIN"})
    @DisplayName("case-4")
    void testRowLevelSecurity_AdminSeesAllCustomers() throws Exception {
        // Given - 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褜鍤熼柍缁樻閺屾洟宕煎┑鍡╀純闂佹悶鍊曢澶婎潖閻戞ɑ鍎熼柟鎯у帠婢规洟姊绘担鍛婃喐闁稿孩鍔欏畷浼村冀椤撶偟鐤呴梺瑙勫劶婵倝宕曞澶嬬厱闁哄洢鍔屾禍浠嬫煕濮橆剙鈧潡寮?
        Customer customer1 = createCustomer("CUST001", "Customer A", salesUser1.getId());
        Customer customer2 = createCustomer("CUST002", "Customer B", salesUser2.getId());
        customerRepository.saveAll(java.util.Arrays.asList(customer1, customer2));

        // When - admin 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛殜闁稿鎹囬悰顕€宕归鍙ョ棯闂備胶纭堕弲婊堟儎椤栨氨鏆︽慨姗嗗劦閺冨牆绀嬮柍瑙勫劤娴?
        mockMvc.perform(get("/api/customers")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));  // 闂傚倷绀侀幉锟犳偡椤栫偛鍨傛い鏍ㄧ〒椤╂煡鏌熼悜姗嗘畷闁稿孩鍨块幃妤呮晲鎼存繄鐩庨梺缁樻尭閸婂潡寮婚悢鍝勬瀳婵☆垵妗ㄦ竟鏇㈡⒒?婵犵數鍋為崹鍫曞箹閳哄倻顩叉繝濠傜墱閺佸洨鎲搁悧鍫濈瑨缂?
    }

    // ========== 闂傚倷绀侀幉锟犲蓟閿濆绀夌€广儱顦悞鍨亜閹寸偛顕滅紒浣峰嵆閺屾盯寮埀顒€鐣濈粙娆惧殨妞ゆ劑鍎抽悿鈧梺鍝勫暙閸婂爼宕戦悙鐑樼厽?==========

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

        // When - sales1 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛殜闁稿鎹囬悰顕€宕归鍙ョ棯闂備胶纭堕弲婊呯矙閹捐绠柣妯肩帛閺呮粓鏌﹀Ο渚Ц濠?
        mockMvc.perform(get("/api/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", containsString("*")))  // 闂傚倷娴囬崑鎰八夐幇顓滀粓闁归棿绀侀悿?
            .andExpect(jsonPath("$.phone").value("139****5678"))  // 闂傚倷娴囬崑鎰八夐幇顓滀粓闁归棿绀侀悿?
            .andExpect(jsonPath("$.email").value("z***@example.com"))  // 闂傚倷娴囬崑鎰八夐幇顓滀粓闁归棿绀侀悿?
            .andExpect(jsonPath("$.address").value("123456789***"));  // 闂傚倷娴囬崑鎰八夐幇顓滀粓闁归棿绀侀悿?
    }

    @Test
    @Order(5)
    @WithMockUser(username = "admin", authorities = {"customer:view", "TENANT_ADMIN"})
    @DisplayName("case-6")
    void testDynamicMasking_AdminSeesOriginalData() throws Exception {
        // Given
        Customer customer = createCustomer("CUST001", "Customer A", salesUser1.getId());
        customer.setPhone("13912345678");
        customer.setEmail("zhangsan@example.com");
        customer.setAddress("1234567890Address");
        customerRepository.save(customer);

        // When - admin 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛殜闁稿鎹囬悰顕€宕归鍙ョ棯闂備胶纭堕弲婊呯矙閹捐绠柣妯肩帛閺呮粓鏌﹀Ο渚Ц濠?
        mockMvc.perform(get("/api/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Customer A"))  // 闂傚倷绀侀幉锟犫€﹂崶顒€绐楅幖鎼厜缂嶆牠鏌熼悧鍫熺凡闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭?
            .andExpect(jsonPath("$.phone").value("13912345678"))  // 闂傚倷绀侀幉锟犫€﹂崶顒€绐楅幖鎼厜缂嶆牠鏌熼悧鍫熺凡闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭?
            .andExpect(jsonPath("$.email").value("zhangsan@example.com"))  // 闂傚倷绀侀幉锟犫€﹂崶顒€绐楅幖鎼厜缂嶆牠鏌熼悧鍫熺凡闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭?
            .andExpect(jsonPath("$.address").value("1234567890Address"));  // 闂傚倷绀侀幉锟犫€﹂崶顒€绐楅幖鎼厜缂嶆牠鏌熼悧鍫熺凡闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭?
    }

    // ========== 婵犵數鍎戠徊钘壝归崒鐐茬獥婵°倕鎳庨弸渚€鏌涜箛姘汗闁崇懓绉电换婵嬫濞戞瑦鎮欓悶姘埞鎴︽倷鐎涙绋囧銈嗗灥閻楁捇骞?==========

    @Test
    @Order(6)
    @WithMockUser(username = "sales1", authorities = {"customer:edit", "SALESPERSON"})
    @DisplayName("case-7")
    void testEditProtection_MaskedFieldsNotUpdated() throws Exception {
        // Given - 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褏浜柛瀣崌閻涱噣宕归鍙ョ棯闂?
        Customer customer = createCustomer("CUST001", "Customer A", salesUser1.getId());
        customer.setPhone("13912345678");
        customer.setEmail("zhangsan@example.com");
        customer.setAddress("1234567890Address");
        customer = customerRepository.save(customer);

        // When - 闂傚倷绀佸﹢杈╁垝椤栫偛绀夐柟鐑樺焾濞尖晠鏌ㄩ弴鐐测偓鍝ョ矆瀹€鍕厱婵炴垵宕獮妤呮煕鎼淬垺宕岄柡宀€鍠撻幃浼村焺閸愨晝褰囬梻浣虹帛閻楁鍒掗幘璇茬畾闁告劦鍠栫粈瀣亜閹哄秷鍏岄柛鎴滅矙濮婃椽宕崟顐熷亾鐟欏嫬鍨斿ù鐘差儛閺佸銇勯幒鍡椾壕闁?
        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("Updated Customer A")
            .contact("Updated Contact A")
            .phone("139****5678")
            .email("z***@example.com")
            .address("123456789***")
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .build();

        mockMvc.perform(put("/api/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andDo(print())
            .andExpect(status().isOk());

        // Then - 婵犲痉鏉库偓妤佹叏閹绢喗鍎楀〒姘ｅ亾闁诡垯鐒﹀鍕箛椤掍胶鈧剟姊虹紒姗嗘當闁绘妫涚划濠囨晝閸屾氨顔愰柣搴㈢⊕钃遍柟顖氱墢缁辨帡宕ｅΟ鍝勭ギ濡ょ姷鍋炵敮锟犲春閿熺姴绀冩い蹇撳娴狀垶姊虹拠鎻掝劉缂佸甯￠妴鍐幢濞戣鲸鏅╅柣蹇曞仜婢т粙鍩炲鍡欑瘈闂傚牊绋掗幖鎰版煟閿旇娅嶉柡灞剧☉铻ｉ柣鎾冲閻庡姊?
        Customer updatedCustomer = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(updatedCustomer.getName()).isEqualTo("Updated Customer A");
        assertThat(updatedCustomer.getContact()).isEqualTo("Updated Contact A");
        assertThat(updatedCustomer.getPhone()).isEqualTo("13912345678");
        assertThat(updatedCustomer.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(updatedCustomer.getAddress()).isEqualTo("1234567890Address");
    }

    // ========== 闂備浇顕уù鐑藉箠閹捐瀚夋い鎺戝濮?CRUD 濠电姷鏁搁崑鐔烘崲濠靛鍤勯柛顐ｆ礀閻撴繈鏌ｉ幘宕囩槏闁革富鍘搁崑鎾绘晲鎼粹€崇濠?==========

    @Test
    @Order(7)
    @WithMockUser(username = "sales1", authorities = {"customer:create", "customer:view", "customer:edit", "customer:delete", "SALESPERSON"})
    @DisplayName("case-8")
    void testCompleteCRUDFlow() throws Exception {
        // 1. 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褏浜柛瀣崌閻涱噣宕归鍙ョ棯闂?
        CreateCustomerRequest createRequest = CreateCustomerRequest.builder()
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

        // 2. 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛殜闁稿鎹囬悰顕€宕归鍙ョ棯闂備胶纭堕弲婊呯矙閹捐绠柣妯肩帛閺呮粓鏌﹀Ο渚Ц濠?
        mockMvc.perform(get("/api/customers/" + customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(createdCustomer.getCode()));

        // 3. 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃顏ら柛瀣崌閻涱噣宕归鍙ョ棯闂?
        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
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
            .andExpect(jsonPath("$.name", containsString("*")));  // 闂傚倷娴囬崑鎰八夐幇顓滀粓闁归棿绀侀悿楣冩⒑椤掆偓缁夊鎲撮敃鍌氱閺夊牆澧界粙濠氭煟閿濆鎲鹃柡灞剧☉椤繈顢楅崒鍌樺€楅幃?

        // 4. 闂傚倷绀侀幉锛勬暜閻愬绠鹃柍褜鍓氱换娑㈠川椤撶喐宕抽梺閫炲苯澧紒鐘茬Ч瀹曠懓煤椤忓嫭妲┑鐐村灟閸ㄦ椽寮查鍕厱妞ゆ劦鍓涚粔顒勬煙婵傚浜伴柡灞剧洴楠炴帡寮惔鎾寸€版繝鐢靛仜閹冲酣鏌婇敐澶婃瀬?
        mockMvc.perform(delete("/api/customers/" + customerId))
            .andExpect(status().isNoContent());

        // 5. 婵犲痉鏉库偓妤佹叏閹绢喗鍎楀〒姘ｅ亾闁诡垯鐒﹀鍕偓锝庡亜椤€愁渻閵堝棛澧柤褰掔畺閹﹢鍩￠崨顔规嫽?
        Customer deletedCustomer = customerRepository.findById(customerId).orElseThrow();
        assertThat(deletedCustomer.getIsActive()).isFalse();
    }

    // ========== 闂傚倷绀侀幖顐λ囬鐐茬柈闁哄鍩堥悗鑸电箾瀹割喕绨荤紒鐘茬－閹茬顓兼径濠冩К濠德板€愰崑鎾绘婢舵劖鍊甸柨婵嗛楠炴ɑ淇?==========

    @Test
    @Order(8)
    @WithMockUser(username = "sales1", authorities = {"SALESPERSON"})
    @DisplayName("case-9")
    void testPermissionControl_AccessDenied() throws Exception {
        // Given
        Customer customer = createCustomer("CUST001", "Customer A", salesUser1.getId());
        customerRepository.save(customer);

        // When & Then - 濠电姷鏁搁崑娑欏緞閸ヮ剙绀堟繝闈涙４閼?customer:view 闂傚倷绀侀幖顐λ囬鐐茬柈闁哄鍩堥悗?
        mockMvc.perform(get("/api/customers/" + customer.getId()))
            .andDo(print())
            .andExpect(status().isForbidden());
    }

    // ========== 闂備礁鎼ˇ顖炴偋閸℃稑绠犻幖娣灪閸欏繑銇勮箛鎾跺婵☆偅锕㈤幃宄扳枎韫囨搩浼€闂?==========

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
