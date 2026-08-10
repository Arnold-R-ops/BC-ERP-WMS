package com.wms.system.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Submits a draft to the built-in simple approval template. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleReviewSubmitRequest {
    @Size(max = 500)
    private String reason;
}
