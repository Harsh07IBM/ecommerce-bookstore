package com.harsh.bookstore.exception;

/**
 * GiftPointsExceedBasketTotalException — thrown by OrderService when
 * giftPointsToRedeem is greater than the basket total (spec BR-06).
 * Prevents totalAmount going negative.
 *
 * Mapped to HTTP 400 Bad Request by GlobalExceptionHandler.
 */
public class GiftPointsExceedBasketTotalException extends RuntimeException {

    public GiftPointsExceedBasketTotalException() {
        super("Gift points exceed basket total");
    }
}
