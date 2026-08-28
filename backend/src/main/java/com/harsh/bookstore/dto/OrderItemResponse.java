package com.harsh.bookstore.dto;

import java.math.BigDecimal;


/**
 * OrderItemResponse — one book line item inside the POST /api/orders 201 response.
 */
public class OrderItemResponse {

    private Long bookId;
    private String title;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;


    public OrderItemResponse() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
