package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for copying an effective permission snapshot into a custom role. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCopyRequest {

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,49}$")
    private String roleCode;

    @NotBlank
    @Size(max = 100)
    private String roleName;

    /** Required business purpose for the new permission package. */
    @NotBlank
    @Size(max = 500)
    private String description;

    @NotBlank
    @Size(min = 64, max = 64)
    private String snapshotFingerprint;

    private Boolean riskAcknowledged;

    @Size(max = 50)
    private String confirmationCode;

    @Size(max = 500)
    private String operationReason;
}

