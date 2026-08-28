# Technical Design: FEAT-09 — Gift Points Redemption

## 1. Overview

This document records every concrete technical decision needed to implement
Gift Points as specified in `docs/specs/feature-09-gift-points.md` and planned
in `docs/plans/feature-09-gift-points-plan.md`.

It is the authoritative reference for the coding and testing phases.

---

## 2. Design Decisions

### D-01 — Gift point balance stored as `int` on `User`, not a ledger table

A separate ledger table would enable full transaction history but is explicitly
out of scope (spec §7). A single `gift_points int` column on `User` is the
minimal, correct solution. The balance is always the source of truth.

`int` (primitive) is used rather than `Integer` (wrapper) to guarantee a
well-defined default of 0 and to prevent null comparisons — the same rationale
as `DeliveryAddress.isDefault`.

### D-02 — `giftPointsRedeemed` and `pointsAwarded` stored on `Order`

Both values are stored as `int` columns on the `Order` row (default 0). This
supports order history display (FEAT-10) without a separate query, and records
the exact redemption/award amounts that applied to each order immutably.

### D-03 — `UserRepository` added as a 5th constructor dependency of `OrderService`

`OrderService` already holds `userId`. It calls `userRepository.findById(userId)`
to load the `User` entity, validates and mutates `giftPoints`, and saves. Adding
`UserRepository` keeps the service self-contained — no need for a separate
`UserService` call that would introduce a circular dependency risk.

### D-04 — Gift point validation inserted between card decline and charge computation

The full validation order inside `placeOrder` is:

```
1.  expiryYear check
2.  empty basket check
3.  address ownership check
4.  card decline check
5a. load User
5b. giftPointsToRedeem > user.giftPoints  → InsufficientGiftPointsException
5c. giftPointsToRedeem > basketTotal      → GiftPointsExceedBasketTotalException
6.  compute charges (now including gift discount)
7.  stock validation pass (no mutations)
8.  stock decrement pass
9.  build + save Order
10. clear basket
11. mutate + save User (deduct redeemed, award new points)
12. return response
```

Gift point validation is placed after the card decline check (Step 4) so that
a declined card returns 402 before gift point checks run — consistent with the
principle that payment validation comes before loyalty bookkeeping.

### D-05 — `pointsAwarded` computed from final `totalAmount` using `RoundingMode.FLOOR`

```java
int pointsAwarded = totalAmount
    .multiply(new BigDecimal("0.05"))
    .setScale(0, RoundingMode.FLOOR)
    .intValue();
```

`RoundingMode.FLOOR` truncates toward negative infinity — for positive amounts
this is equivalent to truncating the decimal (e.g. ₹199 × 0.05 = 9.95 → 9).
`FLOOR` is correct per spec BR-09 ("floor") and is preferable to `HALF_UP`
which would round 9.95 up to 10.

`totalAmount` here is already reduced by `giftPointsToRedeem` (spec BR-09 —
points are awarded on the post-discount total).

### D-06 — `giftPointsToRedeem` compared to `basketTotal` (not `totalAmount`)

The guard is `giftPointsToRedeem > basketTotal` (spec BR-06), not
`> totalAmount`. `totalAmount` includes delivery charge, so using it would
allow scenarios where points cover delivery but not items — unintuitive.
Using `basketTotal` ensures points only offset the cost of the items themselves.
The comparison:

```java
new BigDecimal(req.getGiftPointsToRedeem()).compareTo(basket.getBasketTotal()) > 0
```

### D-07 — User balance mutation happens after order save and basket clear

The order of mutations within the transaction:
1. Stock decremented
2. Order saved
3. Basket cleared
4. **User balance mutated (deduct + award) and saved**

Placing the user save last means that if a transient failure occurs after
order creation but before the user save, the entire transaction rolls back
(JPA `@Transactional`) — no orphaned order with incorrect balance.

### D-08 — `GET /api/users/me/gift-points` reloads User from DB

