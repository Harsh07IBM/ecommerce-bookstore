# Implementation Plan: FEAT-09 — Gift Points Redemption

## 1. Overview

This plan describes every concrete step required to implement Gift Points
as specified in `docs/specs/feature-09-gift-points.md`.

FEAT-09 extends the existing payment flow (FEAT-08) rather than introducing
a separate flow. The changes are deliberately minimal:

- Two new columns (`User.giftPoints`, `Order.giftPointsRedeemed`,
  `Order.pointsAwarded`)
- One new endpoint (`GET /api/users/me/gift-points`)
- Two new response fields on the existing `POST /api/orders` 201 body
- Extended validation and logic inside `OrderService.placeOrder`
- A new `GiftPointsInsufficientException` and one new handler in
  `GlobalExceptionHandler`

No new tables. No new repositories. No new controllers beyond a single
new method on an existing-pattern controller.

---

## 2. Key Design Decisions

### Gift points stored on `User`, not in a separate ledger

A dedicated ledger table would allow full transaction history but is out of
scope (§7 of spec). A single `gift_points` integer column on `User` is the
minimal solution. The balance is always the source of truth.

### Two-step point mutation inside `OrderService.placeOrder` (same transaction)

Within one `@Transactional` call:
1. Deduct `giftPointsToRedeem` from `user.giftPoints`
2. Compute `totalAmount = basketTotal + deliveryCharge − giftPointsToRedeem`
3. Compute `pointsAwarded = floor(totalAmount × 0.05)`
4. Award `pointsAwarded` to `user.giftPoints`
5. Save `user`

Both mutations happen in the same transaction as order creation. A rollback
(e.g. after stock decrement) leaves the balance unchanged.

### `OrderService` loads `User` from `UserRepository`

`OrderService` already has `userId` (from the JWT principal). It calls
`userRepository.findById(userId)` to get the `User` entity, mutates
`giftPoints`, and saves it. This adds `UserRepository` as a new constructor
dependency of `OrderService`.

### Validation order in `placeOrder` (extended)

After the existing checks (year, basket, address, card decline), two new
checks are inserted before stock validation:

```
5a. if giftPointsToRedeem > user.giftPoints → throw InsufficientGiftPointsException
5b. if giftPointsToRedeem > basketTotal (integer vs BigDecimal comparison) → throw GiftPointsExceedBasketTotalException
```

### `giftPointsToRedeem` compared to `basketTotal` as integer

`giftPointsToRedeem` is an `int` (each point = ₹1). `basketTotal` is a
`BigDecimal`. The comparison uses:

```java
new BigDecimal(req.getGiftPointsToRedeem()).compareTo(basket.getBasketTotal()) > 0
```

### `pointsAwarded` computed with integer floor

```java
int pointsAwarded = basket.getBasketTotal()
    .add(deliveryCharge)
    .subtract(new BigDecimal(req.getGiftPointsToRedeem()))
    .multiply(new BigDecimal("0.05"))
    .setScale(0, RoundingMode.FLOOR)
    .intValue();
```

### `GET /api/users/me/gift-points` served by a new `UserController`

A new `UserController` handles user-profile endpoints. It has a single
method in FEAT-09. Adding this endpoint to `OrderController` or
`AuthController` would violate separation of concerns.

### `GiftPointsResponse` DTO — one field

```java
public class GiftPointsResponse {
    private int giftPoints;
}
```

### No `SecurityConfig` change needed

`/api/users/**` is not listed under `permitAll`. It falls under
`anyRequest().authenticated()` — correct for FEAT-09.

### No new Maven dependencies

`RoundingMode` is `java.math.RoundingMode` (JDK). Nothing else is new.

---

## 3. New Files

| Layer | File | Purpose |
|---|---|---|
| controller | `UserController.java` | `GET /api/users/me/gift-points` |
| dto | `GiftPointsResponse.java` | `{ "giftPoints": 120 }` response body |
| exception | `InsufficientGiftPointsException.java` | → 400 `"Insufficient gift points"` |
| exception | `GiftPointsExceedBasketTotalException.java` | → 400 `"Gift points exceed basket total"` |

---

## 4. Modified Files

