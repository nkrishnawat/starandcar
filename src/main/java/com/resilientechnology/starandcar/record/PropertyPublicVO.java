package com.resilientechnology.starandcar.record;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.entity.Room;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Public view of a listing as returned by search.
 *
 * <p>The owner's e-mail address is deliberately absent. Listings are reached through STARMail
 * using the owner's device id (MAC / DeviceID / MachineID); the registered e-mail stays in
 * MariaDB and is used only as the destination of the independent carbon copy.</p>
 */
@Getter
@Setter
@Builder
public class PropertyPublicVO {

    Long propertyId;

    String address;

    String description;

    String notes;

    /** STARMail routing key for the owner's browser. */
    String contactDeviceId;

    String contactPhoneNo;

    List<Room> rooms;

    public static PropertyPublicVO from(Property property) {
        List<Room> propertyRooms = property.getRooms() == null
                ? new ArrayList<>()
                : property.getRooms();

        return PropertyPublicVO.builder()
                .propertyId(property.getPropertyId())
                .address(property.getAddress())
                .description(property.getDescription())
                .notes(property.getNotes())
                .contactDeviceId(property.getContactDeviceId())
                .contactPhoneNo(property.getContactPhoneNo())
                .rooms(propertyRooms)
                .build();
    }
}
