# Technical Design: FEAT-10 — Order Management & History

## 1. Overview

This document records every concrete technical decision needed to implement
order history as specified in `docs/specs/feature-10-order-history.md` and
planned in `docs/plans/feature-10-order-history-plan.md`.

---

## 2. Design Decisions

### D-01 — `toResponse(Order)` reused unchanged from FEAT-08/09

The private helper already maps every field needed for history display. No new
DTO, no new mapper. Both new service methods call the existing `toResponse`.

### D-02 — Sorting done in service with stream, not a new repository method

`findAllByUserId` returns `List<Order>`. The service sorts in memory:
```java
orders.stream()
    .sorted(Comparator.comparing(Order::getOrderDate).reversed())
    .map(this::toResponse)
    .toList()
```
Order history lists are expected to be small (tens of orders per user), so
in-memory sort is appropriate. No `@Query` or `findAllByUserIdOrderByOrderDateDesc`
is needed.

### D-03 — Ownership check follows the `AddressService` 404-then-403 pattern

`getOrderById` calls `findById` first (→ 404 if absent), then checks
`order.getUserId().equals(userId)` (→ 403 if mismatch). This gives the
correct distinct HTTP status for each failure mode — not a single
`findByIdAndUserId` which collapses both into "not found".

### D-04 — `OrderNotFoundException` message is `"Order not found"` (no id exposed)

Not including the id in the message avoids information disclosure to users
probing for other users' order IDs.

---

## 3. File Inventory

### 3.1 New production files

| # | Path | Role |
|---|------|------|
| 1 | `exception/OrderNotFoundException.java` | → 404 `"Order not found"` |
| 2 | `exception/OrderAccessForbiddenException.java` | → 403 `"Forbidden"` |

### 3.2 Modified production files

| File | Change |
|---|---|
| `service/OrderService.java` | Add `getOrders` and `getOrderById` |
| `controller/OrderController.java` | Add `GET /api/orders` and `GET /api/orders/{id}` |
| `exception/GlobalExceptionHandler.java` | Add `// FEAT-10 handlers` section |

### 3.3 Modified test files

| File | Additions |
|---|---|
| `test/service/OrderServiceTest.java` | 5 new test methods |
| `test/controller/OrderControllerTest.java` | 7 new test methods |

---

## 4. Exception Design

### `OrderNotFoundException`

```java
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() {
        super("Order not found");
    }
}
```

Handled in `GlobalExceptionHandler` → **404 Not Found**.

---

### `OrderAccessForbiddenException`

```java
public class OrderAccessForbiddenException extends RuntimeException {
    public OrderAccessForbiddenException() {
        super("Forbidden");
    }
}
```

Handled in `GlobalExceptionHandler` → **403 Forbidden**.

---

## 5. Service Design — `OrderService` additions

### `getOrders(Long userId)` → `List<OrderResponse>`

```java
public List<OrderResponse> getOrders(Long userId) {
    return orderRepository.findAllByUserId(userId)
            .stream()
            .sorted(Comparator.comparing(Order::getOrderDate).reversed())
            .map(this::toResponse)
            .toList();
}
```

No `@Transactional` needed — read-only, single repository call.

---

### `getOrderById(Long userId, Long orderId)` → `OrderResponse`

```java
public OrderResponse getOrderById(Long userId, Long orderId) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(OrderNotFoundException::new);
    if (!order.getUserId().equals(userId)) {
        throw new OrderAccessForbiddenException();
    }
    return toResponse(order);
}
```

---

## 6. Controller Design — `OrderController` additions

Add to the existing `OrderController` (which already has `POST /api/orders`):

| Method | HTTP | Path | Status | Auth |
|---|---|---|---|---|
| `listOrders` | GET | `/api/orders` | 200 | Required |
| `getOrder` | GET | `/api/orders/{id}` | 200 | Required |

```java
@GetMapping
public List<OrderResponse> listOrders(Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return orderService.getOrders(user.getId());
}

@GetMapping("/{id}")
public OrderResponse getOrder(@PathVariable Long id,
                               Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return orderService.getOrderById(user.getId(), id);
}
```

---

## 7. `GlobalExceptionHandler` additions

Insert a `// FEAT-10 handlers` section with two new `@ExceptionHandler` methods:

```
OrderNotFoundException          → 404 Not Found
OrderAccessForbiddenException   → 403 Forbidden
```

---

## 8. HTTP Status Mapping Summary

| Scenario | Exception | HTTP |
|---|---|---|
| Order not found | `OrderNotFoundException` | 404 |
| Order belongs to another user | `OrderAccessForbiddenException` | 403 |
| No JWT | — (Spring Security) | 401 |
| Success (list) | — | 200 |
| Success (detail) | — | 200 |

---

## 9. No SecurityConfig changes

`/api/orders` GET endpoints fall under the existing `anyRequest().authenticated()`
rule. No modifications to `SecurityConfig.java` are needed.

---

## 10. No new Maven dependencies

No new types required beyond what is already on the classpath.
