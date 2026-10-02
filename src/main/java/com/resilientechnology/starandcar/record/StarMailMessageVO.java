package com.resilientechnology.starandcar.record;

import com.resilientechnology.starandcar.entity.StarMailMessage;

import java.time.LocalDateTime;

/**
 * Public view of a StarMail message as delivered to a browser.
 *
 * <p>Contains no e-mail addresses by construction - recipients are identified by device id
 * only. {@code emailCopyStatus} is included so the sender/recipient can see whether the
 * independent registered-e-mail copy went out, without ever exposing that address.</p>
 */
public record StarMailMessageVO(
        Long messageId,
        String senderDeviceId,
        String recipientDeviceId,
        String subject,
        String body,
        LocalDateTime sentDate,
        String localDeliveryStatus,
        String emailCopyStatus,
        String emailCopyDetail
) {

    public static StarMailMessageVO from(StarMailMessage message) {
        return new StarMailMessageVO(
                message.getMessageId(),
                message.getSenderDeviceId(),
                message.getRecipientDeviceId(),
                message.getSubject(),
                message.getBody(),
                message.getSentDate(),
                message.getLocalDeliveryStatus(),
                message.getEmailCopyStatus(),
                message.getEmailCopyDetail());
    }
}
