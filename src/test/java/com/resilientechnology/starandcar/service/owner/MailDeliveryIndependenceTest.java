package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import com.resilientechnology.starandcar.service.notification.EmailCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STARMail core contract: browser-to-browser delivery with a Linux folder copy, working
 * completely independently of external e-mail.
 *
 * <p>Regression guard for the open issue "when sender sends message nothing arrives".</p>
 */
@ExtendWith(MockitoExtension.class)
class MailDeliveryIndependenceTest {

    private static final String SENDER = "02:aa:11:22:33:44";
    private static final String RECIPIENT = "02:bb:55:66:77:88";

    @Mock
    private StarMailSpoolService spoolService;

    @Mock
    private StarMailRepository repository;

    @Mock
    private JavaMailSender mailSender;

    private MailRelayService mailRelayService;

    @BeforeEach
    void setUp() {
        EmailCopyService emailCopyService =
                new EmailCopyService(mailSender, repository, spoolService, "noreply@starandcar.test", true);
        mailRelayService = new MailRelayService(spoolService, emailCopyService);
        when(spoolService.spoolFile(anyString())).thenReturn(Path.of("/var/lib/starmail/starmail-queue.gz"));
    }

    private MailMessageVO outgoing() {
        return MailMessageVO.builder()
                .fromDeviceId(SENDER)
                .toDeviceId(RECIPIENT)
                .subject("Availability?")
                .body("Is the room free in June?")
                .build();
    }

    /** CORE: a copy is kept in the Linux folder while the receiver's device is switched off. */
    @Test
    void messageIsKeptInTheLinuxFolderWhenTheReceiverIsOffline() {
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.empty());
        when(repository.findRegisteredEmailFromListing(RECIPIENT)).thenReturn(Optional.empty());

        MailRelayService.SendResult result = mailRelayService.send(outgoing());

        // No browser was connected, so nothing could be pushed...
        assertThat(result.delivered()).isFalse();
        // ...but the message is queued on disk, waiting for that device to come online.
        verify(spoolService).append(eq(RECIPIENT), any(MailMessageVO.class));
        assertThat(result.status()).contains("offline");
        assertThat(result.status()).contains("Nothing is dropped");
    }

    /** CORE: browser-to-browser works even when the external e-mail leg fails outright. */
    @Test
    void browserDeliveryWorksEvenWhenTheExternalEmailFails() {
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.of("owner@example.com"));
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        MailRelayService.SendResult result = mailRelayService.send(outgoing());

        // The message is safely queued regardless of the failing e-mail leg.
        verify(spoolService).append(eq(RECIPIENT), any(MailMessageVO.class));
        assertThat(result.messageId()).isNotBlank();
        // The failure is recorded rather than swallowed silently.
        verify(spoolService).recordEmailCopyStatus(eq(RECIPIENT), anyString(), eq("FAILED"), anyString());
    }

    /** CORE: switching external e-mail off leaves browser-to-browser fully working. */
    @Test
    void disablingExternalEmailStillAllowsBrowserToBrowserMessaging() {
        EmailCopyService disabled =
                new EmailCopyService(mailSender, repository, spoolService, "noreply@starandcar.test", false);
        MailRelayService service = new MailRelayService(spoolService, disabled);

        service.send(outgoing());

        // Delivery is unaffected...
        verify(spoolService).append(eq(RECIPIENT), any(MailMessageVO.class));
        // ...and no external e-mail is ever attempted.
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(spoolService).recordEmailCopyStatus(eq(RECIPIENT), anyString(), eq("DISABLED"), anyString());
    }

    /** The parallel leg is a copy only: it goes to the MariaDB-registered address. */
    @Test
    void externalCopyIsSentToTheMariaDBRegisteredAddress() {
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.empty());
        when(repository.findRegisteredEmailFromListing(RECIPIENT)).thenReturn(Optional.of("listing-owner@example.com"));

        mailRelayService.send(outgoing());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("listing-owner@example.com");
        verify(spoolService).recordEmailCopyStatus(eq(RECIPIENT), anyString(), eq("SENT"), anyString());
    }

    /** No registered address anywhere: the copy is skipped, delivery is untouched. */
    @Test
    void missingRegisteredEmailSkipsTheCopyButStillDelivers() {
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.empty());
        when(repository.findRegisteredEmailFromListing(RECIPIENT)).thenReturn(Optional.empty());

        mailRelayService.send(outgoing());

        verify(spoolService).append(eq(RECIPIENT), any(MailMessageVO.class));
        verify(spoolService).recordEmailCopyStatus(eq(RECIPIENT), anyString(), eq("SKIPPED"), anyString());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    /** Addressing is by device id; no e-mail address is ever part of the routing. */
    @Test
    void messagesAreRoutedByDeviceIdAndCarryNoEmailAddress() {
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.empty());
        when(repository.findRegisteredEmailFromListing(RECIPIENT)).thenReturn(Optional.empty());

        MailMessageVO message = outgoing();
        mailRelayService.send(message);

        ArgumentCaptor<MailMessageVO> captor = ArgumentCaptor.forClass(MailMessageVO.class);
        verify(spoolService).append(eq(RECIPIENT), captor.capture());

        MailMessageVO queued = captor.getValue();
        assertThat(queued.getToDeviceId()).isEqualTo(RECIPIENT);
        assertThat(queued.getFromDeviceId()).isEqualTo(SENDER);
        assertThat(queued.getMessageId()).isNotBlank();
        assertThat(queued.getDelivery()).isEqualTo("QUEUED");
    }

    /** A missing from-address must not break the send. */
    @Test
    void aMissingFromAddressSkipsTheCopyWithoutFailingTheSend() {
        EmailCopyService noFrom =
                new EmailCopyService(mailSender, repository, spoolService, "", true);
        MailRelayService service = new MailRelayService(spoolService, noFrom);

        service.send(outgoing());

        verify(spoolService).append(eq(RECIPIENT), any(MailMessageVO.class));
        verify(spoolService).recordEmailCopyStatus(eq(RECIPIENT), anyString(), eq("SKIPPED"), anyString());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
