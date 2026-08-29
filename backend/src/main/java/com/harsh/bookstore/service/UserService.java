package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.LoginRequest;
import com.harsh.bookstore.dto.LoginResponse;
import com.harsh.bookstore.dto.RegisterRequest;
import com.harsh.bookstore.dto.UserDto;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.exception.EmailAlreadyExistsException;
import com.harsh.bookstore.exception.InvalidCredentialsException;
import com.harsh.bookstore.repository.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;


/**
 * UserService — all business logic for user registration and login.
 *
 * WHAT THIS CLASS DOES:
 *   1. register(RegisterRequest) — validates uniqueness, hashes password,
 *      saves the User row, returns a UserDto (no password field).
 *   2. login(LoginRequest) — verifies credentials, generates a JWT,
 *      returns a LoginResponse containing the token and UserDto.
 *   3. loadUserByUsername(email) — implements UserDetailsService so Spring
 *      Security's authentication infrastructure can load users by email
 *      when it needs to. Required for the JWT filter to work correctly.
 *
 * WHY THIS CLASS IMPLEMENTS UserDetailsService:
 *   Spring Security's authentication manager needs a way to look up a user
 *   by their "username" (in our case, email). By implementing this interface
 *   here, we tell Spring: "when you need a user, call this method". This is
 *   wired into the SecurityConfig in Phase 5 so the authentication manager
 *   knows to use our UserService. Without this, Spring Security would try
 *   to use its own in-memory user store (which has no real users).
 *
 * DEPENDENCY INJECTION — WHY BCryptPasswordEncoder (not PasswordEncoder):
 *   We inject the concrete BCryptPasswordEncoder type rather than the
 *   PasswordEncoder interface. This is a deliberate choice: BCrypt is the
 *   only hashing algorithm we support and we want the test to be able to
 *   inject a real BCryptPasswordEncoder without any mocking gymnastics.
 *   If we later add multiple algorithms, we'd switch to the interface.
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    /**
     * Constructor injection. Spring finds the UserRepository, BCryptPasswordEncoder,
     * and JwtService beans and passes them in.
     *
     * Note: BCryptPasswordEncoder is declared as a @Bean in SecurityConfig
     * (Phase 5). Spring wires it here automatically via constructor injection.
     */
    public UserService(UserRepository userRepository,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }


    // ==================================================================
    // REGISTRATION
    // ==================================================================

    /**
     * Create a new user account.
     *
     * STEPS:
     *   1. Duplicate check — if the email is already taken, throw 409.
     *   2. Hash the password with BCrypt (never store the raw password).
     *   3. Normalise the email to lower-case before saving.
     *   4. Save the User entity to the database.
     *   5. Map to UserDto (no password field) and return.
     *
     * WHY WE CHECK BEFORE SAVE (not just catch the constraint violation):
     *   We could skip the existsBy check and let the DB unique constraint
     *   throw a DataIntegrityViolationException on duplicate. But that
     *   exception wraps a vendor-specific SQL error — parsing it to produce
     *   a clean 409 response is fragile and database-specific. An explicit
     *   check before save gives us full control over the error message and
     *   status code. The tiny window between check and save (concurrent
     *   registrations with the same email) is covered by the DB constraint
     *   as a fail-safe — so both layers protect us.
     *
     * @param req the validated registration form data
     * @return a UserDto representing the newly created account
     * @throws EmailAlreadyExistsException if the email is already registered
     */
    public LoginResponse register(RegisterRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());

        // Store email in lower-case so "Harsh@Example.COM" and
        // "harsh@example.com" resolve to the same account. Locale.ROOT
        // avoids locale-specific surprises (e.g. Turkish 'I' → 'ı').
        user.setEmail(req.getEmail().toLowerCase(Locale.ROOT));

        // BCrypt: hash the raw password. The result is a 60-character
        // string like "$2a$10$...", which is what gets stored in the DB.
        // The raw password is never stored, never logged, never returned.
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));

        User saved = userRepository.save(user);

        // Issue a JWT immediately so the client is logged in right after
        // registering — no need for a separate login round-trip.
        String token = jwtService.generateToken(saved);
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(toDto(saved));
        return response;
    }


    // ==================================================================
    // LOGIN
    // ==================================================================

    /**
     * Authenticate a user and issue a JWT.
     *
     * STEPS:
     *   1. Look up the user by email. If not found → throw 401.
     *   2. Verify the submitted password against the stored hash.
     *      If wrong → throw 401.
     *   3. Generate a signed JWT via JwtService.
     *   4. Return a LoginResponse containing the token and user profile.
     *
     * SAME EXCEPTION FOR BOTH FAILURE MODES (anti-enumeration):
     *   Step 1 and step 2 both throw InvalidCredentialsException with the
     *   same message: "Invalid email or password". This is intentional —
     *   see the InvalidCredentialsException Javadoc for the full explanation.
     *
     * HOW BCrypt VERIFICATION WORKS:
     *   passwordEncoder.matches(rawPassword, storedHash) re-hashes the
     *   submitted password using the salt embedded in the storedHash and
     *   compares. It never "decrypts" the hash — BCrypt is one-way.
     *
     * @param req the login form data (email + raw password)
     * @return a LoginResponse containing the JWT and user profile
     * @throws InvalidCredentialsException if email is unknown or password is wrong
     */
    public LoginResponse login(LoginRequest req) {
        // Step 1 — find by email (case-insensitive). Same exception whether
        // email is not found or password is wrong — anti-enumeration pattern.
        User user = userRepository.findByEmailIgnoreCase(req.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        // Step 2 — verify the submitted password against the stored BCrypt hash.
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Step 3 — credentials are valid: generate a 24-hour signed JWT.
        String token = jwtService.generateToken(user);

        // Step 4 — assemble and return the response.
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(toDto(user));
        return response;
    }


    // ==================================================================
    // UserDetailsService (required by Spring Security)
    // ==================================================================

    /**
     * Load a user by their email address (Spring Security calls this
     * "username" — for us it's the email).
     *
     * WHY THIS EXISTS:
     *   Spring Security's AuthenticationManager needs to verify credentials
     *   when we register it in SecurityConfig. It calls loadUserByUsername
     *   to fetch the stored user, then checks the password against the hash.
     *   Without this method, the authentication manager has no way to find
     *   our users — it would fall back to an in-memory store with no real
     *   accounts.
     *
     * HOW THE RETURNED UserDetails IS USED:
     *   Spring Security uses it internally for authentication checks. In our
     *   JWT filter (Phase 5) we bypass this and load the User entity directly
     *   by id — so the UserDetails returned here is only used during
     *   Spring Security's own form-based auth flow (which we don't expose).
     *   We still need this method because SecurityConfig wires UserService
     *   as the UserDetailsService for the authentication manager bean.
     *
     * @param email the email address (Spring Security calls this "username")
     * @return a UserDetails representation of the found user
     * @throws UsernameNotFoundException if no user has that email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .map(u -> org.springframework.security.core.userdetails.User
                        .withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() ->
                        new UsernameNotFoundException("No user found with email: " + email));
    }


    // ==================================================================
    // PRIVATE HELPER
    // ==================================================================

    /**
     * Map a User entity to a UserDto for API responses.
     *
     * This is the ONLY place entity → DTO conversion happens for users.
     * Keeping it private and centralised means:
     *   1. If we add a field to User later, we only need to update this
     *      one method to start (or intentionally not start) exposing it.
     *   2. passwordHash is never accidentally included — it simply isn't
     *      mapped here, and UserDto doesn't have the field anyway.
     */
    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        return dto;
    }
}
