package com.wms.system.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Basic identity fields shown in SKU-level inventory views. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuInfoDto {
    private String image;
    private String name;
    private String skuCode;
    private String barcode;
    private String specs;
}
