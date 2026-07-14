package com.wms.system.service;

import com.wms.system.entity.ChannelSkuMapping;
import com.wms.system.entity.Product;
import com.wms.system.repository.ChannelSkuMappingRepository;
import com.wms.system.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChannelSkuResolver 单元测试（P1 批次2，四层解析漏斗）
 */
@ExtendWith(MockitoExtension.class)
class ChannelSkuResolverTest {

    @Mock
    private ChannelSkuMappingRepository mappingRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ChannelSkuResolver resolver;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(1L).barcode("TOP0002-2-20KG").name("木薯珍珠 20KG").build();
    }

    @Test
    void layer1_ExactMappingHit() {
        ChannelSkuMapping mapping = ChannelSkuMapping.builder()
            .channel("SHOPIFY").externalSku("TOP0002 - 2-20KG")
            .normalizedSku("TOP0002-2-20KG")
            .mappingType(ChannelSkuMapping.TYPE_PRODUCT).product(product).quantityRatio(20)
            .status(ChannelSkuMapping.STATUS_ACTIVE).build();
        when(mappingRepository.findByChannelAndExternalSkuAndStatus("SHOPIFY", "TOP0002 - 2-20KG", "ACTIVE"))
            .thenReturn(Optional.of(mapping));

        var resolution = resolver.resolve("SHOPIFY", "store", "TOP0002 - 2-20KG");

        assertThat(resolution.getType()).isEqualTo(ChannelSkuResolver.ResolutionType.PRODUCT);
        assertThat(resolution.getProduct().getId()).isEqualTo(1L);
        assertThat(resolution.getQuantityRatio()).isEqualTo(20);
        // 第一层命中不再往下走
        verify(productRepository, never()).findByBarcode(anyString());
    }

    @Test
    void layer2_NormalizedMappingHit() {
        when(mappingRepository.findByChannelAndExternalSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        ChannelSkuMapping mapping = ChannelSkuMapping.builder()
            .channel("SHOPIFY").externalSku("TOP0002-2-20KG").normalizedSku("TOP0002-2-20KG")
            .mappingType(ChannelSkuMapping.TYPE_PRODUCT).product(product).quantityRatio(1)
            .status(ChannelSkuMapping.STATUS_ACTIVE).build();
        // "top0002 - 2-20kg" 归一化 → "TOP0002-2-20KG"
        when(mappingRepository.findFirstByChannelAndNormalizedSkuAndStatus("SHOPIFY", "TOP0002-2-20KG", "ACTIVE"))
            .thenReturn(Optional.of(mapping));

        var resolution = resolver.resolve("SHOPIFY", "store", "top0002 - 2-20kg");

        assertThat(resolution.getType()).isEqualTo(ChannelSkuResolver.ResolutionType.PRODUCT);
        assertThat(resolution.getProduct().getId()).isEqualTo(1L);
    }

    @Test
    void layer3_BarcodeHit_AutoLearns() {
        when(mappingRepository.findByChannelAndExternalSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(mappingRepository.findFirstByChannelAndNormalizedSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.findByBarcode("TOP0002-2-20KG")).thenReturn(Optional.of(product));
        when(mappingRepository.existsByChannelAndExternalSku("SHOPIFY", "TOP0002-2-20KG")).thenReturn(false);

        var resolution = resolver.resolve("SHOPIFY", "store", "TOP0002-2-20KG");

        assertThat(resolution.getType()).isEqualTo(ChannelSkuResolver.ResolutionType.PRODUCT);
        // 自学：写回映射表，source=AUTO
        ArgumentCaptor<ChannelSkuMapping> captor = ArgumentCaptor.forClass(ChannelSkuMapping.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(ChannelSkuMapping.SOURCE_AUTO);
        assertThat(captor.getValue().getExternalSku()).isEqualTo("TOP0002-2-20KG");
    }

    @Test
    void layer4_NormalizedBarcodeHit() {
        when(mappingRepository.findByChannelAndExternalSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(mappingRepository.findFirstByChannelAndNormalizedSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.findByBarcode("TOP0002 - 2-20KG")).thenReturn(Optional.empty());
        when(productRepository.findByNormalizedBarcode("TOP0002-2-20KG")).thenReturn(Optional.of(product));
        when(mappingRepository.existsByChannelAndExternalSku(anyString(), anyString())).thenReturn(false);

        var resolution = resolver.resolve("SHOPIFY", "store", "TOP0002 - 2-20KG");

        assertThat(resolution.getType()).isEqualTo(ChannelSkuResolver.ResolutionType.PRODUCT);
        verify(mappingRepository).save(any(ChannelSkuMapping.class));
    }

    @Test
    void virtualMapping_ReturnsVirtual() {
        ChannelSkuMapping virtualMapping = ChannelSkuMapping.builder()
            .channel("SHOPIFY").externalSku("NOSKU::Shipping Protection")
            .normalizedSku("NOSKU::SHIPPINGPROTECTION")
            .mappingType(ChannelSkuMapping.TYPE_VIRTUAL)
            .status(ChannelSkuMapping.STATUS_ACTIVE).build();
        when(mappingRepository.findByChannelAndExternalSkuAndStatus("SHOPIFY", "NOSKU::Shipping Protection", "ACTIVE"))
            .thenReturn(Optional.of(virtualMapping));

        var resolution = resolver.resolve("SHOPIFY", "store", "NOSKU::Shipping Protection");

        assertThat(resolution.getType()).isEqualTo(ChannelSkuResolver.ResolutionType.VIRTUAL);
        assertThat(resolution.getProduct()).isNull();
    }

    @Test
    void allLayersMiss_ReturnsMiss() {
        when(mappingRepository.findByChannelAndExternalSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(mappingRepository.findFirstByChannelAndNormalizedSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.empty());
        when(productRepository.findByNormalizedBarcode(anyString())).thenReturn(Optional.empty());

        var resolution = resolver.resolve("SHOPIFY", "store", "UNKNOWN-SKU");

        assertThat(resolution.getType()).isEqualTo(ChannelSkuResolver.ResolutionType.MISS);
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void autoLearn_SkippedWhenMappingExists() {
        // 已有映射记录（可能被人工停用）——自学不得覆盖人工决策
        when(mappingRepository.findByChannelAndExternalSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(mappingRepository.findFirstByChannelAndNormalizedSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.findByBarcode("SKU-X")).thenReturn(Optional.of(product));
        when(mappingRepository.existsByChannelAndExternalSku("SHOPIFY", "SKU-X")).thenReturn(true);

        var resolution = resolver.resolve("SHOPIFY", "store", "SKU-X");

        assertThat(resolution.getType()).isEqualTo(ChannelSkuResolver.ResolutionType.PRODUCT);
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void normalizeRule() {
        assertThat(ChannelSkuMapping.normalize("top0002 - 2-20kg")).isEqualTo("TOP0002-2-20KG");
        assertThat(ChannelSkuMapping.normalize(" SYR0009 - 2 ")).isEqualTo("SYR0009-2");
        assertThat(ChannelSkuMapping.normalize(null)).isNull();
    }
}
