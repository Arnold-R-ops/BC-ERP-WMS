package com.wms.system.repository;

import com.wms.system.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomerRepository 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * V3.7 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥ鍡楀⒒闁告艾鍊块弻鐔煎箻椤曞懏顥栧銈嗘尰閹倿骞?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎?CustomerRepository 闂備焦鐪归崝宀€鈧凹鍘介弲璺侯吋婢跺﹤鐝樻繝銏ｆ硾妤犵鈻撴导瀛樺€甸悷娆忓閻擃垳绱掗悩闈涙灈鐎殿喖顭锋俊鐑解€﹂幋婵囩暠闂備礁鎼崐浠嬶綖婢跺本鍏滈柛顐ｆ礃閺?
 * 1. findByCode() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝堛€€閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎾舵閹捐鍐€妞ゆ劑鍊曢悘锝夋⒑閸濆嫯顫﹂柛搴㈡尦椤?
 * 2. existsByCode() - 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑濡炪倖鎸哥紞濠囩嵁鐎ｎ偒妲归幖杈剧稻閵囨繈姊哄ú璇叉灆闁绘帪绠戝嵄闁归棿绀佺憴锕傛煥閺囨浜鹃梺瀹︽澘濮傜€?
 * 3. findByIsActiveTrue() - 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閻庯綆鍓濈粈瀣攽閻愯泛钄肩€规洟娼ч埢鎾诲箣閻橆偄浜炬鐐茬仢閻忣亪鏌?
 * 4. findByNameContaining() - 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝堛€€閸嬫捇鐛崹顔句痪闂佺硶鏅滈惄顖氱暦閵夈儺鍚嬮柛銉ユ閸嬫稖鐏掗梺鎸庣☉鐎氼喛妫熼梻浣告惈鐞氼偊宕曢幘顕呮晪?
 *
 * 濠电偠鎻紞鈧繛澶嬫礋瀵?@DataJpaTest 婵犵數鍋涢ˇ顓㈠礉韫囨凹鏆伴梻?
 * - 闂備胶鍘ч〃搴㈢濠婂嫭鍙忛柍鍝勬噺閻撯偓閻庡箍鍎卞ú銊╁几?JPA 闂備胶鍎甸弲婵嬧€﹂崼銉ョ煑鐟滃繒妲愰幒妤€绀嬫い蹇撴媼閸?
 * - 婵犳鍣徊鐣屾崲鐎ｎ喗鐓傛繝濠傚幘閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锟犲磼濠垫劖缍堢紒缁㈠幖閻栧ジ鐛鍫▉濡炪們鍨洪崹鍨暦閵夛附鍎熼柕鍫濇噺椤斿洭姊洪崨濠冪厽闁告柨鑻叅闁哄诞宀€鍙嗗┑顔斤供閸嬪棝鎯冮幋锔界厱?
 * - 濠电偞鍨堕幐鍝ョ矓鐎涙ɑ鍙忛柣鎰摠婵粓鏌﹀Ο渚Ш闁哄棙宀搁弻鈩冩媴閸撴彃娈跺┑?Spring 濠电偞鍨堕幐鎼佹晝閿濆洨绠旈柛娑欐綑濡﹢鏌涢妷鈺婃缂佲偓閸曨垱鐓欐い鎴墮濡宕㈤幘顔界厸闁搞儯鍔嶇紞鎴︽煏閸粎鐭欓柡?
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("case-1")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Customer activeCustomer1;
    private Customer activeCustomer2;
    private Customer inactiveCustomer;

    /**
     * 婵犳鍣徊鐣屾崲鐎ｎ喗鐓傛繝濠傚幘閸︻厸鍋撻敐搴″箻婵″弶鎮傞弻锟犲磼濠垫劖缍堢紒缁㈠幖閻栧ジ鐛鍫▉濡炪們鍨洪崹鍨暦濠婂喚鍚嬮柛娑卞幘娴犲瓨绻濆▓鍨灈妞ゆ垵鎳愰埀顒€鐏氳ぐ鍐箒闁诲函缍嗛崢鎯ｉ幖浣圭厸濞达絽鎼。鑲┾偓?
     */
    @BeforeEach
    void setUp() {
        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鐐灱閺€浠嬫煃瑜滈崜娆戝弲闂佺厧顫曢崐鏍偩闁秵鍊垫鐐茬仢閻忣亪鏌?
        activeCustomer1 = Customer.builder()
                .code("CUST001")
                .name("Customer A")
                .contact("Alice")
                .phone("13800138001")
                .email("zhangsan@example.com")
                .address("Address A")
                .creditLimit(new BigDecimal("100000.00"))
                .isActive(true)
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鐐灱閺€浠嬫煃瑜滈崜娆戝弲闂佺厧顫曢崐鏍偩闁秵鍊垫鐐茬仢閻忣亪鏌?
        activeCustomer2 = Customer.builder()
                .code("CUST002")
                .name("Customer B")
                .contact("Bob")
                .phone("13800138002")
                .email("lisi@example.com")
                .address("Address B")
                .creditLimit(new BigDecimal("50000.00"))
                .isActive(true)
                .build();

        // 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯у閻霉閿濆洤鍔嬮柡鍡楃箻閺岋綁濡搁妷銉患濡炪倖鎸哥紞濠囩嵁?
        inactiveCustomer = Customer.builder()
                .code("CUST003")
                .name("Customer Inactive")
                .contact("Carol")
                .phone("13800138003")
                .email("wangwu@example.com")
                .address("Address C")
                .creditLimit(BigDecimal.ZERO)
                .isActive(false)
                .build();

        // 闂備礁缍婇弲鎻掝渻閹烘梻涓嶆繛鍡樻尭缁€宀勬煛瀹ュ啫濡块柕鍫熸尦閹綊宕堕妸锔绢槰闂佸搫妫涢崰鏍嵁?
        entityManager.persist(activeCustomer1);
        entityManager.persist(activeCustomer2);
        entityManager.persist(inactiveCustomer);
        entityManager.flush();
    }

    @Test
    @DisplayName("case-2")
    void testFindByCode_Success() {
        // When: 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅù锝堛€€閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎾舵閹捐鍐€妞ゆ劑鍊曢悘锝夋⒑閸濆嫯顫﹂柛搴㈡尦椤?
        Optional<Customer> found = customerRepository.findByCode("CUST001");

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧壕濂告煙閹屽殶濞?
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("CUST001");
        assertThat(found.get().getName()).isEqualTo("Customer A");
        assertThat(found.get().getContact()).isEqualTo("Alice");
        assertThat(found.get().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("case-3")
    void testFindByCode_NotFound() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈勮兌閳绘梻鈧箍鍎遍幊鎰板箺閻樼粯鐓曢柨鏂挎惈婵℃寧绻涢崼鐔风仼闁逞屽墮缁犲秹宕瑰ú顏勬槬婵炴垶姘ㄧ壕浠嬫煛瀹ュ骸浜為柛?
        Optional<Customer> found = customerRepository.findByCode("NONEXISTENT");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱?
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("case-4")
    void testExistsByCode_Exists() {
        // When: 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑闂佸憡鍩婄槐鏇㈠焵椤掍胶鈯曢柨姘節閳ь剟顢旈崼鐔峰壄闂佸憡娲﹂崑鎺楀触閸岀偞鐓欓柟缁㈠櫘濡垹绱掓鏍﹂偗闁?
        boolean exists = customerRepository.existsByCode("CUST001");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳?true
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("case-5")
    void testExistsByCode_NotExists() {
        // When: 婵犵妲呴崑鈧柛瀣崌閺岋紕浠︾拠鎻掑缂備焦顨呴ˇ閬嶅焵椤掍胶鈯曢柨姘節閳ь剟顢旈崼鐔峰壄闂佸憡娲﹂崑鎺楀触閸岀偞鐓欓柟缁㈠櫘濡垹绱掓鏍﹂偗闁?
        boolean exists = customerRepository.existsByCode("NONEXISTENT");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳?false
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("case-6")
    void testFindByIsActiveTrue() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞閻庯綆鍓濈粈瀣攽閻愯泛钄肩€规洟娼ч埢鎾诲箣閻橆偄浜炬鐐茬仢閻忣亪鏌?
        List<Customer> activeCustomers = customerRepository.findByIsActiveTrue();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?2 濠电偞鍨堕幖鈺傜濠靛牏绱﹂柛婵嗗▕濞差亝鍤掗柕鍫濇閺嗙娀鏌ｆ惔锛勭暛闁搞劍妞藉畷?
        assertThat(activeCustomers).hasSize(2);
        assertThat(activeCustomers)
                .extracting(Customer::getCode)
                .containsExactlyInAnyOrder("CUST001", "CUST002");
        assertThat(activeCustomers)
                .allMatch(Customer::getIsActive);
    }

    @Test
    @DisplayName("case-7")
    void testFindByNameContaining_WithKeyword() {
        // When: 婵犵妲呴崹鐣屾閺囩姵鍏滈柨鏇炲€搁拑鐔兼煏婢舵鍘涢柛銈呭閺屾盯寮借閸ｅ綊鏌?"婵犵數鍋炲娆擃敄閸儲鍎? 闂備焦鐪归崝宀€鈧凹鍓熼、妯裤亹閹烘垹顓洪悗鐟板婢ф宕愬畷鍥╃＜?
        List<Customer> customers = customerRepository.findByNameContaining("Customer");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顓奸梺璇″瀻閸愵亜甯?2 濠电偞鍨堕幖鈺傜濠婂牜鏁囩憸鐗堝笒缁?
        assertThat(customers).hasSize(3);
        assertThat(customers)
                .extracting(Customer::getCode)
                .containsExactlyInAnyOrder("CUST001", "CUST002", "CUST003");
    }

    @Test
    @DisplayName("case-8")
    void testFindByNameContaining_SpecificCustomer() {
        // When: 婵犵妲呴崹鐣屾閺囩姵鍏滈柨鏇炲€搁拑鐔兼煏婢舵鍘涢柛銈呭閺屾盯寮借閸ｅ綊鏌?"闂佽楠哥粻宥夊垂濞差亜鏄ユ慨? 闂備焦鐪归崝宀€鈧凹鍓熼、妯裤亹閹烘垹顓洪悗鐟板婢ф宕愬畷鍥╃＜?
        List<Customer> customers = customerRepository.findByNameContaining("Customer A");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣鐓戝銈呯箰閹冲酣藟濠靛鐓?1 濠电偞鍨堕幖鈺傜濠婂牜鏁囩憸鐗堝笒缁?
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).getCode()).isEqualTo("CUST001");
        assertThat(customers.get(0).getName()).isEqualTo("Customer A");
    }

    @Test
    @DisplayName("case-9")
    void testFindByNameContaining_InactiveCustomer() {
        // When: 婵犵妲呴崹鐣屾閺囩姵鍏滈柨鏇炲€搁拑鐔兼煏婢舵鍘涢柛銈呭閺屾盯寮借閸ｅ綊鏌?"缂傚倷绀侀崐鐑芥嚄閸洖鏋? 闂備焦鐪归崝宀€鈧凹鍓熼、妯裤亹閹烘垹顓洪悗鐟板婢ф宕愬畷鍥╃＜?
        List<Customer> customers = customerRepository.findByNameContaining("Inactive");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣顓奸梺璇″瀻閸愵亜甯?1 濠电偞鍨堕幖鈺傜濠婂牜鏁囩憸鐗堝笒缁狅綁鏌涢幋娆忊偓妤冪矆閸曨垱鐓曢柡宓嫬娅ら柣搴㈠嚬閸樺墽鍒掔€ｎ偅濯撮柣鎴炆戝▓銏ゆ⒑濮瑰洤濡奸悗姘煎墴椤㈡銇愰幒鎴狀吅闂佸憡鍨崐妤冪矆?
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).getCode()).isEqualTo("CUST003");
        assertThat(customers.get(0).getIsActive()).isFalse();
    }

    @Test
    @DisplayName("case-10")
    void testFindByNameContaining_NoMatch() {
        // When: 婵犵妲呴崹鐣屾閺囩姵鍏滈柨鏇炲€搁拑鐔兼煏婢舵鍘涢柛銈呭閳藉骞樺畷鍥嗐垽鏌ｆ幊閸旀垵鐣烽悜钘壩╃憸搴ㄦ偩闁秵鐓曢煫鍥ㄦ煥閳绘洟鏌℃笟鍥у箻闁?
        List<Customer> customers = customerRepository.findByNameContaining("NO_MATCH");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩椤撶喍姘﹂梺鍝勫€圭€笛呯矆閳ь剛绱撴担姝屽闁圭⒈鍋婂畷褰掝敂閸℃ê浠?
        assertThat(customers).isEmpty();
    }

    @Test
    @DisplayName("case-11")
    void testSaveNewCustomer() {
        // Given: 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙濡﹢鏌℃径瀣靛劌闁告艾鍊块弻?
        Customer newCustomer = Customer.builder()
                .code("CUST004")
                .name("Customer D")
                .contact("David")
                .phone("13800138004")
                .email("zhaoliu@example.com")
                .address("Address D")
                .creditLimit(new BigDecimal("80000.00"))
                .isActive(true)
                .build();

        // When: 濠电儑绲藉ú锔炬崲閸岀偞鍋ら柕濞р偓閸嬫捇鐛崹顔句痪闂?
        Customer saved = customerRepository.save(newCustomer);

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勭€ｎ兘鏀冲┑鐘绘涧濡盯骞楅悩缁樼厵閻庢稒锚婵洤鈹?
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When: 闂傚倷鐒﹁ぐ鍐矓閻㈢钃熷┑鐘叉搐閽冪喖鏌曟径妯煎帥闁?
        Optional<Customer> found = customerRepository.findByCode("CUST004");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩閼搁潧娈戦梺鍏间航閸庨亶藟濠靛鐓?
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Customer D");
    }

    @Test
    @DisplayName("case-12")
    void testUpdateCustomer() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬫繈鏌ｅΔ鈧悧濠勭不閹烘鍊垫鐐茬仢閻忣亪鏌?
        Customer customer = customerRepository.findByCode("CUST001").orElseThrow();

        // When: 濠电儑绲藉ù鍌炲窗濡ゅ懎鏋侀柤娴嬫杹閸嬫捇鐛崹顔句痪闂佺硶鏅滅粙鎺旂矚闁秴鐒洪柛鎰屽懐顦?
        customer.setName("Customer A Updated");
        customer.setContact("Alice Updated");
        customer.setCreditLimit(new BigDecimal("150000.00"));
        customerRepository.save(customer);

        // 婵犵數鍋為幐鎼佸箠濡　鏋嶉幖娣妼缁犳澘霉閿濆妫戦柣锝勭矙閺屾盯寮介妸褍鈪剁紓浣诡殔閸婂湱绮欐径灞稿亾閿濆骸浜濋柣搴櫍閺屻劌鈽夊Ο鍨伃閻庤鎮傛禍璺虹暦濮樿泛閱囬柡鍥╁仦椤忕喖姊洪崫鍕偓缁樻櫠濡ゅ懏鍋傞柨娑樺鐎?
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒€宓嗛柣搴㈢⊕钃遍柣鎾亾闂備胶鎳撻悺銊╁礉閺囩喐鍙?
        Customer reloaded = customerRepository.findByCode("CUST001").orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Customer A Updated");
        assertThat(reloaded.getContact()).isEqualTo("Alice Updated");
        assertThat(reloaded.getCreditLimit()).isEqualByComparingTo("150000.00");
    }

    @Test
    @DisplayName("case-13")
    void testDisableCustomer() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉戔偓閺€浠嬫煃瑜滈崜娆戝弲闂佺厧顫曢崐鏍偩闁秵鍊垫鐐茬仢閻忣亪鏌?
        Customer customer = customerRepository.findByCode("CUST001").orElseThrow();
        assertThat(customer.getIsActive()).isTrue();

        // When: 缂傚倷绀侀崐鐑芥嚄閸洖鏋侀柕鍫濇礌閸嬫捇鐛崹顔句痪闂?
        customer.setIsActive(false);
        customerRepository.save(customer);
        entityManager.flush();
        entityManager.clear();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勫畝鈧惌澶娒归敐鍥у妺闁哄棗绻橀弻鐔衡偓娑櫭慨鍥р攽?
        Customer reloaded = customerRepository.findByCode("CUST001").orElseThrow();
        assertThat(reloaded.getIsActive()).isFalse();

        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈傚亾闁宠鍨块崺鈧い鎺嗗亾閻撱倝鏌ゆ慨鎰偓鏍偩闁秵鍊垫鐐茬仢閻忣亪鏌?
        List<Customer> activeCustomers = customerRepository.findByIsActiveTrue();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠鸿櫣鐓戝銈呯箰閹冲繒绮?1 濠电偞鍨堕幖鈺傜濠靛牏绱﹂柛婵嗗▕濞差亝鍤掗柕鍫濇閺嗙娀鏌ｆ惔锛勭暛闁搞劍妞藉畷?
        assertThat(activeCustomers).hasSize(1);
        assertThat(activeCustomers.get(0).getCode()).isEqualTo("CUST002");
    }

    @Test
    @DisplayName("case-14")
    void testDeleteCustomer() {
        // Given: 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬫繈鏌ｅΔ鈧悧濠勭不閹烘鍊垫鐐茬仢閻忣亪鏌?
        Customer customer = customerRepository.findByCode("CUST003").orElseThrow();
        Long customerId = customer.getId();

        // When: 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓熸崳闁逞屽墮缁犲秹宕瑰ú顏勬槬?
        customerRepository.delete(customer);
        entityManager.flush();

        // Then: 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤槴闂佺粯顭囩划顖炴偟閻斿吋鐓欓悗娑櫭慨鍥р攽?
        Optional<Customer> found = customerRepository.findById(customerId);
        assertThat(found).isEmpty();

        // When: 闂備礁鎲￠崝鏇犵矓閻㈠壊鏁冮柤娴嬫櫃閻掑﹥绻濋棃娑冲姛婵″弶鎮傞弻锛勪沪鐠囨彃濮ゅ?
        Optional<Customer> foundByCode = customerRepository.findByCode("CUST003");

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠洪缚鎽曢梺闈涱樈閻撳牓宕甸崟顐熸闁规儳纾瓭闂?
        assertThat(foundByCode).isEmpty();
    }

    @Test
    @DisplayName("case-15")
    void testCountCustomers() {
        // When: 缂傚倸鍊烽懗鍫曞窗閺囥埄鏁囬柟闂寸缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞濠电姴楠搁幃鍛存⒑?
        long count = customerRepository.count();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?3 濠电偞鍨堕幖鈺傜濠婂牜鏁囩憸鐗堝笒缁?
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("case-16")
    void testFindAll() {
        // When: 闂備礁鎼悮顐﹀磿閹绢噮鏁嬫俊銈呮噹缁犮儵鏌嶈閸撶喎顕ｉ崹顐㈢窞濠电姴楠搁幃鍛存⒑?
        List<Customer> allCustomers = customerRepository.findAll();

        // Then: 闂佸湱鍘ч悺銊ノ涙笟鈧、姘潩鐠哄搫鐝?3 濠电偞鍨堕幖鈺傜濠婂牜鏁囩憸鐗堝笒缁?
        assertThat(allCustomers).hasSize(3);
        assertThat(allCustomers)
                .extracting(Customer::getCode)
                .containsExactlyInAnyOrder("CUST001", "CUST002", "CUST003");
    }
}
