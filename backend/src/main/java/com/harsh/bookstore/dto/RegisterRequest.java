package com.harsh.bookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * RegisterRequest — the JSON body the client sends to POST /api/auth/register.
 *
 * WHAT THE VALIDATION ANNOTATIONS DO:
 *   These are Jakarta Bean Validation annotations. When the controller puts
 *   @Valid on the @RequestBody parameter, Spring automatically runs all of
 *   these checks before our method body even executes. If any check fails,
 *   Spring throws MethodArgumentNotValidException, which our
 *   GlobalExceptionHandler turns into a 400 Bad Request response with the
 *   annotation's `message` as the body.
 *
 *   This means the service layer never receives invalid data — it can trust
 *   that if it's been called, all inputs are already valid.
 *
 * WHY CUSTOM message= STRINGS:
 *   The default messages from the annotations are technical (e.g.
 *   "must not be blank", "size must be between 8 and 2147483647"). Our
 *   custom messages are in plain English, match the spec §6 wording exactly,
 *   and are what the client receives in the error response body.
 */
public class RegisterRequest {

    /**
     * Given name — "Harsh". Required, not blank, max 100 chars (spec FR-03).
     *
     * @NotBlank covers both null AND empty/whitespace-only strings.
     * @NotNull alone would allow "   " (spaces). @NotBlank is the right
     * choice for string fields that must have actual content.
     */
    @NotBlank(message = "firstName is required")
    @Size(max = 100, message = "firstName must be 100 characters or fewer")
    private String firstName;

    /**
     * Family name — "Sharma". Required, not blank, max 100 chars (spec FR-03).
     */
    @NotBlank(message = "lastName is required")
    @Size(max = 100, message = "lastName must be 100 characters or fewer")
    private String lastName;

    /**
     * Email address — used as login identifier.
     *
     * @Email validates the format ("x@y.z") but does NOT check that the
     * address is reachable — that would require sending an email (out of
     * scope for FEAT-04, see spec §8).
     */
    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Size(max = 255, message = "email must be 255 characters or fewer")
    private String email;

    /**
     * Raw password submitted by the user.
     *
     * IMPORTANT: This field is ONLY ever used as the input to BCrypt's
     * encode() call inside UserService. It is never stored, never logged,
     * and never appears in any response. The minimum length of 8 is spec
     * FR-05.
     *
     * We deliberately do NOT set a maximum length. BCrypt internally works
     * on the first 72 bytes of input — very long passwords are safe but
     * silently truncated by the algorithm. A max-length constraint on the
     * API would leak this implementation detail. Simpler to leave it open.
     */
    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public RegisterRequest() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
