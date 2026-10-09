package com.resilientechnology.starandcar.mapers;

import com.resilientechnology.starandcar.entity.StarMailDevice;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class StarMailDeviceRowMapper implements RowMapper<StarMailDevice> {

    @Override
    public StarMailDevice mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp registered = rs.getTimestamp("registered_date");
        Timestamp lastSeen = rs.getTimestamp("last_seen");

        return StarMailDevice.builder()
                .deviceId(rs.getString("device_id"))
                .deviceLabel(rs.getString("device_label"))
                .registeredEmail(rs.getString("registered_email"))
                .registeredDate(registered == null ? null : registered.toLocalDateTime())
                .lastSeen(lastSeen == null ? null : lastSeen.toLocalDateTime())
                .build();
    }
}
