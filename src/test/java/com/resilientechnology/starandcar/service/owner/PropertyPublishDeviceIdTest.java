package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.record.PropertyDetailVO;
import com.resilientechnology.starandcar.repository.owner.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Regression guard for the reported defect: clicking the mail icon on a freshly published
 * listing says <em>"This owner has not activated STARMail yet"</em>.
 *
 * <p>Cause: {@code PropertyService.to_entity} built the {@link Property} from the publish form
 * but never copied {@code contactDeviceId} across, so the column was written {@code NULL} on
 * every single publish. No listing was ever reachable on STARMail, regardless of what the
 * browser sent.</p>
 */
@ExtendWith(MockitoExtension.class)
class PropertyPublishDeviceIdTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @TempDir
    private Path uploadDir;

    private PropertyService service;

    @BeforeEach
    void setUp() {
        service = new PropertyService();
        ReflectionTestUtils.setField(service, "propertyRepository", propertyRepository);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    private PropertyDetailVO details(String deviceId) {
        return PropertyDetailVO.builder()
                .address("221B Baker Street")
                .email("owner@example.com")
                .phone("+91 90000 00000")
                .description("Room to let")
                .notes("Nice place")
                .contactDeviceId(deviceId)
                .build();
    }

    private MockMultipartFile anImage() {
        return new MockMultipartFile("files", "room.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private Property publish(String deviceId) {
        service.save(details(deviceId), List.of(anImage()));
        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void theDeviceIdFromThePublishFormIsStoredOnTheListing() {
        Property saved = publish("02:aa:11:22:33:44");

        assertThat(saved.getContactDeviceId()).isEqualTo("02:aa:11:22:33:44");
    }

    @Test
    void theRestOfTheListingIsStillMapped() {
        Property saved = publish("02:aa:11:22:33:44");

        assertThat(saved.getAddress()).isEqualTo("221B Baker Street");
        assertThat(saved.getContactEmail()).isEqualTo("owner@example.com");
        assertThat(saved.getContactPhoneNo()).isEqualTo("+91 90000 00000");
        assertThat(saved.getManageTokenHash()).isNotBlank();
    }

    @Test
    void aPublishWithNoDeviceIdStoresNothingRatherThanInventingOne() {
        Property saved = publish(null);

        assertThat(saved.getContactDeviceId()).isNull();
        // a key is never fabricated server-side - only the owner's browser can mint one
        assertThat(saved.getContactEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void publishingStillFiresTheCreatedEvent() {
        publish("02:aa:11:22:33:44");

        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
