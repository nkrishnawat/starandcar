package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.service.notification.EmailCopyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * STARMail relay - browser to browser, addressed by MAC address / DeviceID / MachineID.
 *
 * <p>Delivery needs nothing external. A message is written to a plain Linux folder first
 * (one compressed queue file per device), so it is never lost even when the receiver's
 * device is switched off. When that device comes online its browser pulls the queue and
 * stores the messages in localStorage, then acknowledges them so the queued copy can be
 * removed from the folder.</p>
 *
 * <p>Two independent legs:</p>
 * <ol>
 *   <li><strong>Browser leg (primary).</strong> Spool to the Linux folder, then push over SSE
 *       if the receiver is online. A polling endpoint covers browsers whose event stream is
 *       buffered or blocked. This leg never uses e-mail and never needs one.</li>
 *   <li><strong>External e-mail copy (parallel, optional).</strong> Dispatched on its own
 *       thread when enabled. It can be switched off - or simply fail - with no effect at all
 *       on browser-to-browser delivery.</li>
 * </ol>
 */
@Service
public class MailRelayService {

    private static final Logger logger = LoggerFactory.getLogger(MailRelayService.class);

    private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Live browser connections per device. A device may hold several open tabs. */
    private final Map<String, Set<SseEmitter>> connectedBrowsers = new ConcurrentHashMap<>();

    private final StarMailSpoolService spoolService;
    private final EmailCopyService emailCopyService;

    public MailRelayService(StarMailSpoolService spoolService, EmailCopyService emailCopyService) {
        this.spoolService = spoolService;
        this.emailCopyService = emailCopyService;
    }

    // ------------------------------------------------------------------ send

    /**
     * Send a STARMail message to a device.
     *
     * <p>The message is queued in the Linux folder first, which is what guarantees it survives
     * until the receiver's device is turned on. Everything after that is best effort.</p>
     */
    public SendResult send(MailMessageVO message) {
        if (message.getMessageId() == null || message.getMessageId().isBlank()) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        if (message.getSentAt() == null || message.getSentAt().isBlank()) {
            message.setSentAt(LocalDateTime.now().format(SENT_AT));
        }
        message.setDelivery("QUEUED");

        // Primary leg: keep a copy on disk so nothing depends on the receiver being online.
        spoolService.append(message.getToDeviceId(), message);
        boolean pushed = push(message);

        // Parallel, independent leg: external e-mail copy. Never blocks, never breaks the above.
        emailCopyService.dispatchCopyAsync(message);

        String status = pushed
                ? "Pushed to the receiver's browser and kept in " + spoolService.spoolFile(message.getToDeviceId()).getFileName()
                + " until that browser confirms storage in its localStorage."
                : "The receiver's device is offline - the copy is waiting in "
                + spoolService.spoolFile(message.getToDeviceId()).getFileName()
                + " and will reach its browser when that device comes online. Nothing is dropped.";

        return new SendResult(message.getMessageId(), pushed, message.getEmailCopyStatus(), status);
    }

    // ------------------------------------------------------------- subscription

    /**
     * Keeps the receiver's browser on the line so incoming mail can be pushed straight into
     * its localStorage. Everything queued while it was away is flushed on connect.
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
        for (MailMessageVO pending : spoolService.readAll(device)) {
            if (!pushTo(emitter, pending)) {
                break;
            }
        }
        return emitter;
    }

    /**
     * Polling fallback: returns the queue waiting in the Linux folder for this device. A
     * browser whose event stream is blocked still receives mail this way.
     */
    public List<MailMessageVO> inbox(String deviceId) {
        return spoolService.readAll(normalize(deviceId));
    }

    /**
     * The receiver's browser confirms the message now lives in its localStorage, so the copy
     * can be removed from the Linux folder.
     */
    public void received(String deviceId, String messageId) {
        spoolService.prune(normalize(deviceId), messageId);
    }

    // ------------------------------------------------------------------ push

    private boolean push(MailMessageVO message) {
        Set<SseEmitter> emitters = connectedBrowsers.get(normalize(message.getToDeviceId()));
        if (emitters == null || emitters.isEmpty()) {
            return false;
        }
        boolean delivered = false;
        for (SseEmitter emitter : emitters) {
            delivered |= pushTo(emitter, message);
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
            logger.debug("STARMail push failed, message stays queued: {}", e.getMessage());
            emitter.complete();
            return false;
        }
    }

    private String normalize(String deviceId) {
        return deviceId == null ? "" : deviceId.trim();
    }

    /** Outcome of the browser leg; the e-mail leg is tracked on the message itself. */
    public record SendResult(String messageId, boolean delivered, String emailCopyStatus, String status) {
    }
}
