package com.wms.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Approves or rejects a pending custom permission package. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleReviewRequest {
    @NotNull
    private Boolean approved;

    @Size(max = 500)
    private String comment;
}
