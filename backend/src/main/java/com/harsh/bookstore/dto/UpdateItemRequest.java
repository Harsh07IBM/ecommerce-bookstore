package com.harsh.bookstore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;


/**
 * UpdateItemRequest — request body for PUT /api/basket/items/{bookId}.
 *
 * Validation:
 *   quantity — 0..7 inclusive.
 *              0  → the service removes the item (BR-07).
 *              >7 → rejected by @Max before reaching the service.
 */
public class UpdateItemRequest {

    @Min(value = 0, message = "quantity must be 0 or greater")
    @Max(value = 7, message = "quantity must be at most 7")
    private int quantity;


    public UpdateItemRequest() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
