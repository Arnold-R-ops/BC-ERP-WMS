package com.wms.system.service;

import com.wms.system.dto.productsku.CreateProductSkuRequest;
import com.wms.system.dto.productsku.ProductSkuResponse;
import com.wms.system.entity.Category;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSkuExpiryManagementServiceTest {

    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private ProductRepository productRepository;

    private ProductSkuService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new ProductSkuService(productSkuRepository, productRepository);

        Category category = Category.builder()
            .id(7L)
            .categoryCode("EXPIRY")
            .categoryName("Expiry")
            .enabled(true)
            .build();
        product = Product.builder()
            .id(11L)
            .productCode("SPU-EXPIRY")
            .productName("Expiry product")
            .category(category)
            .enabled(true)
            .build();

        when(productSkuRepository.save(any(ProductSku.class)))
            .thenAnswer(invocation -> {
                ProductSku saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(21L);
                }
                return saved;
            });
    }

    @Test
    void createDefaultsExpiryManagementToFalseWhenRequestOmitsIt() {
        when(productSkuRepository.existsByBarcode("BAR-21")).thenReturn(false);
        when(productRepository.findById(11L)).thenReturn(Optional.of(product));
        when(productSkuRepository.nextSkuCodeSequence()).thenReturn(21L);

        ProductSkuResponse response = service.createProductSku(request(null));

        ArgumentCaptor<ProductSku> captor = ArgumentCaptor.forClass(ProductSku.class);
        org.mockito.Mockito.verify(productSkuRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiryManagementEnabled()).isFalse();
        assertThat(response.getExpiryManagementEnabled()).isFalse();
    }

    @Test
    void createAndResponsePreserveExplicitOptIn() {
        when(productSkuRepository.existsByBarcode("BAR-21")).thenReturn(false);
        when(productRepository.findById(11L)).thenReturn(Optional.of(product));
        when(productSkuRepository.nextSkuCodeSequence()).thenReturn(21L);

        ProductSkuResponse response = service.createProductSku(request(true));

        assertThat(response.getExpiryManagementEnabled()).isTrue();
    }

    @Test
    void updateOnlyChangesExpiryManagementWhenRequestProvidesAValue() {
        ProductSku existing = ProductSku.builder()
            .id(21L)
            .skuCode("SKU00000021")
            .barcode("BAR-21")
            .name("Expiry SKU")
            .skuName("Expiry SKU")
            .product(product)
            .unitPrice(BigDecimal.TEN)
            .expiryManagementEnabled(true)
            .build();
        when(productSkuRepository.findById(21L)).thenReturn(Optional.of(existing));

        ProductSkuResponse unchanged = service.updateProductSku(21L, request(null));
        assertThat(unchanged.getExpiryManagementEnabled()).isTrue();

        ProductSkuResponse disabled = service.updateProductSku(21L, request(false));
        assertThat(disabled.getExpiryManagementEnabled()).isFalse();
    }

    private CreateProductSkuRequest request(Boolean expiryManagementEnabled) {
        return CreateProductSkuRequest.builder()
            .barcode("BAR-21")
            .name("Expiry SKU")
            .skuName("Expiry SKU")
            .productId(11L)
            .unitPrice(BigDecimal.TEN)
            .expiryManagementEnabled(expiryManagementEnabled)
            .build();
    }
}