The JWT principal (`User` loaded by `JwtAuthFilter`) was loaded at request
authentication time and may not reflect balance changes from concurrent
requests. `UserController.getGiftPoints` reloads the user via
`userRepository.findById` to guarantee the latest balance is returned.

### D-09 — `UserController` is a new class under `/api/users`

The gift points endpoint fits under a `UserController` at
`@RequestMapping("/api/users")`. This keeps user-profile concerns separate from
order concerns (`OrderController`) and is extensible for future user-profile
endpoints (e.g. `GET /api/users/me`).

---

## 3. File Inventory

### 3.1 New production files

| # | Path | Role |
|---|------|------|
| 1 | `controller/UserController.java` | `GET /api/users/me/gift-points` → 200 |
| 2 | `dto/GiftPointsResponse.java` | `{ "giftPoints": 120 }` response body |
| 3 | `exception/InsufficientGiftPointsException.java` | → 400 `"Insufficient gift points"` |
| 4 | `exception/GiftPointsExceedBasketTotalException.java` | → 400 `"Gift points exceed basket total"` |

### 3.2 Modified production files

| File | Change |
|------|--------|
| `entity/User.java` | Add `giftPoints int` field, default 0, column `gift_points` |
| `entity/Order.java` | Add `giftPointsRedeemed int` and `pointsAwarded int` fields |
| `dto/OrderResponse.java` | Add `giftPointsRedeemed int` and `pointsAwarded int` fields |
| `service/OrderService.java` | Add `UserRepository` dep; extend `placeOrder`; extend `toResponse` |
| `exception/GlobalExceptionHandler.java` | Add `// FEAT-09 handlers` section with 2 new handlers |

### 3.3 New test files

| # | Path |
|---|------|
| 1 | `test/controller/UserControllerTest.java` |

### 3.4 Modified test files

| File | Additions |
|------|-----------|
| `test/service/OrderServiceTest.java` | 6 new test methods |
| `test/controller/OrderControllerTest.java` | 2 new test methods |

---

## 4. Entity Changes

### 4.1 `User` — new field

```
field:   giftPoints
column:  gift_points
type:    int  (primitive — default 0, never null)
column:  @Column(name = "gift_points", nullable = false)
```

Getter: `getGiftPoints()` → `int`
Setter: `setGiftPoints(int giftPoints)`

---

### 4.2 `Order` — two new fields

```
field:   giftPointsRedeemed
column:  gift_points_redeemed
type:    int  (default 0)
column:  @Column(name = "gift_points_redeemed", nullable = false)

field:   pointsAwarded
column:  points_awarded
type:    int  (default 0)
column:  @Column(name = "points_awarded", nullable = false)
```

Getters and setters for both.

---

## 5. Exception Design

### `InsufficientGiftPointsException`

```java
public class InsufficientGiftPointsException extends RuntimeException {
    public InsufficientGiftPointsException() {
        super("Insufficient gift points");
    }
}
```

Handled in `GlobalExceptionHandler` → **400 Bad Request**.

---

### `GiftPointsExceedBasketTotalException`

```java
public class GiftPointsExceedBasketTotalException extends RuntimeException {
    public GiftPointsExceedBasketTotalException() {
        super("Gift points exceed basket total");
    }
}
```

Handled in `GlobalExceptionHandler` → **400 Bad Request**.

---

## 6. DTO Changes

### `GiftPointsResponse` (new)

| Field | Type |
|---|---|
| `giftPoints` | `int` |

No-arg constructor + getter/setter.

---

### `OrderResponse` — additions

| New Field | Type | Notes |
|---|---|---|
| `giftPointsRedeemed` | `int` | Points applied from balance to this order |
| `pointsAwarded` | `int` | Points credited to balance for this order |

Add getters and setters for both fields. Existing fields unchanged.

---

## 7. Service Design — `OrderService` changes

### Constructor (updated)

```java
public OrderService(OrderRepository orderRepository,
                    BasketService basketService,
                    DeliveryAddressRepository addressRepository,
                    BookRepository bookRepository,
                    UserRepository userRepository)
```

