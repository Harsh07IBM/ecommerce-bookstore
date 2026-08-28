package com.harsh.bookstore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;


/**
 * PaymentRequest — request body for POST /api/orders.
 *
 * VALIDATION NOTES:
 *   cardNumber:          @Pattern enforces exactly 16 numeric digits (no Luhn — spec BR-09).
 *   expiryMonth:         @Min(1) @Max(12) enforces valid calendar month.
 *   expiryYear:          No static annotation — validated dynamically in OrderService
 *                        because LocalDate.now().getYear() is not a compile-time constant.
 *   cvv:                 @Pattern enforces exactly 3 numeric digits.
 *   cardholderName:      @NotBlank rejects absent or whitespace-only values.
 *   giftPointsToRedeem:  @Min(0) rejects negatives; defaults to 0 if omitted (FEAT-09 placeholder).
 *
 * SECURITY NOTE (spec BR-17):
 *   These fields are used only for format validation and the decline check.
 *   They are never stored — no entity, no column, no log line should ever contain card values.
 */
public class PaymentRequest {

    @NotNull(message = "addressId is required")
    private Long addressId;

    @NotBlank(message = "cardNumber is required")
    @Pattern(regexp = "\\d{16}", message = "cardNumber must be exactly 16 numeric digits")
    private String cardNumber;

    @Min(value = 1, message = "expiryMonth must be between 1 and 12")
    @Max(value = 12, message = "expiryMonth must be between 1 and 12")
    private int expiryMonth;

    // expiryYear validated dynamically in OrderService (see class Javadoc)
    private int expiryYear;

    @NotBlank(message = "cvv is required")
    @Pattern(regexp = "\\d{3}", message = "cvv must be exactly 3 numeric digits")
    private String cvv;

    @NotBlank(message = "cardholderName must not be blank")
    private String cardholderName;

    @Min(value = 0, message = "giftPointsToRedeem must be non-negative")
    private int giftPointsToRedeem = 0;


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public int getExpiryMonth() { return expiryMonth; }
    public void setExpiryMonth(int expiryMonth) { this.expiryMonth = expiryMonth; }

    public int getExpiryYear() { return expiryYear; }
    public void setExpiryYear(int expiryYear) { this.expiryYear = expiryYear; }

    public String getCvv() { return cvv; }
    public void setCvv(String cvv) { this.cvv = cvv; }

    public String getCardholderName() { return cardholderName; }
    public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }

    public int getGiftPointsToRedeem() { return giftPointsToRedeem; }
    public void setGiftPointsToRedeem(int giftPointsToRedeem) { this.giftPointsToRedeem = giftPointsToRedeem; }
}
