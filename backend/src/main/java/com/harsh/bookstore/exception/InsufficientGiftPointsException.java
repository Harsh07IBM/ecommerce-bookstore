package com.harsh.bookstore.exception;

/**
 * InsufficientGiftPointsException — thrown by OrderService when
 * giftPointsToRedeem exceeds the user's current balance (spec BR-05).
 *
 * Mapped to HTTP 400 Bad Request by GlobalExceptionHandler.
 */
public class InsufficientGiftPointsException extends RuntimeException {

    public InsufficientGiftPointsException() {
        super("Insufficient gift points");
    }
}
