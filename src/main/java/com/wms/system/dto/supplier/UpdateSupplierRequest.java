package com.wms.system.dto.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
    @NotBlank(message = "supplier name is required")
    @Size(max = 200, message = "supplier name must be at most 200 characters")
    String name,

    @Size(max = 100, message = "supplier contact must be at most 100 characters")
    String contact,

    @Size(max = 255, message = "supplier address must be at most 255 characters")
    String address,

    @Email(message = "supplier email format is invalid")
    @Size(max = 100, message = "supplier email must be at most 100 characters")
    String email,

    @Size(max = 50, message = "supplier phone must be at most 50 characters")
    String phone,

    @Size(max = 500, message = "supplier remark must be at most 500 characters")
    String remark
) {
}
