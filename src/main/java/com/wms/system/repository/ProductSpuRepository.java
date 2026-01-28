package com.wms.system.repository;

import com.wms.system.entity.ProductSpu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ProductSpu Repository
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@Repository
public interface ProductSpuRepository extends JpaRepository<ProductSpu, Long> {

    /**
     * 根据 SPU 编码查询
     */
    Optional<ProductSpu> findBySpuCode(String spuCode);

    /**
     * 检查 SPU 编码是否存在
     */
    boolean existsBySpuCode(String spuCode);
}
