package com.wms.system.dto.inbound;

import com.wms.system.entity.enums.OtherInboundBusinessType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class OtherInboundFeaturePolicyUpdateRequest {
    private boolean enabled;

    @NotEmpty
    private Set<OtherInboundBusinessType> allowedBusinessTypes;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @AssertTrue
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true")
    private boolean confirmed;
}
