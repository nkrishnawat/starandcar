package com.resilientechnology.starandcar.mapers;

import com.resilientechnology.starandcar.entity.Room;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RoomRowMapper implements RowMapper<Room> {

    private final JdbcTemplate jdbcTemplate;

    public RoomRowMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Room mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long roomId = rs.getLong("room_id");
        Long propertyId = rs.getLong("property_id");
        boolean isAc = rs.getBoolean("is_ac");

        String urls = rs.getString("image_urls");
        Set<String> imageUrls = urls == null || urls.isEmpty() ?
                new HashSet<>() :
                new HashSet<>(Arrays.asList(urls.split(",")));

        return Room.builder()
                .roomId(roomId)
                .propertyId(propertyId)
                .isAc(isAc)
                .imageUrlS3(imageUrls)
                .build();
    }
}