package com.resilientechnology.starandcar.service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailNotificationService {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${starandcar.mail.from:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String recipient, String subject, String body, String replyTo) {
        if (!StringUtils.hasText(fromAddress)) {
            throw new IllegalStateException("MAIL_FROM or MAIL_USERNAME must be configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body + "\n\nSent from StarMail - Starandcar.com");
        if (StringUtils.hasText(replyTo)) {
            message.setReplyTo(replyTo);
        }
        mailSender.send(message);
    }
}