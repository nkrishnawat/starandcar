package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.record.MailMessageVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * STARMail relay - no message is ever dropped.
 *
 * Every message is first appended to a compressed spool file under
 * {@code file.mail-spool-dir} (default /tmp/starmail): exactly one gzip file
 * per user, so a phone, a desktop or any other browser of the same user shares
 * one queue file. The message is then pushed to the receiver's browser if it is
 * online (Server-Sent Events). Only after the receiver's browser confirms that
 * the message now lives in its localStorage is the entry pruned from the spool
 * file - so nothing is lost while a receiver is away, and nothing is kept on
 * the server once the receiver has it.
 */
@Service
public class MailRelayService {

    private static final JsonMapper MAPPER = JsonMapper.shared();
    private static final TypeReference<List<MailMessageVO>> MESSAGE_LIST = new TypeReference<>() {
    };

    /** Live browser connections only - not a message store. */
    private final Map<String, SseEmitter> connectedBrowsers = new ConcurrentHashMap<>();

    private final Map<String, Object> spoolLocks = new ConcurrentHashMap<>();

    @Value("${file.mail-spool-dir:/tmp/starmail}")
    private String spoolDir;

    public SseEmitter subscribe(String email) {
        String address = normalize(email);
        SseEmitter emitter = new SseEmitter(0L); // stay open until the browser leaves

        synchronized (lockFor(address)) {
            SseEmitter previous = connectedBrowsers.put(address, emitter);
            if (previous != null) {
                previous.complete();
            }
            // hand over everything queued while this browser was away
            for (MailMessageVO pending : readSpool(address)) {
                push(emitter, pending);
            }
        }

        Runnable forget = () -> connectedBrowsers.remove(address, emitter);
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(error -> forget.run());
        return emitter;
    }

    /**
     * Email endpoint: the message is spooled to disk first (never dropped),
     * then pushed to the receiver's browser when it is online.
     */
    public SendResult send(MailMessageVO message) {
        if (message.getMessageId() == null || message.getMessageId().isBlank()) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        String address = normalize(message.getTo());

        synchronized (lockFor(address)) {
            appendToSpool(address, message); // survive anything that follows
            SseEmitter receiver = connectedBrowsers.get(address);
            if (receiver != null && push(receiver, message)) {
                return new SendResult(message.getMessageId(), true,
                        "Pushed to the receiver's browser and kept in the spool file until that browser confirms storage in its localStorage.");
            }
            return new SendResult(message.getMessageId(), false,
                    "Queued in the spool file " + spoolFile(address).getFileName()
                            + " - it will reach the receiver's browser as soon as it comes online. Nothing is dropped.");
        }
    }

    /**
     * The receiver's browser confirms the message is now in its localStorage,
     * so the spool entry can be pruned.
     */
    public void received(String email, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        String address = normalize(email);
        synchronized (lockFor(address)) {
            List<MailMessageVO> remaining = new ArrayList<>();
            for (MailMessageVO message : readSpool(address)) {
                if (!Objects.equals(message.getMessageId(), messageId)) {
                    remaining.add(message);
                }
            }
            writeSpool(address, remaining);
        }
    }

    private boolean push(SseEmitter receiver, MailMessageVO message) {
        try {
            receiver.send(SseEmitter.event()
                    .name("starmail")
                    .data(message, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            connectedBrowsers.remove(normalize(message.getTo()), receiver);
            return false;
        }
    }

    /* ── Compressed per-user spool file ── */

    private void appendToSpool(String address, MailMessageVO message) {
        List<MailMessageVO> messages = readSpool(address);
        messages.add(message);
        writeSpool(address, messages);
    }

    private List<MailMessageVO> readSpool(String address) {
        Path file = spoolFile(address);
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

    private void writeSpool(String address, List<MailMessageVO> messages) {
        Path file = spoolFile(address);
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

    private Path spoolFile(String address) {
        String safe = address.replaceAll("[^a-z0-9._-]", "_");
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        return Paths.get(spoolDir, "starmail-" + safe + "-" + sha256(address).substring(0, 10) + ".gz");
    }

    private Object lockFor(String address) {
        return spoolLocks.computeIfAbsent(address, key -> new Object());
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to name the STARMail spool file", e);
        }
    }

    public record SendResult(String messageId, boolean delivered, String status) {
    }
}
