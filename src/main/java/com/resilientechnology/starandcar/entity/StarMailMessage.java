package com.resilientechnology.starandcar.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A StarMail message.
 *
 * <p>Addressing is by device id only. Two independent delivery legs are tracked:</p>
 * <ul>
 *   <li>{@code localDeliveryStatus} - the primary leg. The message is persisted so the
 *       recipient's browser can pull it by {@code recipientDeviceId} and store it in
 *       localStorage.</li>
 *   <li>{@code emailCopyStatus} - the independent carbon-copy leg. A copy is e-mailed to
 *       the registered address looked up in MariaDB. Failures here never block the local leg.</li>
 * </ul>
 */
@Getter
@Setter
@Builder
public class StarMailMessage {

    Long messageId;

    String senderDeviceId;

    String recipientDeviceId;

    Long listingId;

    String listingAddress;

    String subject;

    String body;

    LocalDateTime sentDate;

    /** Primary leg: DELIVERED once persisted for browser pickup. */
    String localDeliveryStatus;

    /** Copy leg: PENDING | SENT | FAILED | SKIPPED. */
    String emailCopyStatus;

    /** Why the copy failed / was skipped. Safe to show - never contains credentials. */
    String emailCopyDetail;
}
