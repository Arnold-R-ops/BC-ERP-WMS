package com.wms.system.platform.dto;

import java.util.List;
import java.util.Map;

public record PlatformDataPage(
    String resource,
    int page,
    int size,
    long totalElements,
    int totalPages,
    List<Map<String, Object>> content
) {}
