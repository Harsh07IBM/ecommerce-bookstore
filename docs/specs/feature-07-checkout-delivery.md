# Feature Specification: FEAT-07 — Checkout & Delivery Address

## 1. Overview

Allow authenticated users to manage saved delivery addresses on their profile
and select an address at checkout. The checkout summary endpoint combines the
user's basket (FEAT-06) with a chosen address, the calculated delivery charge,
and an estimated delivery date, producing a single response that the payment
step (FEAT-08) will consume.

Guest checkout is explicitly **not** supported — an authenticated session (JWT)
is required for all endpoints in this feature.

**Dependencies:**
- **FEAT-04** (User / JWT auth) — addresses belong to a user account.
- **FEAT-06** (Shopping Basket) — checkout summary reads the basket total to
  determine the delivery charge.

**Blocks:**
- **FEAT-08** (Payment) — the payment step receives the chosen delivery address
  and the confirmed delivery charge from this feature.

---

## 2. Business Rules

| #     | Rule |
|-------|------|
| BR-01 | All address and checkout endpoints require a valid JWT. Requests without a valid JWT return 401 Unauthorized. |
| BR-02 | A `DeliveryAddress` record is always owned by the authenticated user derived from the JWT. A user may not read, update, or delete another user's address. Attempts return 403 Forbidden. |
| BR-03 | A user may save multiple delivery addresses to their profile. |
| BR-04 | Exactly one address per user may be marked `isDefault = true`. When a new address is saved with `isDefault = true`, any previously default address for that user is automatically set to `isDefault = false`. |
| BR-05 | All address fields except `line2` are mandatory. `line2` is nullable. |
| BR-06 | `pincode` must be exactly 6 numeric digits. Requests with an invalid pincode return 400 Bad Request. |
| BR-07 | `phoneNumber` must be exactly 10 numeric digits. Requests with an invalid phone number return 400 Bad Request. |
| BR-08 | A user may not delete an address that is currently marked `isDefault = true` while other addresses exist. The user must first assign a different default, or delete all addresses individually. Attempts return 400 Bad Request with the message `"Cannot delete the default address while other addresses exist"`. |
| BR-09 | Deleting the only saved address (regardless of `isDefault`) is permitted. |
| BR-10 | **Delivery charge:** if the basket total is ≥ ₹500 the delivery charge is ₹0 (free delivery); if the basket total is < ₹500 the delivery charge is ₹50. |
| BR-11 | **Estimated delivery date:** `LocalDate.now() + 3 calendar days`, returned as an ISO-8601 date string (`YYYY-MM-DD`). No business-day logic is applied. |
| BR-12 | The checkout summary endpoint (`GET /api/checkout/summary`) requires the caller to supply an `addressId` query parameter identifying the chosen delivery address. The address must belong to the authenticated user; otherwise 403 Forbidden is returned. |
| BR-13 | If the user's basket is empty, `GET /api/checkout/summary` returns 400 Bad Request with the message `"Basket is empty"`. |
| BR-14 | The checkout summary is read-only and does not modify basket contents, stock levels, or address records. |

---

## 3. Actors

- **Authenticated User** — a user who has completed login and presents a valid
  JWT on every request. This is the only actor for this feature; guest access
  is not supported.

---

## 4. REST API Contract

### 4.1 List Saved Addresses

```
GET /api/addresses
```

- **Auth:** required (JWT).
- **Response 200:**
```json
[
  {
    "id": 1,
    "userId": 42,
    "recipientName": "Priya Sharma",
    "phoneNumber": "9876543210",
    "line1": "12 MG Road",
    "line2": "Apt 4B",
    "city": "Bengaluru",
    "state": "Karnataka",
    "pincode": "560001",
    "isDefault": true
  },
  {
    "id": 2,
    "userId": 42,
    "recipientName": "Priya Sharma",
    "phoneNumber": "9876543210",
    "line1": "7 Park Street",
    "line2": null,
    "city": "Kolkata",
    "state": "West Bengal",
    "pincode": "700016",
    "isDefault": false
  }
]
```
- Returns an empty array `[]` if the user has no saved addresses.
- **Response 401:** no valid JWT supplied.

