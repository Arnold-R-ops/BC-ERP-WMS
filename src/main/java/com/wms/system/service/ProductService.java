package com.wms.system.service;

import com.wms.system.dto.product.CreateProductRequest;
import com.wms.system.dto.product.ProductResponse;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSpu;
import com.wms.system.entity.enums.BatchTrackingMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSpuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSpuRepository productSpuRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsByBarcode(request.getBarcode())) {
            throw new BusinessException(ErrorKeys.PRODUCT_ALREADY_EXISTS, Map.of("barcode", request.getBarcode()));
        }

        ProductSpu spu = productSpuRepository.findById(request.getSpuId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SPU_NOT_FOUND, Map.of("spuId", request.getSpuId())));

        Product product = Product.builder()
            .barcode(request.getBarcode())
            .name(request.getName())
            .skuName(request.getSkuName())
            .spu(spu)
            .specs(request.getSpecs())
            .specification(request.getSpecification())
            .unitPrice(request.getUnitPrice())
            .minSalesPrice(request.getMinSalesPrice() != null ? request.getMinSalesPrice() : java.math.BigDecimal.ZERO)
            .minStock(request.getMinStock() != null ? request.getMinStock() : 0)
            .safetyStock(request.getSafetyStock() != null ? request.getSafetyStock() : 0)
            .leadTime(request.getLeadTime() != null ? request.getLeadTime() : 7)
            .perPackQty(request.getPerPackQty() != null ? request.getPerPackQty() : 1)
            .conversionRate(request.getConversionRate() != null ? request.getConversionRate() : 1)
            .packUnit(request.getPackUnit() != null ? request.getPackUnit() : "Box")
            .nearExpiryDays(request.getNearExpiryDays() != null ? request.getNearExpiryDays() : 90)
            .category(request.getCategory())
            .supplier(request.getSupplier())
            .description(request.getDescription())
            .batchTrackingMode(request.getBatchTrackingMode() != null
                ? request.getBatchTrackingMode()
                : BatchTrackingMode.PRINTED_LABEL)
            .enabled(request.getEnabled() != null ? request.getEnabled() : true)
            .isDeleted(false)
            .build();

        product = productRepository.save(product);
        log.info("创建商品成功: id={}, barcode={}", product.getId(), product.getBarcode());
        return convertToResponse(product);
    }

    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("id", id)));
        return convertToResponse(product);
    }

    public List<ProductResponse> listProducts(Boolean enabledOnly) {
        List<Product> products = Boolean.TRUE.equals(enabledOnly)
            ? productRepository.findByEnabledTrue()
            : productRepository.findAll();
        return products.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse updateProduct(Long id, CreateProductRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("id", id)));

        if (!product.getBarcode().equals(request.getBarcode())
                && productRepository.existsByBarcode(request.getBarcode())) {
            throw new BusinessException(ErrorKeys.PRODUCT_ALREADY_EXISTS, Map.of("barcode", request.getBarcode()));
        }

        if (!product.getSpu().getId().equals(request.getSpuId())) {
            ProductSpu spu = productSpuRepository.findById(request.getSpuId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SPU_NOT_FOUND, Map.of("spuId", request.getSpuId())));
            product.setSpu(spu);
        }

        product.setBarcode(request.getBarcode());
        product.setName(request.getName());
        product.setSkuName(request.getSkuName());
        if (request.getSpecs() != null)         product.setSpecs(request.getSpecs());
        if (request.getSpecification() != null) product.setSpecification(request.getSpecification());
        if (request.getUnitPrice() != null)     product.setUnitPrice(request.getUnitPrice());
        if (request.getMinSalesPrice() != null) product.setMinSalesPrice(request.getMinSalesPrice());
        if (request.getMinStock() != null)      product.setMinStock(request.getMinStock());
        if (request.getSafetyStock() != null)   product.setSafetyStock(request.getSafetyStock());
        if (request.getLeadTime() != null)      product.setLeadTime(request.getLeadTime());
        if (request.getPerPackQty() != null)    product.setPerPackQty(request.getPerPackQty());
        if (request.getConversionRate() != null) product.setConversionRate(request.getConversionRate());
        if (request.getPackUnit() != null)      product.setPackUnit(request.getPackUnit());
        if (request.getNearExpiryDays() != null) product.setNearExpiryDays(request.getNearExpiryDays());
        if (request.getCategory() != null)      product.setCategory(request.getCategory());
        if (request.getSupplier() != null)      product.setSupplier(request.getSupplier());
        if (request.getDescription() != null)   product.setDescription(request.getDescription());
        if (request.getBatchTrackingMode() != null) product.setBatchTrackingMode(request.getBatchTrackingMode());
        if (request.getEnabled() != null)       product.setEnabled(request.getEnabled());

        product = productRepository.save(product);
        log.info("更新商品成功: id={}, barcode={}", product.getId(), product.getBarcode());
        return convertToResponse(product);
    }

    private ProductResponse convertToResponse(Product product) {
        return ProductResponse.builder()
            .id(product.getId())
            .version(product.getVersion())
            .barcode(product.getBarcode())
            .name(product.getName())
            .skuName(product.getSkuName())
            .specs(product.getSpecs())
            .specification(product.getSpecification())
            .spuId(product.getSpu().getId())
            .spuName(product.getSpu().getSpuName())
            .unitPrice(product.getUnitPrice())
            .minSalesPrice(product.getMinSalesPrice())
            .minStock(product.getMinStock())
            .safetyStock(product.getSafetyStock())
            .leadTime(product.getLeadTime())
            .perPackQty(product.getPerPackQty())
            .conversionRate(product.getConversionRate())
            .packUnit(product.getPackUnit())
            .nearExpiryDays(product.getNearExpiryDays())
            .category(product.getCategory())
            .supplier(product.getSupplier())
            .description(product.getDescription())
            .batchTrackingMode(product.getBatchTrackingMode())
            .enabled(product.getEnabled())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }
}
