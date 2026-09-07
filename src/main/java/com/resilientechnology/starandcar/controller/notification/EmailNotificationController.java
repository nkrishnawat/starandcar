package com.resilientechnology.starandcar.controller.notification;

import com.resilientechnology.starandcar.record.EmailNotificationRequest;
import com.resilientechnology.starandcar.service.notification.EmailNotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("notifications")
public class EmailNotificationController {
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationController.class);

    private final EmailNotificationService emailNotificationService;

    public EmailNotificationController(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @PostMapping("email")
    public ResponseEntity<Void> sendEmail(@Valid @RequestBody EmailNotificationRequest request) {
        try {
            emailNotificationService.send(
                    request.recipient(),
                    request.subject(),
                    request.body(),
                    request.replyTo());
            return ResponseEntity.accepted().build();
        } catch (MailException | IllegalStateException exception) {
            logger.warn("StarMail email delivery failed: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}