package com.wms.system.signup.mail;

import com.wms.system.signup.config.SignupProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "wms.signup.delivery-mode", havingValue = "DEVELOPMENT", matchIfMissing = true)
public class DevelopmentVerificationMailbox implements VerificationMailSender {
    private final ConcurrentHashMap<String, DevelopmentMessage> messages = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String normalizedEmail, String code, int validMinutes) {
        messages.put(normalizedEmail, new DevelopmentMessage(code, OffsetDateTime.now(), validMinutes));
    }

    public Optional<DevelopmentMessage> latest(String normalizedEmail) {
        return Optional.ofNullable(messages.get(normalizedEmail));
    }

    public record DevelopmentMessage(String code, OffsetDateTime createdAt, int validMinutes) {}
}
