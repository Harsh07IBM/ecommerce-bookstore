package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Basket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * BasketRepository — data access for the Basket entity.
 *
 * All three query methods use Spring Data's derived-query naming convention;
 * no SQL or JPQL is written by hand.
 *
 *   findByUserId     — looks up the persistent basket for a logged-in user
 *   findBySessionId  — looks up the anonymous basket for a guest session
 *   deleteBySessionId — used by future cleanup jobs to expire old guest baskets
 */
@Repository
public interface BasketRepository extends JpaRepository<Basket, Long> {

    Optional<Basket> findByUserId(Long userId);

    Optional<Basket> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
