package com.wms.system.repository;

import com.wms.system.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 供应商数据访问接口
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndCompanyIdAndIsDeletedFalse(Long id, Long companyId);

    /**
     * 根据供应商编码查询
     */
    Optional<Supplier> findByCode(String code);

    /**
     * 检查供应商编码是否已存在
     */
    boolean existsByCode(String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    /**
     * 根据供应商名称模糊查询
     */
    List<Supplier> findByNameContaining(String keyword);

    /**
     * 查询所有启用的供应商
     */
    List<Supplier> findByIsActiveTrue();

    List<Supplier> findAllByCompanyIdAndIsDeletedFalseOrderByNameAsc(Long companyId);

    List<Supplier> findAllByCompanyIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(Long companyId);

    /**
     * 根据供应商名称查询
     */
    Optional<Supplier> findByName(String name);
}
