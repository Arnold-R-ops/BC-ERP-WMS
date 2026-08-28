package com.wms.system.dto.inbound;

import com.wms.system.entity.enums.OtherInboundBusinessType;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class OtherInboundFeaturePolicyResponse {
    private boolean enabled;
    private Set<OtherInboundBusinessType> allowedBusinessTypes;
}
