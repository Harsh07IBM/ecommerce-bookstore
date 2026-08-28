# Implementation Plan: FEAT-12 — Order Cancellation

## 1. Overview

One new endpoint: `POST /api/orders/{id}/cancel`. Validates ownership,
status, and 48-hour window; then sets `Order.status = CANCELLED` and
restores stock for each item — all within one `@Transactional` method.

---

## 2. Key Design Decisions

### `cancelOrder` in `OrderService`, new exception classes for each 400 case
Two distinct 400 error messages require two distinct exception classes so
`GlobalExceptionHandler` can map them individually:
- `OrderNotCancellableException` → 400 `"Order cannot be cancelled"`
- `CancellationWindowExpiredException` → 400 `"Cancellation window has expired"`

### 48-hour check using `ChronoUnit.HOURS.between`
```java
ChronoUnit.HOURS.between(order.getOrderDate(), LocalDateTime.now()) > 48
    → throw CancellationWindowExpiredException
```

### Status check before window check
Check status first (BR-04) then window (BR-05). A non-PAID order is
rejected regardless of age — consistent with the principle of cheapest
check first.

### Stock restoration mirrors FEAT-08 stock decrement
For each `OrderItem`, load the `Book` by `bookId`, increment
`stockQuantity` by `item.getQuantity()`, and save. Same two-pass pattern
used in `placeOrder` — but here just one pass (restoration always succeeds).

### Gift points untouched (spec BR-08 / BR-09)
No `userRepository` call needed in `cancelOrder`. Points are left as-is.

### Return updated `OrderResponse`
The method returns `toResponse(savedOrder)` — same shape as all other order
endpoints, now with `status = "CANCELLED"`.

---

## 3. New Files

| Layer | File | Purpose |
|---|---|---|
| exception | `OrderNotCancellableException.java` | → 400 `"Order cannot be cancelled"` |
| exception | `CancellationWindowExpiredException.java` | → 400 `"Cancellation window has expired"` |

---

## 4. Modified Files

| File | Change |
|---|---|
| `service/OrderService.java` | Add `cancelOrder(Long userId, Long orderId)` |
| `controller/OrderController.java` | Add `POST /api/orders/{id}/cancel` |
| `exception/GlobalExceptionHandler.java` | Add `// FEAT-12 handlers` section (2 new handlers) |

---

## 5. Step-by-Step Implementation

### Step 1 — New exceptions

**`OrderNotCancellableException`**
- Message: `"Order cannot be cancelled"`
- Maps to → **400 Bad Request**

**`CancellationWindowExpiredException`**
- Message: `"Cancellation window has expired"`
- Maps to → **400 Bad Request**

---

### Step 2 — Extend `OrderService`

**`@Transactional cancelOrder(Long userId, Long orderId)`** → `OrderResponse`

```
1. Order order = orderRepository.findById(orderId)
           .orElseThrow(OrderNotFoundException::new)
   if !order.getUserId().equals(userId):
       throw new OrderAccessForbiddenException()

2. if order.getStatus() != OrderStatus.PAID:
       throw new OrderNotCancellableException()

3. if ChronoUnit.HOURS.between(order.getOrderDate(), LocalDateTime.now()) > 48:
       throw new CancellationWindowExpiredException()

4. for each OrderItem item in order.getItems():
       Book book = bookRepository.findById(item.getBookId())
               .orElse(null)           // book may have been deleted; skip if so
       if book != null:
           book.setStockQuantity(book.getStockQuantity() + item.getQuantity())
           bookRepository.save(book)

5. order.setStatus(OrderStatus.CANCELLED)
   Order saved = orderRepository.save(order)

6. return toResponse(saved)
```

`OrderService` already has `OrderRepository` and `BookRepository` — no new
constructor dependency needed.

---

### Step 3 — Extend `OrderController`

| HTTP | Path | Status |
|---|---|---|
| POST | `/api/orders/{id}/cancel` | 200 |

```java
@PostMapping("/{id}/cancel")
public OrderResponse cancelOrder(@PathVariable Long id,
                                  Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return orderService.cancelOrder(user.getId(), id);
}
```

---

### Step 4 — `GlobalExceptionHandler` additions

```
OrderNotCancellableException        → 400 Bad Request
CancellationWindowExpiredException  → 400 Bad Request
```

---

## 6. Test Plan

### 6.1 Service Tests — additions to `OrderServiceTest`

| Test | What it verifies |
|---|---|
| `cancelOrder_success_statusCancelled` | Order status set to CANCELLED; response returned |
| `cancelOrder_success_stockRestored` | Each book's stockQuantity incremented by ordered qty |
| `cancelOrder_throws404_orderNotFound` | Unknown orderId → `OrderNotFoundException` |
| `cancelOrder_throws403_wrongOwner` | Wrong userId → `OrderAccessForbiddenException` |
| `cancelOrder_throws400_notPaid` | Status != PAID → `OrderNotCancellableException` |
| `cancelOrder_throws400_windowExpired` | orderDate > 48h ago → `CancellationWindowExpiredException` |

### 6.2 Controller Tests — additions to `OrderControllerTest`

| Test | Expected |
|---|---|
| `cancelOrder_returns200` | 200 + `$.status == "CANCELLED"` |
| `cancelOrder_returns401_noJwt` | 401 |
| `cancelOrder_returns400_notCancellable` | service throws `OrderNotCancellableException` → 400 |
| `cancelOrder_returns400_windowExpired` | service throws `CancellationWindowExpiredException` → 400 |
| `cancelOrder_returns403_wrongOwner` | service throws `OrderAccessForbiddenException` → 403 |
| `cancelOrder_returns404_notFound` | service throws `OrderNotFoundException` → 404 |
