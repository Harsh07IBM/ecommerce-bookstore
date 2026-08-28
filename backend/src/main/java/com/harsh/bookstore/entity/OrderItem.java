package com.harsh.bookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;


/**
 * OrderItem — one book line item inside a confirmed order.
 *
 * TITLE AND PRICE SNAPSHOT (design D-03):
 *   title and unitPrice are copied from the Book at order time. If the book is
 *   later removed from the catalogue or its price changes, order history still
 *   shows the correct values.
 *
 * bookId IS A PLAIN COLUMN (design D-03):
 *   bookId is stored as a plain Long column with no @ManyToOne to Book.
 *   No FK constraint is created against the books table. If a book is deleted,
 *   the order item row is not affected and the title snapshot preserves the name.
 *
 * PARENT REFERENCE:
 *   The @ManyToOne to Order is LAZY — items are always accessed via the parent
 *   Order's items collection, not queried independently.
 */
@Entity
@Table(
    name = "order_item",
    indexes = {
        @Index(name = "idx_order_item_order_id", columnList = "order_id")
    }
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Reference to the book — plain column, no FK constraint (design D-03). */
    @Column(name = "book_id", nullable = false)
    private Long bookId;

    /** Snapshot of Book.title at order time. */
    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private int quantity;

    /** Snapshot of Book.price at order time. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** unitPrice × quantity — stored for read performance. */
    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public OrderItem() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

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


    // ==================================================================
    // equals / hashCode / toString
    // ==================================================================

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrderItem)) return false;
        OrderItem that = (OrderItem) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OrderItem{id=" + id + ", bookId=" + bookId + ", quantity=" + quantity + "}";
    }
}
