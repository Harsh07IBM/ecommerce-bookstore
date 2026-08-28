package com.harsh.bookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


/**
 * BasketItem — one book + quantity inside a Basket.
 *
 * FETCH STRATEGY RATIONALE:
 *   book  — EAGER: every basket read immediately maps items to DTOs that
 *           need title, price, coverImageUrl. Eager collapses the N+1
 *           selects into a single join and avoids LazyInitializationException
 *           outside a transaction.
 *
 *   basket — LAZY: navigation from item → parent basket is never needed.
 *            Lazy avoids loading the whole basket when only the item is
 *            in scope.
 */
@Entity
@Table(name = "basket_item")
public class BasketItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basket_id", nullable = false)
    private Basket basket;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private int quantity;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public BasketItem() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Basket getBasket() { return basket; }
    public void setBasket(Basket basket) { this.basket = basket; }

    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }


    // ==================================================================
    // equals / hashCode / toString
    // ==================================================================

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BasketItem)) return false;
        BasketItem that = (BasketItem) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "BasketItem{id=" + id
                + ", bookId=" + (book != null ? book.getId() : null)
                + ", quantity=" + quantity + "}";
    }
}
