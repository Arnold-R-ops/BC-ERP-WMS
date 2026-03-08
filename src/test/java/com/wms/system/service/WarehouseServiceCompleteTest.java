package com.wms.system.service;

import com.wms.system.entity.Warehouse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WarehouseService 闂傚倷娴囬褍霉閻戣棄绠犻柟鎹愵嚙鐎氬銇勯幒鎴濐仼婵鐓￠弻銊モ攽閸♀晜笑缂佺虎鍘搁崑鎾绘⒒娴ｅ憡鎯堟繛灞傚姂瀹曟垵鈽夊顓熸濠德板€撻懗鍓佺不妤ｅ啯鐓涘璺侯儏閻掗箖鏌涙惔銏″磳闁?
 *
 * 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁稿骸锕ラ妵鍕冀椤愵澀娌梺鎼炲€栭崹鍧楀蓟濞戙垹绠涢柍杞扮椤绱撴担楦挎闁搞劌鐖煎?
 * 1. 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箚瑜滈崵鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈銊ノ熺粙鍨潎闂佸搫琚崝宀勫煡婢跺á鐔兼偂鎼搭喗婢戦梻鍌欑劍閹爼宕濆鍛殕缂佸娉曢弳?缂傚倸鍊搁崐鎼佸磹閹间礁纾圭憸鐗堝笚閸嬪绻濇繝鍌滃闁稿顦扮换婵囩節閸屾粌顤€闂佺粯鎸搁崐濠氬焵椤掆偓缁犲秹宕曢柆宥呯閻庯綆鈧叏绲剧换婵嗩潩椤撶姴寮?
 * 2. 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢埥澶愬箼閸愨晜鍎為梻鍌氬€风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿婵☆垳顭堟慨鍌炴煛鐏炶濡奸柍钘夘槸铻ｉ柣鎾冲瘨閺夋悂姊绘担瑙勫仩闁告柨绉撮悾婵堢矙鐠恒劍娈?濠电姷鏁搁崑鐐哄垂閸洖绠伴柛婵勫劤閻捇鏌熺紒銏犳灍闁稿鍊濋弻鏇熺箾閻愵剚鐝旈梺鍦攰閸╂牠濡甸崟顖ｆ晣闁绘棃顥撻鍌滅磽娴ｆ彃浜?
 * 3. 闂傚倸鍊风粈渚€骞栭銈囩煋闁绘垶鏋荤紞鏍ь熆鐠虹尨鍔熼柡鍡愬€曢湁闁挎繂鐗婇鐘电棯閸欍儳鐭欓柡灞糕偓鎰佸悑閹肩补鈧尙鏁栭梻浣规偠閸斿秵绻涙繝鍥ц摕闁挎稑瀚▽顏堟偣閸ャ劌绲诲┑顔哄€曢—鍐Χ閸℃顦ㄥ銈冨妼濡稓鍒掔€ｎ喖绠虫俊銈傚亾缂佺姾宕甸埀顒冾潐濞叉牕煤閿曞倹鍎嶉柟杈鹃檮閳锋垿鏌熺粙鎸庢崳闁宠棄顦甸弻锝呂旈埀顒勬晝椤忓嫮鏆﹂柣鐔煎亰濞尖晠鎮规ウ鎸庮仩妞?濠电姷鏁搁崑鐐哄垂閸洖绠伴柛婵勫劤閻捇鏌熺紒銏犳灍闁稿鍊濋弻鏇熺箾閻愵剚鐝旈梺鍦攰閸╂牠濡甸崟顖ｆ晣闁绘棃顥撻鍌滅磽娴ｆ彃浜?
 * 4. 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崨顔兼缂備胶濮伴崕鐢稿蓟瀹ュ牜妾ㄩ梺鍛婃尵閸犲酣顢氶敐澶婂瀭妞ゆ劑鍨荤粣鐐烘煟鎼搭垳绉甸柛鎾寸箘缁牏鈧綆浜栭弨浠嬫煟閹邦垱纭鹃柦鍕亹閳ь剝顫夊ú鐔奉焽瑜旈崺銏ゅ箻鐠囪尙顓洪梺缁樺姈椤旀牕霉閸曨垱鈷戦悷娆忓閸斻倗鐥紒銏犲箹妞?
 * 5. 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崨顔兼缂備胶濮伴崕鐢稿蓟瀹ュ牜妾ㄩ梺鍛婃尵閸犲酣顢氶敐澶婂瀭妞ゆ劑鍨荤粣鐐寸節閵忥絾纭鹃柡鍫墰娴滅鈹戠€ｎ偆鍘?
 * 6. 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷戦悢灏佹斀闁绘ɑ褰冮弳鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈銊ノ熺粙鍨瀳闁兼寧鍔欓弻娑㈠Ψ椤栨粌鍩屾繝鈷€鍕祷闂囧鏌ｉ幘鍐差劉闁搞倕娲弻鈩冩媴閸濄儛褏鈧娲滈崢褔鍩為幋锕€骞㈡俊鐐插⒔缁€瀣⒒閸屾艾鈧娆㈤敓鐘茬獥闁规崘顕х粈澶屸偓鍏夊亾闁告洦鍓欐禒閬嶆偡濠婂啰效闁?闂傚倸鍊搁崐椋庢閿熺姴绐楅柟鎹愵嚙缁€澶屸偓鍏夊亾闁告洦鍋勯悗顓㈡煙閼圭増褰х紒鍙夋そ瀹曟垿骞橀弬銉︻潔濠电偛妫欒摫闁伙絽鍢查—鍐Χ閸℃鍙嗛梺娲诲幖閸婂灝顕?
 * 7. 婵犵數濮烽弫鍛婃叏閻戝鈧倹绂掔€ｎ亞锛涢梺瑙勫劤椤曨厾绮绘ィ鍐╃厾濠靛倸澹婇弶娲煕閻愯埖顏犵紒杈ㄥ浮椤㈡瑩宕崟顐€撮梻?
 * 8. 闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁糕剝鐟ラ崹婵嬪箹濞ｎ剙濡奸柡瀣╃窔閺屾洟宕煎┑鍥舵￥濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿?
 * 9. 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ閿濆懎顬嬪銈傛櫇閸忔﹢寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿婵☆垳顭堟慨鍌炴煛瀹€瀣М濠碘剝鎮傛俊鐑藉Ψ椤旂粯鍋呯紓鍌氬€烽梽宥夊礉韫囨柣浠堥柟闂寸缁狀垶鏌涘☉鍗炲季闁告艾顑夐弻娑㈠焺閸忊晜鍨块、娆忣吋婢跺鎷?
 * 10. 闂傚倷绀侀幖顐λ囬鐐村亱闁糕剝顨愰懓鍧楁⒑椤掆偓缁夋挳鎮块悙顑句簻闁规澘澧庨悾杈╃磼閻樺啿鍝洪柡灞剧☉閳藉宕￠悙鑼啇闂備礁鎼鍛村嫉椤掑倹宕叉繛鎴欏灩缁狙囨煙閹碱厼骞栭柛鎾舵暬濮?
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class WarehouseServiceCompleteTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse testWarehouse1;
    private Warehouse testWarehouse2;
    private Warehouse inactiveWarehouse;

    @BeforeEach
    void setUp() {
        testWarehouse1 = Warehouse.builder()
            .id(1L)
            .code("WH01")
            .name("Warehouse One")
            .address("Address One")
            .contact("闂備浇顕х€涒晠顢欓弽顓炵獥婵﹩鍘介崣蹇曗偓瑙勬礀濞层劎绮?13800138000")
            .isActive(true)
            .build();

        testWarehouse2 = Warehouse.builder()
            .id(2L)
            .code("WH02")
            .name("Warehouse Two")
            .address("Address Two")
            .contact("闂傚倸鍊风粈渚€骞栭位鍥敍濞戣鲸顔旂紓浣割儐閿?13900139000")
            .isActive(true)
            .build();

        inactiveWarehouse = Warehouse.builder()
            .id(3L)
            .code("WH03")
            .name("Warehouse Three")
            .address("Address Three")
            .contact("闂傚倸鍊烽懗鍓佸垝椤栨粌鍨濈€光偓閸曞灚鏅為梺鍛婃处閸欌偓濠?13700137000")
            .isActive(false)
            .build();
    }

    // ========== 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎箚瑜滈崵鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈銊ノ熺粙鍨瀴缂備胶绮惄顖氼嚕閸洖绠ｆい鎾跺仜婢瑰秹姊?==========

    @Test
    @DisplayName("case-2")
    void createWarehouse_Success() {
        // Given
        when(warehouseRepository.existsByCode("WH04")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.createWarehouse(
            "WH04", "Warehouse Four", "Address Four", "13600136000"
        );
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);

        // Verify save was called with correct parameters
        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(captor.capture());
        Warehouse savedWarehouse = captor.getValue();
        assertThat(savedWarehouse.getCode()).isEqualTo("WH04");
        assertThat(savedWarehouse.getName()).isEqualTo("Warehouse Four");
        assertThat(savedWarehouse.getAddress()).isEqualTo("Address Four");
        assertThat(savedWarehouse.getContact()).isEqualTo("13600136000");
        assertThat(savedWarehouse.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("case-3")
    void createWarehouse_CodeAlreadyExists() {
        // Given
        when(warehouseRepository.existsByCode("WH01")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> warehouseService.createWarehouse(
            "WH01", "Existing Warehouse", null, null
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_ALREADY_EXISTS);

        // Verify save was never called
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("case-4")
    void createWarehouse_OptionalFieldsNull() {
        // Given
        when(warehouseRepository.existsByCode("WH05")).thenReturn(false);
        Warehouse savedWarehouse = Warehouse.builder()
            .id(5L)
            .code("WH05")
            .name("Warehouse Five")
            .address(null)
            .contact(null)
            .isActive(true)
            .build();
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(savedWarehouse);

        // When
        Warehouse result = warehouseService.createWarehouse("WH05", "Warehouse Five", null, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAddress()).isNull();
        assertThat(result.getContact()).isNull();
    }

    // ========== 闂傚倸鍊风粈渚€骞栭銈嗗仏妞ゆ劧绠戠壕鍧楁煙缂併垹娅橀柡浣割儐娣囧﹪濡堕崟顔煎帯濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿婵☆垳顭堟俊鐣岀磼缂佹娲寸€殿喖鐖奸獮瀣敇閻愭彃顥掗梻?==========

    @Test
    @DisplayName("case-5")
    void getWarehouseById_Success() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));

        // When
        Warehouse result = warehouseService.getWarehouseById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("WH01");
        assertThat(result.getName()).isEqualTo("Warehouse One");
    }

    @Test
    @DisplayName("case-6")
    void getWarehouseById_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.getWarehouseById(999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("case-7")
    void getWarehouseByCode_Success() {
        // Given
        when(warehouseRepository.findByCode("WH01")).thenReturn(Optional.of(testWarehouse1));

        // When
        Warehouse result = warehouseService.getWarehouseByCode("WH01");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("WH01");
        assertThat(result.getName()).isEqualTo("Warehouse One");
    }

    @Test
    @DisplayName("case-8")
    void getWarehouseByCode_NotFound() {
        // Given
        when(warehouseRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.getWarehouseByCode("INVALID"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("case-9")
    void getAllActiveWarehouses_Success() {
        // Given
        List<Warehouse> activeWarehouses = Arrays.asList(testWarehouse1, testWarehouse2);
        when(warehouseRepository.findAllActive()).thenReturn(activeWarehouses);

        // When
        List<Warehouse> result = warehouseService.getAllActiveWarehouses();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(testWarehouse1, testWarehouse2);
        assertThat(result).allMatch(Warehouse::getIsActive);
    }

    @Test
    @DisplayName("case-10")
    void getAllActiveWarehouses_EmptyList() {
        // Given
        when(warehouseRepository.findAllActive()).thenReturn(Collections.emptyList());

        // When
        List<Warehouse> result = warehouseService.getAllActiveWarehouses();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("case-11")
    void getAllWarehouses_Success() {
        // Given
        List<Warehouse> allWarehouses = Arrays.asList(testWarehouse1, testWarehouse2, inactiveWarehouse);
        when(warehouseRepository.findAll()).thenReturn(allWarehouses);

        // When
        List<Warehouse> result = warehouseService.getAllWarehouses();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).contains(testWarehouse1, testWarehouse2, inactiveWarehouse);
    }

    // ========== 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷戦悢灏佹斀闁绘ɑ褰冮弳鐔搞亜閳哄啫鍘撮柡宀€鍠栭獮鍡氼槾闁挎稑绉甸幈銊ノ熺粙鍨瀴缂備胶绮惄顖氼嚕閸洖绠ｆい鎾跺仜婢瑰秹姊?==========

    @Test
    @DisplayName("case-12")
    void updateWarehouse_AllFields() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.updateWarehouse(
            1L, "Updated Warehouse One", "Updated Address", "13600999000"
        );

        // Then
        assertThat(result).isNotNull();
        verify(warehouseRepository).save(testWarehouse1);
        assertThat(testWarehouse1.getName()).isEqualTo("Updated Warehouse One");
        assertThat(testWarehouse1.getAddress()).isEqualTo("Updated Address");
        assertThat(testWarehouse1.getContact()).isEqualTo("13600999000");
    }

    @Test
    @DisplayName("case-13")
    void updateWarehouse_OnlyName() {
        // Given
        String originalAddress = testWarehouse1.getAddress();
        String originalContact = testWarehouse1.getContact();
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.updateWarehouse(1L, "Warehouse One Renamed", null, null);

        // Then
        assertThat(result.getName()).isEqualTo("Warehouse One Renamed");
        assertThat(result.getAddress()).isEqualTo(originalAddress);  // 闂傚倸鍊风粈渚€骞栭锔藉亱婵犲﹤瀚々鍙夌節闂堟侗鍎忛柡瀣╃閳规垿宕掑搴ｅ姼闂?
        assertThat(result.getContact()).isEqualTo(originalContact);  // 闂傚倸鍊风粈渚€骞栭锔藉亱婵犲﹤瀚々鍙夌節闂堟侗鍎忛柡瀣╃閳规垿宕掑搴ｅ姼闂?
    }

    @Test
    @DisplayName("case-14")
    void updateWarehouse_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.updateWarehouse(
            999L, "Warehouse Missing", null, null
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    // ========== 婵犵數濮烽弫鍛婃叏閻戝鈧倹绂掔€ｎ亞锛涢梺瑙勫劤椤曨厾绮?闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁糕剝鐟ラ崹婵嬪箹濞ｎ剙濡奸柡瀣╃窔閺屾洟宕煎┑鍥舵￥濡炪倐鏅濋崗姗€寮婚悢鐓庣畾鐟滃繘鏁嶅鍡樺弿婵☆垳顭堟俊鐣岀磼缂佹娲寸€殿喖鐖奸獮瀣敇閻愭彃顥掗梻?==========

    @Test
    @DisplayName("case-15")
    void activateWarehouse_Success() {
        // Given
        inactiveWarehouse.setIsActive(false);
        when(warehouseRepository.findById(3L)).thenReturn(Optional.of(inactiveWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(inactiveWarehouse);

        // When
        Warehouse result = warehouseService.activateWarehouse(3L);

        // Then
        assertThat(result.getIsActive()).isTrue();
        verify(warehouseRepository).save(inactiveWarehouse);
    }

    @Test
    @DisplayName("case-16")
    void activateWarehouse_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.activateWarehouse(999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("case-17")
    void deactivateWarehouse_Success() {
        // Given
        testWarehouse1.setIsActive(true);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.deactivateWarehouse(1L);

        // Then
        assertThat(result.getIsActive()).isFalse();
        verify(warehouseRepository).save(testWarehouse1);
    }

    @Test
    @DisplayName("case-18")
    void deactivateWarehouse_NotFound() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> warehouseService.deactivateWarehouse(999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.WAREHOUSE_NOT_FOUND);
    }

    // ========== 闂傚倷绀佸﹢閬嶅储瑜旈幃娲Ω閳轰胶顔囨俊鐐差儏濞撮绮堟繝鍥ㄧ厱婵炴垶鐟︾紞鎴︽煛鐎ｂ晝顦﹂棁澶愭煕韫囨挸鎮戠紓宥嗗灦缁绘盯宕ㄩ鈶╁亾濡ゅ啯顫曢柟鎯х摠婵挳鏌涘┑鍡楊仾闁诲繐妫楅—鍐Χ閸℃ê鏆楀銈庡幖閻楁挸锕㈡笟鈧弻锝嗘償椤栨粎校闂佸憡蓱閸庡啿宓?==========

    @Test
    @DisplayName("case-19")
    void getLocationCount_WithLocations() {
        // Given
        when(warehouseRepository.countLocationsByWarehouseId(1L)).thenReturn(50L);

        // When
        long count = warehouseService.getLocationCount(1L);

        // Then
        assertThat(count).isEqualTo(50L);
    }

    @Test
    @DisplayName("case-20")
    void getLocationCount_NoLocations() {
        // Given
        when(warehouseRepository.countLocationsByWarehouseId(2L)).thenReturn(0L);

        // When
        long count = warehouseService.getLocationCount(2L);

        // Then
        assertThat(count).isZero();
    }

    // ========== 闂傚倷绀侀幖顐λ囬鐐村亱闁糕剝顨愰懓鍧楁⒑椤掆偓缁夋挳鎮块悙顑句簻闁规澘澧庨悾杈╃磼閻樺啿鍝洪柡灞剧☉閳藉宕￠悙鑼啇闂備礁鎼鍛村嫉椤掑倹宕叉繛鎴欏灩缁狙囨煙閹碱厼骞栭柛鎾舵暬濮?==========

    @Test
    @DisplayName("case-21")
    void createWarehouse_EmptyCode() {
        // Given
        when(warehouseRepository.existsByCode("")).thenReturn(false);

        // When & Then
        // 婵犵數濮烽弫鎼佸磻濞戔懞鍥敇閵忕姷顦悗骞垮劚椤︻垳绮堥崼婢濆綊鎮℃惔锝嗘喖闂佸搫鎷嬮崜姘跺箞閵娿儺娼ㄩ柛鈩冾殔椤偊姊洪崫鍕垫Х缂侇喗鐟ラ～蹇旂節濮橆剛顦板銈嗗姧缁茶棄鈻嶉敐鍥╃＝濞达綁娼ф慨鈧柣蹇撶箲閻熲晠鐛箛娑欏亹閻犲洤寮妸鈺傜厪濠㈣埖绋撻悾鎶芥煕?Controller 闂傚倷娴囬褏鎹㈤幒妤€纾绘繛鎴欏灩绾惧綊鎮归崶銊с偞闁哄妫冮弻鐔衡偓鐢殿焾閸撹鲸绻涢崼銏℃珪闁逞屽墮閸樻粓宕戦幘缁樼厱闁规澘鍚€缁ㄥ吋銇勯銏⑿ｇ紒缁樼洴楠炲鎮欑€涙ê鍓梻浣告啞椤洭寮查锕€鐒垫い鎺嶇閹兼悂鏌涙繝鍐疄闁绘侗鍠氶埀顒婄秵閸ｎ噣寮崒鐐茬閺夊牆澧介幃鍏笺亜閿旇娅嶉柡灞稿墲瀵板嫰宕卞Ο鑽ゅ絾闂備浇宕甸崯鍧楀疾閻樿尙鏆﹂柛鎰皺閺嗗鏌熼崘璇у伐婵犫偓闁秴鐒垫い鎺戯功缁夌敻鏌涘Ο璇插姢閸?Service 闂傚倷娴囬褏鎹㈤幒妤€纾绘繛鎴欏灩绾剧懓霉閻樺樊鍎嶉柍褜鍏涚欢姘剁嵁鐎ｎ喗鍊风痪鐗埳戦悘?
        assertThatThrownBy(() -> warehouseService.createWarehouse(
            "", "Warehouse Name", "Address", "13600123000"
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("case-22")
    void updateWarehouse_AllFieldsNull() {
        // Given
        String originalName = testWarehouse1.getName();
        String originalAddress = testWarehouse1.getAddress();
        String originalContact = testWarehouse1.getContact();
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.updateWarehouse(1L, null, null, null);

        // Then - 闂傚倸鍊风粈浣革耿闁秲鈧倹绂掔€ｎ亞锛涢梺鐟板⒔缁垶鎮″☉銏＄厱妞ゆ劧绲跨粻銉︿繆閸欏鍊愰柡宀嬬秮閸╁﹤鈻庤箛鎾存疁闂佸搫顑嗗Λ鍐蓟濞戞粎鐤€闁规儳鐡ㄩ崰鎰磽娴ｅ搫小缂侇喗鐟╁濠氭偄閸忓皷鎷归梺褰掑亰閸欏骸螣閸屾粎纾藉ù锝堫嚃濞堟梹銇勯敃鍌涙锭闁?
        assertThat(result.getName()).isEqualTo(originalName);
        assertThat(result.getAddress()).isEqualTo(originalAddress);
        assertThat(result.getContact()).isEqualTo(originalContact);
    }

    @Test
    @DisplayName("case-23")
    void activateWarehouse_AlreadyActive() {
        // Given
        testWarehouse1.setIsActive(true);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse1));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse1);

        // When
        Warehouse result = warehouseService.activateWarehouse(1L);

        // Then - 濠电姷鏁搁崑娑㈩敋椤撶喐鍙忛悗闈涙憸閻捇鏌涢锝嗙缂佲偓婢跺本鍠愰柡鍌涱儥閸ゆ洖霉閸忓吋鍎楅柡浣哥У缁绘盯骞嬮悙棰佸缂備讲鍋撻悗锝庝簴閺€浠嬫煟閹邦垱纭鹃柦鍕亹閳ь剝顫夊ú鏍倶濮樿泛绠為柕濞炬櫅缁犵懓霉閿濆懏鎲搁柣?
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("case-24")
    void deactivateWarehouse_AlreadyInactive() {
        // Given
        inactiveWarehouse.setIsActive(false);
        when(warehouseRepository.findById(3L)).thenReturn(Optional.of(inactiveWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(inactiveWarehouse);

        // When
        Warehouse result = warehouseService.deactivateWarehouse(3L);

        // Then - 濠电姷鏁搁崑娑㈩敋椤撶喐鍙忛悗闈涙憸閻捇鏌涢锝嗙缂佲偓婢跺本鍠愰柡鍌涱儥閸ゆ洖霉閸忓吋鍎楅柡浣哥У閹便劌顫滈崱妞剧敖婵炴垶鎸哥粔鐟邦潖閸濆娊铏圭磼濡　鏋忛梻浣告啞閺屻劎鈧稈鏅犻獮鍫ュΩ閳轰胶顔愭繛杈剧到閹芥粓鎮?
        assertThat(result.getIsActive()).isFalse();
    }
}

