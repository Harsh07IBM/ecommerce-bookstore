package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.dao.DataIntegrityViolationException;


/**
 * Integration tests for UserRepository.
 *
 * @DataJpaTest — spins up an in-memory H2 database, creates all tables from
 * the JPA entities, runs each test in a transaction that rolls back afterward.
 * No Spring Security, no seed loader, no controllers — just the DB layer.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;


    // ==================================================================
    // save
    // ==================================================================

    @Test
    void save_assignsIdAndCreatedAt() {
        User user = sampleUser("save@example.com");

        User saved = userRepository.save(user);

        // DB assigns the id on first INSERT
        assertThat(saved.getId()).isNotNull();
        // @PrePersist should have stamped createdAt
        assertThat(saved.getCreatedAt()).isNotNull();
    }


    // ==================================================================
    // findByEmailIgnoreCase
    // ==================================================================

    @Test
    void findByEmailIgnoreCase_returnsUser_whenEmailMatches() {
        userRepository.save(sampleUser("found@example.com"));

        assertThat(userRepository.findByEmailIgnoreCase("found@example.com"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("found@example.com");
    }

    @Test
    void findByEmailIgnoreCase_isCaseInsensitive() {
        // Saved lower-case, looked up mixed-case — must still find it.
        userRepository.save(sampleUser("case@example.com"));

        assertThat(userRepository.findByEmailIgnoreCase("CASE@EXAMPLE.COM"))
                .isPresent();
    }

    @Test
    void findByEmailIgnoreCase_returnsEmpty_whenEmailNotFound() {
        assertThat(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .isEmpty();
    }


    // ==================================================================
    // existsByEmailIgnoreCase
    // ==================================================================

    @Test
    void existsByEmailIgnoreCase_returnsTrue_whenEmailExists() {
        userRepository.save(sampleUser("exists@example.com"));

        assertThat(userRepository.existsByEmailIgnoreCase("exists@example.com"))
                .isTrue();
    }

    @Test
    void existsByEmailIgnoreCase_returnsFalse_whenEmailAbsent() {
        assertThat(userRepository.existsByEmailIgnoreCase("absent@example.com"))
                .isFalse();
    }


    // ==================================================================
    // Unique constraint on email
    // ==================================================================

    @Test
    void save_duplicateEmail_throwsDataIntegrityViolation() {
        // The @Column(unique=true) on email must be enforced by H2.
        userRepository.save(sampleUser("dup@example.com"));
        userRepository.flush(); // flush first insert before second save

        assertThatThrownBy(() -> {
            userRepository.save(sampleUser("dup@example.com"));
            userRepository.flush(); // trigger the actual SQL INSERT
        }).isInstanceOf(DataIntegrityViolationException.class);
    }


    // ==================================================================
    // Helper
    // ==================================================================

    private User sampleUser(String email) {
        User u = new User();
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEmail(email);
        // In production UserService hashes this with BCrypt.
        // The repository doesn't care about the value — any non-null string is fine.
        u.setPasswordHash("$2a$10$hashedpasswordplaceholder000000000");
        return u;
    }
}
