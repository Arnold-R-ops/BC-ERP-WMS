package com.wms.system.repository;

import com.wms.system.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product Repository
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 根据 SPU 编码查询
     */
    Optional<Product> findByProductCode(String productCode);

    /**
     * 检查 SPU 编码是否存在
     */
    boolean existsByProductCode(String productCode);

    boolean existsByProductCodeAndIdNot(String productCode, Long id);

    @EntityGraph(attributePaths = {"category", "category.parent", "skus"})
    List<Product> findAllByOrderByProductNameAsc();

    @EntityGraph(attributePaths = {"category", "category.parent", "skus"})
    List<Product> findAllByEnabledTrueOrderByProductNameAsc();

    long countByCategoryId(Long categoryId);
}
