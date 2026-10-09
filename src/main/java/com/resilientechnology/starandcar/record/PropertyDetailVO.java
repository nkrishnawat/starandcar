package com.resilientechnology.starandcar.record;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PropertyDetailVO {
        String address;
        String email;
        /**
         * MAC address / DeviceID / MachineID of the owner's browser. This is the only
         * contact key exposed on the listing - {@code email} is kept for the registered
         * address used by the independent STARMail carbon copy.
         */
        String contactDeviceId;
        String phone;
        String description;
        String notes;
        Long count;
        String title;
        String updateLink;
        String deleteLink;
        Float price;
        byte[] qrCode;
        String ownerEmail;
}
