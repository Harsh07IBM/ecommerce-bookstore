package com.harsh.bookstore.service;

import com.harsh.bookstore.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;


/**
 * JwtService — builds, signs, and parses JSON Web Tokens.
 *
 * WHAT A JWT IS (in plain English):
 *   A JWT is a small, self-contained piece of text that proves identity.
 *   It has three parts separated by dots:
 *
 *     eyJhbGciOiJIUzI1NiJ9          ← Header  (algorithm: HS256)
 *     .eyJzdWIiOiIxIiwiZW1haWwi...  ← Payload (the claims — our data)
 *     .SflKxwRJSMeKKF2QT4fwpMeJ...  ← Signature (proves it wasn't tampered with)
 *
 *   The payload is Base64-encoded (not encrypted — anyone can decode it).
 *   The signature is a cryptographic hash of header+payload using our secret
 *   key. Tampering with any bit of the payload invalidates the signature,
 *   so we can trust claims in a valid token.
 *
 *   HOW AUTHENTICATION WORKS WITH A JWT:
 *     1. Client logs in → server issues a signed JWT.
 *     2. Client stores the JWT and sends it in every request:
 *          Authorization: Bearer eyJhbGci...
 *     3. Server verifies the signature. If valid, trusts the claims inside
 *        (user id, email) without hitting the database for authentication.
 *     4. No session table needed — the token is the session. This is what
 *        "stateless" means.
 *
 * WHAT THIS CLASS DOES:
 *   - generateToken(user) — builds and signs a JWT for a given User.
 *   - isTokenValid(token) — returns true if the token parses and isn't expired.
 *   - extractUserId(token) — reads the user id out of a valid token's `sub`.
 *
 * WHAT THIS CLASS DOES NOT DO:
 *   - It has no dependency on Spring Security — it is a plain @Service.
 *   - It does not fetch users from the database.
 *   - It does not throw checked exceptions — invalid tokens become `false`
 *     from isTokenValid(), which keeps the calling filter code simple.
 *
 * THE KEY:
 *   The secret string from application.properties is converted to a
 *   javax.crypto.SecretKey object ONCE at construction time. JJWT's
 *   Keys.hmacShaKeyFor() validates the key is at least 256 bits (32 bytes)
 *   immediately — if the secret is too short, the app fails at startup with
 *   a clear error rather than silently producing weak tokens at runtime.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;


    /**
     * Constructor injection — Spring reads the two properties and passes
     * them in. @Value("${property.name}") binds a single property value
     * to a constructor parameter. Same as @Autowired but for primitives
     * and strings rather than beans.
     *
     * WHY CONSTRUCTOR (not @Value on fields):
     *   Field injection with @Value is fine for simple apps, but constructor
     *   injection lets us test this class without a Spring context: just
     *   call new JwtService("my-32-char-secret...", 86400000L) in a test.
     */
    public JwtService(
            @Value("${bookstore.jwt.secret}") String secret,
            @Value("${bookstore.jwt.expiration-ms}") long expirationMs) {

        // Keys.hmacShaKeyFor validates the byte length immediately.
        // If the secret is shorter than 256 bits (32 ASCII chars), it
        // throws WeakKeyException RIGHT HERE at startup — fail-fast.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }


    // ==================================================================
    // PUBLIC API
    // ==================================================================

    /**
     * Build and sign a JWT for the given user.
     *
     * CLAIMS INCLUDED (spec FR-09):
     *   sub   — subject: the user's numeric id, stored as a String.
     *            Using the id (not email) as `sub` means the token stays
     *            valid even if the user changes their email address later.
     *   email — included as a convenience claim so the client can read it
     *            from the token payload without making an API call.
     *   iat   — issued-at: timestamp of token creation (set by JJWT automatically).
     *   exp   — expiration: iat + expirationMs (24 hours).
     *
     * WHY Instant (not System.currentTimeMillis()):
     *   Instant is Java's modern, clock-independent time representation.
     *   It's also easier to override in tests (pass a custom clock).
     *
     * @param user the authenticated user to issue a token for
     * @return a compact, signed JWT string like "eyJhbGci...SflK..."
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())          // "sub" claim — user id
                .claim("email", user.getEmail())           // custom "email" claim
                .issuedAt(Date.from(now))                  // "iat" claim
                .expiration(Date.from(now.plusMillis(expirationMs)))  // "exp" claim
                .signWith(key)                             // HMAC-SHA256 signature
                .compact();                                // produce the string
    }

    /**
     * Check whether a token string is valid and not expired.
     *
     * Returns false (never throws) if:
     *   - The string is null, blank, or not a JWT format.
     *   - The signature doesn't match our key (tampered token).
     *   - The token's `exp` claim is in the past (expired).
     *
     * WHY CATCH-AND-RETURN-FALSE (not re-throw):
     *   This method is called from JwtAuthFilter on every incoming request.
     *   If it threw, the filter would need a try/catch, and unhandled
     *   exceptions in filters produce ugly 500 responses instead of clean
     *   401s. Returning false lets the filter simply skip setting the auth
     *   context, and Spring Security's own downstream filter produces the
     *   correct 401 automatically.
     *
     * @param token the JWT string to validate
     * @return true if valid and not expired, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);   // throws on any problem
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException covers: ExpiredJwtException, MalformedJwtException,
            // SignatureException, UnsupportedJwtException.
            // IllegalArgumentException covers null/blank input.
            return false;
        }
    }

    /**
     * Extract the user id from a valid token's `sub` claim.
     *
     * IMPORTANT: Only call this after isTokenValid() returns true.
     * If the token is invalid, parseClaims() will throw.
     *
     * @param token a valid, non-expired JWT string
     * @return the user's database id
     */
    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }


    // ==================================================================
    // PRIVATE HELPER
    // ==================================================================

    /**
     * Parse the token and return its claims payload.
     *
     * This is the single place the JJWT parser is constructed. Keeping it
     * private ensures external callers always go through the safer public
     * methods (isTokenValid / extractUserId).
     *
     * HOW JJWT 0.12.x PARSING WORKS:
     *   Jwts.parser()           — start building a parser
     *     .verifyWith(key)      — "use this key to check the signature"
     *     .build()              — produce the immutable parser
     *     .parseSignedClaims()  — parse AND verify in one call
     *     .getPayload()         — return the Claims map if everything passed
     *
     * If signature verification fails or the token is expired, JJWT throws
     * a JwtException subclass before returning.
     *
     * @throws JwtException if the token is invalid, expired, or tampered
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
