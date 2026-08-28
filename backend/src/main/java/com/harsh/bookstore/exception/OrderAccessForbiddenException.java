package com.harsh.bookstore.exception;

/**
 * OrderAccessForbiddenException — thrown by OrderService when the requested
 * order exists but belongs to a different user (spec BR-02).
 *
 * Mapped to HTTP 403 Forbidden by GlobalExceptionHandler.
 */
public class OrderAccessForbiddenException extends RuntimeException {

    public OrderAccessForbiddenException() {
        super("Forbidden");
    }
}
