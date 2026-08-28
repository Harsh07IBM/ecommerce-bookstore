package com.harsh.bookstore.dto;


/**
 * OrderAddressSnapshot — the deliveryAddress object inside the POST /api/orders
 * 201 response. Contains address fields only (no userId, no isDefault, no id).
 *
 * These values are read from the persisted Order row (the snapshot), not from
 * DeliveryAddress, so they remain correct even if the original address is later
 * edited or deleted.
 */
public class OrderAddressSnapshot {

    private String recipientName;
    private String phoneNumber;
    private String line1;
    private String line2;   // nullable
    private String city;
    private String state;
    private String pincode;


    public OrderAddressSnapshot() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

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
