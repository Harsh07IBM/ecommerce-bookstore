package com.harsh.bookstore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


/**
 * AddItemRequest — request body for POST /api/basket/items.
 *
 * Validation:
 *   bookId   — required; 404 if the book doesn't exist (checked in service)
 *   quantity — 1..7 inclusive; defaults to 1 if omitted from the JSON body.
 *              The @Min/@Max guards the syntactic range.  The service's
 *              MaxQuantityExceededException guards the aggregate basket total.
 */
public class AddItemRequest {

    @NotNull(message = "bookId is required")
    private Long bookId;

    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 7, message = "quantity must be at most 7")
    private int quantity = 1;


    public AddItemRequest() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
