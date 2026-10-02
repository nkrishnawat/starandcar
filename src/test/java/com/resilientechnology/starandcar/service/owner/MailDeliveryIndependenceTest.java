package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.entity.StarMailMessage;
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
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two STARMail delivery legs must be independent.
 *
 * <p>Browser delivery is the primary leg and must succeed even when the registered-e-mail
 * carbon copy cannot be sent. This is the regression guard for the open issue "when sender
 * sends message nothing arrives".</p>
 */
@ExtendWith(MockitoExtension.class)
class MailDeliveryIndependenceTest {

    private static final String SENDER = "02:aa:11:22:33:44";
    private static final String RECIPIENT = "02:bb:55:66:77:88";

    @Mock
    private StarMailRepository repository;

    @Mock
    private JavaMailSender mailSender;

    private MailRelayService mailRelayService;

    @BeforeEach
    void setUp() {
        EmailCopyService emailCopyService = new EmailCopyService(mailSender, repository, "noreply@starandcar.test");
        mailRelayService = new MailRelayService(repository, emailCopyService);
    }

    private StarMailMessage storedMessage(long id) {
        return StarMailMessage.builder()
                .messageId(id)
                .senderDeviceId(SENDER)
                .recipientDeviceId(RECIPIENT)
                .subject("Availability?")
                .body("Is the room free in June?")
                .build();
    }

    private MailMessageVO outgoing() {
        return MailMessageVO.builder()
                .fromDeviceId(SENDER)
                .toDeviceId(RECIPIENT)
                .subject("Availability?")
                .body("Is the room free in June?")
                .build();
    }

    @Test
    void browserDeliverySucceedsEvenWhenTheEmailCopyFails() {
        when(repository.insertMessage(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(7L);
        when(repository.findMessage(7L)).thenReturn(Optional.of(storedMessage(7L)));
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.of("owner@example.com"));
        doThrow(new MailSendException("SMTP down"))
                .when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        MailRelayService.SendResult result = mailRelayService.send(outgoing());

        // Primary leg is unaffected by the failing carbon copy.
        assertThat(result.delivered()).isTrue();
        assertThat(result.messageId()).isEqualTo("7");

        // The failure is recorded rather than swallowed silently.
        verify(repository).updateEmailCopyStatusQuietly(
                eq(7L), eq(StarMailRepository.STATUS_FAILED), anyString());
    }

    @Test
    void messageIsStillDeliveredWhenNoRegisteredEmailExists() {
        when(repository.insertMessage(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(8L);
        when(repository.findMessage(8L)).thenReturn(Optional.of(storedMessage(8L)));
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.empty());
        when(repository.findRegisteredEmailFromListing(RECIPIENT)).thenReturn(Optional.empty());

        MailRelayService.SendResult result = mailRelayService.send(outgoing());

        assertThat(result.delivered()).isTrue();
        // Carbon copy is skipped, but the message still reaches the recipient's browser.
        verify(repository).updateEmailCopyStatus(8L, StarMailRepository.STATUS_SKIPPED, "No registered email in MariaDB for device " + RECIPIENT);
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void registeredEmailIsLookedUpFromTheListingTableAsAFallback() {
        when(repository.insertMessage(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(9L);
        when(repository.findMessage(9L)).thenReturn(Optional.of(storedMessage(9L)));
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.empty());
        when(repository.findRegisteredEmailFromListing(RECIPIENT)).thenReturn(Optional.of("listing-owner@example.com"));

        mailRelayService.send(outgoing());

        ArgumentCaptor<org.springframework.mail.SimpleMailMessage> captor =
                ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        // The copy goes to the MariaDB-registered address, never to a client-supplied one.
        assertThat(captor.getValue().getTo()).containsExactly("listing-owner@example.com");
        verify(repository).updateEmailCopyStatus(9L, StarMailRepository.STATUS_SENT, "Copy delivered to the registered address.");
    }

    @Test
    void aMissingFromAddressSkipsTheCopyWithoutFailingTheSend() {
        EmailCopyService noFrom = new EmailCopyService(mailSender, repository, "");
        MailRelayService service = new MailRelayService(repository, noFrom);

        when(repository.insertMessage(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(10L);
        when(repository.findMessage(10L)).thenReturn(Optional.of(storedMessage(10L)));

        MailRelayService.SendResult result = service.send(outgoing());

        assertThat(result.delivered()).isTrue();
        verify(repository).updateEmailCopyStatus(eq(10L), eq(StarMailRepository.STATUS_SKIPPED), anyString());
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void sendPersistsTheMessageBeforeAnythingElse() {
        when(repository.insertMessage(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(11L);
        when(repository.findMessage(11L)).thenReturn(Optional.of(storedMessage(11L)));
        when(repository.findRegisteredEmail(RECIPIENT)).thenReturn(Optional.empty());
        when(repository.findRegisteredEmailFromListing(RECIPIENT)).thenReturn(Optional.empty());

        mailRelayService.send(outgoing());

        // The message is written for browser pickup by device id - no email anywhere in the row.
        verify(repository).insertMessage(eq(SENDER), eq(RECIPIENT), any(), any(), eq("Availability?"), eq("Is the room free in June?"));
        verify(repository, never()).updateEmailCopyStatusQuietly(anyLong(), anyString(), anyString());
    }
}
