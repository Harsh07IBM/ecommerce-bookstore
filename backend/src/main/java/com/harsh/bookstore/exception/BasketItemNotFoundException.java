package com.harsh.bookstore.exception;


/**
 * BasketItemNotFoundException — thrown by BasketService when the caller tries
 * to update or remove a book that is not in their basket.
 *
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class BasketItemNotFoundException extends RuntimeException {

    public BasketItemNotFoundException(Long bookId) {
        super("Book " + bookId + " is not in your basket");
    }
}
