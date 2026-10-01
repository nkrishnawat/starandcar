package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A STARMail message travelling from a buyer's browser to an owner's browser.
 * It is only relayed live - it is never persisted on the server.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class MailMessageVO {

    /**
     * Set by the server when a message is queued - used to prune the spool
     * entry once the receiver's browser confirms it holds the message.
     */
    String messageId;

    Long listingId;

    @Size(max = 500, message = "Max 500 chars allowed.")
    String listingAddress;

    @NotBlank(message = "Sender email is required.")
    @Size(max = 320, message = "Max 320 chars allowed.")
    String from;

    @NotBlank(message = "Receiver email is required.")
    @Size(max = 320, message = "Max 320 chars allowed.")
    String to;

    @NotBlank(message = "Subject is required.")
    @Size(max = 200, message = "Max 200 chars allowed.")
    String subject;

    @NotBlank(message = "Message is required.")
    @Size(max = 5000, message = "Max 5000 chars allowed.")
    String body;

    @Size(max = 40, message = "Max 40 chars allowed.")
    String sentAt;
}
