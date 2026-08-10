package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/** Editable fields for a disabled custom permission-package draft. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePackageDraftRequest {
    @NotBlank
    @Size(max = 100)
    private String roleName;

    @NotBlank
    @Size(max = 500)
    private String description;

    @NotNull
    private Set<Long> permissionIds;

    @Size(max = 500)
    private String reason;
}
