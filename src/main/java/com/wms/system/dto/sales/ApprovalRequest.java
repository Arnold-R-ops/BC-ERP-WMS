package com.wms.system.dto.sales;

import com.wms.system.entity.enums.AllocationPolicy;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Size(max = 500)
    private String comment;

    @Size(max = 500)
    private String reason;

    private AllocationPolicy allocationPolicy;

    private LocalDate requestedShipDate;

    private LocalDate promisedShipDate;
}
