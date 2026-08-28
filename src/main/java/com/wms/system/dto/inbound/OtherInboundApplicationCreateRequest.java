package com.wms.system.dto.inbound;

import com.wms.system.entity.enums.OtherInboundBusinessType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class OtherInboundApplicationCreateRequest {
    @NotNull
    private OtherInboundBusinessType businessType;

    @NotBlank
    @Size(max = 120)
    private String sourceReference;

    @NotBlank
    @Size(max = 500)
    private String businessReason;

    @NotNull
    private Long warehouseId;

    @NotEmpty
    @Valid
    private List<OtherInboundApplicationItemRequest> items;

    @Size(max = 500)
    private String remark;
}
