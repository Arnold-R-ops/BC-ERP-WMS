package com.wms.system.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MaskingUtils 闂備礁鎲￠〃鍡椕洪弽顓炲偍闁规崘绉崷顓涘亾閿濆骸骞樻俊?
 *
 * V4.1 闂備礁鎼鍫ュ春閺嶎厽鍊垫い鏍仦閺咁剚鎱ㄥ鍡楀箺婵炲牆鐖奸弻鐔烘嫚閳ヨ櫕鐏曢梺鍛婃⒐瀹€绋款嚕椤愩儳鐤€闁规崘灏欓惌妤呮⒑缁嬭法绠绘い銉︽尵閹噣顢曢埛姘贡閳ь剨缍嗛崢鎯?
 *
 * 婵犵數鍋炲娆擃敄閸儲鍎婃い鏍ㄧ矋閸熸椽鏌涢埄鍐噭缁惧彞鍗抽弻?
 * 1. 濠电姵顔栭崰妤呭箰閹间礁绠栭柡鍥ュ灪閸ゅ洭寮堕崼娑樺婵?- maskName()
 * 2. 闂備礁缍婂褔顢栭崱妞绘敠闁逞屽墴閺屾稑鈻庨幋鐐靛姽闂佸憡姊瑰畝绋款嚕?- maskPhone()
 * 3. 闂傚倷绶￠崣搴ㄥ窗濮橀鏁婇柛顐犲劜閸ゅ洭寮堕崼娑樺婵?- maskEmail()
 * 4. 闂備線娼婚梽鍕熆濡警鐒介柛鎰靛枟閸ゅ洭寮堕崼娑樺婵?- maskAddress()
 * 5. 闂備胶顢婇妴鈧柡鍛箞閹儱顭ㄩ崗鐐洴閸┾偓妞ゆ巻鍋撻棁?- isMasked()
 * 6. 闂佸搫顦悧鍡楋耿闁秴鐤炬い鎰剁畱缁犳岸鏌涘☉鍗炲箹闁哄鐭傞弻娑橆煥閸愵厼顏紓浣介哺閹搁箖寮鍓х煓婵炲棛鍋撹ⅸ闂?
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Customer Data Security)
 */
@DisplayName("case-1")
class MaskingUtilsTest {

    // ========== maskName() 婵犵數鍋炲娆擃敄閸儲鍎?==========

    @Test
    @DisplayName("case-2")
    void testMaskName_TwoCharacters() {
        String result = MaskingUtils.maskName("Li");
        assertThat(result).isEqualTo("L*");
    }

    @Test
    @DisplayName("case-3")
    void testMaskName_ThreeCharacters() {
        String result = MaskingUtils.maskName("Bob");
        assertThat(result).isEqualTo("B**");
    }

    @Test
    @DisplayName("case-4")
    void testMaskName_CompoundSurname() {
        String result = MaskingUtils.maskName("Anna");
        assertThat(result).isEqualTo("A***");
    }

