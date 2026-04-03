package com.resilientechnology.starandcar.mapers;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.entity.Room;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PropertyRowMapper implements RowMapper<Property> {

    private JdbcTemplate jdbcTemplate;
    public PropertyRowMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Property mapRow(ResultSet rs, int rowNum) throws SQLException {
        Property property = Property.builder()
                .propertyId(rs.getLong("property_id"))
                .address(rs.getString("address"))
                .description(rs.getString("description"))
                .notes(rs.getString("notes"))
                .contactEmail(rs.getString("contact_email"))
                .contactPhoneNo(rs.getString("contact_phone_no"))
                .build();



        // Load rooms separately
        String roomSql = "SELECT room_id, property_id, is_ac, image_urls FROM ROOM WHERE property_id = ?";
        Set<Room> rooms = new HashSet<>(jdbcTemplate.query(roomSql, new Object[]{property.getPropertyId()}, new RoomRowMapper(jdbcTemplate)));
        property.setRooms(new ArrayList<>(rooms));

        return property;
    }
}
