# Feature Specification: FEAT-11 — Buy Again

## 1. Overview

Allow authenticated users to re-add all items from a previous order into their
current basket in a single action. The operation reuses the existing basket
(`POST /api/basket/items`) logic — it respects all existing basket rules
(stock, max quantity) for each item added.

**Dependencies:**
- **FEAT-04** (User / JWT auth) — requires authentication.
- **FEAT-06** (Shopping Basket) — items are added to the basket.
- **FEAT-10** (Order History) — the source order must belong to the user.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | The endpoint requires a valid JWT. Requests without one return 401 Unauthorized. |
| BR-02 | The order identified by `orderId` must exist. If not, return 404 Not Found. |
| BR-03 | The order must belong to the authenticated user. If it belongs to another user, return 403 Forbidden. |
| BR-04 | Each item from the order is added to the current basket by incrementing quantity, exactly as if the user had called `POST /api/basket/items` for each item. |
| BR-05 | If a book from the order is out of stock (`stockQuantity == 0`), that item is silently skipped — it does not block the other items from being added. |
| BR-06 | If adding an item would push its basket quantity above 7, that item is silently skipped — it does not block the other items. |
| BR-07 | If a book from the order no longer exists in the catalogue, that item is silently skipped. |
| BR-08 | After processing all items, the updated basket is returned as the response, even if some items were skipped. |
| BR-09 | If every item was skipped (e.g. all books are out of stock), the response is still 200 with the unchanged basket — no error is returned. |

---

## 3. Actors

- **Authenticated User** — the only actor.

---

## 4. REST API Contract

### 4.1 Buy Again

```
POST /api/orders/{id}/buy-again
```

- **Auth:** required (JWT).
- **Request body:** none.
- **Response 200 — basket after adding available items:**
```json
{
  "items": [
    {
      "bookId": 12,
      "title": "Clean Code",
      "author": "Robert C. Martin",
      "coverImageUrl": "https://...",
      "unitPrice": 299.00,
      "quantity": 2,
      "lineTotal": 598.00
    }
  ],
  "totalItems": 2,
  "basketTotal": 598.00
}
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

None. This feature only reads from `orders`/`order_item` and writes to `basket`/`basket_item`.

---

## 6. Out of Scope

- Item-level "buy again" (adding a single item from an order).
- Clearing the basket before adding (items are always incremented on top of existing basket contents).
- Reporting which items were skipped and why.

---

## 7. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `POST /api/orders/{id}/buy-again` without a JWT returns 401. |
| AC-02 | `POST /api/orders/{id}/buy-again` with a non-existent order ID returns 404. |
| AC-03 | `POST /api/orders/{id}/buy-again` with an order belonging to another user returns 403. |
| AC-04 | `POST /api/orders/{id}/buy-again` with a valid order returns 200 with the updated basket. |
| AC-05 | In-stock items from the order are added to the basket (quantity incremented). |
| AC-06 | Items whose book has `stockQuantity == 0` are skipped without error. |
| AC-07 | Items whose book no longer exists in the catalogue are skipped without error. |
| AC-08 | Items that would push basket quantity above 7 are skipped without error. |
| AC-09 | If all items are skipped, the response is 200 with the unchanged basket. |
| AC-10 | The response is a `BasketResponse` (same shape as all other basket endpoints). |
