package com.harsh.bookstore.dto;

import java.math.BigDecimal;
import java.util.List;


/**
 * OrderConfirmationResponse — response body for GET /api/orders/{id}/confirmation.
 *
 * Contains all fields from OrderResponse plus a confirmationMessage string
 * for display on the post-payment confirmation screen (FEAT-13).
 */
public class OrderConfirmationResponse {

    private String confirmationMessage;
    private Long orderId;
    private String status;
    private String orderDate;
    private List<OrderItemResponse> items;
    private BigDecimal basketTotal;
    private BigDecimal deliveryCharge;
    private int giftPointsRedeemed;
    private BigDecimal totalAmount;
    private int pointsAwarded;
    private String estimatedDeliveryDate;
    private OrderAddressSnapshot deliveryAddress;


    public OrderConfirmationResponse() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public String getConfirmationMessage() { return confirmationMessage; }
    public void setConfirmationMessage(String confirmationMessage) { this.confirmationMessage = confirmationMessage; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public List<OrderItemResponse> getItems() { return items; }
    public void setItems(List<OrderItemResponse> items) { this.items = items; }

    public BigDecimal getBasketTotal() { return basketTotal; }
    public void setBasketTotal(BigDecimal basketTotal) { this.basketTotal = basketTotal; }

    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(BigDecimal deliveryCharge) { this.deliveryCharge = deliveryCharge; }

    public int getGiftPointsRedeemed() { return giftPointsRedeemed; }
    public void setGiftPointsRedeemed(int giftPointsRedeemed) { this.giftPointsRedeemed = giftPointsRedeemed; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public int getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(int pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(String estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }

    public OrderAddressSnapshot getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(OrderAddressSnapshot deliveryAddress) { this.deliveryAddress = deliveryAddress; }
}
