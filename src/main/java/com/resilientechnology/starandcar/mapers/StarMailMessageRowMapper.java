package com.resilientechnology.starandcar.mapers;

import com.resilientechnology.starandcar.entity.StarMailMessage;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class StarMailMessageRowMapper implements RowMapper<StarMailMessage> {

    @Override
    public StarMailMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp sent = rs.getTimestamp("sent_date");

        return StarMailMessage.builder()
                .messageId(rs.getLong("message_id"))
                .senderDeviceId(rs.getString("sender_device_id"))
                .recipientDeviceId(rs.getString("recipient_device_id"))
                .listingId(getNullableLong(rs, "listing_id"))
                .listingAddress(rs.getString("listing_address"))
                .subject(rs.getString("subject"))
                .body(rs.getString("body"))
                .sentDate(sent == null ? null : sent.toLocalDateTime())
                .localDeliveryStatus(rs.getString("local_delivery_status"))
                .emailCopyStatus(rs.getString("email_copy_status"))
                .emailCopyDetail(rs.getString("email_copy_detail"))
                .build();
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
