# Feature Specification: FEAT-10 — Order Management & History

## 1. Overview

Allow authenticated users to view their past orders. Two read-only endpoints
are provided: a list endpoint returning all orders in reverse-chronological
order, and a detail endpoint returning a single order by ID.

All order data already exists in the database from FEAT-08 (payment). This
feature purely exposes it through the API — no new writes, no status
transitions, no mutations of any kind.

**Dependencies:**
- **FEAT-04** (User / JWT auth) — all endpoints require authentication.
- **FEAT-08** (Payment) — orders are created there; read here.

**Blocks:**
- **FEAT-11** (Buy Again) — reads order items from history.
- **FEAT-12** (Order Cancellation) — changes order status.
- **FEAT-14** (Recommendations) — uses order history for suggestions.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | All endpoints require a valid JWT. Requests without a valid JWT return 401 Unauthorized. |
| BR-02 | A user may only retrieve their own orders. Requesting an order that belongs to another user returns 403 Forbidden. |
| BR-03 | Requesting an order that does not exist returns 404 Not Found. |
| BR-04 | `GET /api/orders` returns all orders belonging to the authenticated user, sorted by `orderDate` descending (most recent first). |
| BR-05 | `GET /api/orders/{id}` returns the full detail of a single order including all items and the delivery address snapshot. |
| BR-06 | The response shape for both endpoints mirrors the 201 response from `POST /api/orders` (FEAT-08/09), ensuring a consistent order object across the API. |
| BR-07 | No pagination is required for the order list in this feature. All orders are returned in a single response. |

---

## 3. Actors

- **Authenticated User** — the only actor. Guest access is not supported.

---

## 4. REST API Contract

### 4.1 List Orders

```
GET /api/orders
```

- **Auth:** required (JWT).
- **Response 200:**
```json
[
  {
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
      "line2": "Apt 4B",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560001"
    }
  }
]
```

Returns an empty array `[]` if the user has no orders.

- **Response 401 — No valid JWT:**
```json
{ "error": "Unauthorized" }
```

---

### 4.2 Get Order Detail

```
GET /api/orders/{id}
```

- **Auth:** required (JWT).
- **Response 200** — same shape as a single element from the list above.
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

None. All data is already present from FEAT-08/09:
- `orders` table — exists
- `order_item` table — exists
- `OrderRepository.findAllByUserId` — already declared in FEAT-08

---

## 6. Out of Scope

- Order status transitions (Placed → Shipped → Delivered).
- Pagination of the order list.
- Filtering or sorting by parameters other than the default (date descending).
- Admin endpoints to view any user's orders.
- Order cancellation (FEAT-12).

---

## 7. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `GET /api/orders` without a JWT returns 401. |
| AC-02 | `GET /api/orders` with a valid JWT returns 200 with an array of the user's orders, most recent first. |
| AC-03 | `GET /api/orders` returns `[]` when the user has no orders. |
| AC-04 | `GET /api/orders` does not return orders belonging to other users. |
| AC-05 | `GET /api/orders/{id}` without a JWT returns 401. |
| AC-06 | `GET /api/orders/{id}` for a non-existent order returns 404. |
| AC-07 | `GET /api/orders/{id}` for an order belonging to another user returns 403. |
| AC-08 | `GET /api/orders/{id}` for the user's own order returns 200 with full order detail including items and delivery address. |
| AC-09 | The response for both endpoints includes all fields from the `POST /api/orders` 201 response: `orderId`, `status`, `orderDate`, `items`, `basketTotal`, `deliveryCharge`, `giftPointsRedeemed`, `totalAmount`, `pointsAwarded`, `estimatedDeliveryDate`, `deliveryAddress`. |
| AC-10 | Orders in the list are sorted by `orderDate` descending. |
