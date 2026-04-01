package com.resilientechnology.starandcar.repository.owner;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.mapers.PropertyRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class PropertyRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public PropertyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Get property by ID
    public Property getPropertyById(Long propertyId) {
        String sql = "SELECT property_id, address, description, notes FROM property WHERE property_id = ?";
        return jdbcTemplate.queryForObject(sql, new Object[]{propertyId}, new PropertyRowMapper(jdbcTemplate));
    }

    // Search by text in address or description (MariaDB-compatible)
    public List<Property> searchByText(String searchText) {
        String sql = "SELECT p.property_id, p.address, p.description, p.notes, contact_email, contact_phone_no " +
                "FROM property p " +
                "JOIN room r ON p.property_id = r.property_id " +
                "WHERE LOWER(address) LIKE ? OR LOWER(description) LIKE ? OR LOWER(notes) LIKE ?";

        String searchPattern = "%" + searchText.toLowerCase() + "%";

        return jdbcTemplate.query(
                sql,
                new Object[]{searchPattern, searchPattern, searchPattern},
                new PropertyRowMapper(jdbcTemplate)
        );
    }

    // List rooms for a ZIP code extracted from the address string
    public List<Property> listRoomsForZip(Long zip) {
        String zipPattern = "%" + zip + "%";

        String sql = "SELECT p.property_id, p.address, p.description, p.notes, contact_email, contact_phone_no " +
                "FROM property p " +
                "JOIN room r ON p.property_id = r.property_id " +
                "WHERE p.address LIKE ?";

        return jdbcTemplate.query(
                sql,
                new Object[]{zipPattern},
                new PropertyRowMapper(jdbcTemplate)
        );
    }

    @Transactional
    public boolean save(Property property) {

        final String INSERT_PROPERTY = """
        INSERT INTO PROPERTY 
        (property_id, address, description, notes, contact_email, contact_phone_no)
        VALUES (?, ?, ?, ?, ?, ?)
    """;

        final String INSERT_ROOM = """
        INSERT INTO ROOM 
        (room_id, property_id, image_url_s3)
        VALUES (?, ?, ?)
    """;

        try {
            // 1. Insert property
            jdbcTemplate.update(INSERT_PROPERTY,
                    property.getPropertyId(),
                    property.getAddress(),
                    property.getDescription(),
                    property.getNotes(),
                    property.getContactEmail(),
                    property.getContactPhoneNo()
            );

            // 2. Insert rooms (batch for performance)
            if (property.getRooms() != null && !property.getRooms().isEmpty()) {

                jdbcTemplate.batchUpdate(
                        INSERT_ROOM,
                        property.getRooms(),
                        property.getRooms().size(),
                        (ps, room) -> {
                            ps.setLong(1, room.getRoomId());
                            ps.setLong(2, property.getPropertyId()); // enforce relation
                            ps.setString(3, room.getImageUrlS3().stream().collect(Collectors.joining(", ")));
                        }
                );
            }

            return true;

        } catch (Exception ex) {
            throw new RuntimeException("Failed to save property with rooms", ex);
        }
    }
}