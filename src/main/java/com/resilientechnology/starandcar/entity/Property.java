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

    @Column("contact_phone_no")
    String contactPhoneNo;

}