| File | Change |
|---|---|
| `entity/User.java` | Add `giftPoints` field — `int`, `@Column(name="gift_points", nullable=false)`, default `0` |
| `entity/Order.java` | Add `giftPointsRedeemed` and `pointsAwarded` fields |
| `dto/OrderResponse.java` | Add `giftPointsRedeemed` (int) and `pointsAwarded` (int) fields |
| `service/OrderService.java` | Add `UserRepository` dependency; extend `placeOrder` with gift point validation, deduction, award; extend `toResponse` to populate new fields |
| `exception/GlobalExceptionHandler.java` | Add handlers for `InsufficientGiftPointsException` (400) and `GiftPointsExceedBasketTotalException` (400) |

---

## 5. Step-by-Step Implementation Order

### Step 1 — Extend `User` entity

Add to `User.java`:
```java
@Column(name = "gift_points", nullable = false)
private int giftPoints = 0;
```
Plus getter `getGiftPoints()` and setter `setGiftPoints(int giftPoints)`.

---

### Step 2 — Extend `Order` entity

Add two fields to `Order.java`:
```java
@Column(name = "gift_points_redeemed", nullable = false)
private int giftPointsRedeemed = 0;

@Column(name = "points_awarded", nullable = false)
private int pointsAwarded = 0;
```
Plus getters and setters for both.

---

### Step 3 — New exceptions

**`InsufficientGiftPointsException`**
- Message: `"Insufficient gift points"`
- Maps to → **400 Bad Request**

**`GiftPointsExceedBasketTotalException`**
- Message: `"Gift points exceed basket total"`
- Maps to → **400 Bad Request**

---

### Step 4 — New DTO

**`GiftPointsResponse`** — fields: `giftPoints` (int)
No-arg constructor + getter/setter.

---

### Step 5 — Extend `OrderResponse` DTO

Add two new fields:
- `giftPointsRedeemed` (int)
- `pointsAwarded` (int)

Plus getters and setters for both.

---

### Step 6 — Extend `OrderService`

Add `UserRepository` as a constructor dependency (5th parameter).

Extend `placeOrder(Long userId, PaymentRequest req)`:

```
[after existing Step 4 — card decline check]

Step 5a — load user
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + userId))

Step 5b — gift point validation
    if req.getGiftPointsToRedeem() > user.getGiftPoints():
        throw new InsufficientGiftPointsException()
    if new BigDecimal(req.getGiftPointsToRedeem()).compareTo(basket.getBasketTotal()) > 0:
        throw new GiftPointsExceedBasketTotalException()

[Step 5 — compute charges, now extended]
    BigDecimal giftDiscount = new BigDecimal(req.getGiftPointsToRedeem())
    BigDecimal totalAmount  = basketTotal + deliveryCharge − giftDiscount
    int pointsAwarded = totalAmount
            .multiply(new BigDecimal("0.05"))
            .setScale(0, RoundingMode.FLOOR)
            .intValue()

[before Step 9 — save order, set new fields]
    order.setGiftPointsRedeemed(req.getGiftPointsToRedeem())
    order.setPointsAwarded(pointsAwarded)

[after Step 9 — order saved, after Step 10 — basket cleared]

Step 11 — mutate user balance
    user.setGiftPoints(user.getGiftPoints() - req.getGiftPointsToRedeem() + pointsAwarded)
    userRepository.save(user)
```

Extend `toResponse(Order order)` to populate:
```
response.setGiftPointsRedeemed(order.getGiftPointsRedeemed())
response.setPointsAwarded(order.getPointsAwarded())
```

---

### Step 7 — New `UserController`

```
@RestController
@RequestMapping("/api/users")
Constructor-injected: UserRepository userRepository
```

| Method | HTTP | Path | Status |
|---|---|---|---|
| `getGiftPoints` | GET | `/api/users/me/gift-points` | 200 |

```java
@GetMapping("/me/gift-points")
public GiftPointsResponse getGiftPoints(Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    // Principal is the User object loaded by JwtAuthFilter — already has latest giftPoints
    // But we reload from DB to guarantee freshness
    User fresh = userRepository.findById(user.getId())
            .orElseThrow(() -> new IllegalStateException("User not found"));
    GiftPointsResponse response = new GiftPointsResponse();
    response.setGiftPoints(fresh.getGiftPoints());
    return response;
}
```

---

### Step 8 — `GlobalExceptionHandler` additions

Two new `@ExceptionHandler` methods in a `// FEAT-09 handlers` section:

```
InsufficientGiftPointsException          → 400 Bad Request
GiftPointsExceedBasketTotalException     → 400 Bad Request
```

