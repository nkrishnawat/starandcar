package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.event.listener.PropertyEventListener;
import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STARMail core contract: machine-to-machine delivery with a Linux folder copy, so nothing is
 * lost while the receiver's device is off.
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
    private PropertyEventListener propertyEventListener;

    private MailRelayService mailRelayService;

    @BeforeEach
    void setUp() {
        mailRelayService = new MailRelayService(spoolService, repository, propertyEventListener);
        when(spoolService.spoolFile(anyString())).thenReturn(Path.of("/var/lib/starmail/starmail-queue.gz"));
    }

    private MailMessageVO outgoing() {
        return MailMessageVO.builder()
                .listingId(4242L)
                .fromDeviceId(SENDER)
                .toDeviceId(RECIPIENT)
                .subject("Availability?")
                .body("Is the room free in June?")
                .build();
    }

    /** CORE: the message is queued for the receiver's device - the regression guard. */
    @Test
    void theMessageIsQueuedForTheReceiverDevice() {
        MailRelayService.SendResult result = mailRelayService.send(outgoing());

        verify(spoolService).append(eq(RECIPIENT), any(MailMessageVO.class));
        assertThat(result.queued()).isTrue();
        // ...waiting in the Linux folder for that device to come online.
        assertThat(result.status()).contains("Queued");
        assertThat(result.status()).contains("collect it");
    }

    /** Addressing is by device id; no e-mail address is ever part of the routing. */
    @Test
    void messagesAreRoutedByDeviceIdAndCarryNoEmailAddress() {
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
}
