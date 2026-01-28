package com.wms.system.service;

import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSpu;
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
 * SpuSkuDateBatchCodeGenerator 单元测试
 *
 * 测试 SPU-SKU-DATE 批次码生成器的核心功能
 *
 * 测试场景：
 * 1. 生成基础批次码（无冲突）
 * 2. 生成批次码（有冲突，添加序号）
 * 3. 生成批次码（序号用尽，抛出异常）
 * 4. 验证批次码格式
 * 5. 从批次码中提取日期
 * 6. 处理产品无 SPU/SKU 的情况
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuSkuDateBatchCodeGenerator 单元测试")
class SpuSkuDateBatchCodeGeneratorTest {

    @Mock
    private InboundOrderItemRepository inboundOrderItemRepository;

    @InjectMocks
    private SpuSkuDateBatchCodeGenerator batchCodeGenerator;

    private Product testProduct;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2026, 1, 26);

        ProductSpu spu = ProductSpu.builder()
                .id(1L)
                .spuCode("SPU001")
                .spuName("Test SPU")
                .build();

        testProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .barcode("SKU001")
                .skuName("Test SKU")
                .spu(spu)
                .build();
    }

    // ==================== 生成批次码测试 ====================

    @Test
    @DisplayName("生成批次码 - 无冲突")
    void generateUnique_NoCollision() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU001-20260126");
        verify(inboundOrderItemRepository).existsByBatchCode("SPU001-SKU001-20260126");
    }

    @Test
    @DisplayName("生成批次码 - 有冲突，添加序号-01")
    void generateUnique_WithCollision_AddSequence01() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126-01"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU001-20260126-01");
        verify(inboundOrderItemRepository).existsByBatchCode("SPU001-SKU001-20260126");
        verify(inboundOrderItemRepository).existsByBatchCode("SPU001-SKU001-20260126-01");
    }

    @Test
    @DisplayName("生成批次码 - 多次冲突，添加序号-05")
    void generateUnique_MultipleCollisions_AddSequence05() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126-01"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126-02"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126-03"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126-04"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126-05"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU001-20260126-05");
    }

    @Test
    @DisplayName("生成批次码 - 序号用尽，抛出异常")
    void generateUnique_SequenceExhausted_ThrowsException() {
        // Given
        String baseBatchCode = "SPU001-SKU001-20260126";
        when(inboundOrderItemRepository.existsByBatchCode(anyString())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> batchCodeGenerator.generateUnique(testProduct, testDate))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "BATCH_CODE_GENERATION_FAILED");
    }

    // ==================== 产品无 SPU/SKU 测试 ====================

    @Test
    @DisplayName("生成批次码 - 产品无 SPU，使用默认值")
    void generateUnique_ProductWithoutSpu_UseDefault() {
        // Given
        testProduct.setSpu(null);
        when(inboundOrderItemRepository.existsByBatchCode("SPU1-SKU001-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU1-SKU001-20260126");
    }

    @Test
    @DisplayName("生成批次码 - 产品无 SKU，使用默认值")
    void generateUnique_ProductWithoutSku_UseDefault() {
        // Given
        testProduct.setBarcode(null);
        testProduct.setSkuName(null);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU1-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-SKU1-20260126");
    }

    @Test
    @DisplayName("生成批次码 - 使用 skuName 作为 SKU")
    void generateUnique_UseSkuName() {
        // Given
        testProduct.setBarcode(null);
        testProduct.setSkuName("Test-SKU-Name");
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-TestSKUName-20260126"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).isEqualTo("SPU001-TestSKUName-20260126");
    }

    // ==================== 验证批次码格式测试 ====================

    @Test
    @DisplayName("验证批次码格式 - 有效格式（无序号）")
    void isValidFormat_ValidWithoutSequence() {
        // When & Then
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-20260126")).isTrue();
    }

    @Test
    @DisplayName("验证批次码格式 - 有效格式（有序号）")
    void isValidFormat_ValidWithSequence() {
        // When & Then
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-20260126-01")).isTrue();
        assertThat(batchCodeGenerator.isValidFormat("SPU001-SKU001-20260126-99")).isTrue();
    }

    @Test
    @DisplayName("验证批次码格式 - 无效格式")
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

    // ==================== 提取日期测试 ====================

    @Test
    @DisplayName("从批次码提取日期 - 成功（无序号）")
    void extractDate_Success_WithoutSequence() {
        // When
        LocalDate date = batchCodeGenerator.extractDate("SPU001-SKU001-20260126");

        // Then
        assertThat(date).isEqualTo(LocalDate.of(2026, 1, 26));
    }

    @Test
    @DisplayName("从批次码提取日期 - 成功（有序号）")
    void extractDate_Success_WithSequence() {
        // When
        LocalDate date = batchCodeGenerator.extractDate("SPU001-SKU001-20260126-05");

        // Then
        assertThat(date).isEqualTo(LocalDate.of(2026, 1, 26));
    }

    @Test
    @DisplayName("从批次码提取日期 - 无效格式返回 null")
    void extractDate_InvalidFormat_ReturnsNull() {
        // When & Then
        assertThat(batchCodeGenerator.extractDate(null)).isNull();
        assertThat(batchCodeGenerator.extractDate("")).isNull();
        assertThat(batchCodeGenerator.extractDate("INVALID")).isNull();
        assertThat(batchCodeGenerator.extractDate("SPU001-SKU001-INVALID")).isNull();
    }

    @Test
    @DisplayName("从批次码提取日期 - 日期解析失败返回 null")
    void extractDate_ParseError_ReturnsNull() {
        // When & Then
        assertThat(batchCodeGenerator.extractDate("SPU001-SKU001-99999999")).isNull();
    }

    // ==================== 边界测试 ====================

    @Test
    @DisplayName("生成批次码 - 不同日期生成不同批次码")
    void generateUnique_DifferentDates_DifferentBatchCodes() {
        // Given
        LocalDate date1 = LocalDate.of(2026, 1, 26);
        LocalDate date2 = LocalDate.of(2026, 1, 27);

        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126"))
                .thenReturn(false);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260127"))
                .thenReturn(false);

        // When
        String batchCode1 = batchCodeGenerator.generateUnique(testProduct, date1);
        String batchCode2 = batchCodeGenerator.generateUnique(testProduct, date2);

        // Then
        assertThat(batchCode1).isEqualTo("SPU001-SKU001-20260126");
        assertThat(batchCode2).isEqualTo("SPU001-SKU001-20260127");
        assertThat(batchCode1).isNotEqualTo(batchCode2);
    }

    @Test
    @DisplayName("生成批次码 - 序号格式为两位数")
    void generateUnique_SequenceFormat_TwoDigits() {
        // Given
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126"))
                .thenReturn(true);
        when(inboundOrderItemRepository.existsByBatchCode("SPU001-SKU001-20260126-01"))
                .thenReturn(false);

        // When
        String batchCode = batchCodeGenerator.generateUnique(testProduct, testDate);

        // Then
        assertThat(batchCode).endsWith("-01"); // 两位数格式
        assertThat(batchCode).doesNotEndWith("-1"); // 不是一位数
    }
}
