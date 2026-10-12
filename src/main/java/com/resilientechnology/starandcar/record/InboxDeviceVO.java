package com.resilientechnology.starandcar.record;

import com.resilientechnology.starandcar.entity.InboxDevice;

/**
 * Public view of a Inbox endpoint for the address book.
 *
 * <p>The registered e-mail address is intentionally NOT included. Clients only ever see the
 * device id (MAC / DeviceID / MachineID) and a label; {@code hasRegisteredEmail} merely
 * signals whether a carbon copy can be delivered.</p>
 */
public record InboxDeviceVO(
        String deviceId,
        String deviceLabel,
        boolean hasRegisteredEmail
) {

    public static InboxDeviceVO from(InboxDevice device) {
        return new InboxDeviceVO(
                device.getDeviceId(),
                device.getDeviceLabel(),
                device.getRegisteredEmail() != null && !device.getRegisteredEmail().isBlank());
    }
}
