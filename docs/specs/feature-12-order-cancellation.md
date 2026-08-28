# Feature Specification: FEAT-12 — Order Cancellation

## 1. Overview

Allow authenticated users to cancel a `PAID` order within 48 hours of placing
it. On cancellation the order status is set to `CANCELLED`, each book's stock
quantity is restored, and the order record is updated. Redeemed gift points are
not refunded. No payment refund is processed.

**Dependencies:**
- **FEAT-04** (User / JWT auth) — requires authentication.
- **FEAT-08** (Payment) — orders created here are cancelled here.
- **FEAT-09** (Gift Points) — redeemed points are not restored on cancellation.
- **FEAT-10** (Order History) — order status is visible in history.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | The endpoint requires a valid JWT. Requests without one return 401 Unauthorized. |
| BR-02 | The order must exist. If not, return 404 Not Found. |
| BR-03 | The order must belong to the authenticated user. If not, return 403 Forbidden. |
| BR-04 | Only orders with status `PAID` can be cancelled. Any other status returns 400 Bad Request with message `"Order cannot be cancelled"`. |
| BR-05 | The order must have been placed within the last 48 hours (measured from `orderDate`). If more than 48 hours have passed, return 400 Bad Request with message `"Cancellation window has expired"`. |
| BR-06 | On successful cancellation, the order status is set to `CANCELLED`. |
| BR-07 | On successful cancellation, each ordered book's `stockQuantity` is incremented by the ordered quantity (stock restoration). |
| BR-08 | Redeemed gift points (`giftPointsRedeemed`) are **not** restored to the user's balance on cancellation. |
| BR-09 | Points awarded (`pointsAwarded`) for the cancelled order are **not** deducted from the user's balance. The user keeps any points they earned. |
| BR-10 | No payment refund is processed — cancellation only changes status and restores stock. |
| BR-11 | The cancellation is atomic: status update and all stock restorations occur within a single `@Transactional` method. |

---

## 3. Actors

- **Authenticated User** — the only actor.

---

## 4. REST API Contract

### 4.1 Cancel Order

```
POST /api/orders/{id}/cancel
```

- **Auth:** required (JWT).
- **Request body:** none.
- **Response 200 — cancellation successful:**
```json
{
  "orderId": 42,
  "status": "CANCELLED",
  "orderDate": "2025-08-21T14:30:00",
  "items": [...],
  "basketTotal": 598.00,
  "deliveryCharge": 0.00,
  "giftPointsRedeemed": 0,
  "totalAmount": 598.00,
  "pointsAwarded": 29,
  "estimatedDeliveryDate": "2025-08-24",
  "deliveryAddress": {...}
}
```

The response is the updated `OrderResponse` with `status = "CANCELLED"`.

- **Response 400 — wrong status:**
```json
{ "error": "Order cannot be cancelled" }
```

- **Response 400 — window expired:**
```json
{ "error": "Cancellation window has expired" }
```

- **Response 401 — No valid JWT:**
```json
{ "error": "Unauthorized" }
```

- **Response 403 — Order belongs to another user:**
```json
{ "error": "Forbidden" }
```

- **Response 404 — Order not found:**
```json
{ "error": "Order not found" }
```

---

## 5. Data Model Changes

None. `Order.status` already supports `CANCELLED` (defined in `OrderStatus` enum since FEAT-08).

---

## 6. Out of Scope

- Payment refunds.
- Gift point restoration on cancellation.
- Admin-initiated cancellation.
- Cancellation of orders in any status other than `PAID`.

---

## 7. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `POST /api/orders/{id}/cancel` without a JWT returns 401. |
| AC-02 | `POST /api/orders/{id}/cancel` with a non-existent order ID returns 404. |
| AC-03 | `POST /api/orders/{id}/cancel` with an order belonging to another user returns 403. |
| AC-04 | `POST /api/orders/{id}/cancel` on an order with status other than `PAID` returns 400 with message `"Order cannot be cancelled"`. |
| AC-05 | `POST /api/orders/{id}/cancel` on an order placed more than 48 hours ago returns 400 with message `"Cancellation window has expired"`. |
| AC-06 | `POST /api/orders/{id}/cancel` on a valid `PAID` order within 48 hours returns 200 with `status = "CANCELLED"`. |
| AC-07 | After successful cancellation each book's `stockQuantity` is incremented by its ordered quantity. |
| AC-08 | After successful cancellation the user's gift point balance is unchanged. |
| AC-09 | The cancelled order remains visible in order history (`GET /api/orders`) with status `CANCELLED`. |