New private final field: `private final UserRepository userRepository;`

New constant:
```java
private static final BigDecimal POINTS_RATE = new BigDecimal("0.05");
```

---

### `placeOrder` — extended step sequence

Only the new/changed steps are shown; all FEAT-08 steps are otherwise unchanged.

```
[after Step 4 — card decline check]

Step 5a — load user
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + userId))

Step 5b — gift point validation
    if req.getGiftPointsToRedeem() > user.getGiftPoints():
        throw new InsufficientGiftPointsException()
    if new BigDecimal(req.getGiftPointsToRedeem()).compareTo(basket.getBasketTotal()) > 0:
        throw new GiftPointsExceedBasketTotalException()

[Step 6 — compute charges, extended]
    BigDecimal deliveryCharge = ... (unchanged)
    BigDecimal giftDiscount   = new BigDecimal(req.getGiftPointsToRedeem())
    BigDecimal totalAmount    = basket.getBasketTotal().add(deliveryCharge).subtract(giftDiscount)
    int pointsAwarded         = totalAmount
                                    .multiply(POINTS_RATE)
                                    .setScale(0, RoundingMode.FLOOR)
                                    .intValue()
    String estimatedDeliveryDate = LocalDate.now().plusDays(3).toString()

[Step 8 — build Order, two new field sets]
    order.setGiftPointsRedeemed(req.getGiftPointsToRedeem())
    order.setPointsAwarded(pointsAwarded)

[after Step 10 — basket cleared]

Step 11 — mutate and save user balance
    user.setGiftPoints(user.getGiftPoints() - req.getGiftPointsToRedeem() + pointsAwarded)
    userRepository.save(user)
```

---

### `toResponse` — extended

```java
response.setGiftPointsRedeemed(order.getGiftPointsRedeemed());
response.setPointsAwarded(order.getPointsAwarded());
```

---

## 8. Controller Design — `UserController` (new)

```
@RestController
@RequestMapping("/api/users")
Constructor-injected: UserRepository userRepository
```

| Method | HTTP | Path | Status | Auth |
|---|---|---|---|---|
| `getGiftPoints` | GET | `/api/users/me/gift-points` | 200 | Required (JWT) |

```java
@GetMapping("/me/gift-points")
public GiftPointsResponse getGiftPoints(Authentication authentication) {
    User principal = (User) authentication.getPrincipal();
    User fresh = userRepository.findById(principal.getId())
            .orElseThrow(() -> new IllegalStateException("User not found"));
    GiftPointsResponse response = new GiftPointsResponse();
    response.setGiftPoints(fresh.getGiftPoints());
    return response;
}
```

---

## 9. `GlobalExceptionHandler` additions

Insert a `// FEAT-09 handlers` section with two new `@ExceptionHandler` methods:

```
InsufficientGiftPointsException          → 400 Bad Request
GiftPointsExceedBasketTotalException     → 400 Bad Request
```

Same pattern as every existing handler: build `ErrorResponse`, return
`ResponseEntity.status(...).body(body)`.

---

## 10. HTTP Status Mapping Summary

| Scenario | Exception | HTTP |
|---|---|---|
| `giftPointsToRedeem > balance` | `InsufficientGiftPointsException` | 400 |
| `giftPointsToRedeem > basketTotal` | `GiftPointsExceedBasketTotalException` | 400 |
| Balance endpoint, no JWT | — (Spring Security) | 401 |
| Balance endpoint, valid JWT | — | 200 |

All FEAT-08 status codes are unchanged.

---

## 11. No SecurityConfig changes

`/api/users/me/gift-points` falls under the existing
`anyRequest().authenticated()` rule. No modifications to `SecurityConfig.java`
are needed.

---

## 12. No new Maven dependencies

| Type | Source |
|---|---|
| `RoundingMode` | `java.math` (JDK) |
| `UserRepository` | already on classpath |
