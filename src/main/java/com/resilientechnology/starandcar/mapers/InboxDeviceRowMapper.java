package com.resilientechnology.starandcar.mapers;

import com.resilientechnology.starandcar.entity.InboxDevice;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class InboxDeviceRowMapper implements RowMapper<InboxDevice> {

    @Override
    public InboxDevice mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp registered = rs.getTimestamp("registered_date");
        Timestamp lastSeen = rs.getTimestamp("last_seen");

        return InboxDevice.builder()
                .deviceId(rs.getString("device_id"))
                .deviceLabel(rs.getString("device_label"))
                .registeredEmail(rs.getString("registered_email"))
                .registeredDate(registered == null ? null : registered.toLocalDateTime())
                .lastSeen(lastSeen == null ? null : lastSeen.toLocalDateTime())
                .build();
    }
}
