# Implementation Plan: FEAT-11 — Buy Again

## 1. Overview

This plan describes every concrete step to implement Buy Again as specified
in `docs/specs/feature-11-buy-again.md`.

One new endpoint: `POST /api/orders/{id}/buy-again` — reads all items from
a previous order and adds each available book to the user's current basket,
silently skipping out-of-stock, missing, or max-quantity items.

---

## 2. Key Design Decisions

### New method in `OrderService`, not `BasketService`
`buyAgain` needs both the order (ownership check) and basket logic. It lives
in `OrderService` (which already has `OrderRepository` and `UserRepository`)
and calls `BasketService.addItem` for each item. This avoids circular
dependencies and keeps basket mutation logic in one place.

### Silent skip, not exception
For each order item, attempt `basketService.addItem`. Catch
`OutOfStockException`, `MaxQuantityExceededException`, and
`BookNotFoundException` and continue to the next item. No error is surfaced
to the caller (spec BR-05 to BR-08).

### `AddItemRequest` reused for each item
`BasketService.addItem` accepts an `AddItemRequest`. For each `OrderItem`,
construct `AddItemRequest(bookId, 1)` — quantity 1 per "buy again" attempt,
consistent with a normal add-to-basket action.

### Return value: final basket state
After processing all items, call `basketService.getBasket(userId, null)` and
return it as `BasketResponse`.

### No new DTOs, no schema changes
`BasketResponse` is the response. Nothing new needed.

---

## 3. New Files

None.

---

## 4. Modified Files

| File | Change |
|---|---|
| `service/OrderService.java` | Add `buyAgain(Long userId, Long orderId)` → `BasketResponse` |
| `controller/OrderController.java` | Add `POST /api/orders/{id}/buy-again` |

No new exceptions — `OrderNotFoundException` and `OrderAccessForbiddenException`
already exist from FEAT-10 and cover BR-02 / BR-03.

---

## 5. Step-by-Step Implementation

### Step 1 — Extend `OrderService`

**`buyAgain(Long userId, Long orderId)`** → `BasketResponse`

```
1. Order order = orderRepository.findById(orderId)
           .orElseThrow(OrderNotFoundException::new)
   if !order.getUserId().equals(userId):
       throw new OrderAccessForbiddenException()

2. for each OrderItem item in order.getItems():
       try:
           AddItemRequest req = new AddItemRequest()
           req.setBookId(item.getBookId())
           req.setQuantity(1)
           basketService.addItem(userId, null, req)
       catch (OutOfStockException | MaxQuantityExceededException | BookNotFoundException):
           // skip silently

3. return basketService.getBasket(userId, null)
```

`OrderService` already has `BasketService` as a dependency — no constructor change needed.

---

### Step 2 — Extend `OrderController`

Add one method:

| HTTP | Path | Status |
|---|---|---|
| POST | `/api/orders/{id}/buy-again` | 200 |

```java
@PostMapping("/{id}/buy-again")
public BasketResponse buyAgain(@PathVariable Long id,
                                Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return orderService.buyAgain(user.getId(), id);
}
```

---

## 6. Test Plan

### 6.1 Service Tests — additions to `OrderServiceTest`

| Test | What it verifies |
|---|---|
| `buyAgain_addsAvailableItemsToBasket` | All in-stock items added; basket returned |
| `buyAgain_skipsOutOfStockItems` | `OutOfStockException` caught, other items still added |
| `buyAgain_skipsMaxQuantityItems` | `MaxQuantityExceededException` caught, other items still added |
| `buyAgain_throws404_orderNotFound` | Unknown orderId → `OrderNotFoundException` |
| `buyAgain_throws403_wrongOwner` | Order owned by another user → `OrderAccessForbiddenException` |

### 6.2 Controller Tests — additions to `OrderControllerTest`

| Test | Expected |
|---|---|
| `buyAgain_returns200` | 200 + basket response |
| `buyAgain_returns401_noJwt` | 401 |
| `buyAgain_returns403_wrongOwner` | service throws `OrderAccessForbiddenException` → 403 |
| `buyAgain_returns404_notFound` | service throws `OrderNotFoundException` → 404 |
