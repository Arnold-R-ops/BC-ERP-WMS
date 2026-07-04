package com.wms.system.dto;

import com.wms.system.entity.Location;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.LocationStatus;
import com.wms.system.entity.enums.Zone;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LocationResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseCode;
    private final Zone zone;
    private final String shelfNumber;
    private final String positionNumber;
    private final String locationCode;
    private final Boolean enabled;
    private final LocationStatus status;
    private final Integer posX;
    private final Integer posY;
    private final String remark;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static LocationResponse from(Location location) {
        Warehouse warehouse = location.getWarehouse();

        return LocationResponse.builder()
            .id(location.getId())
            .warehouseId(warehouse == null ? null : warehouse.getId())
            .warehouseCode(location.getWarehouseCode())
            .zone(location.getZone())
            .shelfNumber(location.getShelfNumber())
            .positionNumber(location.getPositionNumber())
            .locationCode(location.getLocationCode())
            .enabled(location.getEnabled())
            .status(location.getStatus())
            .posX(location.getPosX())
            .posY(location.getPosY())
            .remark(location.getRemark())
            .createdAt(location.getCreatedAt())
            .updatedAt(location.getUpdatedAt())
            .build();
    }
}
