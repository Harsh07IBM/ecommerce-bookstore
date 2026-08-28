# Feature Specification: FEAT-09 — Gift Points Redemption

## 1. Overview

Gift points are a loyalty currency earned automatically on every successful
order. Each point is worth ₹1 and can be applied against the total of a future
order, reducing the amount due. Points never expire and are never refunded after
a cancellation.

This feature builds directly on FEAT-08 (Payment): the `giftPointsToRedeem`
field already accepted in `POST /api/orders` is wired up here to actually
reduce the order total. In addition, a new endpoint exposes the user's current
point balance, and the order creation flow is extended to both apply redemptions
and award new points.

**Dependencies:**
- **FEAT-04** (User / JWT auth) — all gift point operations require an
  authenticated session.
- **FEAT-08** (Payment) — gift points are earned at payment time and the
  `giftPointsToRedeem` request field introduced in FEAT-08 is activated here.

**Blocks:**
- **FEAT-12** (Order Cancellation) — redeemed points are forfeited on
  cancellation (no refund), which must be respected there.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | Every successful order awards the user gift points equal to `floor(totalAmount × 0.05)`. Points are awarded after the order is persisted and the basket is cleared. |
| BR-02 | 1 gift point = ₹1. Points are always whole integers; fractional points are not possible. |
| BR-03 | A user's gift point balance is stored as a non-negative integer on the `User` entity. It starts at 0 on registration. |
| BR-04 | `giftPointsToRedeem` in `POST /api/orders` must be ≥ 0 (already enforced by Bean Validation from FEAT-08). |
| BR-05 | `giftPointsToRedeem` must not exceed the user's current balance. If it does, return 400 Bad Request with message `"Insufficient gift points"`. |
| BR-06 | `giftPointsToRedeem` must not exceed the basket total (you cannot redeem more points than the cost of the basket items, making `totalAmount` go negative). If it does, return 400 Bad Request with message `"Gift points exceed basket total"`. |
| BR-07 | When gift points are redeemed, the `totalAmount` is recalculated as: `basketTotal + deliveryCharge − giftPointsToRedeem`. The minimum `totalAmount` is ₹0.00. |
| BR-08 | On a successful order where `giftPointsToRedeem > 0`, the user's balance is reduced by exactly `giftPointsToRedeem` before points are awarded for the new order. |
| BR-09 | Points are awarded for the new order based on the final `totalAmount` (after gift point deduction): `pointsAwarded = floor(totalAmount × 0.05)`. |
| BR-10 | The `giftPointsRedeemed` and `pointsAwarded` values are stored on the `Order` row for order history display. |
| BR-11 | Gift points are never refunded after order cancellation (FEAT-12 will not restore points). |
| BR-12 | Points do not expire. |
| BR-13 | There is no per-order cap on redemption — a user may redeem their entire balance in one order, subject to BR-06. |
| BR-14 | The `GET /api/users/me/gift-points` endpoint returns the authenticated user's current point balance. |
| BR-15 | All gift point mutations (deduct redeemed points, award new points) happen within the same `@Transactional` method as the order creation, so a rollback leaves the balance unchanged. |

---

## 3. Actors

- **Authenticated User** — the only actor. Guests cannot earn or redeem points.

---

## 4. REST API Contract

### 4.1 Get Gift Point Balance

```
GET /api/users/me/gift-points
```

- **Auth:** required (JWT).
- **Response 200:**
```json
{
  "giftPoints": 120
}
```

- **Response 401 — No valid JWT:**
```json
{ "error": "Unauthorized" }
```

---

### 4.2 Place Order (extended from FEAT-08)

```
POST /api/orders
```

The request and response shapes are unchanged from FEAT-08. The
`giftPointsToRedeem` field is now fully active.

- **Request body (unchanged from FEAT-08):**
```json
{
  "addressId": 1,
  "cardNumber": "4111111111111111",
  "expiryMonth": 12,
  "expiryYear": 2027,
  "cvv": "123",
  "cardholderName": "Priya Sharma",
  "giftPointsToRedeem": 50
}
```

