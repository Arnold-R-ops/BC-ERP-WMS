package com.wms.system.service;

import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Product;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.InboundOrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SpuSkuDateBatchCodeGenerator 閸楁洖鍘撳ù瀣槸
 *
 * 濞村鐦?SPU-SKU-DATE 閹佃顐奸惍浣烘晸閹存劕娅掗惃鍕壋韫囧啫濮涢懗?
 *
 * 濞村鐦崷鐑樻珯閿?
 * 1. 閻㈢喐鍨氶崺铏诡攨閹佃顐奸惍渚婄礄閺冪姴鍟跨粣渚婄礆
 * 2. 閻㈢喐鍨氶幍瑙勵偧閻緤绱欓張澶婂暱缁愪緤绱濆ǎ璇插鎼村繐褰块敍?
 * 3. 閻㈢喐鍨氶幍瑙勵偧閻緤绱欐惔蹇撳娇閻劌鏁栭敍灞惧閸戝搫绱撶敮闈╃礆
 * 4. 妤犲矁鐦夐幍瑙勵偧閻焦鐗稿?
 * 5. 娴犲孩澹掑▎锛勭垳娑擃厽褰侀崣鏍ㄦ）閺?
 * 6. 婢跺嫮鎮婃禍褍鎼ч弮?SPU/SKU 閻ㄥ嫭鍎忛崘?
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class SpuSkuDateBatchCodeGeneratorTest {

    @Mock
    private InboundOrderItemRepository inboundOrderItemRepository;

    @InjectMocks
    private SpuSkuDateBatchCodeGenerator batchCodeGenerator;

    private ProductSku testProduct;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2026, 1, 26);

        Product spu = Product.builder()
                .id(1L)
                .productCode("SPU001")
                .productName("Test SPU")
                .build();

        testProduct = ProductSku.builder()
                .skuCode("SKU00000001")
                .id(1L)
                .name("Test ProductSku")
                .barcode("SKU001")
                .skuName("Test SKU")
                .product(spu)
                .build();
    }

    // ==================== 閻㈢喐鍨氶幍瑙勵偧閻焦绁寸拠?====================

    @Test
    @DisplayName("case-2")
    void generateUnique_NoCollision() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU00000001-20260126");
        verify(inboundOrderItemRepository).existsByBatchCode("SPU001-SKU00000001-20260126");
    }

    @Test
    @DisplayName("case-3")
    void generateUnique_WithCollision_AddSequence01() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126-01"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU00000001-20260126-01");
        verify(inboundOrderItemRepository).existsByBatchCode("SPU001-SKU00000001-20260126");
        verify(inboundOrderItemRepository).existsByBatchCode("SPU001-SKU00000001-20260126-01");
    }

    @Test
    @DisplayName("case-4")
    void generateUnique_MultipleCollisions_AddSequence05() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126-01"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126-02"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126-03"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126-04"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126-05"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU00000001-20260126-05");
    }

    @Test
    @DisplayName("case-5")
    void generateUnique_SequenceExhausted_ThrowsException() {
        // Given
        String baseBatchCode = "SPU001-SKU00000001-20260126";
        when(inboundOrderItemRepository.existsByBatchCode(anyString())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> batchCodeGenerator.generateUnique(testProduct, testDate))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "BATCH_CODE_GENERATION_FAILED");
    }

    // ==================== 娴溠冩惂閺?SPU/SKU 濞村鐦?====================

    @Test
    @DisplayName("case-6")
    void generateUnique_ProductWithoutSpu_UseDefault() {
        // Given
        testProduct.setProduct(null);
        when(inboundOrderItemRepository.existsByBatchCode("SPU1-SKU00000001-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU1-SKU00000001-20260126");
    }

    @Test
    @DisplayName("case-7")
    void generateUnique_ProductWithoutSku_UseDefault() {
        // Given
        testProduct.setSkuCode(null);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU1-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU1-20260126");
    }

    @Test
    @DisplayName("case-8")
    void generateUnique_BarcodeDoesNotChangeInternalBatchCode() {
        // Given
        testProduct.setBarcode("A-VERY-LONG-CHANGEABLE-BARCODE");
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU00000001-20260126");
    }

    // ==================== 妤犲矁鐦夐幍瑙勵偧閻焦鐗稿蹇旂ゴ鐠?====================

    @Test
    @DisplayName("case-9")
    void isValidFormat_ValidWithoutSequence() {
        // When & Then
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-20260126")).isTrue();
    }

    @Test
    @DisplayName("case-10")
    void isValidFormat_ValidWithSequence() {
        // When & Then
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-20260126-01")).isTrue();
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-20260126-99")).isTrue();
    }

    @Test
    @DisplayName("case-11")
    void isValidFormat_Invalid() {
        // When & Then
        assertThat(batchCodeGenerator.isValidFormat(null)).isFalse();
        assertThat(batchCodeGenerator.isValidFormat("")).isFalse();
        assertThat(batchCodeGenerator.isValidFormat("   ")).isFalse();
        assertThat(batchCodeGenerator.isValidFormat("INVALID")).isFalse();
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001")).isFalse();
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-2026")).isFalse();
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-20260126-ABC")).isFalse();
    }

    // ==================== 閹绘劕褰囬弮銉︽埂濞村鐦?====================

    @Test
    @DisplayName("case-12")
    void extractDate_Success_WithoutSequence() {
        // When
        LocalDate date = batchCodeGenerator.extractDate("SPU001-SKU001-20260126");

        // Then
        assertThat(date).isEqualTo(LocalDate.of(2026, 1, 26));
    }

    @Test
    @DisplayName("case-13")
    void extractDate_Success_WithSequence() {
        // When
        LocalDate date = batchCodeGenerator.extractDate("SPU001-SKU001-20260126-05");

        // Then
        assertThat(date).isEqualTo(LocalDate.of(2026, 1, 26));
    }

    @Test
    @DisplayName("case-14")
    void extractDate_InvalidFormat_ReturnsNull() {
        // When & Then
        assertThat(batchCodeGenerator.extractDate(null)).isNull();
        assertThat(batchCodeGenerator.extractDate("")).isNull();
        assertThat(batchCodeGenerator.extractDate("INVALID")).isNull();
        assertThat(batchCodeGenerator.extractDate("SPU001-SKU001-INVALID")).isNull();
    }

    @Test
    @DisplayName("case-15")
    void extractDate_ParseError_ReturnsNull() {
        // When & Then
        assertThat(batchCodeGenerator.extractDate("SPU001-SKU001-99999999")).isNull();
    }

    // ==================== 鏉堝湱鏅ù瀣槸 ====================

    @Test
    @DisplayName("case-16")
    void generateUnique_DifferentDates_DifferentBatchCodes() {
        // Given
        LocalDate date1 = LocalDate.of(2026, 1, 26);
        LocalDate date2 = LocalDate.of(2026, 1, 27);

        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126"))
                .thenReturn(false);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260127"))
                .thenReturn(false);

        // When
        String batchCode1 = batchCodeGenerator.generateUnique(testProduct, date1);
        String batchCode2 = batchCodeGenerator.generateUnique(testProduct, date2);

        // Then
        assertThat(batchCode1).isEqualTo("SPU001-SKU00000001-20260126");
        assertThat(batchCode2).isEqualTo("SPU001-SKU00000001-20260127");
        assertThat(batchCode1).isNotEqualTo(batchCode2);
    }

    @Test
    @DisplayName("case-17")
    void generateUnique_SequenceFormat_TwoDigits() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU00000001-20260126-01"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).endsWith("-01"); // 娑撱倓缍呴弫鐗堢壐瀵?
        assertThat(batchCode).doesNotEndWith("-1"); // 娑撳秵妲告稉鈧担宥嗘殶
    }

    @Test
    @DisplayName("long product codes stay within the database batch-code limit")
    void generateUnique_LongProductCode_IsBounded() {
        testProduct.getProduct().setProductCode("PRODUCT-CODE-WITH-A-VERY-LONG-HUMAN-READABLE-NAME-999");
        when(inboundOrderItemRepository.existsByBatchCode(anyString())).thenReturn(false);

        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        assertThat(batchCode).hasSizeLessThanOrEqualTo(47);
        assertThat(batchCode).contains("-SKU00000001-20260126");
        assertThat(batchCodeGenerator.isValidFormat(batchCode)).isTrue();
    }
}
