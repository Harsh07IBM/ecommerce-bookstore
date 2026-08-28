package com.harsh.bookstore.exception;


/**
 * DefaultAddressDeleteException — thrown by AddressService when the caller
 * attempts to delete the default address while other addresses still exist (BR-08).
 *
 * Mapped to HTTP 400 by GlobalExceptionHandler.
 * Message matches spec BR-08 / AC-14 exactly.
 */
public class DefaultAddressDeleteException extends RuntimeException {

    public DefaultAddressDeleteException() {
        super("Cannot delete the default address while other addresses exist");
    }
}
