package com.harsh.bookstore.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Basket — one shopping basket per visitor.
 *
 * IDENTITY STRATEGY:
 *   Exactly one of the two identity columns is non-null per row:
 *     - userId     set for authenticated users (extracted from JWT)
 *     - sessionId  set for guests (HttpSession.getId())
 *
 *   BasketService.resolveBasket() enforces this invariant when creating
 *   a new basket. The DB allows both columns to be nullable so that
 *   either variant can be stored without a partial-index trick.
 *
 * CASCADE / ORPHAN-REMOVAL:
 *   cascade = ALL  — saving a Basket also saves/updates its items.
 *   orphanRemoval  — removing a BasketItem from the list deletes its row.
 *   Together these let BasketService manipulate the items list directly
 *   and call basketRepository.save(basket) once to persist the whole tree.
 */
@Entity
@Table(name = "basket")
public class Basket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Non-null for authenticated users; null for guests. */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Non-null for guest sessions; null for authenticated users.
     * HttpSession IDs are typically 32–64 hex chars; 128 is a safe upper bound.
     */
    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "basket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BasketItem> items = new ArrayList<>();


    // ==================================================================
    // LIFECYCLE CALLBACK
    // ==================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public Basket() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<BasketItem> getItems() { return items; }
    public void setItems(List<BasketItem> items) { this.items = items; }


    // ==================================================================
    // equals / hashCode / toString
    // ==================================================================

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Basket)) return false;
        Basket that = (Basket) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Basket{id=" + id + ", userId=" + userId + ", sessionId='" + sessionId + "'}";
    }
}