---

### 4.2 Save a New Address

```
POST /api/addresses
```

- **Auth:** required (JWT).
- **Request body:**
```json
{
  "recipientName": "Priya Sharma",
  "phoneNumber": "9876543210",
  "line1": "12 MG Road",
  "line2": "Apt 4B",
  "city": "Bengaluru",
  "state": "Karnataka",
  "pincode": "560001",
  "isDefault": true
}
```
- `line2` is optional; all other fields are required.
- If `isDefault` is omitted it defaults to `false`.
- If `isDefault: true`, any existing default address for the user is
  automatically demoted to `isDefault: false`.
- **Response 201:** the newly created address object (same shape as a single
  element in the list response), including the assigned `id`.
- **Response 400:** any required field is missing, `pincode` is not 6 digits,
  or `phoneNumber` is not 10 digits.
- **Response 401:** no valid JWT supplied.

---

### 4.3 Update a Saved Address

```
PUT /api/addresses/{id}
```

- **Auth:** required (JWT).
- **Path parameter:** `id` — the address to update. Must belong to the
  authenticated user.
- **Request body:** same shape as POST (all updatable fields; partial updates
  are not supported — all fields must be provided).
- If `isDefault: true`, any other default address for the user is automatically
  demoted to `isDefault: false`.
- **Response 200:** the updated address object.
- **Response 400:** validation failure (missing field, invalid pincode or phone
  number).
- **Response 401:** no valid JWT supplied.
- **Response 403:** address does not belong to the authenticated user.
- **Response 404:** address not found.

---

### 4.4 Delete a Saved Address

```
DELETE /api/addresses/{id}
```

- **Auth:** required (JWT).
- **Path parameter:** `id` — the address to delete. Must belong to the
  authenticated user.
- **Response 204:** address deleted successfully (no response body).
- **Response 400:** address is the default and the user has other saved
  addresses (message: `"Cannot delete the default address while other addresses exist"`).
- **Response 401:** no valid JWT supplied.
- **Response 403:** address does not belong to the authenticated user.
- **Response 404:** address not found.

---

### 4.5 Checkout Summary

```
GET /api/checkout/summary?addressId={id}
```

- **Auth:** required (JWT).
- **Query parameter:** `addressId` (required) — the ID of the delivery address
  to use for this order. Must belong to the authenticated user.
