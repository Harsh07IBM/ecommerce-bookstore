package com.harsh.bookstore.exception;


/**
 * AddressAccessForbiddenException — thrown by AddressService and CheckoutService
 * when the authenticated user tries to access an address that belongs to a
 * different user (BR-02).
 *
 * Mapped to HTTP 403 by GlobalExceptionHandler.
 */
public class AddressAccessForbiddenException extends RuntimeException {

    public AddressAccessForbiddenException() {
        super("You do not have permission to access this address");
    }
}
