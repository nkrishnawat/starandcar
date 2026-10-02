package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A STARMail message travelling from a sender's browser to a recipient's browser.
 *
 * <p>Addressing is done with the <strong>MAC address / DeviceID / MachineID</strong> of the
 * two endpoints - never with e-mail addresses. E-mail is only used behind the scenes as the
 * destination of an independent carbon copy, taken from the registered address held in
 * MariaDB, so no e-mail address ever appears on this wire format or in the browser.</p>
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

    /** Recipient MAC address / DeviceID / MachineID - the routing key. */
    @NotBlank(message = "Recipient device id is required.")
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

    /** Independent carbon-copy leg: PENDING | QUEUED | SENT | FAILED | SKIPPED. */
    String emailCopyStatus;

    /** Why the carbon copy failed or was skipped. Never contains credentials. */
    String emailCopyDetail;
}
