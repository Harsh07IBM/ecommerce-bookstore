package com.harsh.bookstore.exception;

/**
 * Thrown by CategoryService when a caller requests a category slug that
 * does not exist in the catalogue.
 *
 * Extends RuntimeException (unchecked) — no method in the call chain needs
 * to declare `throws`, and GlobalExceptionHandler catches it centrally to
 * return HTTP 404 with a structured ErrorResponse body.
 */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String slug) {
        super("Category with slug '" + slug + "' was not found");
    }
}
