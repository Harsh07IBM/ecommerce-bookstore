package com.harsh.bookstore.exception;


/**
 * InvalidCredentialsException — thrown by UserService.login() when the
 * submitted email/password combination does not match any account.
 *
 * Handled by GlobalExceptionHandler.handleInvalidCredentials()
 * → HTTP 401 Unauthorized.
 *
 * CRITICAL SECURITY DESIGN — ONE EXCEPTION FOR TWO FAILURE MODES:
 *   Login can fail in exactly two ways:
 *     1. No account exists with that email address.
 *     2. An account exists but the password is wrong.
 *
 *   Both cases throw THIS SAME EXCEPTION with THIS SAME MESSAGE.
 *   This is intentional — it is the anti-enumeration pattern (design D-05).
 *
 *   If we used different exceptions (EmailNotFoundException vs
 *   WrongPasswordException), an attacker could tell the difference:
 *     - "Invalid email" → that address is not registered → useful info
 *     - "Wrong password" → that address IS registered → useful info
 *
 *   With a single generic exception, both cases return:
 *     HTTP 401 — "Invalid email or password"
 *   The attacker learns nothing about which part was wrong.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
