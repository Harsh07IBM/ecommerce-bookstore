# Feature Specification: FEAT-13 — Purchase Confirmation

## 1. Overview

Provide a dedicated endpoint that returns the confirmation data for a
successfully placed order. This powers the "order confirmed" screen shown
immediately after payment succeeds.

The data is a superset of the `POST /api/orders` 201 response — it adds a
`confirmationMessage` field so the frontend has a ready-to-display success
string without hard-coding it.

All confirmation data already exists in the `orders` table from FEAT-08/09.
This feature is a thin read endpoint with ownership check — no new writes,
no schema changes.

**Dependencies:**
- **FEAT-04** (JWT auth) — requires authentication.
- **FEAT-08/09** (Payment + Gift Points) — order data source.
- **FEAT-10** (Order History) — reuses `OrderNotFoundException` /
  `OrderAccessForbiddenException`.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | The endpoint requires a valid JWT. Requests without one return 401. |
| BR-02 | The order must exist. If not, return 404. |
| BR-03 | The order must belong to the authenticated user. If not, return 403. |
| BR-04 | Any order status (`PAID` or `CANCELLED`) may be retrieved — the confirmation screen is accessible regardless of whether the order was later cancelled. |
| BR-05 | The response includes a fixed `confirmationMessage`: `"Your order has been placed successfully!"`. |

---

## 3. REST API Contract

### 3.1 Get Purchase Confirmation

```
GET /api/orders/{id}/confirmation
```

- **Auth:** required (JWT).
- **Response 200:**
```json
{
  "confirmationMessage": "Your order has been placed successfully!",
  "orderId": 42,
  "status": "PAID",
  "orderDate": "2025-08-21T14:30:00",
  "items": [
    {
      "bookId": 12,
      "title": "Clean Code",
      "quantity": 2,
      "unitPrice": 299.00,
      "lineTotal": 598.00
    }
  ],
  "basketTotal": 598.00,
  "deliveryCharge": 0.00,
  "giftPointsRedeemed": 0,
  "totalAmount": 598.00,
  "pointsAwarded": 29,
  "estimatedDeliveryDate": "2025-08-24",
  "deliveryAddress": {
    "recipientName": "Priya Sharma",
    "phoneNumber": "9876543210",
    "line1": "12 MG Road",
    "line2": null,
    "city": "Bengaluru",
    "state": "Karnataka",
    "pincode": "560001"
  }
}
```

- **Response 401:** `{ "error": "Unauthorized" }`
- **Response 403:** `{ "error": "Forbidden" }`
- **Response 404:** `{ "error": "Order not found" }`

---

## 4. Data Model Changes

None.

---

## 5. Out of Scope

- Email confirmation (separate notification feature).
- PDF receipt generation.
- Confirmation for guest orders.

---

## 6. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `GET /api/orders/{id}/confirmation` without a JWT returns 401. |
| AC-02 | `GET /api/orders/{id}/confirmation` for a non-existent order returns 404. |
| AC-03 | `GET /api/orders/{id}/confirmation` for another user's order returns 403. |
| AC-04 | `GET /api/orders/{id}/confirmation` for the user's own order returns 200. |
| AC-05 | The response includes `confirmationMessage = "Your order has been placed successfully!"`. |
| AC-06 | The response includes all standard order fields (orderId, status, items, totals, deliveryAddress, estimatedDeliveryDate). |
