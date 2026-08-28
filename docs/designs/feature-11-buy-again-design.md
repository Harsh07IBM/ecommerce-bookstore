# Technical Design: FEAT-11 — Buy Again

## 1. Overview

This document records every concrete technical decision needed to implement
Buy Again as specified in `docs/specs/feature-11-buy-again.md` and planned
in `docs/plans/feature-11-buy-again-plan.md`.

---

## 2. Design Decisions

### D-01 — `buyAgain` lives in `OrderService`

`OrderService` already holds `OrderRepository` (for the ownership check) and
`BasketService` (for adding items). No new dependency is needed. Placing the
method here avoids a circular dependency between `OrderService` and
`BasketService`.

### D-02 — Quantity 1 per item, not the original ordered quantity

Each item from the order is added with quantity 1. This is the natural
"add to basket" action — the user can adjust quantities themselves. Adding
the original ordered quantity (e.g. 5 copies) would risk jumping straight
to or past the max-quantity limit.

### D-03 — Silent skip via try/catch on three exception types

`BasketService.addItem` throws `OutOfStockException`,
`MaxQuantityExceededException`, or `BookNotFoundException` for the three
skip conditions. Each is caught individually and the loop continues. No
wrapper exception, no response field listing skipped items (spec §6).

### D-04 — Final basket state fetched with `getBasket` after the loop

Rather than tracking the `BasketResponse` from the last successful `addItem`
call, `getBasket(userId, null)` is called once after the loop. This guarantees
the returned basket is always complete and consistent, even if zero items
were added.

### D-05 — No new DTOs, no schema changes

`BasketResponse` (from FEAT-06) is the response type. No new exceptions —
`OrderNotFoundException` and `OrderAccessForbiddenException` from FEAT-10
cover the 404 and 403 cases.

---

## 3. File Inventory

### 3.1 Modified production files

| File | Change |
|---|---|
| `service/OrderService.java` | Add `buyAgain(Long userId, Long orderId)` |
| `controller/OrderController.java` | Add `POST /api/orders/{id}/buy-again` |

### 3.2 Modified test files

| File | Additions |
|---|---|
| `test/service/OrderServiceTest.java` | 5 new test methods |
| `test/controller/OrderControllerTest.java` | 4 new test methods |

---

## 4. Service Design

### `buyAgain(Long userId, Long orderId)` → `BasketResponse`

```java
public BasketResponse buyAgain(Long userId, Long orderId) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(OrderNotFoundException::new);
    if (!order.getUserId().equals(userId)) {
        throw new OrderAccessForbiddenException();
    }
    for (OrderItem item : order.getItems()) {
        try {
            AddItemRequest req = new AddItemRequest();
            req.setBookId(item.getBookId());
            req.setQuantity(1);
            basketService.addItem(userId, null, req);
        } catch (OutOfStockException | MaxQuantityExceededException | BookNotFoundException e) {
            // skip silently — spec BR-05, BR-06, BR-07
        }
    }
    return basketService.getBasket(userId, null);
}
```

No `@Transactional` needed — each `addItem` call is independently transactional
via `BasketService`. A failure on one item does not roll back previous items
(desired behaviour per spec BR-05 to BR-08).

---

## 5. Controller Design

Add to existing `OrderController`:

| Method | HTTP | Path | Status | Auth |
|---|---|---|---|---|
| `buyAgain` | POST | `/api/orders/{id}/buy-again` | 200 | Required |

```java
@PostMapping("/{id}/buy-again")
public BasketResponse buyAgain(@PathVariable Long id,
                                Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return orderService.buyAgain(user.getId(), id);
}
```

---

## 6. HTTP Status Mapping Summary

| Scenario | Exception | HTTP |
|---|---|---|
| Order not found | `OrderNotFoundException` | 404 |
| Order belongs to another user | `OrderAccessForbiddenException` | 403 |
| No JWT | — (Spring Security) | 401 |
| Success (including all-skipped) | — | 200 |

---

## 7. No SecurityConfig changes, no new dependencies
