package com.harsh.bookstore.dto;

import java.math.BigDecimal;
import java.util.List;


/**
 * CheckoutSummaryResponse — the full response for GET /api/checkout/summary.
 *
 * Combines:
 *   items / basketTotal  — passed through unchanged from BasketService (FEAT-06)
 *   deliveryCharge       — 0.00 when basketTotal >= 500; 50.00 otherwise (BR-10)
 *   estimatedDeliveryDate — today + 3 calendar days, ISO-8601 string (BR-11)
 *   deliveryAddress      — the chosen address (without userId / isDefault)
 *
 * BasketItemDto is reused from FEAT-06 — no duplication of the line-item shape.
 * estimatedDeliveryDate is a String (not LocalDate) to avoid Jackson date
 * configuration; see design decision D-08.
 */
public class CheckoutSummaryResponse {

    /** Line items — reuses BasketItemDto from FEAT-06. */
    private List<BasketItemDto> items;

    private BigDecimal basketTotal;

    /** 0.00 when basketTotal >= 500; 50.00 otherwise. */
    private BigDecimal deliveryCharge;

    /** ISO-8601 date string, e.g. "2025-08-21". Today + 3 calendar days. */
    private String estimatedDeliveryDate;

    private DeliveryAddressDto deliveryAddress;


    public CheckoutSummaryResponse() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public List<BasketItemDto> getItems() { return items; }
    public void setItems(List<BasketItemDto> items) { this.items = items; }

    public BigDecimal getBasketTotal() { return basketTotal; }
    public void setBasketTotal(BigDecimal basketTotal) { this.basketTotal = basketTotal; }

    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(BigDecimal deliveryCharge) { this.deliveryCharge = deliveryCharge; }

    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(String estimatedDeliveryDate) {
        this.estimatedDeliveryDate = estimatedDeliveryDate;
    }

    public DeliveryAddressDto getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(DeliveryAddressDto deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }
}
