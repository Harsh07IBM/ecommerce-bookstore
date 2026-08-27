package com.harsh.bookstore.service;

import com.harsh.bookstore.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Unit tests for JwtService.
 *
 * NO Spring context needed — JwtService is a plain @Service with constructor
 * injection. We call new JwtService(secret, expirationMs) directly, which
 * also means tests run in milliseconds (no context startup overhead).
 *
 * The secret must be at least 32 characters (256 bits) because JJWT enforces
 * this via Keys.hmacShaKeyFor() at construction time.
 */
class JwtServiceTest {

    // 49-character secret — same length as the one in application.properties.
    private static final String SECRET =
            "bookstore-dev-secret-key-change-in-production-min32c";

    private static final long EXPIRATION_24H = 86_400_000L;  // 24 hours in ms

    private JwtService jwtService;
    private User sampleUser;


    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_24H);

        sampleUser = new User();
        sampleUser.setId(42L);
        sampleUser.setEmail("test@example.com");
        sampleUser.setFirstName("Test");
        sampleUser.setLastName("User");
        sampleUser.setPasswordHash("$2a$10$irrelevant");
    }


    // ==================================================================
    // generateToken
    // ==================================================================

    @Test
    void generateToken_producesNonBlankString() {
        String token = jwtService.generateToken(sampleUser);

        assertThat(token).isNotBlank();
    }

    @Test
    void generateToken_producesThreePartJwtFormat() {
        // A compact JWT is always header.payload.signature — exactly 2 dots.
        String token = jwtService.generateToken(sampleUser);
        long dotCount = token.chars().filter(c -> c == '.').count();

        assertThat(dotCount).isEqualTo(2);
    }


    // ==================================================================
    // isTokenValid
    // ==================================================================

    @Test
    void isTokenValid_returnsTrueForFreshToken() {
        String token = jwtService.generateToken(sampleUser);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_forExpiredToken() {
        // Build a JwtService with 0ms expiry so the token expires immediately.
        JwtService shortLived = new JwtService(SECRET, 0L);
        String token = shortLived.generateToken(sampleUser);

        // Token was already expired at the moment of creation (exp = iat + 0ms).
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forRandomString() {
        assertThat(jwtService.isTokenValid("this.is.garbage")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forBlankString() {
        assertThat(jwtService.isTokenValid("   ")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forTokenSignedWithDifferentSecret() {
        // Token signed with a different key should fail our validator's signature check.
        JwtService otherService = new JwtService(
                "completely-different-secret-key-min32chars!!", EXPIRATION_24H);
        String foreignToken = otherService.generateToken(sampleUser);

        assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
    }


    // ==================================================================
    // extractUserId
    // ==================================================================

    @Test
    void extractUserId_returnsCorrectId() {
        String token = jwtService.generateToken(sampleUser);

        // The "sub" claim was set to user.getId().toString() in generateToken.
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void extractUserId_roundTripsForDifferentIds() {
        // Verify the id survives the encode → decode round-trip for an edge-case value.
        sampleUser.setId(1L);
        String token = jwtService.generateToken(sampleUser);

        assertThat(jwtService.extractUserId(token)).isEqualTo(1L);
    }
}
