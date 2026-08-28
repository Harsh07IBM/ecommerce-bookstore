package com.harsh.bookstore.dto;


/**
 * DeliveryAddressDto — the delivery address embedded inside CheckoutSummaryResponse.
 *
 * Intentionally leaner than AddressResponse: omits userId (the caller already
 * knows who they are) and isDefault (irrelevant at checkout time).
 * See design decision D-04.
 */
public class DeliveryAddressDto {

    private Long id;
    private String recipientName;
    private String phoneNumber;
    private String line1;
    private String line2;       // may be null
    private String city;
    private String state;
    private String pincode;


    public DeliveryAddressDto() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
}