- **Response 201 — extended fields:**
```json
{
  "orderId": 42,
  "status": "PAID",
  "orderDate": "2025-08-21T14:30:00",
  "items": [...],
  "basketTotal": 598.00,
  "deliveryCharge": 0.00,
  "giftPointsRedeemed": 50,
  "totalAmount": 548.00,
  "pointsAwarded": 27,
  "estimatedDeliveryDate": "2025-08-24",
  "deliveryAddress": {...}
}
```

New fields in the response:

| Field | Type | Notes |
|---|---|---|
| `giftPointsRedeemed` | `int` | Points deducted from balance for this order. 0 if none redeemed. |
| `pointsAwarded` | `int` | Points credited to balance for this order. `floor(totalAmount × 0.05)`. |

- **Response 400 — Insufficient gift points:**
```json
{ "error": "Insufficient gift points" }
```

- **Response 400 — Gift points exceed basket total:**
```json
{ "error": "Gift points exceed basket total" }
```

---

## 5. Data Model Changes

### 5.1 `User` entity — new field

| Field | Column | Type | Nullable | Notes |
|---|---|---|---|---|
| `giftPoints` | `gift_points` | `int` | No | Default 0. Non-negative. |

### 5.2 `Order` entity — two new fields

| Field | Column | Type | Nullable | Notes |
|---|---|---|---|---|
| `giftPointsRedeemed` | `gift_points_redeemed` | `int` | No | Default 0. |
| `pointsAwarded` | `points_awarded` | `int` | No | `floor(totalAmount × 0.05)`. |

### 5.3 No new tables

Gift points are stored on the `User` row. No separate points ledger table is
needed in FEAT-09.

---

## 6. Order Total Calculation (updated from FEAT-08)

```
basketTotal        = sum of all line totals
deliveryCharge     = 0 if basketTotal >= 500, else 50
giftDiscount       = giftPointsToRedeem (each point = ₹1)
totalAmount        = basketTotal + deliveryCharge − giftDiscount
pointsAwarded      = floor(totalAmount × 0.05)
```

---

## 7. Out of Scope

- A points transaction ledger / history (only the current balance is stored).
- Points earned for actions other than purchases (referrals, sign-up bonuses, etc.).
- Partial refund of points on cancellation.
- Points expiry.
- Transferring points between users.
- A separate admin endpoint to manually adjust balances.

---

## 8. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `GET /api/users/me/gift-points` without a JWT returns 401. |
| AC-02 | `GET /api/users/me/gift-points` with a valid JWT returns 200 with `giftPoints` equal to the user's current balance. |
| AC-03 | After a successful `POST /api/orders` with `giftPointsToRedeem = 0`, the user's balance increases by `floor(totalAmount × 0.05)`. |
| AC-04 | After a successful `POST /api/orders` with `giftPointsToRedeem > 0`, the user's balance is reduced by `giftPointsToRedeem` and then increased by `floor(newTotalAmount × 0.05)`. |
| AC-05 | `POST /api/orders` with `giftPointsToRedeem` greater than the user's current balance returns 400 with message `"Insufficient gift points"`. |
| AC-06 | `POST /api/orders` with `giftPointsToRedeem` greater than `basketTotal` returns 400 with message `"Gift points exceed basket total"`. |
| AC-07 | The 201 response includes `giftPointsRedeemed` equal to the value supplied in the request. |
| AC-08 | The 201 response includes `pointsAwarded` equal to `floor(totalAmount × 0.05)`. |
| AC-09 | The 201 response `totalAmount` equals `basketTotal + deliveryCharge − giftPointsToRedeem`. |
| AC-10 | When `giftPointsToRedeem = 0`, `totalAmount` is unchanged from FEAT-08 behaviour. |
| AC-11 | `giftPointsRedeemed` and `pointsAwarded` are persisted on the `Order` row. |
| AC-12 | A new user's gift point balance is 0. |
| AC-13 | Two consecutive successful orders each correctly accumulate points (second order's starting balance = first order's awarded points). |
| AC-14 | The declined-card path (card `0000000000000000`) does not award or deduct any points. |
| AC-15 | `pointsAwarded` uses `floor` — an order with `totalAmount = ₹199` awards 9 points (not 10). |