- **Response 200:**
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
  "basketTotal": 598.00,
  "deliveryCharge": 0.00,
  "estimatedDeliveryDate": "2025-08-18",
  "deliveryAddress": {
    "id": 1,
    "recipientName": "Priya Sharma",
    "phoneNumber": "9876543210",
    "line1": "12 MG Road",
    "line2": "Apt 4B",
    "city": "Bengaluru",
    "state": "Karnataka",
    "pincode": "560001"
  }
}
```
- `deliveryCharge` is `0.00` when `basketTotal ≥ 500`; otherwise `50.00`.
- `estimatedDeliveryDate` is today's date plus 3 calendar days in `YYYY-MM-DD`
  format.
- **Response 400:** `addressId` query parameter is missing, OR the basket is
  empty (message: `"Basket is empty"`).
- **Response 401:** no valid JWT supplied.
- **Response 403:** the specified address does not belong to the authenticated
  user.
- **Response 404:** address not found.

---

## 5. Data Model

### `DeliveryAddress` Entity

| Field           | Type          | Nullable | Constraints                                 |
|-----------------|---------------|----------|---------------------------------------------|
| `id`            | Long          | No       | Primary key, auto-generated.                |
| `userId`        | Long          | No       | Foreign key → `User.id`. Not null.          |
| `recipientName` | String        | No       | Max 100 characters.                         |
| `phoneNumber`   | String        | No       | Exactly 10 numeric digits.                  |
| `line1`         | String        | No       | Max 200 characters.                         |
| `line2`         | String        | Yes      | Max 200 characters. Nullable.               |
| `city`          | String        | No       | Max 100 characters.                         |
| `state`         | String        | No       | Max 100 characters.                         |
| `pincode`       | String        | No       | Exactly 6 numeric digits.                   |
| `isDefault`     | Boolean       | No       | Defaults to `false`. At most one `true` per user. |

**Indexes:** composite index on `(userId, isDefault)` to support fast default
address lookup; index on `userId` for list queries.

---

## 6. Out of Scope

- Payment processing (FEAT-08).
- Guest checkout or guest-level address storage.
- Address validation against an external postal / courier service.
- Multiple delivery addresses per order (one address per order).
- Business-day delivery date calculation (weekends and public holidays are not
  excluded from the 3-day window).
- Delivery date personalisation based on pincode or region.
- Address book import / export.
- Basket-to-order promotion or stock reservation at checkout summary time.

---

## 7. Acceptance Criteria

| ID    | Criterion |
|-------|-----------|
| AC-01 | `GET /api/addresses` without a JWT returns 401. |
| AC-02 | `GET /api/addresses` for a user with no saved addresses returns 200 with an empty array. |
| AC-03 | `GET /api/addresses` returns only addresses belonging to the authenticated user. |
| AC-04 | `POST /api/addresses` with all valid fields creates the address and returns 201 with the persisted object including its `id`. |
| AC-05 | `POST /api/addresses` with a missing required field returns 400. |
| AC-06 | `POST /api/addresses` with a `pincode` that is not exactly 6 digits returns 400. |
| AC-07 | `POST /api/addresses` with a `phoneNumber` that is not exactly 10 digits returns 400. |
| AC-08 | Saving a new address with `isDefault: true` sets the new address as default and demotes any previously default address to `isDefault: false`. |
| AC-09 | `PUT /api/addresses/{id}` updates all fields and returns 200 with the updated address. |
| AC-10 | `PUT /api/addresses/{id}` for an address belonging to another user returns 403. |
| AC-11 | `PUT /api/addresses/{id}` for a non-existent address returns 404. |
| AC-12 | `DELETE /api/addresses/{id}` removes the address and returns 204. |
| AC-13 | `DELETE /api/addresses/{id}` for an address belonging to another user returns 403. |
| AC-14 | `DELETE /api/addresses/{id}` when the address is the default and other addresses exist returns 400 with message `"Cannot delete the default address while other addresses exist"`. |
| AC-15 | `DELETE /api/addresses/{id}` on the only saved address (even if default) returns 204. |
| AC-16 | `GET /api/checkout/summary` without a JWT returns 401. |
| AC-17 | `GET /api/checkout/summary` without the `addressId` query parameter returns 400. |
| AC-18 | `GET /api/checkout/summary` when the basket is empty returns 400 with message `"Basket is empty"`. |
| AC-19 | `GET /api/checkout/summary` with an `addressId` belonging to another user returns 403. |
| AC-20 | `GET /api/checkout/summary` with a non-existent `addressId` returns 404. |
| AC-21 | `GET /api/checkout/summary` with a basket total ≥ ₹500 returns `deliveryCharge: 0.00`. |
| AC-22 | `GET /api/checkout/summary` with a basket total < ₹500 returns `deliveryCharge: 50.00`. |
| AC-23 | `GET /api/checkout/summary` returns `estimatedDeliveryDate` equal to today's date plus exactly 3 calendar days in `YYYY-MM-DD` format. |
| AC-24 | `GET /api/checkout/summary` response includes all basket items with correct `unitPrice`, `quantity`, and `lineTotal`, plus the `basketTotal`. |
| AC-25 | `GET /api/checkout/summary` response includes the full delivery address object matching the supplied `addressId`. |
| AC-26 | Calling `GET /api/checkout/summary` does not alter basket contents, address records, or stock levels. |
