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
 * WarehouseService 闂備浇顕уù鐑藉箠閹捐瀚夋い鎺戝濮规煡鏌ㄥ┑鍡╂Ц缁绢厸鍋撻梻浣告惈濞层劑宕戝☉姘殰婵°倓鑳剁粻楣冩煛婢跺鐒鹃柛搴㈡崌閺?
 *
 * 濠电姷鏁搁崑鐐差焽濞嗘搩鏁勯柛顐犲劜閸庡﹥銇勯弽顐沪闁搞倖鍨块弻娑㈠箛閳轰礁顬夌紓浣鸿檸閸ㄥ爼寮?
 * 1. 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褜鍤熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱ㄥΟ绋垮闁哄被鍔岄埥澶娢熼悡搴澑闂備焦鎮堕崝宥呯暆缁嬭法鏆?缂傚倸鍊搁崐鎼佸磹瑜版帗鍋嬫繝濠傜墛閸嬪绻濇繝鍌滃闁绘挸鍊婚埀顒€绠嶉崕閬嶅箯鐎ｎ€絾绻濆顓犲弳?
 * 2. 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕鈽夐幙鍐╂儞闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍕厡妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏濡濮傞柡灞诲妼閳藉螣閻撳寒鏉搁梻浣规偠閸斿秴鐣濈粙璺ㄦ殾?婵犵數鍋為崹鍫曞箰閸濄儳鐭撻柟缁㈠枟閸嬨倝鏌曟繛鐐珔闁圭鍩栭妵鍕敇閻旈顑傜紓浣插亾?
 * 3. 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕霉閿濆牊顏犵痪鍙ョ矙閺屸€愁吋鎼粹€茬敖闂佹悶鍔嶆繛濠囧蓟閿涘嫪娌悹鍥ㄥ絻婵倕顪冮妶鍡樼妞ゃ劌妫涚划瀣箳濡も偓缁犺崵鈧娲栧ú锕傛儍閹达附鈷戦柟绋挎捣閳藉鏌ｅΔ鈧敃顏勭暦閻熼偊娼╅悹楦挎椤?婵犵數鍋為崹鍫曞箰閸濄儳鐭撻柟缁㈠枟閸嬨倝鏌曟繛鐐珔闁圭鍩栭妵鍕敇閻旈顑傜紓浣插亾?
 * 4. 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛櫣缂佺姰鍎甸弻宥堫檨闁告挾鍠庨锝夊垂椤愩垻绐為柣搴秵閸撴繄绮堢€ｎ亖鏀介柣鎰硾閽勮偐鈧娲熷褔鍩㈤幘璇茬闁绘劖顔栧ù鍕⒑鐟欏嫬鍔ょ痪缁㈠幖椤?
 * 5. 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛櫣缂佺姰鍎甸弻宥堫檨闁告挾鍠庨锝夊垂椤愩垻绐炴繝銏ｆ硾閺堫剛浜稿┑瀣厵?
 * 6. 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃鏆熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱ㄥΟ绋垮閼挎劙鏌涢妷顖滃埌濠⒀勫絻闇夐柣鎾冲閸ゅ洭鏌℃担鍝バх€规洜鍘ч埞鎴﹀幢濡炲墽绀嬮梻鍌氬€风欢锟犲窗閹捐绀夌€光偓閸曨剙浠遍悷婊冪Ч閸?闂傚倸鍊风欢锟犲窗閹捐绀夌€光偓閸曨偄鐎柟鑹版彧缁叉椽宕戦幘鏂ユ婵炲棙蓱閻ｅ嘲顪冮妶鍡樼叆闁活厼鍊垮?
 * 7. 濠电姷鏁告慨鐑姐€傛禒瀣；闁规儳顕粻楣冩煠婵傚壊鏉洪柛鐐舵缁辨帡顢欓崫鍕瀴闂?
 * 8. 闂傚倷鑳堕…鍫ヮ敄閸℃瑥鍨濋幖娣妼閺嬩線鏌曢崼婵囶棤妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏?
 * 9. 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷锝呭妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏濡濮傞柡宀嬬秮婵℃悂濡烽妷顔绘偅缂傚倷闄嶉崝蹇斻仈閹间礁绠柛娑卞弾閸氬鏌涢埄鍏╂垿顢欏澶嬧拻?
 * 10. 闂備礁鎼ˇ顖炴偋閸℃鑰块梺顒€绉撮悿鐐亜閹板墎鐣辩紒鐘冲哺閺屾稑鈽夐崡鐐茬闂佸搫顑呴張顒傛崲濞戙垹绾ч柟鎼幖閸撶敻姊?
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
            .contact("闂佽瀛╅鏍窗濮橆厽鍙忕€规洖娲ㄧ粻?13800138000")
            .isActive(true)
            .build();

        testWarehouse2 = Warehouse.builder()
            .id(2L)
            .code("WH02")
            .name("Warehouse Two")
            .address("Address Two")
            .contact("闂傚倷绀侀幖顐λ囬娑辨缂佸锛?13900139000")
            .isActive(true)
            .build();

        inactiveWarehouse = Warehouse.builder()
            .id(3L)
            .code("WH03")
            .name("Warehouse Three")
            .address("Address Three")
            .contact("闂傚倷鑳剁划顖滃垝瀹€鍕垫晞闁告洦鍙€婵?13700137000")
            .isActive(false)
            .build();
    }

    // ========== 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褜鍤熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱ㄥΟ绋垮缂佺粯鐩鍫曞箣椤撶偛澹嶉梻?==========

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
        assertThat(savedWarehouse.getContact()).isEqualTo("闂備浇宕垫慨宥夊炊閵婏箓鏁梻?13600136000");
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

    // ========== 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍕厡妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏濡濡界紒缁樼洴瀵爼骞嬮鐐插闂?==========

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

    // ========== 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃鏆熸い鈺冨厴閺岀喖骞嗚閿涘秵鎱ㄥΟ绋垮缂佺粯鐩鍫曞箣椤撶偛澹嶉梻?==========

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
        assertThat(result.getAddress()).isEqualTo(originalAddress);  // 闂傚倷绀侀幖顐︽偋濠婂嫮顩叉繝闈涱儏閺嬩礁鈹戦崒婊庣劸闁?
        assertThat(result.getContact()).isEqualTo(originalContact);  // 闂傚倷绀侀幖顐︽偋濠婂嫮顩叉繝闈涱儏閺嬩礁鈹戦崒婊庣劸闁?
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

    // ========== 濠电姷鏁告慨鐑姐€傛禒瀣；闁规儳顕粻?闂傚倷鑳堕…鍫ヮ敄閸℃瑥鍨濋幖娣妼閺嬩線鏌曢崼婵囶棤妞も晝鍏橀弻鐔煎箚瑜忛敍宥嗘叏濡濡界紒缁樼洴瀵爼骞嬮鐐插闂?==========

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

    // ========== 闂備礁婀遍崢褔鎮洪妸鈺佺濡炲娴风粈濠囨煕濞戞瑦缍戦柡瀣╃闇夐柛蹇撳悑缂嶆垶绻涢崨顐⑩偓妤冩閹惧瓨濯撮柛婵嗗閻忓棗顪冮妶鍡樺暗妞ゎ厼鐗撳﹢渚€鏌ｆ惔顖滅У闁告ɑ鍎冲嵄?==========

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

    // ========== 闂備礁鎼ˇ顖炴偋閸℃鑰块梺顒€绉撮悿鐐亜閹板墎鐣辩紒鐘冲哺閺屾稑鈽夐崡鐐茬闂佸搫顑呴張顒傛崲濞戙垹绾ч柟鎼幖閸撶敻姊?==========

    @Test
    @DisplayName("case-21")
    void createWarehouse_EmptyCode() {
        // Given
        when(warehouseRepository.existsByCode("")).thenReturn(false);

        // When & Then
        // 濠电姷鏁搁崑娑⑺囬銏犵鐎广儱顦粈鍫澝归悡搴ｆ憼闁哄拋鍓氶幈銊ヮ潨閸℃顫梺鍝勵槷缁瑥顫忔繝姘妞ゆ劧绲藉▍锝囩磽娴ｉ潧濮€閻忓繑鐟╅獮蹇涙偐鐠囧弬銊╂煏婢舵稓鐣抽柛?Controller 闂備浇顕х换鎺楀磻濞戙垹纾归悹鍥ㄧゴ閺嬫棃鏌熺€电鍓辨繛鍫㈡櫕閳ь剙鍘滈崑鎾绘煕閹板吀绨兼い顐㈢У缁绘盯骞嬮悙瀛樺剮闂佸憡顭囬弲顐﹀焵椤掍礁鎼搁柛濠冪箞閻涱喚鈧綆鍣弫鍌炲箹鏉堝墽鎮兼い锔诲櫍閺屸剝寰勯崱妯荤彆闂佽崵鍟块弲鐘茬暦閸愯尙鏆嬮柟鍐诧工濠€閬嶅焵椤掑﹦绉甸柛妯诲劤鍗?Service 闂備浇顕х换鎺楀磻濞戙垹纾瑰ù鐘差儍閳ь兛绶氶獮瀣倷绾版ɑ鐏?
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

        // Then - 闂傚倷绀佸﹢閬嶃€傛禒瀣；闁瑰墽绮悡娑㈡煕椤愶絿绠ユ俊鍙夋倐閺岋綁鍩婂▎蹇撴殜闁哄妫冮弻娑滅疀閹惧瓨鍠愮紓浣哄С缁瑩寮婚悢鍏尖拹闁归偊鍙庡Ο鍌滅磽娴ｈ娈旀い锕傛涧閻?
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

        // Then - 婵犵數鍋涢顓熸叏鐎靛摜鐭撻柛顐ｆ礀缁€澶屾喐閺傛鍤曞ù鍏兼儗閺佸秵绻涢幋鐐颁孩缂佲偓鐎ｎ亖鏀介柣鎰硾閽勮偐鈧娲栭悘姘跺箞閵娾晛绠瑰ù锝呮憸閻?
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

        // Then - 婵犵數鍋涢顓熸叏鐎靛摜鐭撻柛顐ｆ礀缁€澶屾喐閺傛鍤曞ù鍏兼儗閺佸秵鎱ㄥ鍡椾簽濞戞挸绉瑰鍝劽虹紒妯衡枏闂佸憡鏌ㄧ€涒晠骞堥妸鈺佺濞达絽鎽滈悾?
        assertThat(result.getIsActive()).isFalse();
    }
}
