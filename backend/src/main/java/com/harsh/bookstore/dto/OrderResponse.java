package com.harsh.bookstore.dto;

import java.math.BigDecimal;
import java.util.List;


/**
 * OrderResponse — full 201 response body for POST /api/orders.
 *
 * orderDate is serialised as a String (LocalDateTime.toString()) in the format
 * "YYYY-MM-DDTHH:MM:SS" — avoids Jackson date configuration issues (same pattern
 * as CheckoutSummaryResponse.estimatedDeliveryDate, design D-11).
 *
 * status is serialised as a String via OrderStatus.name() — always "PAID"
 * for a successful order created by FEAT-08 (design D-11).
 */
public class OrderResponse {

    private Long orderId;
    private String status;
    private String orderDate;
    private List<OrderItemResponse> items;
    private BigDecimal basketTotal;
    private BigDecimal deliveryCharge;
    private BigDecimal totalAmount;
    private String estimatedDeliveryDate;
    private OrderAddressSnapshot deliveryAddress;


    public OrderResponse() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

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

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(String estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }

    public OrderAddressSnapshot getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(OrderAddressSnapshot deliveryAddress) { this.deliveryAddress = deliveryAddress; }
}
