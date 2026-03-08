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
 * MaskingUtils 闂傚倸鍊风粈渚€骞夐敓鐘偓鍐幢濡炴洘妞藉浠嬵敇閻愭彃浜堕梻浣筋潐瀹曟绮旈鈧畷鐑筋敇濞戞ü澹曢梺鎸庣箓妤犳悂鐛Ο璁崇箚?
 *
 * V4.1 闂傚倸鍊风粈渚€骞栭锔绘晞闁割偁鍎遍弰銉╂煛瀹ュ骸骞楅柛濠傜仛閵囧嫰寮介顫勃闂佸搫鎷嬮崜姘跺箞閵娿儺娼ㄩ柛鈩冾殔缁犲搫鈹戦悙鑼闁绘牕銈稿濠氭偄閻戞ê鐝伴梺鐐藉劥濞呮洟鎮橀弴銏♀拺闁告稑锕ラ埛鎰偓鍏夊亾缂佸顑欓崵鏇熴亜閹扳晛鍔ら柣銈傚亾闂備浇顫夊畷妯间焊濞嗘挻鍎楁俊銈呮噺閳锋垹绱掔€ｎ厽纭剁紒鐘电帛閵囧嫰濡烽敂钘夋殫闂佽鍣崳锝夈€侀弴銏犵厱婵﹩鍓氱拹锟犳煃瑜滈崜銊х礊閸℃稑鍌ㄩ柟顖ｇ仜?
 *
 * 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁稿骸锕ラ妵鍕冀閵娧呯厑闂佸摜鍠愬浠嬪蓟濞戙垹鐒洪柛鎰典簼閸ｎ厾绱掗幆褍缍栭柛妤佸▕瀵?
 * 1. 濠电姷鏁告慨鐢割敊閺嶎厼绐楁俊銈呮噹缁犱即鏌熼梻瀵割槮缂佺姵鐗犻弻锟犲炊閵夈儳浠鹃梺鎼炲€曞ú顓烆嚕閸洖閱囨繛鎴灻‖澶娾攽?- maskName()
 * 2. 闂傚倸鍊风粈浣虹礊婵犲偆鐒界憸鏃堛€侀弽顓炲耿婵＄偟绮弫鐘绘⒑闁偛鑻晶鎾煛鐏炲墽鈽夐柍璇查叄楠炲鎮欓棃娑樞梻鍌欑閹测€愁潖閻熸壆鏆嗙紒瀣儥閸?- maskPhone()
 * 3. 闂傚倸鍊搁崐椋庢閿熺姴鐭楅幖娣妼缁愭鎱ㄥ鈧·鍌炲极婵犲洦鐓曟い鎰Т閸旀粓鏌涢妶鍛枠鐎殿喖鐖煎畷鐓庘槈濡警鐎存繝?- maskEmail()
 * 4. 闂傚倸鍊风欢姘焽婵犳碍鈷旈柛鏇ㄥ亽閻斿棙淇婇娆掝劅闁绘帊绮欓弻娑㈠箛闂堟稒鐏嶉梺鎼炲€曞ú顓烆嚕閸洖閱囨繛鎴灻‖澶娾攽?- maskAddress()
 * 5. 闂傚倸鍊烽懗鍫曘€佹繝鍥ラ柍褜鍓熼弻锟犲川椤斿墽鐤勯梺璇″枔閸庨亶鈥﹂妸鈺佺闁绘劦鍓涘ú鎾煕閳规儳浜炬俊鐐€栧濠氬磻閹剧粯顥?- isMasked()
 * 6. 闂傚倷绀侀幖顐λ囬鐐村亱闁糕剝顨愰懓鍧楁⒑椤掆偓缁夋挳鎮块悙顑句簻闁规澘澧庨悾杈╃磼閻樺啿鍝洪柡灞剧☉閳藉宕￠悙鑼啇闂備礁鎼鍛存儗閸岀偛钃熸繛鎴烇供閻撱儵鏌涢幇闈涘箹妞ゅ浚鍘剧槐鎾存媴娴犲鎽甸梺瑙勬倐缁犳牕顕ｉ锔绘晩闁告挆鍛幆婵犵數鍋涘Λ娑㈠磻閹惧厜鍚€闂?
 *
 * @author WMS Team
 * @since 2026-02-12
 * @version 4.1 (Customer Data Security)
 */
@DisplayName("case-1")
class MaskingUtilsTest {

    // ========== maskName() 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁?==========

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

    // ========== maskPhone() 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁?==========

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
        assertThat(result).isEqualTo("123****4567");
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

    // ========== maskEmail() 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁?==========

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
        String email = "\u6d4b\u8bd5\u7528\u6237@\u793a\u4f8b.com";
        String result = MaskingUtils.maskEmail(email);
        assertThat(result).isEqualTo("\u6d4b***@\u793a\u4f8b.com");
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

