# Implementation Plan: FEAT-13 — Purchase Confirmation

## 1. Overview

One new endpoint: `GET /api/orders/{id}/confirmation`. Returns the order
detail plus a `confirmationMessage` string. Reuses all existing
infrastructure — one new DTO, one new service method, one new controller
method.

---

## 2. Key Design Decisions

### `OrderConfirmationResponse` extends `OrderResponse` conceptually but is a separate DTO
Rather than subclassing `OrderResponse`, a new flat DTO
`OrderConfirmationResponse` holds all the same fields plus
`confirmationMessage`. This avoids inheritance complexity and keeps
Jackson serialisation straightforward.

### `getConfirmation` in `OrderService` delegates to `getOrderById` then wraps
```java
OrderResponse order = getOrderById(userId, orderId);
// map to OrderConfirmationResponse, add confirmationMessage
```
No duplicate ownership/not-found logic — fully reuses existing code.

### `confirmationMessage` is a constant
`"Your order has been placed successfully!"` — hardcoded as a private
static final in `OrderService`. No database column, no configuration.

---

## 3. New Files

| Layer | File | Purpose |
|---|---|---|
| dto | `OrderConfirmationResponse.java` | Adds `confirmationMessage` field to order detail |

---

## 4. Modified Files

| File | Change |
|---|---|
| `service/OrderService.java` | Add `getConfirmation(Long userId, Long orderId)` |
| `controller/OrderController.java` | Add `GET /api/orders/{id}/confirmation` |

---

## 5. Step-by-Step Implementation

### Step 1 — New DTO

**`OrderConfirmationResponse`** — all fields of `OrderResponse` plus:

| Field | Type | Value |
|---|---|---|
| `confirmationMessage` | `String` | `"Your order has been placed successfully!"` |

---

### Step 2 — Extend `OrderService`

**`getConfirmation(Long userId, Long orderId)`** → `OrderConfirmationResponse`

```
OrderResponse order = getOrderById(userId, orderId)   // reuse — handles 404/403

OrderConfirmationResponse response = new OrderConfirmationResponse()
response.setConfirmationMessage("Your order has been placed successfully!")
// copy all fields from order to response
return response
```

---

### Step 3 — Extend `OrderController`

| HTTP | Path | Status |
|---|---|---|
| GET | `/api/orders/{id}/confirmation` | 200 |

---

## 6. Test Plan

### Service Tests — additions to `OrderServiceTest`
| Test | What it verifies |
|---|---|
| `getConfirmation_returnsConfirmationMessage` | `confirmationMessage` field is correct |
| `getConfirmation_includesOrderFields` | `orderId`, `status` are present |
| `getConfirmation_throws404_whenNotFound` | delegates to `getOrderById` → `OrderNotFoundException` |
| `getConfirmation_throws403_whenWrongOwner` | delegates to `getOrderById` → `OrderAccessForbiddenException` |

### Controller Tests — additions to `OrderControllerTest`
| Test | Expected |
|---|---|
| `getConfirmation_returns200` | 200 + `$.confirmationMessage` correct |
| `getConfirmation_returns401_noJwt` | 401 |
| `getConfirmation_returns403_wrongOwner` | 403 |
| `getConfirmation_returns404_notFound` | 404 |
