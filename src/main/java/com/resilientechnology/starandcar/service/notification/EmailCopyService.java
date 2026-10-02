package com.resilientechnology.starandcar.service.notification;

import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import com.resilientechnology.starandcar.service.owner.StarMailSpoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * The <strong>parallel, independent</strong> external e-mail copy of a STARMail message.
 *
 * <p>This leg is completely optional. Browser-to-browser delivery never depends on it: it
 * runs on its own {@code starMailEmailExecutor} thread, never throws back into the send path,
 * and can be switched off entirely with {@code starandcar.mail.copy-enabled=false} - in which
 * case users still send and receive STARMail from browser to browser.</p>
 *
 * <p>When enabled, the recipient address is resolved from MariaDB (the registered address on
 * {@code STARMAIL_DEVICE}, or the listing contact address on {@code PROPERTY}). The address is
 * looked up server-side and never exposed to clients.</p>
 */
@Service
public class EmailCopyService {

    private static final Logger logger = LoggerFactory.getLogger(EmailCopyService.class);

    private final JavaMailSender mailSender;
    private final StarMailRepository starMailRepository;
    private final StarMailSpoolService spoolService;
    private final String fromAddress;
    private final boolean copyEnabled;

    public EmailCopyService(
            JavaMailSender mailSender,
            StarMailRepository starMailRepository,
            StarMailSpoolService spoolService,
            @Value("${starandcar.mail.from:${spring.mail.username:}}") String fromAddress,
            @Value("${starandcar.mail.copy-enabled:true}") boolean copyEnabled) {
        this.mailSender = mailSender;
        this.starMailRepository = starMailRepository;
        this.spoolService = spoolService;
        this.fromAddress = fromAddress;
        this.copyEnabled = copyEnabled;
    }

    /**
     * Queues the carbon copy on a separate thread. Safe to call from the send path: nothing
     * this method does can fail, delay or otherwise affect browser-to-browser delivery.
     */
    @Async("starMailEmailExecutor")
    public void dispatchCopyAsync(MailMessageVO message) {
        if (!copyEnabled) {
            // Independent leg is switched off - browser-to-browser delivery is unaffected.
            record(message, "DISABLED", "External email copy is switched off (starandcar.mail.copy-enabled=false).");
            return;
        }

        String deviceId = message.getToDeviceId();
        String messageId = message.getMessageId();
        try {
            if (!StringUtils.hasText(fromAddress)) {
                record(message, "SKIPPED",
                        "No from-address configured (set starandcar.mail.from or spring.mail.username).");
                return;
            }

            Optional<String> registered = resolveRegisteredEmail(deviceId);
            if (registered.isEmpty()) {
                record(message, "SKIPPED",
                        "No registered email in MariaDB for device " + deviceId);
                return;
            }

            SimpleMailMessage copy = new SimpleMailMessage();
            copy.setFrom(fromAddress);
            copy.setTo(registered.get());
            copy.setSubject("STARMail copy - " + safe(message.getSubject(), "(no subject)"));
            copy.setText(buildBody(message));
            mailSender.send(copy);

            record(message, "SENT", "Copy delivered to the registered address.");
            logger.info("STARMail external copy sent for message {}", messageId);

        } catch (Exception e) {
            // Independent leg: log and record, never propagate to the browser leg.
            logger.warn("STARMail external copy failed for message {}: {}", messageId, e.getMessage());
            recordQuietly(message, "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Registered address from MariaDB: device registry first, then the listing table. */
    private Optional<String> resolveRegisteredEmail(String deviceId) {
        Optional<String> registered = starMailRepository.findRegisteredEmail(deviceId);
        if (registered.isPresent()) {
            return registered;
        }
        return starMailRepository.findRegisteredEmailFromListing(deviceId);
    }

    private void record(MailMessageVO message, String status, String detail) {
        spoolService.recordEmailCopyStatus(message.getToDeviceId(), message.getMessageId(), status, detail);
    }

    /** For error handlers, where a secondary failure must not mask the original problem. */
    private void recordQuietly(MailMessageVO message, String status, String detail) {
        try {
            record(message, status, detail);
        } catch (Exception ignored) {
            // already logging the primary failure elsewhere
        }
    }

    private String buildBody(MailMessageVO message) {
        return "A STARMail message was sent to your STAR&Car device.\n\n"
                + "From device: " + safe(message.getFromDeviceId(), "(unknown)") + "\n"
                + "To device:   " + safe(message.getToDeviceId(), "(unknown)") + "\n"
                + (StringUtils.hasText(message.getListingAddress()) ? "Listing:     " + message.getListingAddress() + "\n" : "")
                + "Sent:        " + safe(message.getSentAt(), "") + "\n\n"
                + "Subject: " + safe(message.getSubject(), "(no subject)") + "\n\n"
                + message.getBody() + "\n\n"
                + "---\n"
                + "This is an independent copy delivered to your registered email address. "
                + "The original message is waiting in STARMail in your browser.\n"
                + "Sent from STARMail - Starandcar.com";
    }

    private static String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
