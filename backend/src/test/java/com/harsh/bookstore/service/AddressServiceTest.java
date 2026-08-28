package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.AddressRequest;
import com.harsh.bookstore.dto.AddressResponse;
import com.harsh.bookstore.entity.DeliveryAddress;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.exception.DefaultAddressDeleteException;
import com.harsh.bookstore.repository.DeliveryAddressRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for AddressService.
 * No Spring context — service is instantiated directly with Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private DeliveryAddressRepository repository;

    private AddressService addressService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ADDRESS_ID = 10L;


    @BeforeEach
    void setUp() {
        addressService = new AddressService(repository);
    }


    // ==================================================================
    // HELPERS
    // ==================================================================

    private DeliveryAddress address(Long id, Long userId, boolean isDefault) {
        DeliveryAddress a = new DeliveryAddress();
        a.setId(id);
        a.setUserId(userId);
        a.setRecipientName("Test User");
        a.setPhoneNumber("9876543210");
        a.setLine1("1 Test Street");
        a.setCity("Mumbai");
        a.setState("Maharashtra");
        a.setPincode("400001");
        a.setDefault(isDefault);
        return a;
    }

    private AddressRequest validRequest(boolean isDefault) {
        AddressRequest req = new AddressRequest();
        req.setRecipientName("Test User");
        req.setPhoneNumber("9876543210");
        req.setLine1("1 Test Street");
        req.setCity("Mumbai");
        req.setState("Maharashtra");
        req.setPincode("400001");
        req.setDefault(isDefault);
        return req;
    }

    /**
     * Stub save to return its argument unchanged.
     * lenient() avoids UnnecessaryStubbing in tests that don't call save.
     */
    private void stubSave() {
        lenient().when(repository.save(any(DeliveryAddress.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }


    // ==================================================================
    // listAddresses
    // ==================================================================

    @Test
    void listAddresses_returnsUserAddresses() {
        when(repository.findAllByUserId(USER_ID))
                .thenReturn(List.of(address(ADDRESS_ID, USER_ID, false),
                                    address(11L, USER_ID, true)));

        List<AddressResponse> results = addressService.listAddresses(USER_ID);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getUserId().equals(USER_ID));
    }


    // ==================================================================
    // saveAddress
    // ==================================================================

    @Test
    void saveAddress_success() {
        stubSave();
        // isDefault=false → findByUserIdAndIsDefaultTrue is never called; no stub needed

        AddressResponse response = addressService.saveAddress(USER_ID, validRequest(false));

        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getCity()).isEqualTo("Mumbai");
    }

    @Test
    void saveAddress_demotesExistingDefault() {
        DeliveryAddress priorDefault = address(9L, USER_ID, true);
        stubSave();
        when(repository.findByUserIdAndIsDefaultTrue(USER_ID))
                .thenReturn(Optional.of(priorDefault));

        addressService.saveAddress(USER_ID, validRequest(true));

        // save called for the demoted old default (now false) + the new address
        verify(repository, org.mockito.Mockito.times(2)).save(any(DeliveryAddress.class));
        assertThat(priorDefault.isDefault()).isFalse();
    }


    // ==================================================================
    // updateAddress
    // ==================================================================

    @Test
    void updateAddress_success() {
        DeliveryAddress existing = address(ADDRESS_ID, USER_ID, false);
        when(repository.findById(ADDRESS_ID)).thenReturn(Optional.of(existing));
        // isDefault=false → findByUserIdAndIsDefaultTrue is never called; no stub needed
        stubSave();

        AddressResponse response = addressService.updateAddress(USER_ID, ADDRESS_ID,
                validRequest(false));

        assertThat(response.getCity()).isEqualTo("Mumbai");
    }

    @Test
    void updateAddress_forbidden() {
        when(repository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(ADDRESS_ID, OTHER_USER_ID, false)));

        assertThatThrownBy(() ->
                addressService.updateAddress(USER_ID, ADDRESS_ID, validRequest(false)))
                .isInstanceOf(AddressAccessForbiddenException.class);
    }

    @Test
    void updateAddress_notFound() {
        when(repository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                addressService.updateAddress(USER_ID, ADDRESS_ID, validRequest(false)))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessageContaining(String.valueOf(ADDRESS_ID));
    }


    // ==================================================================
    // deleteAddress
    // ==================================================================

    @Test
    void deleteAddress_success() {
        DeliveryAddress addr = address(ADDRESS_ID, USER_ID, false);
        when(repository.findById(ADDRESS_ID)).thenReturn(Optional.of(addr));
        when(repository.countByUserId(USER_ID)).thenReturn(2L);

        addressService.deleteAddress(USER_ID, ADDRESS_ID);

        verify(repository).delete(addr);
    }

    @Test
    void deleteAddress_forbidden() {
        when(repository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(ADDRESS_ID, OTHER_USER_ID, false)));

        assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, ADDRESS_ID))
                .isInstanceOf(AddressAccessForbiddenException.class);
    }

    @Test
    void deleteAddress_notFound() {
        when(repository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, ADDRESS_ID))
                .isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void deleteAddress_defaultWithOthersPresent_throws() {
        when(repository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(ADDRESS_ID, USER_ID, true)));
        when(repository.countByUserId(USER_ID)).thenReturn(2L);

        assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, ADDRESS_ID))
                .isInstanceOf(DefaultAddressDeleteException.class)
                .hasMessage("Cannot delete the default address while other addresses exist");
    }

    @Test
    void deleteAddress_onlyAddress_succeeds() {
        DeliveryAddress addr = address(ADDRESS_ID, USER_ID, true);
        when(repository.findById(ADDRESS_ID)).thenReturn(Optional.of(addr));
        when(repository.countByUserId(USER_ID)).thenReturn(1L); // only address

        addressService.deleteAddress(USER_ID, ADDRESS_ID);

        verify(repository).delete(addr);  // deletion proceeds despite isDefault=true
    }

    @Test
    void deleteAddress_nonDefaultWithOthers_success() {
        DeliveryAddress addr = address(ADDRESS_ID, USER_ID, false);
        when(repository.findById(ADDRESS_ID)).thenReturn(Optional.of(addr));
        when(repository.countByUserId(USER_ID)).thenReturn(3L);

        addressService.deleteAddress(USER_ID, ADDRESS_ID);

        verify(repository).delete(addr);
        verify(repository, never()).save(any());
    }
}
