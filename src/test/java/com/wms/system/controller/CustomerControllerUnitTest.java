package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.customer.CustomerResponse;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CustomerController 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * V4.1 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥ鍡楀⒒闁告艾鍊块弻鐔煎箻椤曞懏顥栧銈嗘尰閹倿骞冮崼鏇炲耿婵☆垵妗ㄧ划顖炴⒑閸涘﹤绗╂繛澶嬬☉閳诲秹骞掗幊銊ユ贡閳ь剨缍嗛崢鎯ｉ幖浣圭叆婵炴垶顭囨晶鏇㈡倵閸偓鑰跨€规洏鍎甸、鏇㈡晲閸℃瑧袣闂傚倸鍊哥€氼參宕濆▎蹇婃灁鐟滃繒鍒掔€ｎ剚瀚氶柛娆忣槸椤忣垶姊烘潪鎷屽厡濠⒀勵殔閻ｅ灚绗熼埀顒勫箠濡粯缍囬柕濠忛檮閻濋亶姊?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ矋閸熸椽鏌涢埄鍐噭缁惧彞鍗抽弻?
 * 1. 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯х亪閸嬫捇鐛崹顔句痪闂?
 * 2. 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞濠电姴楠搁幃鍛存⒑?
 * 3. 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾闁宠鍨块崺鈧い鎺嗗亾閻撱倝鎮归崶褎鈻曢柛姘€块弻?
 * 4. 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉㈡杹閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎾诲箯閻樼粯鏅滈柦妯侯槸婢?
 * 5. 闂備礁鎼ú銈夋偤閵娾晛钃熷┑鐘插閸嬫捇鐛崹顔句痪闂?
 * 6. 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁逞屽墮缁犲秹宕瑰ú顏勬槬婵炴垯鍨洪弲顒勬煕椤愵剛绉柟濂夊亰閺屾盯骞掗弬搴撴瀰濠电偛鎳庨柊锝夊极?
 * 7. 闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簻缁犲磭鎲稿澶婃槬婵°倐鍋撻棁澶愭倵閿濆骸骞樻俊?
 * 8. 闂備浇妗ㄩ懗鑸垫櫠濡も偓閻ｅ灚绗熼埀顒勫箠濡粯缍囬柕濠忛檮閻濊鲸淇婇妶鍥㈤柣顓濈窔閹?
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Customer Data Security)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class CustomerControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    // ========== 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯х亪閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎴︽箒闁诲函缍嗛崢鎯?==========

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:create"})
    @DisplayName("case-2")
    void testCreateCustomer_Success() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .contact("闂備礁鎼ˇ顖涱殽缁嬫５?")
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣蹇撴禋閸ㄤ即顢氬▎鎾充紶闁告洦鍓涢々顒勬煟?8闂?")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .build();

        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .contact("闂備礁鎼ˇ顖涱殽缁嬫５?")
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣蹇撴禋閸ㄤ即顢氬▎鎾充紶闁告洦鍓涢々顒勬煟?8闂?")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(customerService.createCustomer(any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("CUST001"))
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.phone").value("13912345678"))
            .andExpect(jsonPath("$.email").value("zhangsan@example.com"));

        verify(customerService, times(1)).createCustomer(any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-3")
    void testCreateCustomer_Failure_NoPermission() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .build();

        // When & Then
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(customerService, never()).createCustomer(any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:create"})
    @DisplayName("case-4")
    void testCreateCustomer_Failure_MissingRequiredFields() throws Exception {
        // Given - 缂傚倸鍊搁崐鎼佸箹椤愶附鍎?code 闂?name
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .phone("13912345678")
            .build();

        // When & Then
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verify(customerService, never()).createCustomer(any());
    }

    // ========== 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呭暊閸嬫捇鐛崹顔句痪闂佺硶鏅滈惄顖氱暦濮橆儵鏃堝礋閳规儳浜鹃柛鎰电厑閸︻厸鍋撻敐搴″箻婵?==========

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-5")
    void testListCustomers_Success() throws Exception {
        // Given
        List<CustomerResponse> responses = Arrays.asList(
            CustomerResponse.builder()
                .id(1L)
                .code("CUST001")
                .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
                .phone("13912345678")
                .email("zhangsan@example.com")
                .isActive(true)
                .build(),
            CustomerResponse.builder()
                .id(2L)
                .code("CUST002")
                .name("闂備礁鎼ˇ顖涱殽缁嬫５?")
                .phone("13800138000")
                .email("lisi@example.com")
                .isActive(true)
                .build()
        );

        when(customerService.listCustomers()).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/customers"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].code").value("CUST001"))
            .andExpect(jsonPath("$[1].code").value("CUST002"));

        verify(customerService, times(1)).listCustomers();
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-6")
    void testListActiveCustomers_Success() throws Exception {
        // Given
        List<CustomerResponse> responses = Arrays.asList(
            CustomerResponse.builder()
                .id(1L)
                .code("CUST001")
                .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
                .isActive(true)
                .build()
        );

        when(customerService.listActiveCustomers()).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/customers")
                .param("activeOnly", "true"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].isActive").value(true));

        verify(customerService, times(1)).listActiveCustomers();
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("list customers filters by customer type")
    void testListCustomers_ByCustomerType() throws Exception {
        List<CustomerResponse> responses = List.of(
            CustomerResponse.builder()
                .id(1L)
                .code("CUST001")
                .name("Client customer")
                .customerType(CustomerType.CLIENT)
                .isActive(true)
                .build()
        );
        when(customerService.listActiveCustomers(CustomerType.CLIENT)).thenReturn(responses);

        mockMvc.perform(get("/api/customers")
                .param("activeOnly", "true")
                .param("customerType", "CLIENT"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].customerType").value("CLIENT"));

        verify(customerService).listActiveCustomers(CustomerType.CLIENT);
        verify(customerService, never()).listActiveCustomers();
    }

    @Test
    @DisplayName("case-7")
    void testListCustomers_Failure_Unauthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/customers"))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(customerService, never()).listCustomers();
    }

    // ========== 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉㈡杹閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎾诲箯閻樼粯鏅滈柦妯侯槸婢瑰牆鈹戦悙瀛樼稇妞ゆ垵鐗撻幆?==========

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-8")
    void testGetCustomer_Success() throws Exception {
        // Given
        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .contact("闂備礁鎼ˇ顖涱殽缁嬫５?")
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣蹇撴禋閸ㄤ即顢氬▎鎾充紶闁告洦鍓涢々顒勬煟?8闂?")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(customerService.getCustomer(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/customers/1"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("CUST001"))
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.phone").value("13912345678"));

        verify(customerService, times(1)).getCustomer(1L);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-9")
    void testGetCustomer_Success_MaskedData() throws Exception {
        // Given - 婵犵妲呴崹顏堝礈濠靛牃鍋?Service 闂佸搫顦弲婊堝蓟閵娿儍娲冀椤撶喎娈楅柡澶婄墑閸斿秴鈻嶅☉銏＄厸濞达絽鎼。鑲┾偓?
        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("闁?")  // 闁诲氦顫夐悺鏇犱焊椤忓牆绀夋慨妯挎硾閺?
            .contact("闂備礁鎼ˇ顖涱殽缁嬫５?")
            .phone("139****5678")  // 闁诲氦顫夐悺鏇犱焊椤忓牆绀夋慨妯挎硾閺?
            .email("z***@example.com")  // 闁诲氦顫夐悺鏇犱焊椤忓牆绀夋慨妯挎硾閺?
            .address("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣?**")  // 闁诲氦顫夐悺鏇犱焊椤忓牆绀夋慨妯挎硾閺?
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .build();

        when(customerService.getCustomer(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/customers/1"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("闁?"))
            .andExpect(jsonPath("$.phone").value("139****5678"))
            .andExpect(jsonPath("$.email").value("z***@example.com"))
            .andExpect(jsonPath("$.address").value("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣?**"));

        verify(customerService, times(1)).getCustomer(1L);
    }

    // ========== 闂備礁鎼ú銈夋偤閵娾晛钃熷┑鐘插閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎴︽箒闁诲函缍嗛崢鎯?==========

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:edit"})
    @DisplayName("case-10")
    void testUpdateCustomer_Success() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠斿〒姘ｅ亾闁轰礁绉瑰畷濂告偄閸濄儱鍤遍梻浣告惈閸婂憡鎯斿鍛潟?")
            .contact("闂備胶绮划宀勵敄閸曨叀濮?")
            .phone("13900000000")
            .email("zhangsan_new@example.com")
            .address("濠电偞鍨堕幐鎼佹晝閵夆晛绠查柨婵嗘处閺嗘粓鏌涚仦鍓ф创闁稿繐绻愰埥澶愬箻鐎靛摜鐓侀梺鍝勮嫰閿曨亜鐣烽悩铏闁规鍠氶幊鎰磽娴ｉ缚妾稿ù婊勭矒婵＄敻宕堕浣哄姸?闂?")
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .build();

        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠斿〒姘ｅ亾闁轰礁绉瑰畷濂告偄閸濄儱鍤遍梻浣告惈閸婂憡鎯斿鍛潟?")
            .contact("闂備胶绮划宀勵敄閸曨叀濮?")
            .phone("13900000000")
            .email("zhangsan_new@example.com")
            .address("濠电偞鍨堕幐鎼佹晝閵夆晛绠查柨婵嗘处閺嗘粓鏌涚仦鍓ф创闁稿繐绻愰埥澶愬箻鐎靛摜鐓侀梺鍝勮嫰閿曨亜鐣烽悩铏闁规鍠氶幊鎰磽娴ｉ缚妾稿ù婊勭矒婵＄敻宕堕浣哄姸?闂?")
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .updatedAt(LocalDateTime.now())
            .build();

        when(customerService.updateCustomer(eq(1L), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/customers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value(notNullValue()))
            .andExpect(jsonPath("$.phone").value("13900000000"));

        verify(customerService, times(1)).updateCustomer(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:edit"})
    @DisplayName("case-11")
    void testUpdateCustomer_Success_MaskedFieldsIgnored() throws Exception {
        // Given - 闂佽崵濮村ú顓㈠绩闁秵鍎戦柣妤€鐗忛埢鏃€銇勯幘璺轰粶闁伙箑鐖奸弻娑橆潩閻撳簼绨奸柣銏╁灡濡炰粙骞嗗鍡樺闁告縿鍎查幉璇测攽?
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .contact("闂備礁鎼ˇ顖涱殽缁嬫５?")
            .phone("139****5678")  // 闂備礁鎲￠悧鏇㈠箠鎼淬劌绠氶柛顐犲劚缁犳娊鏌嶉崫鍕殶闁?
            .email("z***@example.com")  // 闂備礁鎲￠悧鏇㈠箠鎼淬劌绠氶柛顐犲劚缁犳娊鏌嶉崫鍕殶闁?
            .address("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣?**")  // 闂備礁鎲￠悧鏇㈠箠鎼淬劌绠氶柛顐犲劚缁犳娊鏌嶉崫鍕殶闁?
            .build();

        // Service 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩瀹曞洨鏉搁梺浼欑到閺堫剙鈻撻敐澶嬬厵閻炴稈鍓濆▍鍥煟閺嶃劎鐭岄柍褜鍓氱粙鎺椼€冮崱娑辨晩鐎光偓閸曨剚娅栭悗鍏夊亾闁告劦浜為澶愭⒑閹肩偛鍔ら柛瀣〒閺侇噣顢曢敐鍡楀伎闁诲函缍嗛崑鍛枔閸洘鐓?
        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .phone("13912345678")  // 闂備礁鎲￠…鍥窗鎼搭煉缍栭柟鐗堟緲閺嬩線鏌ｅΔ鈧悧鍡欑矈閿曞倹鐓涢柛顐亗濞撮攱銇勯顒€顩柟韫嵆瀹曞崬鈻庨幇顕嗙矗
            .email("zhangsan@example.com")  // 闂備礁鎲￠…鍥窗鎼搭煉缍栭柟鐗堟緲閺嬩線鏌ｅΔ鈧悧鍡欑矈閿曞倹鐓涢柛顐亗濞撮攱銇勯顒€顩柟韫嵆瀹曞崬鈻庨幇顕嗙矗
            .address("闂備礁鎲￠悧妤呭Φ閻愬搫瑙︽い鎰╁€栭弳婊堟煕鐏炲墽鈯曠紒鈥冲€垮濠氬礃閵娿儲姣愰柣蹇撴禋閸ㄤ即顢氬▎鎾充紶闁告洦鍓涢々顒勬煟?8闂?")  // 闂備礁鎲￠…鍥窗鎼搭煉缍栭柟鐗堟緲閺嬩線鏌ｅΔ鈧悧鍡欑矈閿曞倹鐓涢柛顐亗濞撮攱銇勯顒€顩柟韫嵆瀹曞崬鈻庨幇顕嗙矗
            .build();

        when(customerService.updateCustomer(eq(1L), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/customers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("13912345678"))
            .andExpect(jsonPath("$.email").value("zhangsan@example.com"))
            .andExpect(jsonPath("$.address").value(notNullValue()));

        verify(customerService, times(1)).updateCustomer(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-12")
    void testUpdateCustomer_Failure_NoPermission() throws Exception {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .build();

        // When & Then
        mockMvc.perform(put("/api/customers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(customerService, never()).updateCustomer(anyLong(), any());
    }

    // ========== 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁逞屽墮缁犲秹宕瑰ú顏勬槬婵炴垶鈼ら崷顓涘亾閿濆骸骞樻俊?==========

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:delete"})
    @DisplayName("case-13")
    void testDeleteCustomer_Success() throws Exception {
        // Given
        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .code("CUST001")
            .name("闁诲孩顔栭崰姘叏瀹曞洨绠?")
            .isActive(false)  // 闂佸搫顦遍崕鎰板垂娴兼潙鍨傜憸鐗堝笚閳锋棃鏌曢崼婵囧櫤闁稿﹦鏁婚弻锝呂熼崹顔惧帿闂侀€炲苯鍘告俊鐐村笧閹?false
            .build();

        when(customerService.deleteCustomer(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(delete("/api/customers/1"))
            .andDo(print())
            .andExpect(status().isNoContent());

        verify(customerService, times(1)).deleteCustomer(1L);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"customer:view"})
    @DisplayName("case-14")
    void testDeleteCustomer_Failure_NoPermission() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/customers/1"))
            .andDo(print())
            .andExpect(status().isForbidden());

        verify(customerService, never()).deleteCustomer(anyLong());
    }

    // ========== 闂備礁鎼ˇ顖炲疮閺夋埈鐎舵繛宸簻缁犲磭鎲稿澶婃槬婵°倐鍋撻棁澶愭倵閿濆骸骞樻俊?==========

    @Test
    @WithMockUser(username = "sales", authorities = {"SALESPERSON", "customer:view"})
    @DisplayName("case-15")
    void testPermission_SalesCanView() throws Exception {
        // Given
        when(customerService.listCustomers()).thenReturn(Arrays.asList());

        // When & Then
        mockMvc.perform(get("/api/customers"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("case-16")
    void testPermission_AdminHasAllPermissions() throws Exception {
        // Given
        when(customerService.listCustomers()).thenReturn(Arrays.asList());

        // When & Then - 闂備礁婀遍。浠嬪磻閹剧粯鐓涢柛顐ｇ箥濡插綊鏌熸總鍛婃暠闁瑰嘲鎳庨…銊╁醇濠靛棗绠ｉ梺鍦帶閻°劌螞娓氣偓椤㈡艾顫濈捄铏诡吅闂佸綊鍋婇崜娆擄綖?
        mockMvc.perform(get("/api/customers"))
            .andExpect(status().isOk());
    }
}
