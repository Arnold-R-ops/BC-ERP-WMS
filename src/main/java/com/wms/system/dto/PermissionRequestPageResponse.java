package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestPageResponse {
    private List<PermissionRequestDTO> items;
    private long total;
    private int page;
    private int size;
}

