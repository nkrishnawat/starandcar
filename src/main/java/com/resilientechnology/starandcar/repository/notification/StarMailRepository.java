package com.resilientechnology.starandcar.repository.notification;

import com.resilientechnology.starandcar.entity.StarMailDevice;
import com.resilientechnology.starandcar.entity.StarMailMessage;
import com.resilientechnology.starandcar.mapers.StarMailDeviceRowMapper;
import com.resilientechnology.starandcar.mapers.StarMailMessageRowMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for StarMail devices and messages.
 *
 * <p>IMPORTANT: {@code spring.sql.init.mode} is {@code never} in this application, so
 * {@code schema.sql} is never executed. The tables are therefore created defensively here
 * via {@code CREATE TABLE IF NOT EXISTS} (same pattern as
 * {@code PropertyRepository#ensureManagementColumn}). Without this, every StarMail insert
 * would fail and no message would ever arrive.</p>
 */
@Repository
public class StarMailRepository {

    public static final String STATUS_DELIVERED = "DELIVERED";
    /** The recipient's browser confirmed storage in its localStorage. */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

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

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS STARMAIL_MESSAGE (" +
                        " message_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        " sender_device_id VARCHAR(128) NOT NULL," +
                        " recipient_device_id VARCHAR(128) NOT NULL," +
                        " listing_id BIGINT," +
                        " listing_address VARCHAR(500)," +
                        " subject VARCHAR(200)," +
                        " body VARCHAR(10000)," +
                        " sent_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        " local_delivery_status VARCHAR(32) DEFAULT 'DELIVERED'," +
                        " email_copy_status VARCHAR(32) DEFAULT 'PENDING'," +
                        " email_copy_detail VARCHAR(1000)," +
                        " INDEX idx_starmail_recipient (recipient_device_id, message_id)," +
                        " INDEX idx_starmail_sender (sender_device_id, message_id)" +
                        ")");

        // Tolerate a STARMAIL_MESSAGE table created by an earlier build of this feature.
        jdbcTemplate.execute("ALTER TABLE STARMAIL_MESSAGE ADD COLUMN IF NOT EXISTS listing_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE STARMAIL_MESSAGE ADD COLUMN IF NOT EXISTS listing_address VARCHAR(500)");
    }

    // ---------------------------------------------------------------- devices

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
    public Optional<String> findRegisteredEmail(String deviceId) {
        List<String> emails = jdbcTemplate.query(
                "SELECT registered_email FROM STARMAIL_DEVICE WHERE device_id = ?",
                ps -> ps.setString(1, deviceId),
                (rs, rowNum) -> rs.getString(1));

        return emails.stream().filter(e -> e != null && !e.isBlank()).findFirst();
    }

    /**
     * Fallback lookup for the registered e-mail: the MariaDB listing table.
     *
     * <p>A listing owner registers their contact e-mail on the {@code PROPERTY} row and is
     * addressed afterwards by {@code contact_device_id}. This lets the carbon copy reach them
     * even when they never explicitly registered a StarMail device.</p>
     */
    public Optional<String> findRegisteredEmailFromListing(String deviceId) {
        List<String> emails = jdbcTemplate.query(
                "SELECT contact_email FROM PROPERTY WHERE contact_device_id = ? " +
                        "AND contact_email IS NOT NULL AND contact_email <> '' " +
                        "ORDER BY property_id DESC",
                ps -> ps.setString(1, deviceId),
                (rs, rowNum) -> rs.getString(1));

        return emails.stream().filter(e -> e != null && !e.isBlank()).findFirst();
    }

    /** Address book: device id + label only. Registered e-mail is deliberately not selected. */
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

    // ---------------------------------------------------------------- messages

    /**
     * Persist a message for browser pickup by the recipient device id.
     * This is the primary delivery leg and returns the generated message id.
     */
    public Long insertMessage(String senderDeviceId, String recipientDeviceId,
                              Long listingId, String listingAddress,
                              String subject, String body) {
        jdbcTemplate.update(
                "INSERT INTO STARMAIL_MESSAGE " +
                        "(sender_device_id, recipient_device_id, listing_id, listing_address, subject, body, " +
                        " sent_date, local_delivery_status, email_copy_status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                senderDeviceId, recipientDeviceId, listingId, listingAddress, subject, body,
                STATUS_DELIVERED, STATUS_PENDING);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * Messages addressed to a device, newest last. {@code sinceMessageId} makes polling cheap
     * and idempotent - the browser only ever receives messages it has not seen.
     */
    public List<StarMailMessage> pendingInbox(String deviceId) {
        return jdbcTemplate.query(
                "SELECT " + MESSAGE_COLUMNS +
                        "FROM STARMAIL_MESSAGE WHERE recipient_device_id = ? AND local_delivery_status <> ? " +
                        "ORDER BY message_id ASC",
                ps -> {
                    ps.setString(1, deviceId);
                    ps.setString(2, STATUS_CONFIRMED);
                },
                new StarMailMessageRowMapper());
    }

    /**
     * The recipient's browser confirmed the message is in its localStorage, so the body is
     * cleared server-side. Routing metadata is kept so the sender's delivery status stays
     * accurate.
     */
    public void markPickedUp(Long messageId) {
        jdbcTemplate.update(
                "UPDATE STARMAIL_MESSAGE SET local_delivery_status = ?, body = '' WHERE message_id = ?",
                STATUS_CONFIRMED, messageId);
    }

    private static final String MESSAGE_COLUMNS =
            "message_id, sender_device_id, recipient_device_id, listing_id, listing_address, subject, body, " +
                    "sent_date, local_delivery_status, email_copy_status, email_copy_detail ";

    public List<StarMailMessage> inbox(String deviceId, long sinceMessageId) {
        return jdbcTemplate.query(
                "SELECT " + MESSAGE_COLUMNS +
                        "FROM STARMAIL_MESSAGE WHERE recipient_device_id = ? AND message_id > ? " +
                        "ORDER BY message_id ASC",
                ps -> {
                    ps.setString(1, deviceId);
                    ps.setLong(2, sinceMessageId);
                },
                new StarMailMessageRowMapper());
    }

    public List<StarMailMessage> outbox(String deviceId, long sinceMessageId) {
        return jdbcTemplate.query(
                "SELECT " + MESSAGE_COLUMNS +
                        "FROM STARMAIL_MESSAGE WHERE sender_device_id = ? AND message_id > ? " +
                        "ORDER BY message_id ASC",
                ps -> {
                    ps.setString(1, deviceId);
                    ps.setLong(2, sinceMessageId);
                },
                new StarMailMessageRowMapper());
    }

    public Optional<StarMailMessage> findMessage(Long messageId) {
        List<StarMailMessage> messages = jdbcTemplate.query(
                "SELECT " + MESSAGE_COLUMNS + "FROM STARMAIL_MESSAGE WHERE message_id = ?",
                ps -> ps.setLong(1, messageId),
                new StarMailMessageRowMapper());
        return messages.stream().findFirst();
    }

    /** Record the outcome of the independent e-mail copy leg. */
    public void updateEmailCopyStatus(Long messageId, String status, String detail) {
        jdbcTemplate.update(
                "UPDATE STARMAIL_MESSAGE SET email_copy_status = ?, email_copy_detail = ? WHERE message_id = ?",
                status, truncate(detail, 1000), messageId);
    }

    /**
     * Same as {@link #updateEmailCopyStatus} but never throws - for use inside error handlers,
     * where a secondary DB failure must not mask the original problem.
     */
    public void updateEmailCopyStatusQuietly(Long messageId, String status, String detail) {
        try {
            updateEmailCopyStatus(messageId, status, detail);
        } catch (Exception ignored) {
            // already logging the primary failure elsewhere
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
