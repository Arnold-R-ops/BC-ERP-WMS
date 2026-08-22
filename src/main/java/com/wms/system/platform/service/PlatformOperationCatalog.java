package com.wms.system.platform.service;

import java.util.*;

/** Fixed, server-owned operation vocabulary; never accepts arbitrary tables or fields. */
public final class PlatformOperationCatalog {
    private PlatformOperationCatalog() {}
    private static final Map<String, Set<String>> WRITE_FIELDS = Map.of(
        "product", Set.of("productName", "brand", "description"),
        "customer", Set.of("name", "contact", "phone", "email", "address", "creditLimit", "isActive")
    );
    public static boolean supports(String operation, String resource) {
        return ("WRITE".equals(operation) && WRITE_FIELDS.containsKey(resource))
            || ("DELETE".equals(operation) && Set.of("product", "customer").contains(resource));
    }
    public static void validate(String operation, String resource, Map<String, Object> payload) {
        if (!supports(operation, resource)) throw new IllegalArgumentException("Unsupported platform operation");
        if ("DELETE".equals(operation) && !payload.isEmpty()) {
            throw new IllegalArgumentException("Delete operations do not accept a payload");
        }
        if ("WRITE".equals(operation)) {
            if (payload.isEmpty() || !WRITE_FIELDS.get(resource).containsAll(payload.keySet())) {
                throw new IllegalArgumentException("Unsupported platform field");
            }
        }
    }
}
