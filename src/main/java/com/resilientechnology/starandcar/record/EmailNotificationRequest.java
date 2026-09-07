package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailNotificationRequest(
        @NotBlank @Email String recipient,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String body,
        @Email String replyTo
) {
}