package com.wms.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestCreateRequest {
    @NotNull
    @Positive
    private Long targetUserId;

    @NotNull
    @Positive
    private Long requestedRoleId;

    @Size(max = 100)
    private List<@Positive Long> warehouseIds;

    @Size(max = 500)
    private String requestReason;
}

