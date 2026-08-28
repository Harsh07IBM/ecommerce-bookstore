package com.harsh.bookstore.dto;

import java.math.BigDecimal;


/**
 * BasketItemDto — one line item in the basket API response.
 *
 * Fields:
 *   bookId        — the book's database id
 *   title         — book title
 *   author        — first author name (empty string if authors list is empty)
 *   coverImageUrl — URL of the cover image
 *   unitPrice     — current selling price (BigDecimal, never double)
 *   quantity      — how many copies in the basket
 *   lineTotal     — unitPrice × quantity, computed by BasketService.toResponse()
 */
public class BasketItemDto {

    private Long bookId;
    private String title;
    private String author;
    private String coverImageUrl;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal lineTotal;


    public BasketItemDto() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
