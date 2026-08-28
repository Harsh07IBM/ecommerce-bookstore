package com.harsh.bookstore.exception;


/**
 * OutOfStockException — thrown by BasketService when the caller tries to add
 * a book whose stockQuantity is 0.
 *
 * Mapped to HTTP 400 by GlobalExceptionHandler.
 * Message matches spec BR-04 and AC-04 exactly.
 */
public class OutOfStockException extends RuntimeException {

    public OutOfStockException() {
        super("This book is currently out of stock");
    }
}
