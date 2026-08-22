package com.wms.system.repository;

import com.wms.system.entity.SysPermissionRequestWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SysPermissionRequestWarehouseRepository
    extends JpaRepository<SysPermissionRequestWarehouse, Long> {

    List<SysPermissionRequestWarehouse> findByPermissionRequestIdOrderByWarehouseIdAsc(
        Long permissionRequestId
    );

    List<SysPermissionRequestWarehouse>
        findByCompanyIdAndPermissionRequestIdOrderByWarehouseIdAsc(
            Long companyId, Long permissionRequestId);

    List<SysPermissionRequestWarehouse> findByPermissionRequestIdIn(
        Collection<Long> permissionRequestIds
    );
}
