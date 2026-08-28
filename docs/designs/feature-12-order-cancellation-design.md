# Technical Design: FEAT-12 — Order Cancellation

## 1. Overview

This document records every concrete technical decision needed to implement
order cancellation as specified in `docs/specs/feature-12-order-cancellation.md`
and planned in `docs/plans/feature-12-order-cancellation-plan.md`.

---

## 2. Design Decisions

### D-01 — Two distinct exception classes for the two 400 cases

`OrderNotCancellableException` (wrong status) and
`CancellationWindowExpiredException` (time expired) have distinct messages.
Two classes allow `GlobalExceptionHandler` to map each to a clear 400 body
without inspecting the message string.

### D-02 — Status check before window check

BR-04 (status) is checked before BR-05 (window). A `CANCELLED` order is
rejected before the time check runs — avoids the misleading message
"Cancellation window has expired" on an already-cancelled order.

### D-03 — 48-hour check with `ChronoUnit.HOURS.between`

```java
if (ChronoUnit.HOURS.between(order.getOrderDate(), LocalDateTime.now()) > 48) {
    throw new CancellationWindowExpiredException();
}
```

`ChronoUnit.HOURS.between` returns a positive long (truncated toward zero)
which is compared to 48. This is correct for the spec rule: a window exactly
at 48 hours is still valid (`> 48`, not `>= 48`).

### D-04 — Stock restoration: skip missing books

For each `OrderItem`, the book is loaded with `findById().orElse(null)`. If
the book has been deleted from the catalogue since the order was placed, the
restoration is silently skipped. This prevents a 500 error on a valid
cancellation request.

### D-05 — No new constructor dependency on `OrderService`

`cancelOrder` needs `OrderRepository` and `BookRepository`, both of which
are already injected. No constructor change.

### D-06 — Gift points untouched

No `userRepository.save` call in `cancelOrder`. Spec BR-08/BR-09 is
satisfied by simply not touching the balance.

### D-07 — Returns `OrderResponse` (same shape as all other order endpoints)

`toResponse(savedOrder)` is called — the caller sees the updated order with
`status = "CANCELLED"`.

---

## 3. File Inventory

### 3.1 New production files

| # | Path | Role |
|---|------|------|
| 1 | `exception/OrderNotCancellableException.java` | → 400 `"Order cannot be cancelled"` |
| 2 | `exception/CancellationWindowExpiredException.java` | → 400 `"Cancellation window has expired"` |

### 3.2 Modified production files

| File | Change |
|---|---|
| `service/OrderService.java` | Add `cancelOrder(Long userId, Long orderId)` + `ChronoUnit` import |
| `controller/OrderController.java` | Add `POST /api/orders/{id}/cancel` |
| `exception/GlobalExceptionHandler.java` | Add `// FEAT-12 handlers` section |

### 3.3 Modified test files

| File | Additions |
|---|---|
| `test/service/OrderServiceTest.java` | 6 new test methods |
| `test/controller/OrderControllerTest.java` | 6 new test methods |

---

## 4. Exception Design

### `OrderNotCancellableException`
```java
public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException() {
        super("Order cannot be cancelled");
    }
}
```
→ **400 Bad Request**

### `CancellationWindowExpiredException`
```java
public class CancellationWindowExpiredException extends RuntimeException {
    public CancellationWindowExpiredException() {
        super("Cancellation window has expired");
    }
}
```
→ **400 Bad Request**

---

## 5. Service Design

### `@Transactional cancelOrder(Long userId, Long orderId)` → `OrderResponse`

```java
@Transactional
public OrderResponse cancelOrder(Long userId, Long orderId) {
    // 1. load + ownership check
    Order order = orderRepository.findById(orderId)
            .orElseThrow(OrderNotFoundException::new);
    if (!order.getUserId().equals(userId)) {
        throw new OrderAccessForbiddenException();
    }
    // 2. status check
    if (order.getStatus() != OrderStatus.PAID) {
        throw new OrderNotCancellableException();
    }
    // 3. 48-hour window check
    if (ChronoUnit.HOURS.between(order.getOrderDate(), LocalDateTime.now()) > 48) {
        throw new CancellationWindowExpiredException();
    }
    // 4. restore stock
    for (OrderItem item : order.getItems()) {
        bookRepository.findById(item.getBookId()).ifPresent(book -> {
            book.setStockQuantity(book.getStockQuantity() + item.getQuantity());
            bookRepository.save(book);
        });
    }
    // 5. update status and save
    order.setStatus(OrderStatus.CANCELLED);
    Order saved = orderRepository.save(order);
    // 6. return response
    return toResponse(saved);
}
```

---

## 6. Controller Design

Add to existing `OrderController`:

| Method | HTTP | Path | Status | Auth |
|---|---|---|---|---|
| `cancelOrder` | POST | `/api/orders/{id}/cancel` | 200 | Required |

```java
@PostMapping("/{id}/cancel")
public OrderResponse cancelOrder(@PathVariable Long id,
                                  Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return orderService.cancelOrder(user.getId(), id);
}
```

---

## 7. `GlobalExceptionHandler` additions

```
OrderNotCancellableException        → 400 Bad Request
CancellationWindowExpiredException  → 400 Bad Request
```

---

## 8. HTTP Status Mapping Summary

| Scenario | Exception | HTTP |
|---|---|---|
| Order not found | `OrderNotFoundException` | 404 |
| Wrong owner | `OrderAccessForbiddenException` | 403 |
| Status not PAID | `OrderNotCancellableException` | 400 |
| Window expired (> 48h) | `CancellationWindowExpiredException` | 400 |
| No JWT | — (Spring Security) | 401 |
| Success | — | 200 |

---

## 9. No SecurityConfig changes, no new Maven dependencies

`java.time.temporal.ChronoUnit` is JDK standard. No new packages needed.
