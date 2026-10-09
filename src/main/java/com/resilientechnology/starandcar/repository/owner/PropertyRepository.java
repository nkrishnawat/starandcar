package com.resilientechnology.starandcar.repository.owner;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.mapers.PropertyRowMapper;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class PropertyRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Reverse lookup of the routing key for listings published before the device id existed. */
    @Autowired
    private StarMailRepository starMailRepository;

    public PropertyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureManagementColumn() {
        jdbcTemplate.execute("ALTER TABLE PROPERTY ADD COLUMN IF NOT EXISTS manage_token_hash VARCHAR(64)");
    }

    /**
     * StarMail addressing key for the listing owner.
     *
     * <p>StarMail routes by MAC address / DeviceID / MachineID rather than by e-mail, so each
     * listing needs a device id to be reachable on. The registered e-mail stays in
     * {@code contact_email} and is only used server-side for the carbon-copy delivery.</p>
     */
    @PostConstruct
    void ensureContactDeviceColumn() {
        jdbcTemplate.execute("ALTER TABLE PROPERTY ADD COLUMN IF NOT EXISTS contact_device_id VARCHAR(128)");
    }

    /**
     * Makes sure every property that comes back from this repository carries a STARMail routing
     * key whenever one can be established.
     *
     * <p>{@code contact_device_id} is written at publish time from the publishing browser, so any
     * listing created before that field existed - or published by a browser that never ran the
     * STARMail bootstrap - has it {@code NULL}. A {@code NULL} routing key means the compose window
     * has nothing to address to, which is exactly the empty-recipient bug. The owner's browser
     * fingerprint cannot be recomputed from the row, but if that owner registered a STARMail device
     * on the address stored in {@code contact_email} the key can be recovered from the device
     * registry instead of being left blank.</p>
     */
    private void resolveStarMailRouting(Property property) {
        if (property == null || StringUtils.hasText(property.getContactDeviceId())) {
            return;
        }
        if (!StringUtils.hasText(property.getContactEmail()) || starMailRepository == null) {
            return;
        }
        starMailRepository.findDeviceIdByRegisteredEmail(property.getContactEmail())
                .ifPresent(property::setContactDeviceId);
    }

    // Get property by ID
    public Property getPropertyById(Long propertyId) {
        String sql = "SELECT property_id, address, description, notes, contact_email, contact_phone_no, contact_device_id, manage_token_hash FROM PROPERTY WHERE property_id = ?";
        Property property = jdbcTemplate.queryForObject(sql, new Object[]{propertyId}, new PropertyRowMapper(jdbcTemplate));
        resolveStarMailRouting(property);
        return property;
    }

    // Search by text in address or description (MariaDB-compatible)
    public List<Property> searchByText(String searchText) {
        String sql = "SELECT p.property_id, p.address, p.description, p.notes, contact_email, contact_phone_no, contact_device_id, manage_token_hash " +
                "FROM PROPERTY p " +
                "LEFT JOIN ROOM r ON p.property_id = r.property_id " +
                "WHERE LOWER(address) LIKE ? OR LOWER(description) LIKE ? OR LOWER(notes) LIKE ?  GROUP BY property_id";

        String searchPattern = "%" + searchText.toLowerCase() + "%";

        List<Property> properties = jdbcTemplate.query(
                sql,
                new Object[]{searchPattern, searchPattern, searchPattern},
                new PropertyRowMapper(jdbcTemplate)
        );
        properties.forEach(this::resolveStarMailRouting);
        return properties;
    }

    // List rooms for a ZIP code extracted from the address string
    public List<Property> listRoomsForZip(Long zip) {
        String zipPattern = "%" + zip + "%";

        String sql = "SELECT p.property_id, p.address, p.description, p.notes, contact_email, contact_phone_no, contact_device_id, manage_token_hash " +
                "FROM PROPERTY p " +
                "LEFT JOIN ROOM r ON p.property_id = r.property_id " +
                "WHERE p.address LIKE ? GROUP BY property_id";

        List<Property> properties = jdbcTemplate.query(
                sql,
                new Object[]{zipPattern},
                new PropertyRowMapper(jdbcTemplate)
        );
        properties.forEach(this::resolveStarMailRouting);
        return properties;
    }

    @Transactional
    public boolean save(Property property) {

        final String INSERT_PROPERTY = """
        INSERT INTO PROPERTY 
        (property_id, address, description, notes, contact_email, contact_phone_no, contact_device_id, manage_token_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """;

        final String INSERT_ROOM = """
        INSERT INTO ROOM 
        (room_id, property_id, is_ac, image_urls)
        VALUES (?, ?, ?, ?)
    """;

        try {
            // 1. Insert property
            jdbcTemplate.update(INSERT_PROPERTY,
                    property.getPropertyId(),
                    property.getAddress(),
                    property.getDescription(),
                    property.getNotes(),
                    property.getContactEmail(),
                    property.getContactPhoneNo(),
                    property.getContactDeviceId(),
                    property.getManageTokenHash()
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
                            ps.setBoolean(3, room.isAc());
                            ps.setString(4, room.getImageUrlS3().stream().collect(Collectors.joining(", ")));
                        }
                );
            }

            return true;

        } catch (Exception ex) {
            throw new RuntimeException("Failed to save property with rooms", ex);
        }
    }

    public void update(Long propertyId, com.resilientechnology.starandcar.record.PropertyDetailVO details) {
        // COALESCE keeps the STARMail routing key when the management form does not carry it.
        jdbcTemplate.update("""
                UPDATE PROPERTY
                SET address = ?, description = ?, notes = ?, contact_email = ?, contact_phone_no = ?,
                    contact_device_id = COALESCE(?, contact_device_id)
                WHERE property_id = ?
                """,
                details.getAddress(), details.getDescription(), details.getNotes(),
                details.getEmail(), details.getPhone(), details.getContactDeviceId(), propertyId);
    }

    public void delete(Long propertyId) {
        jdbcTemplate.update("DELETE FROM PROPERTY WHERE property_id = ?", propertyId);
    }
}