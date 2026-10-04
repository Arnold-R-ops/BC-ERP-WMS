package com.wms.system.tenant.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Converter @Component @RequiredArgsConstructor
public class TenantCredentialConverter implements AttributeConverter<String, String> {
    private final TenantCredentialCrypto crypto;
    @Override public String convertToDatabaseColumn(String value) { return crypto.protect(value); }
    @Override public String convertToEntityAttribute(String value) { return crypto.reveal(value); }
}
