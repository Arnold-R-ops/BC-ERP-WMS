package com.wms.system.dto.inbound;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtherInboundActionRequest {
    @NotNull
    private Long version;

    @Size(max = 500)
    private String reason;
}
