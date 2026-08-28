package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.AddressRequest;
import com.harsh.bookstore.dto.AddressResponse;
import com.harsh.bookstore.entity.DeliveryAddress;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.exception.DefaultAddressDeleteException;
import com.harsh.bookstore.repository.DeliveryAddressRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


/**
 * AddressService — all business logic for managing saved delivery addresses (FEAT-07).
 *
 * OWNERSHIP ENFORCEMENT (design D-01):
 *   Every mutating method calls repository.findById first (→ 404 if absent),
 *   then explicitly checks address.getUserId().equals(userId) (→ 403 if mismatch).
 *   Using findByIdAndUserId would collapse both failure modes into Optional.empty(),
 *   making it impossible to return the correct HTTP status code.
 *
 * DEFAULT-DEMOTION INVARIANT (BR-04):
 *   When saving or updating with isDefault=true, any prior default for the same
 *   user is fetched and set to false before the new one is saved.
 *
 * DELETE GUARD (BR-08/09):
 *   Deleting the default address is blocked while other addresses exist.
 *   Deleting the only address is always allowed, regardless of isDefault.
 */
@Service
public class AddressService {

    private final DeliveryAddressRepository repository;

    public AddressService(DeliveryAddressRepository repository) {
        this.repository = repository;
    }


    // ==================================================================
    // PUBLIC API
    // ==================================================================

    /**
     * List all saved addresses for the authenticated user.
     * Returns an empty list when no addresses exist (AC-02).
     */
    public List<AddressResponse> listAddresses(Long userId) {
        return repository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }


    /**
     * Save a new delivery address for the authenticated user.
     * If isDefault=true, demotes any existing default first (BR-04 / AC-08).
     */
    public AddressResponse saveAddress(Long userId, AddressRequest req) {
        if (req.isDefault()) {
            demoteExistingDefault(userId, null);
        }

        DeliveryAddress address = new DeliveryAddress();
        address.setUserId(userId);
        populateFields(address, req);

        return toResponse(repository.save(address));
    }


    /**
     * Update all fields of an existing address.
     * Enforces ownership (403) and existence (404).
     * If isDefault=true and another address is the current default, demotes it (BR-04).
     */
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest req) {
        DeliveryAddress address = repository.findById(addressId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));

        if (!address.getUserId().equals(userId)) {
            throw new AddressAccessForbiddenException();
        }

        if (req.isDefault()) {
            demoteExistingDefault(userId, addressId);
        }

        populateFields(address, req);
        return toResponse(repository.save(address));
    }


    /**
     * Delete a saved address.
     * Enforces ownership (403) and existence (404).
     * Blocks deletion of the default address while others exist (BR-08 / AC-14).
     * Allows deletion of the only address regardless of isDefault (BR-09 / AC-15).
     */
    public void deleteAddress(Long userId, Long addressId) {
        DeliveryAddress address = repository.findById(addressId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));

        if (!address.getUserId().equals(userId)) {
            throw new AddressAccessForbiddenException();
        }

        long count = repository.countByUserId(userId);
        if (count > 1 && address.isDefault()) {
            throw new DefaultAddressDeleteException();
        }

        repository.delete(address);
    }


    // ==================================================================
    // PRIVATE HELPERS
    // ==================================================================

    /**
     * Demote the current default address for userId (if any) to isDefault=false.
     * Skips the address with the given excludeId (used during updates so the
     * address being updated is not counted as "the other default").
     */
    private void demoteExistingDefault(Long userId, Long excludeId) {
        Optional<DeliveryAddress> existing =
                repository.findByUserIdAndIsDefaultTrue(userId);

        existing.ifPresent(prior -> {
            if (excludeId == null || !prior.getId().equals(excludeId)) {
                prior.setDefault(false);
                repository.save(prior);
            }
        });
    }

    /**
     * Copy all request fields onto the address entity.
     * Used by both saveAddress and updateAddress to avoid duplication.
     */
    private void populateFields(DeliveryAddress address, AddressRequest req) {
        address.setRecipientName(req.getRecipientName());
        address.setPhoneNumber(req.getPhoneNumber());
        address.setLine1(req.getLine1());
        address.setLine2(req.getLine2());
        address.setCity(req.getCity());
        address.setState(req.getState());
        address.setPincode(req.getPincode());
        address.setDefault(req.isDefault());
    }

    /**
     * Map a DeliveryAddress entity to the outbound AddressResponse DTO.
     * Single place for entity → DTO conversion; callers never access entity fields.
     */
    private AddressResponse toResponse(DeliveryAddress address) {
        AddressResponse response = new AddressResponse();
        response.setId(address.getId());
        response.setUserId(address.getUserId());
        response.setRecipientName(address.getRecipientName());
        response.setPhoneNumber(address.getPhoneNumber());
        response.setLine1(address.getLine1());
        response.setLine2(address.getLine2());
        response.setCity(address.getCity());
        response.setState(address.getState());
        response.setPincode(address.getPincode());
        response.setDefault(address.isDefault());
        return response;
    }
}
