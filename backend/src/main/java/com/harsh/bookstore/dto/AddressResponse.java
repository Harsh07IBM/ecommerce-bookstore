package com.harsh.bookstore.dto;


/**
 * AddressResponse — the outward-facing shape of a DeliveryAddress as sent to
 * API clients. Includes userId and isDefault (full record for address management).
 *
 * Contrast with DeliveryAddressDto, which is embedded inside the checkout
 * summary and omits userId and isDefault (see design decision D-04).
 */
public class AddressResponse {

    private Long id;
    private Long userId;
    private String recipientName;
    private String phoneNumber;
    private String line1;
    private String line2;       // may be null
    private String city;
    private String state;
    private String pincode;
    private boolean isDefault;


    public AddressResponse() {
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
}
