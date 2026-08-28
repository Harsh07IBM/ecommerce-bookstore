package com.harsh.bookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;


/**
 * DeliveryAddress — one saved delivery address belonging to a registered user.
 *
 * OWNERSHIP:
 *   Each row is owned by exactly one user via the `userId` foreign-key column.
 *   There is no JPA @ManyToOne to User here — ownership is enforced in
 *   AddressService by explicit userId equality checks, which lets us return the
 *   correct 404 vs 403 HTTP status codes (see design D-01).
 *
 * isDefault INVARIANT (BR-04):
 *   Exactly one address per user may have isDefault = true. The invariant is
 *   maintained by AddressService.saveAddress / updateAddress, which demote the
 *   previous default before setting a new one. The field is declared as the
 *   primitive `boolean` (not wrapper `Boolean`) to guarantee a well-defined
 *   default value of false and to prevent null comparisons (see design D-02).
 *
 * INDEXES:
 *   idx_delivery_address_user_id        — speeds up findAllByUserId (list endpoint)
 *   idx_delivery_address_user_default   — composite; speeds up findByUserIdAndIsDefaultTrue
 */
@Entity
@Table(
    name = "delivery_address",
    indexes = {
        @Index(name = "idx_delivery_address_user_id",
               columnList = "user_id"),
        @Index(name = "idx_delivery_address_user_default",
               columnList = "user_id, is_default")
    }
)
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user — non-null always. Not a @ManyToOne (see class Javadoc). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    /**
     * Stored as String: exactly 10 numeric digits.
     * Leading zeros must be preserved — an int/long would silently drop them.
     * No arithmetic is ever performed on this value (see design D-03).
     */
    @Column(name = "phone_number", nullable = false, length = 10)
    private String phoneNumber;

    @Column(name = "line1", nullable = false, length = 200)
    private String line1;

    /** Nullable — line2 is optional per spec BR-05. */
    @Column(name = "line2", length = 200)
    private String line2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    /**
     * Stored as String: exactly 6 numeric digits.
     * Same rationale as phoneNumber — leading zeros, no arithmetic (design D-03).
     */
    @Column(name = "pincode", nullable = false, length = 6)
    private String pincode;

    /**
     * Primitive boolean — Java default is false.
     * Never null: wrapper Boolean would allow null, breaking BR-04 comparisons
     * and JPQL queries. See design decision D-02.
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public DeliveryAddress() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

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

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }


    // ==================================================================
    // equals / hashCode / toString
    // ==================================================================

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeliveryAddress)) return false;
        DeliveryAddress that = (DeliveryAddress) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DeliveryAddress{id=" + id + ", userId=" + userId
                + ", isDefault=" + isDefault + "}";
    }
}
