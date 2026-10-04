package com.wms.system.tenant.web;

import com.wms.system.tenant.security.TenantAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantSecurityExceptionHandler {
    @ExceptionHandler(TenantAccessException.class)
    public ResponseEntity<Map<String, String>> denied(TenantAccessException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("errorKey", exception.errorKey()));
    }
}
