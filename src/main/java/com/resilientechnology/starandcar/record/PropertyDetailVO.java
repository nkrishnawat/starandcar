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
