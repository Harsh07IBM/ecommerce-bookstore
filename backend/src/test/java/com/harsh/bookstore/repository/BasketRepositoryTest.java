package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Basket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Repository tests for BasketRepository.
 *
 * @DataJpaTest spins up an in-memory H2 database, wires only JPA components,
 * and wraps each test in a transaction that rolls back at the end — so tests
 * are fully isolated with no leftover data.
 */
@DataJpaTest
class BasketRepositoryTest {

    @Autowired
    private BasketRepository basketRepository;


    // ==================================================================
    // findByUserId
    // ==================================================================

    @Test
    void findByUserId_returnsBasket() {
        Basket basket = new Basket();
        basket.setUserId(1L);
        basketRepository.save(basket);

        Optional<Basket> result = basketRepository.findByUserId(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void findByUserId_returnsEmpty_whenNotFound() {
        Optional<Basket> result = basketRepository.findByUserId(999L);

        assertThat(result).isEmpty();
    }


    // ==================================================================
    // findBySessionId
    // ==================================================================

    @Test
    void findBySessionId_returnsBasket() {
        Basket basket = new Basket();
        basket.setSessionId("test-session-abc");
        basketRepository.save(basket);

        Optional<Basket> result = basketRepository.findBySessionId("test-session-abc");

        assertThat(result).isPresent();
        assertThat(result.get().getSessionId()).isEqualTo("test-session-abc");
    }

    @Test
    void findBySessionId_returnsEmpty_whenNotFound() {
        Optional<Basket> result = basketRepository.findBySessionId("does-not-exist");

        assertThat(result).isEmpty();
    }
}
