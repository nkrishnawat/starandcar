package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers a browser endpoint so it can be addressed by device id.
 */
public record StarMailRegisterRequest(
        @NotBlank @Size(max = 128) String deviceId,
        @Size(max = 255) String deviceLabel
) {
}
