package com.harsh.bookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;


/**
 * User — represents a registered customer account.
 *
 * WHAT THIS CLASS IS:
 *   A JPA entity that maps to the `users` table. Each row is one customer
 *   who has completed registration (firstName, lastName, email, password).
 *
 * WHY THE TABLE IS NAMED "users" AND NOT "user":
 *   USER is a reserved keyword in SQL (and in H2 specifically). If we used
 *   @Table(name = "user"), Hibernate would generate CREATE TABLE user (...),
 *   and H2 would refuse it with a syntax error at startup. "users" (plural)
 *   is the conventional workaround and is used by virtually every Java project
 *   that stores users in a relational database.
 *
 * WHY THE FIELD IS NAMED passwordHash AND NOT password:
 *   We never store the raw password — only a BCrypt hash. Naming the field
 *   passwordHash makes this intent explicit AND prevents accidental exposure:
 *   there is no getPassword() method on this class, so any DTO that tries to
 *   call user.getPassword() simply won't compile. The only way to include a
 *   password value in a response is to explicitly call getPasswordHash() and
 *   copy it — which is easy to spot in a code review.
 */
@Entity
@Table(name = "users")
public class User {

    // ==================================================================
    // FIELDS
    // ==================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Given name — e.g. "Harsh". Max 100 characters per spec FR-03.
     */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /**
     * Family name — e.g. "Sharma". Max 100 characters per spec FR-03.
     */
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Email address — used as the login identifier.
     *
     * Stored in lower-case (normalised by UserService before save) so that
     * "Harsh@Example.COM" and "harsh@example.com" are treated as the same
     * account. The UNIQUE constraint is enforced at the database level —
     * both as a fail-safe and to give a clear constraint-violation error
     * if two threads somehow bypass the application-level duplicate check.
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash of the user's password. Never the raw password itself.
     *
     * BCrypt output is always exactly 60 characters:
     *   $2a$10$<22-char-salt><31-char-hash>
     * We use length = 60 to be exact. If we ever switch hashing algorithm
     * (e.g. Argon2), this column would need a migration — that's a deliberate
     * reminder to think about the change.
     */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    /**
     * When this account was created. Set once on first save, never updated.
     * Useful for admin views, analytics, and account-age checks later.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    // ==================================================================
    // LIFECYCLE CALLBACK
    // ==================================================================

    /**
     * Automatically stamps createdAt just before the first INSERT.
     * Same pattern used on the Book entity — see Book.java for the
     * detailed explanation of @PrePersist.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    /**
     * No-arg constructor required by JPA/Hibernate for reflective
     * instantiation when reading rows from the database.
     */
    public User() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }


    // ==================================================================
    // equals / hashCode / toString
    // ==================================================================

    /**
     * Two User objects are equal only when they share the same non-null id.
     * Same id-based equality pattern as Book — see Book.java for the
     * detailed explanation of why we use this pattern for JPA entities.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof User)) return false;
        User that = (User) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "'}";
    }
}
