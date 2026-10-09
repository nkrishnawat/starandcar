package com.resilientechnology.starandcar.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

import java.util.List;

@Getter
@Setter
@Builder
@EqualsAndHashCode
public class Property {
    @Id
    Long propertyId;

    String address;

    List<Room> rooms;

    @Column("description")
    String description;

    String notes;

    @Column("contact_email")
    String contactEmail;

    /**
     * StarMail routing key for the listing owner (MAC / DeviceID / MachineID).
     * This is what buyers/guests use to reach the owner's browser; {@code contactEmail}
     * is kept for the registered-address carbon copy only.
     */
    @Column("contact_device_id")
    String contactDeviceId;

    @Column("contact_phone_no")
    String contactPhoneNo;

    @Column("manage_token_hash")
    String manageTokenHash;

}
