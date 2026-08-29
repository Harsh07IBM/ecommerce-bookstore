package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.LoginRequest;
import com.harsh.bookstore.dto.LoginResponse;
import com.harsh.bookstore.dto.RegisterRequest;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.exception.EmailAlreadyExistsException;
import com.harsh.bookstore.exception.InvalidCredentialsException;
import com.harsh.bookstore.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for UserService.
 *
 * @ExtendWith(MockitoExtension.class) — lightweight Mockito integration.
 * No Spring context — UserService is instantiated directly with mocks.
 * A real BCryptPasswordEncoder is used (not mocked) so that encode/matches
 * round-trips work correctly in the login tests.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    // Real BCrypt — not mocked. We need encode() and matches() to actually work.
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;


    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtService);
    }


    // ==================================================================
    // register — happy path
    // ==================================================================

    @Test
    void register_success_returnsLoginResponseWithTokenAndUser() {
        when(userRepository.existsByEmailIgnoreCase("harsh@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);   // simulate DB assigning an id
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("mock.token");

        LoginResponse result = userService.register(registerRequest());

        assertThat(result.getToken()).isEqualTo("mock.token");
        assertThat(result.getUser().getId()).isEqualTo(1L);
        assertThat(result.getUser().getFirstName()).isEqualTo("Harsh");
        assertThat(result.getUser().getLastName()).isEqualTo("Sharma");
        assertThat(result.getUser().getEmail()).isEqualTo("harsh@example.com");
    }

    @Test
    void register_storesPasswordAsHash_notPlainText() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("mock.token");

        userService.register(registerRequest());

        // Capture the User that was passed to save()
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        String storedHash = captor.getValue().getPasswordHash();
        // The stored value must be a BCrypt hash, not the raw password "secret123"
        assertThat(storedHash).isNotEqualTo("secret123");
        // BCrypt hashes always start with "$2a$" or "$2b$"
        assertThat(storedHash).startsWith("$2");
        // And must verify correctly with matches()
        assertThat(passwordEncoder.matches("secret123", storedHash)).isTrue();
    }

    @Test
    void register_emailStoredAsLowerCase() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("mock.token");

        RegisterRequest req = registerRequest();
        req.setEmail("HARSH@EXAMPLE.COM");  // mixed-case input
        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        // Must be stored in lower-case regardless of input case
        assertThat(captor.getValue().getEmail()).isEqualTo("harsh@example.com");
    }


    // ==================================================================
    // register — duplicate email
    // ==================================================================

    @Test
    void register_throwsEmailAlreadyExists_whenEmailTaken() {
        when(userRepository.existsByEmailIgnoreCase("harsh@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest()))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("An account with this email address already exists");

        // save() must never be called when the email is already taken
        verify(userRepository, never()).save(any());
    }


    // ==================================================================
    // login — happy path
    // ==================================================================

    @Test
    void login_success_returnsTokenAndUserDto() {
        User storedUser = storedUser();
        when(userRepository.findByEmailIgnoreCase("harsh@example.com"))
                .thenReturn(Optional.of(storedUser));
        when(jwtService.generateToken(storedUser)).thenReturn("mock.jwt.token");

        LoginResponse response = userService.login(loginRequest("secret123"));

        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUser().getId()).isEqualTo(1L);
        assertThat(response.getUser().getEmail()).isEqualTo("harsh@example.com");
        assertThat(response.getUser().getFirstName()).isEqualTo("Harsh");
    }


    // ==================================================================
    // login — wrong credentials
    // ==================================================================

    @Test
    void login_throwsInvalidCredentials_whenEmailNotFound() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(loginRequest("secret123", "nobody@example.com")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsInvalidCredentials_whenPasswordWrong() {
        when(userRepository.findByEmailIgnoreCase("harsh@example.com"))
                .thenReturn(Optional.of(storedUser()));

        // "wrongpassword" does not match the stored BCrypt hash of "secret123"
        assertThatThrownBy(() -> userService.login(loginRequest("wrongpassword")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_wrongEmailAndWrongPassword_returnSameException() {
        // Anti-enumeration: both failures produce an identical exception type and message.
        when(userRepository.findByEmailIgnoreCase("harsh@example.com"))
                .thenReturn(Optional.of(storedUser()));
        when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        Throwable wrongPassword = null, wrongEmail = null;
        try { userService.login(loginRequest("badpass")); }
        catch (InvalidCredentialsException e) { wrongPassword = e; }

        try { userService.login(loginRequest("badpass", "nobody@example.com")); }
        catch (InvalidCredentialsException e) { wrongEmail = e; }

        assertThat(wrongPassword).isNotNull();
        assertThat(wrongEmail).isNotNull();
        // Same exception type AND same message — client cannot distinguish the two
        assertThat(wrongPassword.getMessage()).isEqualTo(wrongEmail.getMessage());
    }


    // ==================================================================
    // Helpers
    // ==================================================================

    private RegisterRequest registerRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Harsh");
        r.setLastName("Sharma");
        r.setEmail("harsh@example.com");
        r.setPassword("secret123");
        return r;
    }

    /** Returns a User as it would look after having been saved to the DB. */
    private User storedUser() {
        User u = new User();
        u.setId(1L);
        u.setFirstName("Harsh");
        u.setLastName("Sharma");
        u.setEmail("harsh@example.com");
        // BCrypt hash of "secret123" — pre-computed so the test doesn't call encode()
        u.setPasswordHash(passwordEncoder.encode("secret123"));
        return u;
    }

    private LoginRequest loginRequest(String password) {
        return loginRequest(password, "harsh@example.com");
    }

    private LoginRequest loginRequest(String password, String email) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
