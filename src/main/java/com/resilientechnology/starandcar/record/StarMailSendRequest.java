package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to send a StarMail message.
 *
 * <p>Routing is by device id (MAC / DeviceID / MachineID) only. There is deliberately no
 * recipient e-mail field - the registered e-mail is resolved server-side from MariaDB so
 * that e-mail addresses never have to travel through the client.</p>
 */
public record StarMailSendRequest(
        @NotBlank @Size(max = 128) String senderDeviceId,
        @NotBlank @Size(max = 128) String recipientDeviceId,
        @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String body
) {
}