---

## 6. No New Dependencies

| Type | Source |
|---|---|
| `RoundingMode` | `java.math` (JDK) |
| `UserRepository` | already on classpath |

---

## 7. Test Plan

### 7.1 Controller Tests — `@WebMvcTest`

**`UserControllerTest`**

| Test | Expected |
|---|---|
| `getGiftPoints_returns200` | 200 + `$.giftPoints == 120` |
| `getGiftPoints_returns401_noJwt` | 401 |

**`OrderControllerTest` — additions**

| Test | Expected |
|---|---|
| `placeOrder_returns400_insufficientGiftPoints` | service throws `InsufficientGiftPointsException` → 400 |
| `placeOrder_returns400_giftPointsExceedBasket` | service throws `GiftPointsExceedBasketTotalException` → 400 |

---

### 7.2 Service Tests — `@ExtendWith(MockitoExtension.class)`

**`OrderServiceTest` — additions**

| Test | What it verifies |
|---|---|
| `placeOrder_giftPoints_deductedAndAwarded` | balance reduced by redeemed, increased by awarded; `totalAmount` reduced |
| `placeOrder_giftPoints_totalAmountReduced` | `totalAmount = basketTotal + deliveryCharge − giftPointsToRedeem` |
| `placeOrder_giftPoints_pointsAwardedOnReducedTotal` | `pointsAwarded = floor(totalAmount × 0.05)` |
| `placeOrder_insufficientGiftPoints_throws` | balance < redeemed → `InsufficientGiftPointsException` |
| `placeOrder_giftPointsExceedBasket_throws` | redeemed > basketTotal → `GiftPointsExceedBasketTotalException` |
| `placeOrder_zeroGiftPoints_awardsPointsOnly` | redeem 0, balance increases by `floor(totalAmount × 0.05)` |

---

## 8. Acceptance Criteria Traceability

| AC | Criterion (summary) | Covered by |
|----|---------------------|-----------|
| AC-01 | No JWT → 401 | `UserControllerTest`: `getGiftPoints_returns401_noJwt` |
| AC-02 | Balance returned correctly | `UserControllerTest`: `getGiftPoints_returns200` |
| AC-03 | Zero redemption → points awarded | `OrderServiceTest`: `placeOrder_zeroGiftPoints_awardsPointsOnly` |
| AC-04 | Redemption → deduct then award | `OrderServiceTest`: `placeOrder_giftPoints_deductedAndAwarded` |
| AC-05 | Redeem > balance → 400 | `OrderServiceTest`: `placeOrder_insufficientGiftPoints_throws`; `OrderControllerTest`: `placeOrder_returns400_insufficientGiftPoints` |
| AC-06 | Redeem > basketTotal → 400 | `OrderServiceTest`: `placeOrder_giftPointsExceedBasket_throws`; `OrderControllerTest`: `placeOrder_returns400_giftPointsExceedBasket` |
| AC-07 | `giftPointsRedeemed` in response | `OrderServiceTest`: `placeOrder_giftPoints_deductedAndAwarded` |
| AC-08 | `pointsAwarded` = floor(total × 0.05) | `OrderServiceTest`: `placeOrder_giftPoints_pointsAwardedOnReducedTotal` |
| AC-09 | `totalAmount` reduced by redemption | `OrderServiceTest`: `placeOrder_giftPoints_totalAmountReduced` |
| AC-10 | Zero redemption unchanged total | `OrderServiceTest`: `placeOrder_zeroGiftPoints_awardsPointsOnly` |
| AC-11 | Fields persisted on Order | `OrderServiceTest`: `placeOrder_giftPoints_deductedAndAwarded` |
| AC-12 | New user balance = 0 | Covered by `User.giftPoints` default value — no separate test needed |
| AC-13 | Consecutive orders accumulate correctly | `OrderServiceTest`: `placeOrder_zeroGiftPoints_awardsPointsOnly` (first order); `placeOrder_giftPoints_deductedAndAwarded` (subsequent) |
| AC-14 | Declined card — no point change | `OrderServiceTest`: existing `placeOrder_cardDeclined_throws` (userRepository.save never called) |
| AC-15 | `floor` semantics | `OrderServiceTest`: `placeOrder_giftPoints_pointsAwardedOnReducedTotal` (199 total → 9 points) |
