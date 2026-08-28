package com.harsh.bookstore.exception;


/**
 * MaxQuantityExceededException — thrown by BasketService when adding a book
 * would push its aggregate basket quantity above 7 (BR-03 / BR-05).
 *
 * Mapped to HTTP 400 by GlobalExceptionHandler.
 * Message matches spec BR-05 and AC-05 exactly.
 */
public class MaxQuantityExceededException extends RuntimeException {

    public MaxQuantityExceededException() {
        super("Maximum quantity per book is 7");
    }
}
