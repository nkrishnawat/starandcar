package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.entity.StarMailMessage;
import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import com.resilientechnology.starandcar.service.notification.EmailCopyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * STARMail relay - addressed by MAC address / DeviceID / MachineID.
 *
 * <p>Delivery is two independent legs:</p>
 * <ol>
 *   <li><strong>Browser leg (primary).</strong> The message is written to MariaDB first, so it
 *       survives restarts, offline receivers and a wiped {@code /tmp}. It is then pushed over
 *       Server-Sent Events when the recipient's browser is online. The browser stores it in
 *       {@code localStorage} and acknowledges it. A polling endpoint is also exposed so a
 *       message still arrives when SSE is unavailable (proxy buffering, blocked stream).</li>
 *   <li><strong>Registered e-mail leg (independent).</strong> A copy is e-mailed to the
 *       registered address held in MariaDB on a separate thread. It can fail without any
 *       effect on the browser leg.</li>
 * </ol>
 *
 * <p>Neither leg uses an e-mail address as its routing key; both endpoints are identified by
 * device id only.</p>
 */
@Service
public class MailRelayService {

    private static final Logger logger = LoggerFactory.getLogger(MailRelayService.class);

    private static final DateTimeFormatter SENT_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Live browser connections per device. A device may hold several open tabs. */
    private final Map<String, Set<SseEmitter>> connectedBrowsers = new ConcurrentHashMap<>();

    private final StarMailRepository repository;
    private final EmailCopyService emailCopyService;

    public MailRelayService(StarMailRepository repository, EmailCopyService emailCopyService) {
        this.repository = repository;
        this.emailCopyService = emailCopyService;
    }

    // ------------------------------------------------------------------ send

    /**
     * Send a STARMail message.
     *
     * <p>The browser leg is completed first and is guaranteed. The registered e-mail copy is
     * then dispatched in parallel and cannot affect this call's outcome.</p>
     */
    public SendResult send(MailMessageVO message) {
        if (message.getMessageId() == null || message.getMessageId().isBlank()) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        if (message.getSentAt() == null || message.getSentAt().isBlank()) {
            message.setSentAt(LocalDateTime.now().format(SENT_AT));
        }

        // ---- Leg 1: browser delivery. Durable, so nothing can be lost from here on.
        Long id = repository.insertMessage(
                message.getFromDeviceId(),
                message.getToDeviceId(),
                message.getListingId(),
                message.getListingAddress(),
                message.getSubject(),
                message.getBody());

        StarMailMessage stored = repository.findMessage(id)
                .orElseThrow(() -> new IllegalStateException("STARMail message " + id + " could not be read back"));

        // Best-effort instant push; the poll endpoint is the guaranteed path.
        boolean pushed = push(stored);

        // ---- Leg 2: independent carbon copy. Fire and forget on its own thread.
        emailCopyService.dispatchCopyAsync(stored);

        String status = pushed
                ? "Delivered to the recipient's browser and stored in its localStorage. "
                + "A copy is being sent to the registered email address independently."
                : "Stored for the recipient's browser (it will be fetched into localStorage). "
                + "A copy is being sent to the registered email address independently.";

        return new SendResult(
                String.valueOf(id),
                true,
                repository.STATUS_PENDING,
                status);
    }

    // ------------------------------------------------------------- subscription

    /**
     * Keeps the receiver's browser on the line so incoming mail can be pushed straight into
     * its localStorage. Everything already waiting is flushed on connect.
     */
    public SseEmitter subscribe(String deviceId) {
        String device = normalize(deviceId);
        SseEmitter emitter = new SseEmitter(0L); // stay open until the browser leaves

        connectedBrowsers.computeIfAbsent(device, key -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable forget = () -> {
            Set<SseEmitter> emitters = connectedBrowsers.get(device);
            if (emitters != null) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    connectedBrowsers.remove(device, emitters);
                }
            }
        };
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(error -> forget.run());

        // hand over everything queued while this browser was away
        for (MailMessageVO pending : pendingFor(device)) {
            if (!pushTo(emitter, pending)) {
                break;
            }
        }
        return emitter;
    }

    /**
     * Polling fallback. Returns every message addressed to this device that the browser has
     * not acknowledged yet, so a browser without a working SSE stream still receives mail.
     */
    public List<MailMessageVO> inbox(String deviceId) {
        return pendingFor(normalize(deviceId));
    }

    /**
     * The receiver's browser confirms the message now lives in its localStorage. The body is
     * then cleared server-side so the copy does not linger on the server, while the routing
     * metadata is kept for the sender's delivery status.
     */
    public void received(String deviceId, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        try {
            repository.markPickedUp(Long.parseLong(messageId.trim()));
        } catch (NumberFormatException e) {
            logger.warn("Ignoring STARMail ack with non-numeric message id '{}'", messageId);
        }
    }

    /** Messages waiting for this device's browser, newest last. */
    private List<MailMessageVO> pendingFor(String deviceId) {
        List<MailMessageVO> result = new ArrayList<>();
        for (StarMailMessage message : repository.pendingInbox(deviceId)) {
            result.add(toVO(message));
        }
        return result;
    }

    private boolean push(StarMailMessage message) {
        Set<SseEmitter> emitters = connectedBrowsers.get(normalize(message.getRecipientDeviceId()));
        if (emitters == null || emitters.isEmpty()) {
            return false;
        }
        MailMessageVO payload = toVO(message);
        boolean delivered = false;
        for (SseEmitter emitter : emitters) {
            delivered |= pushTo(emitter, payload);
        }
        return delivered;
    }

    private boolean pushTo(SseEmitter emitter, MailMessageVO message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("starmail")
                    .data(message, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            emitter.complete();
            return false;
        }
    }

    private MailMessageVO toVO(StarMailMessage message) {
        return MailMessageVO.builder()
                .messageId(String.valueOf(message.getMessageId()))
                .listingId(message.getListingId())
                .listingAddress(message.getListingAddress())
                .fromDeviceId(message.getSenderDeviceId())
                .toDeviceId(message.getRecipientDeviceId())
                .subject(message.getSubject())
                .body(message.getBody())
                .sentAt(message.getSentDate() == null ? "" : message.getSentDate().format(SENT_AT))
                .delivery(message.getLocalDeliveryStatus())
                .emailCopyStatus(message.getEmailCopyStatus())
                .emailCopyDetail(message.getEmailCopyDetail())
                .build();
    }

    private String normalize(String deviceId) {
        return deviceId == null ? "" : deviceId.trim();
    }

    /** Outcome of the browser leg; the e-mail leg is tracked separately. */
    public record SendResult(String messageId, boolean delivered, String emailCopyStatus, String status) {
    }
}
