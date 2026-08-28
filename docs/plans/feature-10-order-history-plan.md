# Implementation Plan: FEAT-10 — Order Management & History

## 1. Overview

This plan describes every concrete step to implement order history as specified
in `docs/specs/feature-10-order-history.md`.

The feature adds two read-only endpoints to the existing `OrderController`:
- `GET /api/orders` — list all orders for the authenticated user, newest first
- `GET /api/orders/{id}` — single order detail with ownership check

No new tables, no new repositories, no schema changes. All data already exists
from FEAT-08/09. `OrderRepository.findAllByUserId` was declared in FEAT-08.

---

## 2. Key Design Decisions

### Reuse `OrderResponse` and `toResponse` from FEAT-08/09
`OrderResponse` already contains every field needed for history display. The
private `toResponse(Order)` helper in `OrderService` is promoted to package-
accessible (or kept private and called from new public methods). A new private
`toResponse` overload is not needed — the existing one is reused as-is.

### Sort order list in service, not with a custom `@Query`
`OrderRepository.findAllByUserId` returns an unsorted list. Sorting by
`orderDate` descending is applied in `OrderService` using the stream API:
```java
orders.stream()
    .sorted(Comparator.comparing(Order::getOrderDate).reversed())
    .map(this::toResponse)
    .toList()
```
This avoids adding a `@Query` or a new repository method for FEAT-10.

### Ownership check pattern mirrors `AddressService`
`getOrderById` follows the established pattern:
1. `findById` → empty → `OrderNotFoundException`
2. `order.getUserId().equals(userId)` → false → `OrderAccessForbiddenException`

### Two new exceptions
- `OrderNotFoundException(Long id)` → 404 `"Order not found"`
- `OrderAccessForbiddenException()` → 403 `"Forbidden"`

---

## 3. New Files

| Layer | File | Purpose |
|---|---|---|
| exception | `OrderNotFoundException.java` | → 404 |
| exception | `OrderAccessForbiddenException.java` | → 403 |

---

## 4. Modified Files

| File | Change |
|---|---|
| `service/OrderService.java` | Add `getOrders(Long userId)` and `getOrderById(Long userId, Long orderId)` |
| `controller/OrderController.java` | Add `GET /api/orders` and `GET /api/orders/{id}` methods |
| `exception/GlobalExceptionHandler.java` | Add `// FEAT-10 handlers` section with 2 new handlers |

---

## 5. Step-by-Step Implementation

### Step 1 — New exceptions

**`OrderNotFoundException(Long id)`**
- Message: `"Order not found"`
- Maps to → **404 Not Found**

**`OrderAccessForbiddenException()`**
- Message: `"Forbidden"`
- Maps to → **403 Forbidden**

---

### Step 2 — Extend `OrderService`

**`getOrders(Long userId)`** → `List<OrderResponse>`
```
List<Order> orders = orderRepository.findAllByUserId(userId)
return orders sorted by orderDate descending, mapped via toResponse()
```

**`getOrderById(Long userId, Long orderId)`** → `OrderResponse`
```
Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId))
if !order.getUserId().equals(userId):
    throw new OrderAccessForbiddenException()
return toResponse(order)
```

---

### Step 3 — Extend `OrderController`

Add two methods to the existing `OrderController`:

| Method | HTTP | Path | Status |
|---|---|---|---|
| `listOrders` | GET | `/api/orders` | 200 |
| `getOrder` | GET | `/api/orders/{id}` | 200 |

---

### Step 4 — `GlobalExceptionHandler` additions

Two new handlers in a `// FEAT-10 handlers` section:
```
OrderNotFoundException          → 404 Not Found
OrderAccessForbiddenException   → 403 Forbidden
```

---

## 6. Test Plan

### 6.1 Service Tests — additions to `OrderServiceTest`

| Test | What it verifies |
|---|---|
| `getOrders_returnsSortedByDateDesc` | Two orders returned newest first |
| `getOrders_returnsEmpty_whenNone` | No orders → empty list |
| `getOrderById_returnsOrder` | Valid userId + orderId → correct OrderResponse |
| `getOrderById_throws404_whenNotFound` | Unknown orderId → `OrderNotFoundException` |
| `getOrderById_throws403_whenWrongOwner` | Order exists but wrong userId → `OrderAccessForbiddenException` |

### 6.2 Controller Tests — additions to `OrderControllerTest`

| Test | Expected |
|---|---|
| `listOrders_returns200` | 200 + array with correct `$.length` |
| `listOrders_returns200_empty` | 200 + `[]` |
| `listOrders_returns401_noJwt` | 401 |
| `getOrder_returns200` | 200 + `$.orderId` correct |
| `getOrder_returns401_noJwt` | 401 |
| `getOrder_returns403_wrongOwner` | service throws `OrderAccessForbiddenException` → 403 |
| `getOrder_returns404_notFound` | service throws `OrderNotFoundException` → 404 |
