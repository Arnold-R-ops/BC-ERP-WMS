package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Explicit confirmation for enabling or disabling an approved package. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleRuntimeStatusRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotBlank
    @Size(max = 50)
    private String confirmationCode;
}
