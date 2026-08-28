package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.DeliveryAddress;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Repository tests for DeliveryAddressRepository.
 * @DataJpaTest provides an in-memory H2 database and rolls back each test.
 */
@DataJpaTest
class AddressRepositoryTest {

    @Autowired
    private DeliveryAddressRepository repository;


    // ==================================================================
    // HELPERS
    // ==================================================================

    private DeliveryAddress save(Long userId, boolean isDefault) {
        DeliveryAddress a = new DeliveryAddress();
        a.setUserId(userId);
        a.setRecipientName("Test User");
        a.setPhoneNumber("9876543210");
        a.setLine1("1 Test Street");
        a.setCity("Mumbai");
        a.setState("Maharashtra");
        a.setPincode("400001");
        a.setDefault(isDefault);
        return repository.save(a);
    }


    // ==================================================================
    // findAllByUserId
    // ==================================================================

    @Test
    void findAllByUserId_returnsCorrectAddresses() {
        save(1L, false);
        save(1L, true);
        save(2L, false);  // belongs to a different user — must not appear

        List<DeliveryAddress> results = repository.findAllByUserId(1L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(a -> a.getUserId().equals(1L));
    }


    // ==================================================================
    // findByUserIdAndIsDefaultTrue
    // ==================================================================

    @Test
    void findByUserIdAndIsDefaultTrue_returnsDefault() {
        save(1L, false);
        DeliveryAddress defaultAddr = save(1L, true);

        Optional<DeliveryAddress> result = repository.findByUserIdAndIsDefaultTrue(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(defaultAddr.getId());
        assertThat(result.get().isDefault()).isTrue();
    }

    @Test
    void findByUserIdAndIsDefaultTrue_empty_whenNoDefault() {
        save(1L, false);

        Optional<DeliveryAddress> result = repository.findByUserIdAndIsDefaultTrue(1L);

        assertThat(result).isEmpty();
    }


    // ==================================================================
    // countByUserId
    // ==================================================================

    @Test
    void countByUserId_returnsCorrectCount() {
        save(1L, false);
        save(1L, false);
        save(1L, true);

        assertThat(repository.countByUserId(1L)).isEqualTo(3L);
    }
}
