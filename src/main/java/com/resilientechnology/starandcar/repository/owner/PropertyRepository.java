package com.resilientechnology.starandcar.repository.owner;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.mapers.PropertyRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}