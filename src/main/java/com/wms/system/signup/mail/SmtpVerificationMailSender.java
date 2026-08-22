package com.wms.system.signup.mail;

import com.wms.system.signup.config.SignupProperties;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wms.signup.delivery-mode", havingValue = "SMTP")
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final JavaMailSender mailSender;
    private final SignupProperties properties;

    public SmtpVerificationMailSender(JavaMailSender mailSender, SignupProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String normalizedEmail, String code, int validMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(properties.getFromAddress(), properties.getFromName());
            helper.setTo(normalizedEmail);
            helper.setSubject("BCWMS 邮箱验证码");
            helper.setText("您的 BCWMS 验证码是：" + code + "。验证码将在 "
                + validMinutes + " 分钟后失效。若非本人操作，请忽略此邮件。", false);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Verification email delivery failed", exception);
        }
    }
}