    @Test
    @DisplayName("case-5")
    void testMaskName_SingleCharacter() {
        String result = MaskingUtils.maskName("A");
        assertThat(result).isEqualTo("A");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("case-6")
    void testMaskName_NullAndEmpty(String input) {
        String result = MaskingUtils.maskName(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("case-7")
    void testMaskName_EnglishName() {
        String result = MaskingUtils.maskName("John");
        assertThat(result).isEqualTo("J***");
    }

    @Test
    @DisplayName("case-8")
    void testMaskName_LongName() {
        String result = MaskingUtils.maskName("Christopher");
        assertThat(result).isEqualTo("C**********");
    }

    // ========== maskPhone() 婵犵數鍋炲娆擃敄閸儲鍎?==========

    @Test
    @DisplayName("case-9")
    void testMaskPhone_ElevenDigits() {
        String result = MaskingUtils.maskPhone("13912345678");
        assertThat(result).isEqualTo("139****5678");
    }

    @Test
    @DisplayName("case-10")
    void testMaskPhone_DifferentCarriers() {
        assertThat(MaskingUtils.maskPhone("18888888888")).isEqualTo("188****8888");
        assertThat(MaskingUtils.maskPhone("15012345678")).isEqualTo("150****5678");
        assertThat(MaskingUtils.maskPhone("17712345678")).isEqualTo("177****5678");
    }

    @Test
    @DisplayName("case-11")
    void testMaskPhone_SevenDigits() {
        String result = MaskingUtils.maskPhone("1234567");
        assertThat(result).isEqualTo("123****567");
    }

    @Test
    @DisplayName("case-12")
    void testMaskPhone_ShortNumber() {
        String result = MaskingUtils.maskPhone("12345");
        assertThat(result).isEqualTo("12345");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("case-13")
    void testMaskPhone_NullAndEmpty(String input) {
        String result = MaskingUtils.maskPhone(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("case-14")
    void testMaskPhone_WithCountryCode() {
        String result = MaskingUtils.maskPhone("+8613912345678");
        assertThat(result).isEqualTo("+86****5678");
    }

    @Test
    @DisplayName("case-15")
    void testMaskPhone_WithSeparator() {
        String result = MaskingUtils.maskPhone("139-1234-5678");
        assertThat(result).isEqualTo("139****5678");
    }

    // ========== maskEmail() 婵犵數鍋炲娆擃敄閸儲鍎?==========

    @Test
    @DisplayName("case-16")
    void testMaskEmail_StandardEmail() {
        String result = MaskingUtils.maskEmail("john@gmail.com");
        assertThat(result).isEqualTo("j***@gmail.com");
    }

    @Test
    @DisplayName("case-17")
    void testMaskEmail_CorporateEmail() {
        String result = MaskingUtils.maskEmail("alice.wang@company.com");
        assertThat(result).isEqualTo("a***@company.com");
    }

    @Test
    @DisplayName("case-18")
    void testMaskEmail_SingleCharacterUsername() {
        String result = MaskingUtils.maskEmail("a@b.com");
        assertThat(result).isEqualTo("a***@b.com");
    }

    @Test
    @DisplayName("case-19")
    void testMaskEmail_InvalidEmail() {
        String result = MaskingUtils.maskEmail("invalid-email");
        assertThat(result).isEqualTo("invalid-email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("case-20")
    void testMaskEmail_NullAndEmpty(String input) {
        String result = MaskingUtils.maskEmail(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("case-21")
    void testMaskEmail_ChineseEmail() {
        String result = MaskingUtils.maskEmail("闁诲孩顔栭崰姘叏瀹曞洨绠斿☉鎾冲€块弻娑滅疀閿濆懎顫╅悗?com");
        assertThat(result).isEqualTo("闁?**@闂備胶顭堝ù姘跺礈濞嗗浚鍤?com");
    }

    @Test
    @DisplayName("case-22")
    void testMaskEmail_LongUsername() {
        String result = MaskingUtils.maskEmail("verylongusername@example.com");
        assertThat(result).isEqualTo("v***@example.com");
    }

    @Test
    @DisplayName("case-23")
    void testMaskEmail_Subdomain() {
        String result = MaskingUtils.maskEmail("user@mail.company.com");
        assertThat(result).isEqualTo("u***@mail.company.com");
    }

    // ========== maskAddress() 婵犵數鍋炲娆擃敄閸儲鍎?==========

    @Test
    @DisplayName("case-24")
    void testMaskAddress_StandardAddress() {
        String result = MaskingUtils.maskAddress("1234567890Address");
        assertThat(result).isEqualTo("123456789***");
    }

    @Test
    @DisplayName("case-25")
    void testMaskAddress_LongAddress() {
        String result = MaskingUtils.maskAddress("ABCDEFGHIJKLMN");
        assertThat(result).isEqualTo("ABCDEFGHI***");
    }

    @Test
    @DisplayName("case-26")
    void testMaskAddress_ShortAddress() {
        String result = MaskingUtils.maskAddress("Short Rd");
        assertThat(result).isEqualTo("Short Rd");
    }

    @Test
    @DisplayName("case-27")
    void testMaskAddress_NineCharacters() {
        String result = MaskingUtils.maskAddress("123456789");
        assertThat(result).isEqualTo("123456789");
    }

    @Test
    @DisplayName("case-28")
    void testMaskAddress_TenCharacters() {
        String result = MaskingUtils.maskAddress("1234567890");
        assertThat(result).isEqualTo("123456789***");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("case-29")
    void testMaskAddress_NullAndEmpty(String input) {
        String result = MaskingUtils.maskAddress(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("case-30")
    void testMaskAddress_EnglishAddress() {
        String result = MaskingUtils.maskAddress("123 Main Street, New York, NY 10001");
        assertThat(result).isEqualTo("123 Main ***");
    }

    // ========== isMasked() 婵犵數鍋炲娆擃敄閸儲鍎?==========

    @Test
    @DisplayName("case-31")
    void testIsMasked_ContainsMask() {
        assertThat(MaskingUtils.isMasked("139****5678")).isTrue();
        assertThat(MaskingUtils.isMasked("A*")).isTrue();
        assertThat(MaskingUtils.isMasked("j***@gmail.com")).isTrue();
        assertThat(MaskingUtils.isMasked("123456789***")).isTrue();
    }

    @Test
    @DisplayName("case-32")
    void testIsMasked_NoMask() {
        assertThat(MaskingUtils.isMasked("13912345678")).isFalse();
        assertThat(MaskingUtils.isMasked("Alice")).isFalse();
        assertThat(MaskingUtils.isMasked("john@gmail.com")).isFalse();
        assertThat(MaskingUtils.isMasked("1234567890Address")).isFalse();
    }

    @Test
    @DisplayName("case-33")
    void testIsMasked_Null() {
        assertThat(MaskingUtils.isMasked(null)).isFalse();
    }

    @Test
    @DisplayName("case-34")
    void testIsMasked_Empty() {
        assertThat(MaskingUtils.isMasked("")).isFalse();
    }

    // ========== 缂傚倸鍊烽懗鍫曞储瑜旈獮鍐╂償閳泛婀遍埀顒婄秵閸樻儳危?==========

    @Test
    @DisplayName("case-35")
    void testCompleteMasking() {
        // 婵犵妲呴崹顏堝礈濠靛牃鍋撳顓犳噮闁逞屽墰閹虫捇寮甸鍕辈闁挎繂顦伴崕宥夋煕閺囥劌浜介柛姘€块弻鐔哄鐎ｎ偉鍚傛繛锝呮搐閿曨亪鐛笟鈧、娑樷攽閸℃娼涢梻?
        String name = "Alice";
        String phone = "13912345678";
        String email = "zhangsan@example.com";
        String address = "1234567890Address";

        String maskedName = MaskingUtils.maskName(name);
        String maskedPhone = MaskingUtils.maskPhone(phone);
        String maskedEmail = MaskingUtils.maskEmail(email);
        String maskedAddress = MaskingUtils.maskAddress(address);

        assertThat(maskedName).isEqualTo("A****");
        assertThat(maskedPhone).isEqualTo("139****5678");
        assertThat(maskedEmail).isEqualTo("z***@example.com");
        assertThat(maskedAddress).isEqualTo("123456789***");

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傤吋闂侀€炲苯澧寸€殿喖鐏氬鍕償閳ユ剚娼涢梻浣芥〃缁€浣烘崲閹扮増鍋ょ憸宥夊煝閹剧粯鍋ㄧ紒瀣仢楠炲姊洪崨濠勫ⅹ闁瑰啿閰ｉ獮鍡涘醇閵夈儳顔婇梺鍐叉惈閸熶即宕?
        assertThat(MaskingUtils.isMasked(maskedName)).isTrue();
        assertThat(MaskingUtils.isMasked(maskedPhone)).isTrue();
        assertThat(MaskingUtils.isMasked(maskedEmail)).isTrue();
        assertThat(MaskingUtils.isMasked(maskedAddress)).isTrue();
    }

    @Test
    @DisplayName("case-36")
    void testMaskingIsIrreversible() {
        String original = "13912345678";
        String masked = MaskingUtils.maskPhone(original);

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顓炴畻闁哄鐗勯崝宥呪枍濞戙垺鐓曟慨姗嗗墯閹癸絾绻涢崼鐔风伌鐎殿噮鍋婇幃褔宕煎┑鍫涘亰濠电偞鍨堕幐鍝ョ矓閸洘鍋ら柟瀵稿仧椤╂煡骞栫划鍏夊亾閾忣偅鐏冲┑鐘愁問閸犳牠顢栭崱娑樻辈闁绘梻鍘х粻?
        assertThat(masked).isNotEqualTo(original);

        // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顓炴畻闁哄鐗勯崝宥呪枍濞戙垺鐓曟慨姗嗗墯閹癸絾绻涢崼鐔风伌鐎殿噮鍋婇幃褔宕煎┑鍫涘亰闂備礁鎲￠悧鏇㈠箠鎼淬劌绠氶柛顐犲劚缁犳娊鏌嶉崫鍕殶闁?
        assertThat(masked).contains("****");
    }

    @ParameterizedTest
    @CsvSource({
        "Li,L*",
        "Bob,B**",
        "Anna,A***",
        "Chris,C****",
        "Stone,S****"
    })
    @DisplayName("case-37")
    void testMaskName_Parameterized(String input, String expected) {
        assertThat(MaskingUtils.maskName(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "13912345678, 139****5678",
        "18888888888, 188****8888",
        "15012345678, 150****5678",
        "17712345678, 177****5678"
    })
    @DisplayName("case-38")
    void testMaskPhone_Parameterized(String input, String expected) {
        assertThat(MaskingUtils.maskPhone(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "john@gmail.com, j***@gmail.com",
        "alice@company.com, a***@company.com",
        "bob.smith@example.org, b***@example.org"
    })
    @DisplayName("case-39")
    void testMaskEmail_Parameterized(String input, String expected) {
        assertThat(MaskingUtils.maskEmail(input)).isEqualTo(expected);
    }
}
