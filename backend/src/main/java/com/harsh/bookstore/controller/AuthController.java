package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.LoginRequest;
import com.harsh.bookstore.dto.LoginResponse;
import com.harsh.bookstore.dto.RegisterRequest;
import com.harsh.bookstore.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/**
 * AuthController — HTTP entry point for user registration and login.
 *
 * WHAT THIS CLASS DOES:
 *   Two endpoints, each forwarding directly to UserService. This controller
 *   has no business logic — it only handles the HTTP translation:
 *     - Reads the JSON request body into a DTO
 *     - Runs Bean Validation via @Valid BEFORE the service is called
 *     - Returns the service result with the correct HTTP status code
 *     - Lets GlobalExceptionHandler convert any thrown exceptions to JSON
 *
 * ENDPOINT SUMMARY:
 *   POST /api/auth/register  →  201 Created  + UserDto
 *   POST /api/auth/login     →  200 OK       + LoginResponse (token + user)
 *
 * WHY @Valid ON @RequestBody:
 *   @Valid tells Spring to run the Bean Validation annotations on the DTO
 *   (e.g. @NotBlank, @Email, @Size) BEFORE this method body executes.
 *   If any constraint fails, Spring throws MethodArgumentNotValidException,
 *   which GlobalExceptionHandler catches and converts to a 400 Bad Request
 *   with the first failed field's message. The service method is never called
 *   with invalid data.
 *
 * WHY REGISTER RETURNS 201 (not 200):
 *   HTTP 201 Created is the semantically correct status when a new resource
 *   is created. 200 OK means "request succeeded" without implying creation.
 *   Using the right status makes the API's intent clear to any client —
 *   201 means "a new account now exists that didn't exist before".
 *
 * WHY LOGIN RETURNS 200 (not 201):
 *   Login doesn't create anything new — it authenticates and issues a token.
 *   200 OK is correct here.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }


    /**
     * POST /api/auth/register
     *
     * Creates a new user account from the submitted name, email and password.
     *
     * @Valid fires Bean Validation on RegisterRequest before this method runs:
     *   - firstName / lastName: @NotBlank, @Size(max=100)
     *   - email: @NotBlank, @Email, @Size(max=255)
     *   - password: @NotBlank, @Size(min=8)
     * Any failure → 400 Bad Request (handled by GlobalExceptionHandler).
     *
     * @ResponseStatus(CREATED) sets the HTTP status to 201 for the happy path.
     * Exceptions bypass this annotation — the exception handler sets its own
     * status (409 for duplicate email, 400 for validation, etc.).
     *
     * @param req the validated registration data
     * @return a UserDto representing the new account (no password field)
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest req) {
        return userService.register(req);
    }


    /**
     * POST /api/auth/login
     *
     * Authenticates a registered user and issues a 24-hour JWT.
     *
     * @Valid fires Bean Validation on LoginRequest before this method runs:
     *   - email: @NotBlank (format NOT validated — see LoginRequest Javadoc)
     *   - password: @NotBlank
     * Any blank-field failure → 400 Bad Request.
     * Wrong credentials → 401 Unauthorized (thrown by UserService, handled
     * by GlobalExceptionHandler).
     *
     * @param req the login credentials
     * @return a LoginResponse containing the signed JWT and user profile
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req);
    }
}
