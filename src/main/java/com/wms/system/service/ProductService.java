package com.wms.system.service;

import com.wms.system.dto.product.CreateProductRequest;
import com.wms.system.dto.product.ProductResponse;
import com.wms.system.dto.product.UpdateProductRequest;
import com.wms.system.entity.Category;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.CategoryRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final long DEFAULT_COMPANY_ID = 1L;

    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String code = request.productCode().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsByProductCode(code)) {
            throw new BusinessException(ErrorKeys.PRODUCT_ALREADY_EXISTS, Map.of("productCode", code));
        }

        Category category = requireLeafCategory(request.categoryId());
        Product product = Product.builder()
            .productCode(code)
            .productName(request.productName().trim())
            .category(category)
            .brand(normalize(request.brand()))
            .description(normalize(request.description()))
            .enabled(request.enabled() == null || request.enabled())
            .build();
        product.setCompanyId(DEFAULT_COMPANY_ID);
        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(requireProduct(id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(boolean enabledOnly) {
        List<Product> products = enabledOnly
            ? productRepository.findAllByEnabledTrueOrderByProductNameAsc()
            : productRepository.findAllByOrderByProductNameAsc();
        return products.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = requireProduct(id);
        if (request.productName() != null) {
            product.setProductName(request.productName().trim());
        }
        if (request.categoryId() != null && !request.categoryId().equals(product.getCategory().getId())) {
            product.setCategory(requireLeafCategory(request.categoryId()));
        }
        if (request.brand() != null) {
            product.setBrand(normalize(request.brand()));
        }
        if (request.description() != null) {
            product.setDescription(normalize(request.description()));
        }
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse activate(Long id) {
        Product product = requireProduct(id);
        if (!Boolean.TRUE.equals(product.getCategory().getEnabled())) {
            throw new BusinessException(
                ErrorKeys.PRODUCT_CATEGORY_INVALID,
                Map.of("productId", id, "categoryId", product.getCategory().getId())
            );
        }
        product.setEnabled(true);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse deactivate(Long id) {
        Product product = requireProduct(id);
        product.setEnabled(false);
        List<ProductSku> skus = product.getSkus();
        skus.forEach(sku -> sku.setEnabled(false));
        if (!skus.isEmpty()) {
            productSkuRepository.saveAll(skus);
        }
        return toResponse(productRepository.save(product));
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, Map.of("productId", id)));
    }

    private Category requireLeafCategory(Long categoryId) {
        Category category = categoryRepository.findByIdAndCompanyId(categoryId, DEFAULT_COMPANY_ID)
            .orElseThrow(() -> new BusinessException(ErrorKeys.CATEGORY_NOT_FOUND, Map.of("categoryId", categoryId)));
        if (category.getParent() == null || !Boolean.TRUE.equals(category.getEnabled())) {
            throw new BusinessException(
                ErrorKeys.PRODUCT_CATEGORY_INVALID,
                Map.of("categoryId", categoryId, "requiredLevel", 2)
            );
        }
        return category;
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        Category parent = category.getParent();
        return new ProductResponse(
            product.getId(),
            product.getVersion(),
            product.getProductCode(),
            product.getProductName(),
            category.getId(),
            category.getCategoryCode(),
            category.getCategoryName(),
            parent == null ? null : parent.getId(),
            parent == null ? null : parent.getCategoryName(),
            product.getBrand(),
            product.getDescription(),
            product.getEnabled(),
            product.getSkus().size(),
            product.getEnabledSkuCount(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
