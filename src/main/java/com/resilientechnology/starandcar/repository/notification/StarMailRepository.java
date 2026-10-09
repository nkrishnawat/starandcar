package com.resilientechnology.starandcar.repository.notification;

import com.resilientechnology.starandcar.entity.StarMailDevice;
import com.resilientechnology.starandcar.mapers.StarMailDeviceRowMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Registry of STARMail endpoints (devices).
 *
 * <p>Messages are <em>not</em> kept here - they live in the Linux folder managed by
 * {@code StarMailSpoolService}, which is what lets browser-to-browser delivery work with no
 * database and no e-mail at all. This table only holds the mapping from a device id to the
 * registered address used by the optional, parallel external e-mail copy.</p>
 *
 * <p>IMPORTANT: {@code spring.sql.init.mode} is {@code never} in this application, so
 * {@code schema.sql} is never executed. The table is therefore created defensively here
 * via {@code CREATE TABLE IF NOT EXISTS} (same pattern as
 * {@code PropertyRepository#ensureManagementColumn}).</p>
 */
@Repository
public class StarMailRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public StarMailRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureStarMailSchema() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS STARMAIL_DEVICE (" +
                        " device_id VARCHAR(128) PRIMARY KEY," +
                        " device_label VARCHAR(255)," +
                        " registered_email VARCHAR(500)," +
                        " registered_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        " last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
    }

    /** Register (or refresh) a device. The registered e-mail is server-side only. */
    public void upsertDevice(String deviceId, String deviceLabel, String registeredEmail) {
        int updated = jdbcTemplate.update(
                "UPDATE STARMAIL_DEVICE SET device_label = ?, registered_email = ?, last_seen = CURRENT_TIMESTAMP " +
                        "WHERE device_id = ?",
                deviceLabel, registeredEmail, deviceId);

        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO STARMAIL_DEVICE (device_id, device_label, registered_email, registered_date, last_seen) " +
                            "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    deviceId, deviceLabel, registeredEmail);
        }
    }

    /** Look up the registered e-mail for a device. Returns empty when unknown or not registered. */

    /**
     * Fallback lookup for the registered e-mail: the MariaDB listing table.
     *
     * <p>A listing owner registers their contact e-mail on the {@code PROPERTY} row and is
     * addressed afterwards by {@code contact_device_id}. This lets the optional carbon copy
     * reach them even when they never explicitly registered a StarMail device.</p>
     */

    /**
     * Reverse lookup: which device is reachable on this e-mail address?
     *
     * <p>Used to repair listings published before the device id existed - the owner's browser
     * fingerprint cannot be recomputed from the listing, but if that owner registered a STARMail
     * device on the same address the routing key can be recovered from here.</p>
     */
    public Optional<String> findDeviceIdByRegisteredEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<String> ids = jdbcTemplate.query(
                "SELECT device_id FROM STARMAIL_DEVICE WHERE LOWER(registered_email) = LOWER(?) " +
                        "ORDER BY last_seen DESC",
                ps -> ps.setString(1, email.trim()),
                (rs, rowNum) -> rs.getString(1));

        return ids.stream().filter(id -> id != null && !id.isBlank()).findFirst();
    }

    /** Contact address published on one specific listing - what the compose window displays as "To". */
    public Optional<String> findContactEmailByPropertyId(Long propertyId) {
        if (propertyId == null) {
            return Optional.empty();
        }
        List<String> emails = jdbcTemplate.query(
                "SELECT contact_email FROM PROPERTY WHERE property_id = ? " +
                        "AND contact_email IS NOT NULL AND contact_email <> ''",
                ps -> ps.setLong(1, propertyId),
                (rs, rowNum) -> rs.getString(1));

        return emails.stream().filter(e -> e != null && !e.isBlank()).findFirst();
    }

    /** Address book: device id + label only. Registered e-mail is deliberately not returned. */
    public List<StarMailDevice> listDevices() {
        return jdbcTemplate.query(
                "SELECT device_id, device_label, registered_email, registered_date, last_seen " +
                        "FROM STARMAIL_DEVICE ORDER BY device_label, device_id",
                new StarMailDeviceRowMapper());
    }

    public Optional<StarMailDevice> findDevice(String deviceId) {
        List<StarMailDevice> devices = jdbcTemplate.query(
                "SELECT device_id, device_label, registered_email, registered_date, last_seen " +
                        "FROM STARMAIL_DEVICE WHERE device_id = ?",
                ps -> ps.setString(1, deviceId),
                new StarMailDeviceRowMapper());
        return devices.stream().findFirst();
    }
}
