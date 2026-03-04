package com.wms.system.service;

import com.wms.system.dto.customer.CreateCustomerRequest;
import com.wms.system.dto.customer.CustomerResponse;
import com.wms.system.entity.Customer;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CustomerService 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * V4.1 闂佸搫顑堥崺鏍倵椤栫偞鏅慨姗嗗墻閸氬倿鏌熼幘顕呮妞ゆ挻鎮傞幃鍫曞幢濡崵鐤€闂佸憡鏌￠崜婵堢矈鐎靛憡瀚?
 *
 * 濠电偞娼欓鍫ユ儊椤栨粍鍟洪柛鈩冪懄绾句即鏌?
 * 1. 闂佸憡甯楃粙鎴犵磽閹惧灈鍋撻獮鍨仾闁糕晜鐩弫宥夊醇閿濆懏顔囬梺鍛婃煟閸斿繘顢欓弴鐘电＞妞ゆ棁鍋愮粔娲倶閻愰潧浠掓慨鐟邦樀閺?
 * 2. 闂佸搫琚崕鎾敋濡ゅ啠鍋撻獮鍨仾闁糕晜鐩畷姘旈崟鈹惧亾閸愵喗鏅柛顐秵閺€鐣岀磼閻欐瑥娲﹂鍓х磼閸屾瑧涓茬紒?
 * 3. 闂佸搫琚崕鎾敋濡も偓閳规垿鍩€椤掆偓鐓ら悹鍥ф▕閸氬倿鏌熼弶鎴濇缂佽鲸鐟ч幃鏉跨暆閳ь剚顨ラ崶顒佲挄闁哄啫鍋嗛悗鏌ユ煥?
 * 4. 闂佸吋鍎抽崲鑼躲亹閸モ斁鍋撻獮鍨仾闁糕晜绋撻幏鐘绘晜閽樺澹堥梺鎸庣☉閻楀棙鎱ㄩ埡鍛畱濞达綀妫勬慨搴ㄦ煛娴ｆ悂鐛滅紒?
 * 5. 闂佸搫娲ら悺銊╁蓟婵犲嫧鍋撻獮鍨仾闁糕晜鐩弫宥夊醇濠婂嫬寮块梺琛″亾闂侇偅绋撶粻浠嬫煙闊彃鈧呮?
 * 6. 闂佸憡甯炴繛鈧繛鍛捣閳ь剙绠嶉崹娲春濞戙垺鏅柛顐秬閹奉偊鏌涢幒鏂库枅婵炲懎閰ｉ弫?
 * 7. 闁荤喐鐟︾敮鐔哥珶婵犲洤绾ч柛鎰靛枟椤庢瑩鏌涢幒瀣偓鏍蓟?
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Customer Data Security)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CustomerService customerService;

    private User testUser;
    private SecurityUser securityUser;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鏌ｉ～顒€濡介柛?
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .password("password")
            .build();

        securityUser = new SecurityUser(testUser);

        // 闂佸憡甯楃粙鎴犵磽閹捐秮鍦偓锝庡幘濡叉悂鎮楅獮鍨仾闁?
        testCustomer = Customer.builder()
            .id(1L)
            .code("CUST001")
            .name("閻庢鍠氭慨宕囩箔?")
            .contact("闂佸搫顦绋棵?")
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻忓浚鍨伴娆撳传閸曨剛顩柣?8闂?")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .ownerId(1L)
            .build();
        testCustomer.setCreatedAt(LocalDateTime.now());
        testCustomer.setUpdatedAt(LocalDateTime.now());
    }

    // ========== 闂佸憡甯楃粙鎴犵磽閹惧灈鍋撻獮鍨仾闁糕晜绋戦湁閻庯綆鍘惧Σ?==========

    @Test
    @DisplayName("case-2")
    void testCreateCustomer_Success_AutoSetOwner() {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("閻庢鍠氭慨宕囩箔?")
            .contact("闂佸搫顦绋棵?")
            .phone("13912345678")
            .email("zhangsan@example.com")
            .address("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻忓浚鍨伴娆撳传閸曨剛顩柣?8闂?")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .build();

        // Mock Security Context
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SALESPERSON")));

        when(customerRepository.existsByCode("CUST001")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        // When
        CustomerResponse response = customerService.createCustomer(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("CUST001");
        assertThat(response.getName()).isEqualTo("閻庢鍠氭慨宕囩箔?");

        // 婵°倗濮撮惌渚€鎯佹径瀣攳婵犻潧妫涢幗鐘绘煛閸愨晛鍔舵い鏃€娲滅槐鏃堫敊閼姐倕鏅?ownerId
        verify(customerRepository).save(argThat(customer ->
            customer.getOwnerId() != null && customer.getOwnerId().equals(1L)
        ));
    }

    @Test
    @DisplayName("case-3")
    void testCreateCustomer_Failure_CodeExists() {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("閻庢鍠氭慨宕囩箔?")
            .build();

        when(customerRepository.existsByCode("CUST001")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> customerService.createCustomer(request))
            .isInstanceOf(BusinessException.class);

        verify(customerRepository, never()).save(any(Customer.class));
    }

    // ========== 闂佸搫琚崕鎾敋濡ゅ啠鍋撻獮鍨仾闁糕晜鐩畷姘旈崟鈹惧亾閸愵煁鍦偓锝庡幘濡叉悂鏌ㄥ☉妯煎ⅲ妞ゃ垺鍨归惀顏囶樄婵炲爜鍛焿濞村吋鐟х粈?=========

    @Test
    @DisplayName("case-4")
    void testListCustomers_SalesRole_OnlyOwnCustomers() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SALESPERSON")));

        List<Customer> ownCustomers = Arrays.asList(testCustomer);
        when(customerRepository.findByOwnerId(1L)).thenReturn(ownCustomers);

        // When
        List<CustomerResponse> responses = customerService.listCustomers();

        // Then
        assertThat(responses).hasSize(1);
        verify(customerRepository).findByOwnerId(1L);
        verify(customerRepository, never()).findAll();

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锔锯偓瑙勭摃鐏忣亪宕濆璺烘瀬?
        CustomerResponse response = responses.get(0);
        assertThat(response.getName()).isEqualTo("閻?");
        assertThat(response.getPhone()).isEqualTo("139****5678");
        assertThat(response.getEmail()).isEqualTo("z***@example.com");
        assertThat(response.getAddress()).isEqualTo("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻?**");
    }

    @Test
    @DisplayName("case-5")
    void testListCustomers_AdminRole_AllCustomers() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SUPER_ADMIN")));

        List<Customer> allCustomers = Arrays.asList(testCustomer);
        when(customerRepository.findAll()).thenReturn(allCustomers);

        // When
        List<CustomerResponse> responses = customerService.listCustomers();

        // Then
        assertThat(responses).hasSize(1);
        verify(customerRepository).findAll();
        verify(customerRepository, never()).findByOwnerId(anyLong());

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傛煛閸偂娴烽柛鏃€宀稿?
        CustomerResponse response = responses.get(0);
        assertThat(response.getName()).isEqualTo("閻庢鍠氭慨宕囩箔?");
        assertThat(response.getPhone()).isEqualTo("13912345678");
        assertThat(response.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(response.getAddress()).isEqualTo("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻忓浚鍨伴娆撳传閸曨剛顩柣?8闂?");
    }

    @Test
    @DisplayName("case-6")
    void testListCustomers_ChairmanRole_AllCustomers() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("CHAIRMAN")));

        List<Customer> allCustomers = Arrays.asList(testCustomer);
        when(customerRepository.findAll()).thenReturn(allCustomers);

        // When
        List<CustomerResponse> responses = customerService.listCustomers();

        // Then
        assertThat(responses).hasSize(1);
        verify(customerRepository).findAll();

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傛煛閸偂娴烽柛鏃€宀稿?
        CustomerResponse response = responses.get(0);
        assertThat(response.getName()).isEqualTo("閻庢鍠氭慨宕囩箔?");
    }

    // ========== 闂佸搫琚崕鎾敋濡も偓閳规垿鍩€椤掆偓鐓ら悹鍥ф▕閸氬倿鏌熼幘顔芥暠缂佷礁顕幏鐘诲即椤忓棛顦╅柣鐐寸☉閻吋顨ラ崶顒佲挄闁哄啫鍋嗛悗鏌ユ煥?=========

    @Test
    @DisplayName("case-7")
    void testListActiveCustomers_SalesRole_OnlyOwnActiveCustomers() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SALESPERSON")));

        List<Customer> ownActiveCustomers = Arrays.asList(testCustomer);
        when(customerRepository.findByOwnerIdAndIsActiveTrue(1L)).thenReturn(ownActiveCustomers);

        // When
        List<CustomerResponse> responses = customerService.listActiveCustomers();

        // Then
        assertThat(responses).hasSize(1);
        verify(customerRepository).findByOwnerIdAndIsActiveTrue(1L);
        verify(customerRepository, never()).findByIsActiveTrue();

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锔锯偓瑙勭摃鐏忣亪宕濆璺烘瀬?
        CustomerResponse response = responses.get(0);
        assertThat(response.getName()).isEqualTo("閻?");
    }

    @Test
    @DisplayName("case-8")
    void testListActiveCustomers_AdminRole_AllActiveCustomers() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("WAREHOUSE_ADMIN")));

        List<Customer> allActiveCustomers = Arrays.asList(testCustomer);
        when(customerRepository.findByIsActiveTrue()).thenReturn(allActiveCustomers);

        // When
        List<CustomerResponse> responses = customerService.listActiveCustomers();

        // Then
        assertThat(responses).hasSize(1);
        verify(customerRepository).findByIsActiveTrue();

        // 婵°倗濮撮惌渚€鎯佹径鎰瀬闁绘鐗嗙粊锕傛煛閸偂娴烽柛鏃€宀稿?
        CustomerResponse response = responses.get(0);
        assertThat(response.getName()).isEqualTo("閻庢鍠氭慨宕囩箔?");
    }

    // ========== 闂佸吋鍎抽崲鑼躲亹閸モ斁鍋撻獮鍨仾闁糕晜绋撻幏鐘绘晜閽樺澹堝┑鐐存綑椤戝牓鎯侀鐐存櫖闁割偅绻傝闂佽鍏涘ù鍥礉濮樿泛鏋侀煫鍥ュ劤缁€?=========

    @Test
    @DisplayName("case-9")
    void testGetCustomer_SalesRole_MaskedData() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SALESPERSON")));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        // When
        CustomerResponse response = customerService.getCustomer(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("閻?");
        assertThat(response.getPhone()).isEqualTo("139****5678");
        assertThat(response.getEmail()).isEqualTo("z***@example.com");
        assertThat(response.getAddress()).isEqualTo("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻?**");
    }

    @Test
    @DisplayName("case-10")
    void testGetCustomer_AdminRole_OriginalData() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SUPER_ADMIN")));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        // When
        CustomerResponse response = customerService.getCustomer(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("閻庢鍠氭慨宕囩箔?");
        assertThat(response.getPhone()).isEqualTo("13912345678");
        assertThat(response.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(response.getAddress()).isEqualTo("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻忓浚鍨伴娆撳传閸曨剛顩柣?8闂?");
    }

    @Test
    @DisplayName("case-11")
    void testGetCustomer_Failure_NotFound() {
        // Given
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> customerService.getCustomer(1L))
            .isInstanceOf(BusinessException.class);
    }

    // ========== 闂佸搫娲ら悺銊╁蓟婵犲嫧鍋撻獮鍨仾闁糕晜绋戦湁閻庯綆鍘惧Σ鎼佹煥濞戞澧旈柟渚垮姂瀵劑鏌呭☉姘鳖啍闂佺缈伴崐褏妲?=========

    @Test
    @DisplayName("case-12")
    void testUpdateCustomer_Success_NormalUpdate() {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("閻庢鍠氭慨宕囩箔娓氣偓瀵鈧稒蓱閻撯偓")
            .contact("闂佸搫顦绋棵洪幘璇插嵆閻庢稒蓱閻撯偓")
            .phone("13900000000")
            .email("new@example.com")
            .address("婵炴垶鎸搁敃銉╁箲閿濆洦鏆滈柛灞剧洴閸忓繐鈽夐幘瀵哥煁闁哄苯锕畷鐘虫姜閹殿喚鎳愮紓浣鸿檸娴滄粓濡甸崶顒佺劶?闂?")
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .build();

        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SUPER_ADMIN")));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        // When
        CustomerResponse response = customerService.updateCustomer(1L, request);

        // Then
        assertThat(response).isNotNull();
        verify(customerRepository).save(argThat(customer ->
            customer.getPhone().equals("13900000000") &&
            customer.getEmail().equals("new@example.com") &&
            customer.getAddress().equals("婵炴垶鎸搁敃銉╁箲閿濆洦鏆滈柛灞剧洴閸忓繐鈽夐幘瀵哥煁闁哄苯锕畷鐘虫姜閹殿喚鎳愮紓浣鸿檸娴滄粓濡甸崶顒佺劶?闂?")
        ));
    }

    @Test
    @DisplayName("case-13")
    void testUpdateCustomer_EditProtection_IgnoreMaskedFields() {
        // Given
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .code("CUST001")
            .name("閻庢鍠氭慨宕囩箔娓氣偓瀵鈧稒蓱閻撯偓")
            .contact("闂佸搫顦绋棵洪幘璇插嵆閻庢稒蓱閻撯偓")
            .phone("139****5678")  // 闂佸憡鐗曢幊搴ㄥ箚閸儱绠抽柍鍝勫暟閸?
            .email("z***@example.com")  // 闂佸憡鐗曢幊搴ㄥ箚閸儱绠抽柍鍝勫暟閸?
            .address("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻?**")  // 闂佸憡鐗曢幊搴ㄥ箚閸儱绠抽柍鍝勫暟閸?
            .creditLimit(new BigDecimal("200000.00"))
            .isActive(true)
            .build();

        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SALESPERSON")));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        // When
        CustomerResponse response = customerService.updateCustomer(1L, request);

        // Then
        assertThat(response).isNotNull();

        // 婵°倗濮撮惌渚€鎯佹径鎰闁告侗鍘介崕鎾绘煙鐞涒剝娅囬柣鏍ㄧ矒閹啴宕熼銈嗘喕濠电偛鐗嗛悘姘耿椤撶姵鍋栨い鎰剁稻缁绢垶鏌?
        verify(customerRepository).save(argThat(customer ->
            customer.getPhone().equals("13912345678") &&  // 婵烇絽娲︾换鍐偓鍨瀹曘垽鎮㈢亸浣镐壕?
            customer.getEmail().equals("zhangsan@example.com") &&  // 婵烇絽娲︾换鍐偓鍨瀹曘垽鎮㈢亸浣镐壕?
            customer.getAddress().equals("闂佸憡鐗楅妵鐐哄触椤愩倖鏆滈柛灞剧⊕缁″倿姊婚崘銊ユ毐閻忓浚鍨伴娆撳传閸曨剛顩柣?8闂?")  // 婵烇絽娲︾换鍐偓鍨瀹曘垽鎮㈢亸浣镐壕?
        ));
    }

    // ========== 闂佸憡甯炴繛鈧繛鍛捣閳ь剙绠嶉崹娲春濞戞◤鍦偓锝庡幘濡叉悂鏌ㄥ☉妯煎ⅲ闁藉倸顑夊畷姘舵偐缂佹褰戦梺?=========

    @Test
    @DisplayName("case-14")
    void testDeleteCustomer_Success_SoftDelete() {
        // Given
        mockSecurityContext(1L, Arrays.asList(new SimpleGrantedAuthority("SUPER_ADMIN")));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        // When
        CustomerResponse response = customerService.deleteCustomer(1L);

        // Then
        assertThat(response).isNotNull();
        verify(customerRepository).save(argThat(customer ->
            customer.getIsActive().equals(false)
        ));
    }

    // ========== 闁哄鐗嗛幊搴㈡叏椤忓牆妫橀悷娆忓閵?==========

    /**
     * Mock Security Context
     */
    private void mockSecurityContext(Long userId, Collection<? extends GrantedAuthority> authorities) {
        User user = User.builder()
            .id(userId)
            .username("testuser")
            .password("password")
            .build();

        SecurityUser securityUser = new SecurityUser(user);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(securityUser);
        when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

        SecurityContextHolder.setContext(securityContext);
    }
}
