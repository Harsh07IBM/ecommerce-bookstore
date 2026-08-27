package com.harsh.bookstore.dto;

import jakarta.validation.constraints.NotBlank;


/**
 * LoginRequest — the JSON body the client sends to POST /api/auth/login.
 *
 * WHY THIS IS SIMPLER THAN RegisterRequest:
 *   RegisterRequest has @Email and @Size to validate the format of the
 *   email address. LoginRequest deliberately does NOT.
 *
 *   If we validated the email format here and the user typed "notanemail",
 *   the 400 response would tell them "email must be a valid email address".
 *   That reveals information: the request was rejected because of the EMAIL
 *   field specifically, before credentials were even checked. An attacker
 *   probing for account existence could use this.
 *
 *   Instead, LoginRequest only checks that neither field is blank. Any
 *   further failure (unknown email, wrong password) returns the same
 *   generic 401 "Invalid email or password" — regardless of which check
 *   failed. This is the anti-enumeration design decision D-05 from the
 *   design document.
 */
public class LoginRequest {

    /**
     * Email address entered on the login form.
     * Only validated as non-blank — format checking is intentionally skipped.
     */
    @NotBlank(message = "email is required")
    private String email;

    /**
     * Raw password entered on the login form.
     * Only validated as non-blank — length/complexity rules are not rechecked
     * here (they were enforced at registration time).
     */
    @NotBlank(message = "password is required")
    private String password;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public LoginRequest() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
