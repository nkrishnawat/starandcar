package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A STARMail message travelling from a sender's browser to a recipient's browser.
 *
 * <p>Addressing is done with the <strong>MAC address / DeviceID / MachineID</strong> of the
 * two endpoints - never with e-mail addresses. E-mail is used only as a fallback for listings
 * published before device ids existed: the message then travels to the owner's contact
 * address, resolved server-side in MariaDB and never exposed to clients.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class MailMessageVO {

    /** Assigned by the server when the message is first queued. */
    String messageId;

    Long listingId;

    @Size(max = 500, message = "Max 500 chars allowed.")
    String listingAddress;

    /** Sender MAC address / DeviceID / MachineID. */
    @NotBlank(message = "Sender device id is required.")
    @Size(max = 128, message = "Max 128 chars allowed.")
    String fromDeviceId;

    /**
     * Recipient MAC address / DeviceID / MachineID - the routing key for the browser leg.
     *
     * <p>Optional. A listing published before device ids existed has no routing key at all, and
     * for those the message is delivered as an e-mail alone. Either this or {@link #listingId}
     * must be present - {@code MailRelayService} rejects a message that has neither.</p>
     */
    @Size(max = 128, message = "Max 128 chars allowed.")
    String toDeviceId;

    /** Non-identifying display label for the sender. */
    @Size(max = 255, message = "Max 255 chars allowed.")
    String fromLabel;

    /** Non-identifying display label for the recipient. */
    @Size(max = 255, message = "Max 255 chars allowed.")
    String toLabel;

    @NotBlank(message = "Subject is required.")
    @Size(max = 200, message = "Max 200 chars allowed.")
    String subject;

    @NotBlank(message = "Message is required.")
    @Size(max = 5000, message = "Max 5000 chars allowed.")
    String body;

    @Size(max = 40, message = "Max 40 chars allowed.")
    String sentAt;

    /** Primary leg - always DELIVERED once the message is safe for browser pickup. */
    String delivery;
}
