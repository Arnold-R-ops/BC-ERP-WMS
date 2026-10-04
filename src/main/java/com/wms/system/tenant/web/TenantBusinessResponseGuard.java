package com.wms.system.tenant.web;

import com.wms.system.tenant.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Recheck successful in-flight business reads immediately before response serialization. */
@RestControllerAdvice @RequiredArgsConstructor
public class TenantBusinessResponseGuard implements ResponseBodyAdvice<Object> {
    private final TenantSecurityStateService states;
    public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        var scope=TenantExecutionScope.current();
        int status=response instanceof ServletServerHttpResponse servlet ? servlet.getServletResponse().getStatus() : 200;
        if(scope!=null && scope.purpose()==TenantOperationPurpose.BUSINESS && status>=200 && status<300)
            states.require(scope.tenantId(),TenantOperationPurpose.BUSINESS,scope.sessionVersion());
        return body;
    }
}
