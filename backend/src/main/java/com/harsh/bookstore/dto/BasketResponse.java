package com.harsh.bookstore.dto;

import java.math.BigDecimal;
import java.util.List;


/**
 * BasketResponse — the full basket body returned by every basket endpoint.
 *
 * Fields:
 *   items        — ordered list of line items (may be empty)
 *   totalItems   — sum of all item quantities
 *   basketTotal  — sum of all lineTotals (BigDecimal)
 */
public class BasketResponse {

    private List<BasketItemDto> items;
    private int totalItems;
    private BigDecimal basketTotal;


    public BasketResponse() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public List<BasketItemDto> getItems() { return items; }
    public void setItems(List<BasketItemDto> items) { this.items = items; }

    public int getTotalItems() { return totalItems; }
    public void setTotalItems(int totalItems) { this.totalItems = totalItems; }

    public BigDecimal getBasketTotal() { return basketTotal; }
    public void setBasketTotal(BigDecimal basketTotal) { this.basketTotal = basketTotal; }
}
