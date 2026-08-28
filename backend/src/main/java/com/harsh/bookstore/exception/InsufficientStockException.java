package com.harsh.bookstore.exception;

/**
 * InsufficientStockException — thrown by OrderService when any basket item's
 * required quantity exceeds the book's current stockQuantity (spec BR-14 / AC-21).
 *
 * Mapped to HTTP 400 Bad Request by GlobalExceptionHandler.
 *
 * The book title is embedded in the message so the client can identify which item
 * caused the failure: "Insufficient stock for: {title}".
 *
 * Stock is checked for ALL items before any decrement is attempted (design D-07),
 * so the first short item causes the whole request to fail atomically — no partial
 * stock decrements occur.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String title) {
        super("Insufficient stock for: " + title);
    }
}
