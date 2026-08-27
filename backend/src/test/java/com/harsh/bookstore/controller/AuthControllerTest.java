package com.harsh.bookstore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.bookstore.config.SecurityConfig;
import com.harsh.bookstore.dto.LoginRequest;
import com.harsh.bookstore.dto.LoginResponse;
import com.harsh.bookstore.dto.RegisterRequest;
import com.harsh.bookstore.dto.UserDto;
import com.harsh.bookstore.exception.EmailAlreadyExistsException;
import com.harsh.bookstore.exception.InvalidCredentialsException;
import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.JwtService;
import com.harsh.bookstore.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for AuthController.
 *
 * Follows the same @WebMvcTest pattern established for BookControllerTest:
 *   - excludeAutoConfiguration prevents Spring's default in-memory user store
 *     from conflicting with our SecurityConfig.
 *   - @Import(SecurityConfig.class) loads our real permit rules so that
 *     POST /api/auth/** is allowed without a token.
 *   - @MockBean JwtService + UserRepository satisfy JwtAuthFilter's dependencies.
 *   - @MockBean UserService is the subject of the business-logic stubs.
 */
@WebMvcTest(value = AuthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;   // Spring Boot's pre-configured Jackson mapper

    @MockBean
    private UserService userService;

    // Required by JwtAuthFilter (part of SecurityConfig)
    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;


    // ==================================================================
    // POST /api/auth/register
    // ==================================================================

    @Test
    void register_returns201_withUserDto() throws Exception {
        UserDto dto = userDto(1L, "Harsh", "Sharma", "harsh@example.com");
        when(userService.register(any(RegisterRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest())))
                .andExpect(status().isCreated())                      // 201
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Harsh"))
                .andExpect(jsonPath("$.lastName").value("Sharma"))
                .andExpect(jsonPath("$.email").value("harsh@example.com"))
                // passwordHash must NEVER appear in the response
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_returns409_whenEmailAlreadyExists() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest())))
                .andExpect(status().isConflict())                     // 409
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("An account with this email address already exists"));
    }

    @Test
    void register_returns400_whenEmailInvalid() throws Exception {
        RegisterRequest bad = registerRequest();
        bad.setEmail("not-an-email");   // fails @Email validation

        // @Valid fires before the service — no mock setup needed
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())                   // 400
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("email must be a valid email address"));
    }

    @Test
    void register_returns400_whenPasswordTooShort() throws Exception {
        RegisterRequest bad = registerRequest();
        bad.setPassword("short");       // fails @Size(min=8) validation

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("password must be at least 8 characters"));
    }

    @Test
    void register_returns400_whenFirstNameBlank() throws Exception {
        RegisterRequest bad = registerRequest();
        bad.setFirstName("");           // fails @NotBlank validation

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }


    // ==================================================================
    // POST /api/auth/login
    // ==================================================================

    @Test
    void login_returns200_withTokenAndUserDto() throws Exception {
        UserDto dto = userDto(1L, "Harsh", "Sharma", "harsh@example.com");
        LoginResponse loginResp = new LoginResponse();
        loginResp.setToken("eyJhbGciOiJIUzI1NiJ9.mock.token");
        loginResp.setUser(dto);
        when(userService.login(any(LoginRequest.class))).thenReturn(loginResp);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())                           // 200
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.email").value("harsh@example.com"))
                .andExpect(jsonPath("$.user.firstName").value("Harsh"))
                // password must never appear in login response either
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void login_returns401_whenCredentialsWrong() throws Exception {
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isUnauthorized())                 // 401
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_returns400_whenEmailBlank() throws Exception {
        LoginRequest bad = loginRequest();
        bad.setEmail("");               // fails @NotBlank

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email is required"));
    }

    @Test
    void login_returns400_whenPasswordBlank() throws Exception {
        LoginRequest bad = loginRequest();
        bad.setPassword("");            // fails @NotBlank

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password is required"));
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

    private LoginRequest loginRequest() {
        LoginRequest r = new LoginRequest();
        r.setEmail("harsh@example.com");
        r.setPassword("secret123");
        return r;
    }

    private UserDto userDto(Long id, String first, String last, String email) {
        UserDto dto = new UserDto();
        dto.setId(id);
        dto.setFirstName(first);
        dto.setLastName(last);
        dto.setEmail(email);
        return dto;
    }
}
