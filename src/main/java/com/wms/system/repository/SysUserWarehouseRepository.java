package com.wms.system.repository;

import com.wms.system.entity.SysUserWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface SysUserWarehouseRepository extends JpaRepository<SysUserWarehouse, Long> {

    List<SysUserWarehouse> findByUserIdOrderByWarehouseId(Long userId);

    @Query("SELECT suw.warehouseId FROM SysUserWarehouse suw WHERE suw.userId = :userId")
    Set<Long> findWarehouseIdsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT suw.warehouseId
        FROM SysUserWarehouse suw
        WHERE suw.companyId = :companyId AND suw.userId = :userId
        """)
    Set<Long> findWarehouseIdsByCompanyIdAndUserId(
        @Param("companyId") Long companyId,
        @Param("userId") Long userId
    );

    @Modifying
    @Query("DELETE FROM SysUserWarehouse suw WHERE suw.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM SysUserWarehouse suw WHERE suw.companyId = :companyId AND suw.userId = :userId")
    void deleteByCompanyIdAndUserId(
        @Param("companyId") Long companyId,
        @Param("userId") Long userId
    );
}
