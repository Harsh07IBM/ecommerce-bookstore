package com.harsh.bookstore.exception;

/**
 * OrderNotFoundException — thrown by OrderService when the requested order
 * does not exist in the database (spec BR-03).
 *
 * Mapped to HTTP 404 Not Found by GlobalExceptionHandler.
 * Message does not include the id to avoid information disclosure (design D-04).
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("Order not found");
    }
}
