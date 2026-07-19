package com.wms.system.service;

import com.wms.system.dto.productsku.CreateProductSkuRequest;
import com.wms.system.dto.productsku.ProductSkuResponse;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Product;
import com.wms.system.entity.enums.BatchTrackingMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.ProductRepository;
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
public class ProductSkuService {

    private final ProductSkuRepository productSkuRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ProductSkuResponse createProductSku(CreateProductSkuRequest request) {
        if (productSkuRepository.existsByBarcode(request.getBarcode())) {
            throw new BusinessException(ErrorKeys.PRODUCT_SKU_ALREADY_EXISTS, Map.of("barcode", request.getBarcode()));
        }

        Product productMaster = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("productId", request.getProductId())));
        ensureProductEnabled(productMaster);

        long sequence = productSkuRepository.nextSkuCodeSequence();

        ProductSku product = ProductSku.builder()
            .skuCode(formatSkuCode(sequence))
            .barcode(request.getBarcode())
            .name(request.getName())
            .skuName(request.getSkuName())
            .product(productMaster)
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
            .supplier(request.getSupplier())
            .description(request.getDescription())
            .batchTrackingMode(request.getBatchTrackingMode() != null
                ? request.getBatchTrackingMode()
                : BatchTrackingMode.PRINTED_LABEL)
            .enabled(request.getEnabled() != null ? request.getEnabled() : true)
            .isDeleted(false)
            .build();

        product = productSkuRepository.save(product);
        log.info("创建商品成功: id={}, barcode={}", product.getId(), product.getBarcode());
        return convertToResponse(product);
    }

    public ProductSkuResponse getProductSku(Long id) {
        ProductSku product = productSkuRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND, Map.of("id", id)));
        return convertToResponse(product);
    }

    public List<ProductSkuResponse> listProductSkus(Boolean enabledOnly, Long productId) {
        List<ProductSku> products;
        if (productId != null) {
            productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("productId", productId)));
            products = Boolean.TRUE.equals(enabledOnly)
                ? productSkuRepository.findAllByProduct_IdAndEnabledTrueOrderBySkuCodeAsc(productId)
                : productSkuRepository.findAllByProduct_IdOrderBySkuCodeAsc(productId);
        } else {
            products = Boolean.TRUE.equals(enabledOnly)
                ? productSkuRepository.findByEnabledTrue()
                : productSkuRepository.findAll();
        }
        return products.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Transactional
    public ProductSkuResponse updateProductSku(Long id, CreateProductSkuRequest request) {
        ProductSku product = productSkuRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND, Map.of("id", id)));

        if (!product.getBarcode().equals(request.getBarcode())
                && productSkuRepository.existsByBarcode(request.getBarcode())) {
            throw new BusinessException(ErrorKeys.PRODUCT_SKU_ALREADY_EXISTS, Map.of("barcode", request.getBarcode()));
        }

        if (!product.getProduct().getId().equals(request.getProductId())) {
            Product productMaster = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("productId", request.getProductId())));
            ensureProductEnabled(productMaster);
            product.setProduct(productMaster);
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
        if (request.getSupplier() != null)      product.setSupplier(request.getSupplier());
        if (request.getDescription() != null)   product.setDescription(request.getDescription());
        if (request.getBatchTrackingMode() != null) product.setBatchTrackingMode(request.getBatchTrackingMode());
        if (request.getEnabled() != null)       product.setEnabled(request.getEnabled());

        product = productSkuRepository.save(product);
        log.info("更新商品成功: id={}, barcode={}", product.getId(), product.getBarcode());
        return convertToResponse(product);
    }

    @Transactional
    public ProductSkuResponse activate(Long id) {
        ProductSku productSku = requireProductSku(id);
        ensureProductEnabled(productSku.getProduct());
        productSku.setEnabled(true);
        return convertToResponse(productSkuRepository.save(productSku));
    }

    @Transactional
    public ProductSkuResponse deactivate(Long id) {
        ProductSku productSku = requireProductSku(id);
        productSku.setEnabled(false);
        return convertToResponse(productSkuRepository.save(productSku));
    }

    private ProductSku requireProductSku(Long id) {
        return productSkuRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND, Map.of("productSkuId", id)));
    }

    private void ensureProductEnabled(Product product) {
        if (!Boolean.TRUE.equals(product.getEnabled())) {
            throw new BusinessException(
                ErrorKeys.PRODUCT_DISABLED,
                Map.of("productId", product.getId())
            );
        }
    }

    private ProductSkuResponse convertToResponse(ProductSku product) {
        return ProductSkuResponse.builder()
            .id(product.getId())
            .version(product.getVersion())
            .skuCode(product.getSkuCode())
            .barcode(product.getBarcode())
            .name(product.getName())
            .skuName(product.getSkuName())
            .specs(product.getSpecs())
            .specification(product.getSpecification())
            .productId(product.getProduct().getId())
            .productName(product.getProduct().getProductName())
            .unitPrice(product.getUnitPrice())
            .minSalesPrice(product.getMinSalesPrice())
            .minStock(product.getMinStock())
            .safetyStock(product.getSafetyStock())
            .leadTime(product.getLeadTime())
            .perPackQty(product.getPerPackQty())
            .conversionRate(product.getConversionRate())
            .packUnit(product.getPackUnit())
            .nearExpiryDays(product.getNearExpiryDays())
            .categoryId(product.getProduct().getCategory().getId())
            .categoryCode(product.getProduct().getCategory().getCategoryCode())
            .categoryName(product.getProduct().getCategory().getCategoryName())
            .supplier(product.getSupplier())
            .description(product.getDescription())
            .batchTrackingMode(product.getBatchTrackingMode())
            .enabled(product.getEnabled())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }

    static String formatSkuCode(long sequence) {
        return "SKU" + String.format("%08d", sequence);
    }
}
