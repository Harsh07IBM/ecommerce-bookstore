# Technical Design: FEAT-13 — Purchase Confirmation

## 1. Overview

This document records every concrete technical decision for Purchase
Confirmation as specified in `docs/specs/feature-13-purchase-confirmation.md`
and planned in `docs/plans/feature-13-purchase-confirmation-plan.md`.

---

## 2. Design Decisions

### D-01 — `OrderConfirmationResponse` is a flat DTO, not a subclass

A flat DTO with all `OrderResponse` fields duplicated plus `confirmationMessage`
avoids inheritance and Jackson serialisation issues. The extra fields are minimal
so duplication cost is low.

### D-02 — `getConfirmation` delegates to `getOrderById`

Ownership and not-found logic lives once in `getOrderById`. `getConfirmation`
calls it and wraps the result — no duplicated if-statements.

### D-03 — `confirmationMessage` is a constant in `OrderService`

```java
private static final String CONFIRMATION_MESSAGE =
    "Your order has been placed successfully!";
```

No DB column, no configuration file, no i18n in scope.

---

## 3. File Inventory

### 3.1 New production files

| # | Path | Role |
|---|------|------|
| 1 | `dto/OrderConfirmationResponse.java` | Confirmation response DTO |

### 3.2 Modified production files

| File | Change |
|---|---|
| `service/OrderService.java` | Add constant + `getConfirmation` method |
| `controller/OrderController.java` | Add `GET /api/orders/{id}/confirmation` |

### 3.3 Modified test files

| File | Additions |
|---|---|
| `test/service/OrderServiceTest.java` | 4 new tests |
| `test/controller/OrderControllerTest.java` | 4 new tests |

---

## 4. DTO Design

### `OrderConfirmationResponse`

All fields from `OrderResponse` plus:

| Field | Type | Value |
|---|---|---|
| `confirmationMessage` | `String` | set by service |
| `orderId` | `Long` | |
| `status` | `String` | |
| `orderDate` | `String` | |
| `items` | `List<OrderItemResponse>` | |
| `basketTotal` | `BigDecimal` | |
| `deliveryCharge` | `BigDecimal` | |
| `giftPointsRedeemed` | `int` | |
| `totalAmount` | `BigDecimal` | |
| `pointsAwarded` | `int` | |
| `estimatedDeliveryDate` | `String` | |
| `deliveryAddress` | `OrderAddressSnapshot` | |

No-arg constructor + explicit getters/setters.

---

## 5. Service Design

### Constant added to `OrderService`

```java
private static final String CONFIRMATION_MESSAGE =
        "Your order has been placed successfully!";
```

### `getConfirmation(Long userId, Long orderId)` → `OrderConfirmationResponse`

```java
public OrderConfirmationResponse getConfirmation(Long userId, Long orderId) {
    OrderResponse order = getOrderById(userId, orderId);
    OrderConfirmationResponse response = new OrderConfirmationResponse();
    response.setConfirmationMessage(CONFIRMATION_MESSAGE);
    response.setOrderId(order.getOrderId());
    response.setStatus(order.getStatus());
    response.setOrderDate(order.getOrderDate());
    response.setItems(order.getItems());
    response.setBasketTotal(order.getBasketTotal());
    response.setDeliveryCharge(order.getDeliveryCharge());
    response.setGiftPointsRedeemed(order.getGiftPointsRedeemed());
    response.setTotalAmount(order.getTotalAmount());
    response.setPointsAwarded(order.getPointsAwarded());
    response.setEstimatedDeliveryDate(order.getEstimatedDeliveryDate());
    response.setDeliveryAddress(order.getDeliveryAddress());
    return response;
}
```

---

## 6. Controller Design

Add to existing `OrderController`:

| Method | HTTP | Path | Status | Auth |
|---|---|---|---|---|
| `getConfirmation` | GET | `/api/orders/{id}/confirmation` | 200 | Required |

```java
@GetMapping("/{id}/confirmation")
public OrderConfirmationResponse getConfirmation(@PathVariable Long id,
                                                  Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return orderService.getConfirmation(user.getId(), id);
}
```

---

## 7. HTTP Status Mapping Summary

| Scenario | HTTP |
|---|---|
| No JWT | 401 |
| Order not found | 404 |
| Order belongs to another user | 403 |
| Success | 200 |

---

## 8. No new exceptions, no SecurityConfig changes, no new dependencies
