package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.event.listener.PropertyEventListener;
import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A listing published before STARMail device ids existed has no routing key at all.
 *
 * <p>It still has a contact address, so the enquiry must not dead-end - it goes out as an
 * e-mail instead, as a single method call to {@code PropertyEventListener}: the application's
 * proven send, configured by application.yaml. These tests cover that fallback and the abuse
 * guard that has to come with it: once the endpoint will e-mail whatever address is published
 * on a listing, it needs a ceiling on how often it will do so.</p>
 */
@ExtendWith(MockitoExtension.class)
class MailEmailOnlyFallbackTest {

    private static final String SENDER = "02:aa:11:22:33:44";
    private static final Long LISTING = 4242L;

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
    }

    /** The reported case: legacy listing, contact address present, device id absent. */
    private MailMessageVO enquiryToLegacyListing() {
        return MailMessageVO.builder()
                .listingId(LISTING)
                .fromDeviceId(SENDER)
                .toDeviceId("")
                .subject("Availability?")
                .body("Is the room free in June?")
                .build();
    }

    @Test
    void aListingWithNoDeviceIdIsStillReachedByEmail() throws Exception {
        when(repository.findContactEmailByPropertyId(LISTING)).thenReturn(Optional.of("owner@example.com"));

        MailRelayService.SendResult result = mailRelayService.send(enquiryToLegacyListing());

        // one method call to the application's proven e-mail send - nothing else
        verify(propertyEventListener).sendEmail(eq("owner@example.com"), eq("Availability?"), anyString(), eq(false));

        // there is no browser to deliver to, so nothing is queued against a device
        verify(spoolService, never()).append(anyString(), any(MailMessageVO.class));
        assertThat(result.queued()).isFalse();
        assertThat(result.status()).contains("e-mail");
    }

    @Test
    void aMessageWithNeitherDeviceNorListingIsRejected() {
        MailMessageVO message = MailMessageVO.builder()
                .fromDeviceId(SENDER)
                .toDeviceId("")
                .subject("Hi")
                .body("There is nobody to send this to.")
                .build();

        assertThatThrownBy(() -> mailRelayService.send(message))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void bulkEnquiriesToOneListingAreRejected() {
        when(repository.findContactEmailByPropertyId(LISTING)).thenReturn(Optional.of("owner@example.com"));

        for (int i = 0; i < MailRelayService.THROTTLE_LIMIT; i++) {
            mailRelayService.send(enquiryToLegacyListing());
        }

        assertThatThrownBy(() -> mailRelayService.send(enquiryToLegacyListing()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many messages");
    }

    @Test
    void theThrottleIsPerListingNotGlobal() {
        when(repository.findContactEmailByPropertyId(LISTING)).thenReturn(Optional.of("owner@example.com"));
        when(repository.findContactEmailByPropertyId(9999L)).thenReturn(Optional.of("other@example.com"));

        for (int i = 0; i < MailRelayService.THROTTLE_LIMIT; i++) {
            mailRelayService.send(enquiryToLegacyListing());
        }

        // a different owner is unaffected by one recipient being at its ceiling
        MailMessageVO otherOwner = MailMessageVO.builder()
                .listingId(9999L)
                .fromDeviceId(SENDER)
                .toDeviceId("")
                .subject("Availability?")
                .body("Different listing.")
                .build();

        assertThat(mailRelayService.send(otherOwner).messageId()).isNotBlank();
    }
}
