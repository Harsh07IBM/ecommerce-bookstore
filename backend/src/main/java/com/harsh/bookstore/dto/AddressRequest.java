package com.harsh.bookstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


/**
 * AddressRequest — request body for POST /api/addresses and PUT /api/addresses/{id}.
 *
 * Validation:
 *   All fields except line2 are @NotBlank — missing or blank values return 400.
 *   phoneNumber must be exactly 10 numeric digits (spec BR-07 / AC-07).
 *   pincode must be exactly 6 numeric digits (spec BR-06 / AC-06).
 *   isDefault defaults to false when omitted from JSON (primitive boolean default).
 */
public class AddressRequest {

    @NotBlank(message = "recipientName is required")
    private String recipientName;

    @NotBlank(message = "phoneNumber is required")
    @Pattern(regexp = "\\d{10}", message = "phoneNumber must be exactly 10 numeric digits")
    private String phoneNumber;

    @NotBlank(message = "line1 is required")
    private String line1;

    // No @NotBlank — line2 is optional (spec BR-05). May be null or absent from JSON.
    private String line2;

    @NotBlank(message = "city is required")
    private String city;

    @NotBlank(message = "state is required")
    private String state;

    @NotBlank(message = "pincode is required")
    @Pattern(regexp = "\\d{6}", message = "pincode must be exactly 6 numeric digits")
    private String pincode;

    // Primitive boolean — Jackson maps a missing JSON field to the primitive
    // default (false), matching the spec default (BR-04 / §4.2).
    private boolean isDefault;


    public AddressRequest() {
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

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
