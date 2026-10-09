package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.event.listener.PropertyEventListener;
import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * STARMail relay - browser to browser, addressed by MAC address / DeviceID / MachineID.
 *
 * <p>Delivery needs nothing external. A message is written to a plain Linux folder first
 * (one compressed queue file per device), so it is never lost even when the receiver's device
 * is switched off. When that device comes online its browser pulls the queue and stores the
 * messages in localStorage, then acknowledges them so the queued copy can be removed from the
 * folder.</p>
 *
 * <p><strong>Delivery is a pull, not a push.</strong> The browser asks for its mail over a plain
 * REST call when it opens and then every few seconds after that. There is deliberately no
 * long-lived server-to-client connection: the earlier Server-Sent Events channel added a held
 * connection per device per tab, and reverse proxies buffer and silently break such streams
 * anyway. The queue on disk already makes the message safe - a browser that is offline simply
 * picks it up on its next call.</p>
 *
 * <p>Machine-to-machine is the only delivery: every message is addressed by the recipient's
 * device id. A listing published before device ids existed has no routing key at all, and for
 * those - and only those - the message is posted as an e-mail to the owner's contact address
 * with a single method call to the application's proven send ({@code PropertyEventListener},
 * configured in application.yaml). There is no second send implementation and no extra mail
 * configuration anywhere in the application.</p>
 */
@Service
public class MailRelayService {

    private static final Logger logger = LoggerFactory.getLogger(MailRelayService.class);

    private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Recent sends per recipient, for the abuse guard in {@link #throttle}. */
    private final Map<String, Deque<Long>> recentSends = new ConcurrentHashMap<>();

    static final long THROTTLE_WINDOW_MS = 10 * 60 * 1000L;
    static final int THROTTLE_LIMIT = 5;

    private final StarMailSpoolService spoolService;
    private final StarMailRepository starMailRepository;
    private final PropertyEventListener propertyEventListener;

    public MailRelayService(StarMailSpoolService spoolService, StarMailRepository starMailRepository,
            PropertyEventListener propertyEventListener) {
        this.spoolService = spoolService;
        this.starMailRepository = starMailRepository;
        this.propertyEventListener = propertyEventListener;
    }

    // ------------------------------------------------------------------ send

    /**
     * Send a STARMail message to a device.
     *
     * <p>The message is queued in the Linux folder first, which is what guarantees it survives
     * until the receiver's device is turned on and its browser next asks for its mail.</p>
     */
    public SendResult send(MailMessageVO message) {
        if (message.getMessageId() == null || message.getMessageId().isBlank()) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        if (message.getSentAt() == null || message.getSentAt().isBlank()) {
            message.setSentAt(LocalDateTime.now().format(SENT_AT));
        }

        String device = normalize(message.getToDeviceId());
        boolean routable = StringUtils.hasText(device);

        // With no routing key the message can only travel as an e-mail, and that needs an
        // address to resolve from the listing. Reject rather than accept a message that could
        // never be delivered anywhere at all.
        if (!routable && message.getListingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A recipient device id or a listing id is required.");
        }

        throttle(message);

        // Primary leg: keep a copy on disk so nothing is lost while the receiver is away.
        // Skipped when there is no device to deliver to - there is no browser to spool for.
        if (routable) {
            message.setDelivery("QUEUED");
            spoolService.append(device, message);
        }

        // A listing published before device ids existed has no routing key at all: the message
        // then travels as an e-mail to the owner's contact address instead - one method call to
        // the application's proven send (PropertyEventListener, configured in application.yaml).
        if (!routable) {
            Optional<String> contact = starMailRepository.findContactEmailByPropertyId(message.getListingId());
            if (contact.isPresent()) {
                sendAsEmail(contact.get(), message);
            } else {
                logger.warn("Listing {} has no device id and no contact address - the message goes nowhere",
                        message.getListingId());
            }
        }

        String status = !routable
                ? "This listing has no STARMail device, so there is no browser to route to. "
                    + "The message was sent as an e-mail to the owner's contact address only."
                : "Queued in " + spoolService.spoolFile(device).getFileName()
                    + ". The receiver's browser will collect it the next time it asks for its mail.";

        return new SendResult(message.getMessageId(), routable, status);
    }

    /**
     * Abuse guard. Once a listing carries no routing key this endpoint will e-mail whatever
     * address is published on it, which is what a contact form should do - and what a spammer
     * would like. A modest per-recipient ceiling stops bulk abuse without getting in a real
     * person's way.
     */
    private void throttle(MailMessageVO message) {
        String key = StringUtils.hasText(message.getToDeviceId())
                ? "d:" + message.getToDeviceId()
                : "l:" + message.getListingId();

        long now = System.currentTimeMillis();
        Deque<Long> recent = recentSends.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && now - recent.peekFirst() > THROTTLE_WINDOW_MS) {
                recent.pollFirst();
            }
            if (recent.size() >= THROTTLE_LIMIT) {
                logger.info("STARMail throttle hit for {}", key);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many messages to this recipient. Please wait a few minutes and try again.");
            }
            recent.addLast(now);
        }
    }

    // ------------------------------------------------------------- pickup

    /**
     * Returns every message waiting for this device in the Linux folder. The browser calls this
     * when it opens and then on an interval; anything returned is stored in localStorage and
     * acknowledged so the queued copy can be dropped.
     */
    public List<MailMessageVO> inbox(String deviceId) {
        return spoolService.readAll(normalize(deviceId));
    }

    /**
     * The receiver's browser confirms a message now lives in its localStorage, so the copy
     * can be removed from the Linux folder.
     */
    public void received(String deviceId, String messageId) {
        spoolService.prune(normalize(deviceId), messageId);
    }

    /** The legacy-listing fallback: post the message as an e-mail - just a method call. */
    private void sendAsEmail(String contact, MailMessageVO message) {
        try {
            propertyEventListener.sendEmail(contact, message.getSubject(), emailBody(message), false);
        } catch (Exception e) {
            // SMTP trouble must never break the send path
            logger.warn("E-mail delivery to the listing contact failed for message {}: {}",
                    message.getMessageId(), e.getMessage());
        }
    }

    private String emailBody(MailMessageVO message) {
        return message.getBody() + "\n\n---\nFrom device: " + safe(message.getFromDeviceId(), "(unknown)")
                + (StringUtils.hasText(message.getListingAddress()) ? "\nListing: " + message.getListingAddress() : "")
                + "\nSent: " + safe(message.getSentAt(), "") + "\nSent from STARMail - Starandcar.com";
    }

    private static String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalize(String deviceId) {
        return deviceId == null ? "" : deviceId.trim();
    }

    /** Outcome of the send: {@code queued} means the message is safe in the folder queue. */
    public record SendResult(String messageId, boolean queued, String status) {
    }
}
