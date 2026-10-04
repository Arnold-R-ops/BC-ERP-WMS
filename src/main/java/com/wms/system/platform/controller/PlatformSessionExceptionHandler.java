package com.wms.system.platform.controller;

import com.wms.system.platform.service.PlatformSessionException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
@Order(-100)
public class PlatformSessionExceptionHandler {
    @ExceptionHandler(PlatformSessionException.class)
    public ResponseEntity<Map<String, String>> rejected(PlatformSessionException exception) {
        return ResponseEntity.status(401).body(Map.of("errorKey", exception.getErrorKey()));
    }
}
