package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/** Creates an independent disabled custom permission-package draft. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePackageCreateRequest {

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,49}$")
    private String roleCode;

    @NotBlank
    @Size(max = 100)
    private String roleName;

    /** Required business purpose for approval and later audit. */
    @NotBlank
    @Size(max = 500)
    private String description;

    /** Direct business permissions selected in the creation form. */
    @NotNull
    private Set<Long> permissionIds;
}
