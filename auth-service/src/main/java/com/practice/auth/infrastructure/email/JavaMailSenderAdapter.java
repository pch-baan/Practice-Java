package com.practice.auth.infrastructure.email;

import com.practice.auth.application.port.out.IEmailPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Primary
@Component
@RequiredArgsConstructor
@Conditional(MailHostConfiguredCondition.class)
public class JavaMailSenderAdapter implements IEmailPort {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public void sendVerificationEmail(String to, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Verify your email address");
        message.setText(
                "Click the link below to verify your account:\n\n"
                + baseUrl + "/api/v1/auth/verify-email?token=" + rawToken
                + "\n\nThis link expires in 24 hours.\n\n"
                + "If you did not register, please ignore this email."
        );
        mailSender.send(message);
    }
}
