package com.wms.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestReviewRequest {
    @NotNull
    private Boolean approved;

    @Size(max = 500)
    private String comment;
}

