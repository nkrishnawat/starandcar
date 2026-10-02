package com.resilientechnology.starandcar.service.notification;

import com.resilientechnology.starandcar.entity.StarMailMessage;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
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
 * The independent carbon-copy leg of a STARMail delivery.
 *
 * <p>This is deliberately decoupled from the browser-delivery leg:</p>
 * <ul>
 *   <li>it runs on its own {@code starMailEmailExecutor} thread,</li>
 *   <li>it never throws back into the send path,</li>
 *   <li>every outcome is written to {@code STARMAIL_MESSAGE.email_copy_status} so a failure
 *       is visible instead of silent.</li>
 * </ul>
 *
 * <p>The recipient address is resolved from MariaDB - either the registered address on
 * {@code STARMAIL_DEVICE} or, failing that, the listing contact address on {@code PROPERTY}.
 * The address is never sent to the client.</p>
 */
@Service
public class EmailCopyService {

    private static final Logger logger = LoggerFactory.getLogger(EmailCopyService.class);

    private final JavaMailSender mailSender;
    private final StarMailRepository starMailRepository;
    private final String fromAddress;

    public EmailCopyService(
            JavaMailSender mailSender,
            StarMailRepository starMailRepository,
            @Value("${starandcar.mail.from:${spring.mail.username:}}") String fromAddress) {
        this.mailSender = mailSender;
        this.starMailRepository = starMailRepository;
        this.fromAddress = fromAddress;
    }

    /**
     * Queues the carbon copy on a separate thread. Safe to call from the send path: nothing
     * this method does can fail or delay the browser delivery.
     */
    @Async("starMailEmailExecutor")
    public void dispatchCopyAsync(StarMailMessage message) {
        Long messageId = message.getMessageId();
        try {
            if (!StringUtils.hasText(fromAddress)) {
                starMailRepository.updateEmailCopyStatus(messageId, StarMailRepository.STATUS_SKIPPED,
                        "No from-address configured (set starandcar.mail.from or spring.mail.username).");
                return;
            }

            Optional<String> registered = resolveRegisteredEmail(message.getRecipientDeviceId());
            if (registered.isEmpty()) {
                starMailRepository.updateEmailCopyStatus(messageId, StarMailRepository.STATUS_SKIPPED,
                        "No registered email in MariaDB for device " + message.getRecipientDeviceId());
                return;
            }

            SimpleMailMessage copy = new SimpleMailMessage();
            copy.setFrom(fromAddress);
            copy.setTo(registered.get());
            copy.setSubject("STARMail copy - " + safe(message.getSubject(), "(no subject)"));
            copy.setText(buildBody(message));
            mailSender.send(copy);

            starMailRepository.updateEmailCopyStatus(messageId, StarMailRepository.STATUS_SENT,
                    "Copy delivered to the registered address.");
            logger.info("STARMail carbon copy sent for message {}", messageId);

        } catch (Exception e) {
            // Independent leg: log and record, never propagate.
            logger.warn("STARMail carbon copy failed for message {}: {}", messageId, e.getMessage());
            starMailRepository.updateEmailCopyStatusQuietly(messageId, StarMailRepository.STATUS_FAILED,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
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

    private String buildBody(StarMailMessage message) {
        return "A STARMail message was sent to your STAR&Car device.\n\n"
                + "From device: " + safe(message.getSenderDeviceId(), "(unknown)") + "\n"
                + "To device:   " + safe(message.getRecipientDeviceId(), "(unknown)") + "\n"
                + (StringUtils.hasText(message.getListingAddress()) ? "Listing:     " + message.getListingAddress() + "\n" : "")
                + "Sent:        " + safe(String.valueOf(message.getSentDate()), "") + "\n\n"
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
