package com.resilientechnology.starandcar.repository.owner;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.mapers.PropertyRowMapper;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the reported defect: <em>"the To section is empty always"</em> and
 * <em>"why was device id not populating?"</em>.
 *
 * <p>{@code contact_device_id} is stamped from the publishing browser, so a listing created
 * before that field existed - or published by a browser whose STARMail bootstrap never ran -
 * carries {@code NULL} and the compose window has nothing to address to. The owner's browser
 * fingerprint cannot be recomputed from the row, but if that owner registered a STARMail device
 * on the address stored in {@code contact_email} the routing key is recoverable from the device
 * registry. These tests pin that repair down.</p>
 */
@ExtendWith(MockitoExtension.class)
class PropertyStarMailRoutingTest {

    private static final String OWNER_DEVICE = "02:aa:11:22:33:44";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StarMailRepository starMailRepository;

    private PropertyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PropertyRepository(jdbcTemplate);
        ReflectionTestUtils.setField(repository, "starMailRepository", starMailRepository);
    }

    /** A listing published before STARMail device ids existed. */
    private Property legacyListing() {
        return Property.builder()
                .propertyId(7L)
                .address("221B Baker Street")
                .contactEmail("owner@example.com")
                .contactDeviceId(null)
                .build();
    }

    private void stubGetById(Property property) {
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), any(PropertyRowMapper.class)))
                .thenReturn(property);
    }

    @SuppressWarnings("unchecked")
    private void stubSearch(List<Property> properties) {
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(properties);
    }

    @Test
    void aListingPublishedBeforeTheDeviceIdExistedIsRepairedFromTheDeviceRegistry() {
        stubGetById(legacyListing());
        when(starMailRepository.findDeviceIdByRegisteredEmail("owner@example.com"))
                .thenReturn(Optional.of(OWNER_DEVICE));

        Property property = repository.getPropertyById(7L);

        // The compose window reads this field, so this is what stops "To" from rendering empty.
        assertThat(property.getContactDeviceId()).isEqualTo(OWNER_DEVICE);
    }

    @Test
    void searchResultsAreRepairedTheSameWay() {
        stubSearch(List.of(legacyListing()));
        when(starMailRepository.findDeviceIdByRegisteredEmail("owner@example.com"))
                .thenReturn(Optional.of(OWNER_DEVICE));

        List<Property> results = repository.searchByText("baker");

        assertThat(results).singleElement().extracting(Property::getContactDeviceId).isEqualTo(OWNER_DEVICE);
    }

    @Test
    void aListingThatAlreadyCarriesADeviceIdKeepsIt() {
        Property published = Property.builder()
                .propertyId(8L)
                .address("10 Downing Street")
                .contactEmail("owner@example.com")
                .contactDeviceId("02:de:ad:be:ef:01")
                .build();
        stubGetById(published);

        Property property = repository.getPropertyById(8L);

        assertThat(property.getContactDeviceId()).isEqualTo("02:de:ad:be:ef:01");
        // an already-addressable listing must never be re-pointed at some other device
        verify(starMailRepository, never()).findDeviceIdByRegisteredEmail(anyString());
    }

    @Test
    void aListingWithNoContactAddressIsLeftAlone() {
        Property unaddressable = Property.builder()
                .propertyId(9L)
                .address("Nowhere")
                .contactEmail(null)
                .contactDeviceId(null)
                .build();
        stubGetById(unaddressable);

        Property property = repository.getPropertyById(9L);

        assertThat(property.getContactDeviceId()).isNull();
        verify(starMailRepository, never()).findDeviceIdByRegisteredEmail(anyString());
    }

    @Test
    void aListingIsLeftAloneWhenNobodyRegisteredADeviceOnItsAddress() {
        stubGetById(legacyListing());
        when(starMailRepository.findDeviceIdByRegisteredEmail("owner@example.com"))
                .thenReturn(Optional.empty());

        Property property = repository.getPropertyById(7L);

        assertThat(property.getContactDeviceId()).isNull();
    }
}
