package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.record.MailMessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * STARMail message store - one compressed file per device in a plain Linux folder.
 *
 * <p>This is the only place a message lives between send and receipt. Nothing external is
 * involved: a message that cannot be pushed straight to the receiver's browser is kept here
 * until that device comes online and its browser confirms the message is in localStorage.</p>
 *
 * <p>Exactly one gzip file per device, so a phone, a desktop and any other browser of the
 * same device share a single queue file. Writes are atomic (temp file + move) and a queue
 * that cannot be read is preserved as {@code .corrupt-<ts>} rather than dropped.</p>
 */
@Service
public class StarMailSpoolService {

    private static final JsonMapper MAPPER = JsonMapper.shared();
    private static final TypeReference<List<MailMessageVO>> MESSAGE_LIST = new TypeReference<>() {
    };

    private static final Logger logger = LoggerFactory.getLogger(StarMailSpoolService.class);

    @Value("${file.mail-spool-dir:/tmp/starmail}")
    private String spoolDir;

    private final Map<String, Object> spoolLocks = new ConcurrentHashMap<>();

    /** Queue a message for a device. This is what keeps it safe while the receiver is off. */
    public void append(String deviceId, MailMessageVO message) {
        synchronized (lockFor(deviceId)) {
            List<MailMessageVO> messages = readAll(deviceId);
            messages.add(message);
            writeSpool(deviceId, messages);
        }
    }

    /** Everything waiting for this device, oldest first. */
    public List<MailMessageVO> readAll(String deviceId) {
        return readSpoolFile(spoolFile(deviceId));
    }

    private List<MailMessageVO> readSpoolFile(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            List<MailMessageVO> messages = MAPPER.readValue(in, MESSAGE_LIST);
            return messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        } catch (Exception e) {
            // Never drop an unreadable queue: keep it on disk and start a fresh file.
            try {
                Files.move(file, Paths.get(file + ".corrupt-" + System.currentTimeMillis()),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                // the raw file stays where it is
            }
            return new ArrayList<>();
        }
    }

    /**
     * The receiver's browser confirmed the message is in its localStorage, so the queued copy
     * can be removed from the folder.
     */
    public void prune(String deviceId, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        synchronized (lockFor(deviceId)) {
            List<MailMessageVO> remaining = new ArrayList<>();
            for (MailMessageVO message : readAll(deviceId)) {
                if (!Objects.equals(message.getMessageId(), messageId)) {
                    remaining.add(message);
                }
            }
            writeSpool(deviceId, remaining);
        }
    }

    /** One gzip queue file per device, named safely from its id. */
    public Path spoolFile(String deviceId) {
        String address = deviceId == null ? "" : deviceId.trim();
        String safe = address.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        return Paths.get(spoolDir, "starmail-" + safe + "-" + sha256(address).substring(0, 10) + ".gz");
    }

    private void writeSpool(String deviceId, List<MailMessageVO> messages) {
        Path file = spoolFile(deviceId);
        try {
            if (messages.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            Files.createDirectories(file.getParent());
            Path temp = Paths.get(file + ".tmp");
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(temp))) {
                out.write(MAPPER.writeValueAsBytes(messages));
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to queue the STARMail message in " + file
                    + " - the message was rejected rather than silently dropped", e);
        }
    }

    private Object lockFor(String deviceId) {
        return spoolLocks.computeIfAbsent(deviceId == null ? "" : deviceId, key -> new Object());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to name the STARMail spool file", e);
        }
    }
}
