package com.harsh.bookstore.exception;


/**
 * AddressNotFoundException — thrown by AddressService and CheckoutService when
 * the caller references an address ID that does not exist in the database.
 *
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(Long addressId) {
        super("Address not found: " + addressId);
    }
}
