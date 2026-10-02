package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers a browser endpoint so it can be addressed by device id.
 *
 * <p>{@code registeredEmail} is stored server-side in MariaDB and is used only as the
 * destination of the independent carbon-copy e-mail. It is never returned by the API.</p>
 */
public record StarMailRegisterRequest(
        @NotBlank @Size(max = 128) String deviceId,
        @Size(max = 255) String deviceLabel,
        @Email @Size(max = 500) String registeredEmail
) {
}
