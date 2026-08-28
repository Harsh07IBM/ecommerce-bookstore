package com.harsh.bookstore.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Order — one confirmed order placed by a registered user.
 *
 * TABLE NAME:
 *   "orders" — "order" is a reserved SQL keyword; using it without quoting
 *   would cause failures on most databases.
 *
 * ADDRESS SNAPSHOT (design D-02):
 *   The seven address fields are stored directly on this row, not as a foreign
 *   key to DeliveryAddress. This ensures order history remains accurate even if
 *   the user later edits or deletes the original address record (spec BR-16).
 *
 * ORDER DATE (@PrePersist):
 *   orderDate is set to LocalDateTime.now() in a @PrePersist callback, not in
 *   the service. The column is marked updatable=false to prevent accidental
 *   modification after insert.
 *
 * ITEMS (CascadeType.ALL + orphanRemoval):
 *   A single orderRepository.save(order) persists all OrderItem children;
 *   removing an item from the list and saving deletes the row.
 *
 * CARD DETAILS (spec BR-17):
 *   No card fields exist on this entity. Card details are used only in-memory
 *   for format validation and the decline check inside OrderService.
 */
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_order_user_id", columnList = "user_id")
    }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user. No @ManyToOne — ownership-by-id pattern (same as DeliveryAddress). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** Server-assigned at first persist; never updated after that. */
    @Column(name = "order_date", nullable = false, updatable = false)
    private LocalDateTime orderDate;

    @Column(name = "basket_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal basketTotal;

    @Column(name = "delivery_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryCharge;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** Stored as "YYYY-MM-DD" string — avoids Jackson date serialisation config. */
    @Column(name = "estimated_delivery_date", nullable = false, length = 10)
    private String estimatedDeliveryDate;

    // ------------------------------------------------------------------
    // Address snapshot fields (spec BR-16 / design D-02)
    // ------------------------------------------------------------------

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "phone_number", nullable = false, length = 10)
    private String phoneNumber;

    @Column(nullable = false, length = 200)
    private String line1;

    /** Nullable — line2 is optional (same nullability as DeliveryAddress). */
    @Column(length = 200)
    private String line2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;

    /** Points redeemed from the user's balance on this order (FEAT-09). Default 0. */
    @Column(name = "gift_points_redeemed", nullable = false)
    private int giftPointsRedeemed = 0;

    /** Points awarded to the user's balance for this order (FEAT-09). Default 0. */
    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded = 0;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();


    // ==================================================================
    // LIFECYCLE CALLBACK
    // ==================================================================

    @PrePersist
    protected void onCreate() {
        if (orderDate == null) {
            orderDate = LocalDateTime.now();
        }
    }


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public Order() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public BigDecimal getBasketTotal() { return basketTotal; }
    public void setBasketTotal(BigDecimal basketTotal) { this.basketTotal = basketTotal; }

    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(BigDecimal deliveryCharge) { this.deliveryCharge = deliveryCharge; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(String v) { this.estimatedDeliveryDate = v; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }

    public String getLine2() { return line2; }
    public void setLine2(String line2) { this.line2 = line2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public int getGiftPointsRedeemed() { return giftPointsRedeemed; }
    public void setGiftPointsRedeemed(int giftPointsRedeemed) { this.giftPointsRedeemed = giftPointsRedeemed; }

    public int getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(int pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }


    // ==================================================================
    // equals / hashCode / toString
    // ==================================================================

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Order)) return false;
        Order that = (Order) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", userId=" + userId + ", status=" + status + "}";
    }
}
