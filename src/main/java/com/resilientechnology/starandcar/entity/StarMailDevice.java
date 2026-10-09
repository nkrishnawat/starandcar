package com.resilientechnology.starandcar.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A registered StarMail endpoint.
 *
 * <p>The routing key for StarMail is the {@code deviceId} (MAC address / DeviceID / MachineID).
 * The {@code registeredEmail} is kept strictly server-side as the destination for the
 * independent carbon-copy email. It is never exposed through the public API.</p>
 */
@Getter
@Setter
@Builder
public class StarMailDevice {

    /** MAC address / DeviceID / MachineID - the only addressing key used to route to a browser. */
    String deviceId;

    /** Human friendly, non-identifying label shown in the address book. */
    String deviceLabel;

    /** Registered email address (MariaDB). Server-side only - used for the copy email. */
    String registeredEmail;

    LocalDateTime registeredDate;

    LocalDateTime lastSeen;
}
