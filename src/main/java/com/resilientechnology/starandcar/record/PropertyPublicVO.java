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
 * <p>{@code contactEmail} is the owner's own published contact address - the same one they typed
 * into the publish form and that {@code main} has always shown on the listing. It is exposed here
 * so the compose window can name the address the carbon copy is posted to, instead of showing the
 * sender an opaque device id they cannot recognise.</p>
 *
 * <p>Routing itself still uses {@code contactDeviceId}; the address is never used to route.</p>
 */
@Getter
@Setter
@Builder
public class PropertyPublicVO {

    Long propertyId;

    String address;

    String description;

    String notes;

    /** Owner's published contact address - displayed read-only as the carbon-copy destination. */
    String contactEmail;

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
                .contactEmail(property.getContactEmail())
                .contactDeviceId(property.getContactDeviceId())
                .contactPhoneNo(property.getContactPhoneNo())
                .rooms(propertyRooms)
                .build();
    }
}
