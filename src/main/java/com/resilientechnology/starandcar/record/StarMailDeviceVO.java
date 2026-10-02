package com.resilientechnology.starandcar.record;

import com.resilientechnology.starandcar.entity.StarMailDevice;

/**
 * Public view of a StarMail endpoint for the address book.
 *
 * <p>The registered e-mail address is intentionally NOT included. Clients only ever see the
 * device id (MAC / DeviceID / MachineID) and a label; {@code hasRegisteredEmail} merely
 * signals whether a carbon copy can be delivered.</p>
 */
public record StarMailDeviceVO(
        String deviceId,
        String deviceLabel,
        boolean hasRegisteredEmail
) {

    public static StarMailDeviceVO from(StarMailDevice device) {
        return new StarMailDeviceVO(
                device.getDeviceId(),
                device.getDeviceLabel(),
                device.getRegisteredEmail() != null && !device.getRegisteredEmail().isBlank());
    }
}
