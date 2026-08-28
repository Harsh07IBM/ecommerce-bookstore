package com.harsh.bookstore.exception;

/**
 * CancellationWindowExpiredException — thrown by OrderService when a
 * cancellation is requested more than 48 hours after the order was placed
 * (spec BR-05).
 *
 * Mapped to HTTP 400 Bad Request by GlobalExceptionHandler.
 */
public class CancellationWindowExpiredException extends RuntimeException {

    public CancellationWindowExpiredException() {
        super("Cancellation window has expired");
    }
}
