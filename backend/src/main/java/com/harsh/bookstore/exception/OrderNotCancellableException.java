package com.harsh.bookstore.exception;

/**
 * OrderNotCancellableException — thrown by OrderService when a cancellation
 * is requested on an order whose status is not PAID (spec BR-04).
 *
 * Mapped to HTTP 400 Bad Request by GlobalExceptionHandler.
 */
public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException() {
        super("Order cannot be cancelled");
    }
}
