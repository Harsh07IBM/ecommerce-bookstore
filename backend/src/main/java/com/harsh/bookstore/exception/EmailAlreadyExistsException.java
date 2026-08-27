package com.harsh.bookstore.exception;


/**
 * EmailAlreadyExistsException — thrown by UserService.register() when the
 * submitted email address is already registered to an existing account.
 *
 * Handled by GlobalExceptionHandler.handleEmailAlreadyExists()
 * → HTTP 409 Conflict.
 *
 * WHY 409 AND NOT 400:
 *   400 Bad Request means the request itself is malformed or invalid.
 *   The email format here is perfectly valid — the problem is a CONFLICT
 *   between the new registration and an existing resource (the account).
 *   409 Conflict is the precise HTTP status for "the request could not
 *   be completed because of a conflict with the current state of the
 *   target resource" (RFC 9110 §15.5.10). It's the right choice here.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("An account with this email address already exists");
    }
}
