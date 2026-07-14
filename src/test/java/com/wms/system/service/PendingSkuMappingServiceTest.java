package com.wms.system.service;

import com.wms.system.entity.ChannelSkuMapping;
import com.wms.system.entity.PendingSkuMapping;
import com.wms.system.entity.Product;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ChannelSkuMappingRepository;
import com.wms.system.repository.PendingSkuMappingRepository;
import com.wms.system.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PendingSkuMappingService 单元测试（P1 批次2）
 */
@ExtendWith(MockitoExtension.class)
class PendingSkuMappingServiceTest {

    @Mock
    private PendingSkuMappingRepository pendingRepository;

    @Mock
    private ChannelSkuMappingRepository mappingRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PendingSkuMappingService service;

    private PendingSkuMapping pending;
    private Product product;

    @BeforeEach
    void setUp() {
        pending = PendingSkuMapping.builder()
            .id(1L).channel("SHOPIFY").storeIdentifier("store.myshopify.com")
            .externalSku("TOP0002 - 2-20KG").externalTitle("Tapioca Pearl 20KG")
            .occurrenceCount(3).status(PendingSkuMapping.STATUS_PENDING)
            .build();
        product = Product.builder().id(9L).barcode("TOP0002").name("木薯珍珠").build();
    }

    @Test
    void recordMiss_CreatesNewEntry() {
        when(pendingRepository.findByChannelAndExternalSku("SHOPIFY", "NEW-SKU")).thenReturn(Optional.empty());

        service.recordMiss("SHOPIFY", "store", "NEW-SKU", "New Product", new BigDecimal("9.99"), "#1001");

        ArgumentCaptor<PendingSkuMapping> captor = ArgumentCaptor.forClass(PendingSkuMapping.class);
        verify(pendingRepository).save(captor.capture());
        assertThat(captor.getValue().getExternalSku()).isEqualTo("NEW-SKU");
        assertThat(captor.getValue().getOccurrenceCount()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(PendingSkuMapping.STATUS_PENDING);
    }

    @Test
    void recordMiss_IncrementsExistingCounter() {
        when(pendingRepository.findByChannelAndExternalSku("SHOPIFY", "TOP0002 - 2-20KG"))
            .thenReturn(Optional.of(pending));

        service.recordMiss("SHOPIFY", "store", "TOP0002 - 2-20KG", "Tapioca", null, "#1002");

        assertThat(pending.getOccurrenceCount()).isEqualTo(4);
        verify(pendingRepository).save(pending);
    }

    @Test
    void recordMiss_ReopensResolvedEntry() {
        // 已解决的 SKU 再漏进来 = 映射被停用，需要重新引起注意
        pending.setStatus(PendingSkuMapping.STATUS_RESOLVED);
        when(pendingRepository.findByChannelAndExternalSku(anyString(), anyString()))
            .thenReturn(Optional.of(pending));

        service.recordMiss("SHOPIFY", "store", "TOP0002 - 2-20KG", null, null, "#1003");

        assertThat(pending.getStatus()).isEqualTo(PendingSkuMapping.STATUS_PENDING);
    }

    @Test
    void recordMiss_KeepsIgnoredSilent() {
        // IGNORED 是人工明确决定：只累计数，不改状态
        pending.setStatus(PendingSkuMapping.STATUS_IGNORED);
        when(pendingRepository.findByChannelAndExternalSku(anyString(), anyString()))
            .thenReturn(Optional.of(pending));

        service.recordMiss("SHOPIFY", "store", "TOP0002 - 2-20KG", null, null, "#1004");

        assertThat(pending.getStatus()).isEqualTo(PendingSkuMapping.STATUS_IGNORED);
        assertThat(pending.getOccurrenceCount()).isEqualTo(4);
    }

    @Test
    void resolve_Map_CreatesMappingAndCloses() {
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(pendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(mappingRepository.findByChannelAndExternalSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        PendingSkuMapping result = service.resolve(1L, "MAP", 9L, 20, 5L);

        ArgumentCaptor<ChannelSkuMapping> captor = ArgumentCaptor.forClass(ChannelSkuMapping.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getMappingType()).isEqualTo(ChannelSkuMapping.TYPE_PRODUCT);
        assertThat(captor.getValue().getProduct().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getQuantityRatio()).isEqualTo(20);
        assertThat(captor.getValue().getSource()).isEqualTo(ChannelSkuMapping.SOURCE_MANUAL);
        assertThat(captor.getValue().getNormalizedSku()).isEqualTo("TOP0002-2-20KG");

        assertThat(result.getStatus()).isEqualTo(PendingSkuMapping.STATUS_RESOLVED);
        assertThat(result.getResolution()).isEqualTo(PendingSkuMapping.RESOLUTION_MAPPED);
        assertThat(result.getResolvedBy()).isEqualTo(5L);
    }

    @Test
    void resolve_Map_RequiresProductId() {
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.resolve(1L, "MAP", null, null, 5L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PARAMETER_REQUIRED);
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void resolve_Virtual_CreatesVirtualMapping() {
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(pendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mappingRepository.findByChannelAndExternalSkuAndStatus(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        PendingSkuMapping result = service.resolve(1L, "VIRTUAL", null, null, 5L);

        ArgumentCaptor<ChannelSkuMapping> captor = ArgumentCaptor.forClass(ChannelSkuMapping.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getMappingType()).isEqualTo(ChannelSkuMapping.TYPE_VIRTUAL);
        assertThat(captor.getValue().getProduct()).isNull();
        assertThat(result.getResolution()).isEqualTo(PendingSkuMapping.RESOLUTION_VIRTUAL);
    }

    @Test
    void resolve_Ignore_NoMappingCreated() {
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(pendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingSkuMapping result = service.resolve(1L, "IGNORE", null, null, 5L);

        verify(mappingRepository, never()).save(any());
        assertThat(result.getStatus()).isEqualTo(PendingSkuMapping.STATUS_IGNORED);
    }

    @Test
    void resolve_InvalidAction_Throws() {
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.resolve(1L, "NONSENSE", null, null, 5L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);
    }

    @Test
    void suggestions_UsesFirstCodeToken() {
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(productRepository.findTop10ByBarcodeStartingWithIgnoreCaseOrSkuNameContainingIgnoreCase("TOP0002", "TOP0002"))
            .thenReturn(java.util.List.of(product));

        var result = service.suggestions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(9L);
    }
}
