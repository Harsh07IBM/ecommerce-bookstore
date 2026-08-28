package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.DeliveryAddress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


/**
 * DeliveryAddressRepository — data access for the DeliveryAddress entity.
 *
 * All query methods use Spring Data's derived-query naming convention.
 *
 * NOTE ON OWNERSHIP CHECKS:
 *   findByIdAndUserId is provided for completeness but is deliberately NOT used
 *   for ownership enforcement in AddressService. See design decision D-01 and
 *   the design document §6 for the 404 vs 403 rationale.
 */
@Repository
public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, Long> {

    /** Returns all addresses for a user (list endpoint). */
    List<DeliveryAddress> findAllByUserId(Long userId);

    /**
     * Locates the one address marked isDefault=true for a user.
     * Used before saving or updating a new default to demote the old one (BR-04).
     */
    Optional<DeliveryAddress> findByUserIdAndIsDefaultTrue(Long userId);

    /**
     * Counts the number of addresses belonging to a user.
     * Used by the delete guard: if count > 1 and address is default → 400 (BR-08/09).
     */
    long countByUserId(Long userId);

    /**
     * Looks up an address by both id and userId in a single query.
     * Retained for potential future use (e.g. FEAT-08 address confirmation).
     * AddressService does NOT use this method — it uses findById + explicit check.
     */
    Optional<DeliveryAddress> findByIdAndUserId(Long id, Long userId);
}
