package com.wms.system.signup.mail;

public interface VerificationMailSender {
    void sendVerificationCode(String normalizedEmail, String code, int validMinutes);
}
