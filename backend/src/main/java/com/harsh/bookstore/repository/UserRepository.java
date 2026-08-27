package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


/**
 * UserRepository — data access layer for the User entity.
 *
 * WHAT THIS IS:
 *   An empty interface that extends JpaRepository. Spring Data JPA reads
 *   the interface at startup and generates the full implementation — we
 *   write zero SQL. The same pattern as BookRepository and CategoryRepository.
 *
 * WHY TWO METHODS INSTEAD OF ONE:
 *   - findByEmailIgnoreCase — returns the whole User when we need to read
 *     fields (login: we need the passwordHash to verify credentials).
 *   - existsByEmailIgnoreCase — returns only a boolean when we only need to
 *     know "does this email exist?" (registration duplicate-check). Spring
 *     Data translates this to a `SELECT COUNT(*) > 0` query — cheaper than
 *     fetching the full row when we don't need the data.
 *
 * WHY IgnoreCase ON BOTH METHODS:
 *   Emails are case-insensitive by the RFC 5321 standard (the local part
 *   technically can be case-sensitive, but in practice no mail provider
 *   distinguishes "Harsh@Example.com" from "harsh@example.com"). We store
 *   lower-cased emails in UserService, but using IgnoreCase here as an
 *   extra layer ensures lookups work correctly even if a stale row exists
 *   with mixed-case from a future data migration.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by email address (case-insensitive).
     *
     * Used by:
     *   - UserService.login()      — needs the full User to verify password
     *   - JwtAuthFilter (indirectly, via UserRepository.findById)
     *
     * Returns Optional.empty() if no user has that email.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Check whether an email address is already registered (case-insensitive).
     *
     * Used by UserService.register() to detect duplicate registrations
     * before attempting to save — gives a clean 409 response rather than
     * a raw DataIntegrityViolationException from the DB.
     *
     * Returns true if the email exists, false otherwise.
     */
    boolean existsByEmailIgnoreCase(String email);
}