    // ========== maskAddress() 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁?==========

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

    // ========== isMasked() 婵犵數濮烽弫鎼佸磻閻愬樊鐒芥繛鍡樻惄閺佸嫰鏌涢鐘插姕闁?==========

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

    // ========== 缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀閸屻劎鎲搁弮鍫㈠祦闁告劏鏅滈崕鐔兼煃椤撯剝纭炬繝鈧柆宥呯劦妞ゆ帒锕︾粔鐢告煕濡鍔ら崡?==========

    @Test
    @DisplayName("case-35")
    void testCompleteMasking() {
        // 婵犵數濮烽。钘壩ｉ崨鏉戝瀭妞ゅ繐鐗嗙粈鍫熺節闂堟稓澧愰柛瀣尭椤繈顢橀悩鍐叉珮闂備線鈧偛鑻晶浼存煙閾忣偅宕岀€殿喚鏁婚、妤呭礋椤愶綀绶㈤梻浣瑰缁诲倿藝娴兼潙纾跨€广儱顦伴悡鏇㈡煛閸ャ儱濡煎ù婊€绮欓弻娑橆潩椤掆偓閳ь剙娼″濠氭偄閸濆嫷妫滈柣搴秵閸嬪宕ラ崒娑氱闁挎繂鎳忛幖鎰版煥閺囥劋閭柣娑卞枟缁楃喖鍩€椤掑嫨鈧礁鈽夊Ο閿嬫杸闂佺硶鍓濋〃蹇擃焽濞戙垺鈷?
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

        // 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ帒鍋嗛崥瀣⒒娓氣偓閳ь剛鍋涢懟顖涙櫠鐎电硶鍋撳▓鍨灈闁诲繑鑹鹃銉╁礋椤掍礁鍔呴梺鐐藉劜閸撴艾顭囧☉銏♀拻濞达綀濮ら妴鍐磼閳ь剚鎷呴悜妯哄簥闂佽澹嗘晶妤呭磻閵堝洦鍠愮€广儱顦悡婵嬫煙閸撗呭笡闁稿鍔庣槐鎺斺偓锝庝簽娴犮垺顨ラ悙宸剶婵﹥妞藉畷銊︾節閸曨偀鍚傞梻浣烘嚀閸熷潡鏌婇敐澶屽祦闁糕剝绋戦柋鍥煏婢跺牆鍔ゆい鏂匡躬濮婃椽宕橀崣澶嬪創闂佸摜鍠嶉崡鍐茬暦?
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

        // 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ挾鍋為悾濠氭⒑閸濆嫷妲搁柣妤€瀚板畷婵嗩吋閸涱亝鐎哄┑鐐村灟閸ㄦ椽鎮￠弴鐔稿弿婵妫楁晶顖炴煙閻у摜绋荤紒缁樼洴瀹曞ジ鎮㈡搴濈礉闁诲孩顔栭崳顕€宕戞繝鍥х畺鐟滄柨鐣烽悡搴樻斀闁割偅绋戞禍鐗堢節閻㈤潧浠﹂柛銊ョ埣楠炴劙宕妷褏鐓嬮梺鍓插亝濞叉﹢宕戦妶澶嬬厵閻庣數顭堟禒褎銇勯埡鍌滃弨妤犵偞鐗滈崚鎺楀礂婢跺﹣澹曢梺鎯ф禋閸嬪懘鎮橀崘娴嬫斀闁绘ɑ鍓氶崯蹇涙煕閻樺磭澧垫い銏＄墵瀹曞崬鈽夊Ο鏄忕发闂備胶绮濠氬储瑜忕划?
        assertThat(masked).isNotEqualTo(original);

        // 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柟缁㈠枟閸庢銆掑锝呬壕闂佽鍨悞锕€顕ラ崟顖氱疀妞ゆ挾鍋為悾濠氭⒑閸濆嫷妲搁柣妤€瀚板畷婵嗩吋閸涱亝鐎哄┑鐐村灟閸ㄦ椽鎮￠弴鐔稿弿婵妫楁晶顖炴煙閻у摜绋荤紒缁樼洴瀹曞ジ鎮㈡搴濈礉闁诲孩顔栭崳顕€宕戞繝鍥х畺鐟滄柨鐣烽悡搴樻斀闁割偅绋戞禍浼存⒒閸屾瑧顦﹂柟璇х節閹囧即閵忕姷鐤囬柟鍏肩暘閸斿瞼绮诲鑸电厱妞ゆ劗濮撮崝姘辩磼閻樺啿鈻曢柡灞界Ч瀹曨偊宕熼锝嗩啀闂?
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


