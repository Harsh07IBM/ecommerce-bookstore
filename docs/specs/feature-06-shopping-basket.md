# Feature Specification: FEAT-06 — Shopping Basket

## 1. Overview

Allow users (authenticated and guest) to maintain a shopping basket of books
they intend to purchase. The basket is the entry point to the checkout flow
(FEAT-07). Authenticated users have a persistent server-side basket. Guests
have a session-scoped basket that is discarded on logout / session end.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | Any visitor (authenticated or guest) may add books to the basket. |
| BR-02 | A guest basket is server-side and identified by an anonymous session cookie. It does NOT carry over when the guest logs in. |
| BR-03 | Maximum quantity of a single book in the basket is **7**. |
| BR-04 | Adding a book that is out of stock (stock = 0) returns 400 Bad Request with message `"This book is currently out of stock"`. |
| BR-05 | Adding a book whose resulting quantity would exceed 7 returns 400 Bad Request with message `"Maximum quantity per book is 7"`. |
| BR-06 | Removing an item that does not exist in the basket returns 404 Not Found. |
| BR-07 | Setting quantity to 0 via the update endpoint removes the item from the basket. |
| BR-08 | The basket summary includes each item's current unit price and a computed line total; the overall basket total is also returned. |
| BR-09 | Guest baskets are not linked to any User record and are not visible across devices/browsers. |
| BR-10 | Checkout / payment is out of scope for this feature (covered in FEAT-07). |

---

## 3. Actors

- **Guest** — unauthenticated visitor with an active HTTP session.
- **Authenticated User** — user who has completed login (JWT present).

---

## 4. REST API Contract

### 4.1 Get Basket

```
GET /api/basket
```

- **Auth:** optional (JWT if present identifies the user; otherwise session identifies the guest).
- **Response 200:**
```json
{
  "items": [
    {
      "bookId": 12,
      "title": "Clean Code",
      "author": "Robert C. Martin",
      "coverImageUrl": "https://...",
      "unitPrice": 29.99,
      "quantity": 2,
      "lineTotal": 59.98
    }
  ],
  "totalItems": 2,
  "basketTotal": 59.98
}
```
- Returns an empty basket (`items: [], totalItems: 0, basketTotal: 0.00`) if nothing has been added yet.

---

### 4.2 Add Item to Basket

```
POST /api/basket/items
```

- **Auth:** optional.
- **Request body:**
```json
{ "bookId": 12, "quantity": 1 }
```
- `quantity` must be between 1 and 7 (inclusive). Defaults to 1 if omitted.
- **Response 200:** full basket (same shape as GET /api/basket).
- **Response 400:** book out of stock OR resulting quantity > 7 OR quantity < 1.
- **Response 404:** book not found.

---

### 4.3 Update Item Quantity

```
PUT /api/basket/items/{bookId}
```

- **Auth:** optional.
- **Request body:**
```json
{ "quantity": 3 }
```
- If `quantity` = 0, the item is removed. If `quantity` > 7, returns 400.
- **Response 200:** full basket.
- **Response 404:** item not found in basket.

---

### 4.4 Remove Item from Basket

```
DELETE /api/basket/items/{bookId}
```

- **Auth:** optional.
- **Response 200:** full basket (after removal).
- **Response 404:** item not found in basket.

---

### 4.5 Clear Basket

```
DELETE /api/basket
```

- **Auth:** optional.
- **Response 200:** empty basket.

---

## 5. Session Strategy

- Spring Session with HTTP session cookies is used to maintain guest baskets.
- For authenticated users, the basket is stored in the DB keyed by `userId`.
- For guests, the basket is stored in the DB keyed by `sessionId` (HTTP session ID).
- The session cookie is `HttpOnly`, `SameSite=Lax`. No explicit `Secure` flag in dev (HTTP), but must be `Secure` in production.

---

## 6. Out of Scope

- Payment / checkout (FEAT-07).
- Wishlist / save-for-later.
- Merging guest basket into user basket on login.
- Basket expiry / TTL cleanup jobs.
- Stock reservation / hold at basket-add time (stock is only checked at add time, not decremented until checkout).

---

## 7. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `GET /api/basket` returns 200 with an empty basket for a new session. |
| AC-02 | `POST /api/basket/items` with a valid book and quantity 1 adds the item and returns the basket. |
| AC-03 | Adding the same book again increments its quantity. |
| AC-04 | Adding a book with stock = 0 returns 400 with message `"This book is currently out of stock"`. |
| AC-05 | Adding a book such that total quantity exceeds 7 returns 400 with message `"Maximum quantity per book is 7"`. |
| AC-06 | `PUT /api/basket/items/{bookId}` with quantity 0 removes the item. |
| AC-07 | `DELETE /api/basket/items/{bookId}` removes the item; returns 404 if it was not in the basket. |
| AC-08 | `DELETE /api/basket` empties the basket. |
| AC-09 | Basket total = sum of all line totals (unitPrice × quantity). |
| AC-10 | An authenticated user's basket persists across requests (same JWT). |
| AC-11 | A guest basket is isolated to one HTTP session and not visible to other sessions. |
