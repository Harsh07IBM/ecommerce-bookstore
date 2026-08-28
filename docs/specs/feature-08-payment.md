# Feature Specification: FEAT-08 — Payment

## 1. Overview

Allow authenticated users to submit payment for the contents of their basket,
producing a confirmed order. Payment is **simulated** — no real payment gateway
is involved. The backend accepts card details, validates their format, and
decides success or failure according to a deterministic rule: card number
`0000000000000000` always fails; any other validly-formatted card number
always succeeds.

On success, an `Order` record is created capturing a snapshot of the basket
items, delivery address, charges, and estimated delivery date. The user's
basket is then cleared. On failure, no order is created and the basket is
unchanged.

**Dependencies:**
- **FEAT-04** (User / JWT auth) — payment requires an authenticated session;
  guest users cannot place orders.
- **FEAT-06** (Shopping Basket) — the basket is read at payment time and
  cleared on success.
- **FEAT-07** (Checkout & Delivery Address) — an `addressId` from the user's
  saved addresses is required as part of the payment request.

**Blocks:**
- **FEAT-09** (Gift Points) — gift point redemption plugs into the payment
  flow via the `giftPointsToRedeem` field introduced here.
- **FEAT-10** (Order Management & History) — orders created here are the
  source for order history queries.
- **FEAT-13** (Purchase Confirmation) — confirmation data originates from the
  order created here.

---

## 2. Business Rules

| #     | Rule |
|-------|------|
| BR-01 | All payment endpoints require a valid JWT. Requests without a valid JWT return 401 Unauthorized. |
| BR-02 | The basket must not be empty at payment time. If it is, the request returns 400 Bad Request with message `"Basket is empty"`. |
| BR-03 | `addressId` must identify a delivery address that belongs to the authenticated user. If the address exists but belongs to another user, return 403 Forbidden. If the address does not exist, return 404 Not Found. |
| BR-04 | `cardNumber` must be exactly 16 numeric digits. Any other value returns 400 Bad Request. |
| BR-05 | `expiryMonth` must be an integer in the range 1–12 (inclusive). Any other value returns 400 Bad Request. |
| BR-06 | `expiryYear` must be a 4-digit integer greater than or equal to the current calendar year. Any other value returns 400 Bad Request. |
| BR-07 | `cvv` must be exactly 3 numeric digits. Any other value returns 400 Bad Request. |
| BR-08 | `cardholderName` must be non-blank. An absent or blank value returns 400 Bad Request. |
| BR-09 | Luhn / checksum validation of the card number is **not** performed — only the 16-digit numeric format is checked. |
| BR-10 | **Simulated decline rule:** if `cardNumber` equals `"0000000000000000"` (exactly 16 zeros) the payment is declined — return 402 Payment Required with message `"Payment declined"`. No order is created and the basket is not cleared. |
| BR-11 | **Simulated success rule:** any `cardNumber` that passes format validation (BR-04) and is not `"0000000000000000"` results in a successful payment. |
| BR-12 | **On success — order creation:** an `Order` record is persisted with status `PAID`, containing: `userId`, a snapshot of all address fields (not a foreign key), `orderDate` (server-side `LocalDateTime` at time of request), `deliveryCharge` (per the rule in FEAT-07 BR-10: ₹0 if basket total ≥ ₹500, else ₹50), `totalAmount` (`basketTotal + deliveryCharge`), `estimatedDeliveryDate` (`LocalDate.now() + 3` calendar days, stored as a string in `YYYY-MM-DD` format), and a list of `OrderItem` records mirroring the basket at that moment. |
| BR-13 | **On success — basket cleared:** after the order is persisted the user's basket is emptied. |
| BR-14 | **On success — stock decrement:** each book's stock quantity is decremented by the ordered quantity. A book's stock must not go below zero; if any item in the basket has insufficient stock at payment time, return 400 Bad Request with message `"Insufficient stock for: {title}"`. |
| BR-15 | The `giftPointsToRedeem` field is accepted in the request body and stored for future use (FEAT-09), but is **ignored** in the payment calculation for FEAT-08. Its value must be a non-negative integer; negative values return 400 Bad Request. |
| BR-16 | The address snapshot stored on the `Order` is taken at payment time. Subsequent edits or deletions of the `DeliveryAddress` record do not affect the stored order. |
| BR-17 | Card details are used only for format validation and the simulated decline check. They are **never** persisted to the database. |

---

## 3. Actors

- **Authenticated User** — a user who has completed login and presents a valid
  JWT on every request. This is the only actor for this feature; guest access
  is not supported.

---

## 4. REST API Contract

### 4.1 Place Order (Initiate Payment)

```
POST /api/orders
```

- **Auth:** required (JWT).
- **Request body:**
```json
{
  "addressId": 1,
  "cardNumber": "4111111111111111",
  "expiryMonth": 12,
  "expiryYear": 2027,
  "cvv": "123",
  "cardholderName": "Priya Sharma",
  "giftPointsToRedeem": 0
}
```

| Field                | Type    | Required | Validation |
|----------------------|---------|----------|------------|
| `addressId`          | Long    | Yes      | Must exist and belong to the authenticated user. |
| `cardNumber`         | String  | Yes      | Exactly 16 numeric digits. |
| `expiryMonth`        | Integer | Yes      | 1–12 inclusive. |
| `expiryYear`         | Integer | Yes      | 4-digit integer ≥ current year. |
| `cvv`                | String  | Yes      | Exactly 3 numeric digits. |
| `cardholderName`     | String  | Yes      | Non-blank. |
| `giftPointsToRedeem` | Integer | No       | Non-negative integer. Defaults to 0 if omitted. Ignored in FEAT-08. |

- **Response 201 — Payment succeeded:**
```json
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
  "totalAmount": 598.00,
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
```

- **Response 400 — Validation failure or empty basket:**
```json
{ "error": "Basket is empty" }
```
  Other 400 messages (examples): `"cardNumber must be exactly 16 numeric digits"`,
  `"expiryMonth must be between 1 and 12"`, `"cvv must be exactly 3 numeric digits"`,
  `"cardholderName must not be blank"`, `"giftPointsToRedeem must be non-negative"`,
  `"Insufficient stock for: Clean Code"`.

- **Response 401 — No valid JWT:**
```json
{ "error": "Unauthorized" }
```

- **Response 402 — Card declined:**
```json
{ "error": "Payment declined" }
```

- **Response 403 — Address belongs to another user:**
```json
{ "error": "Forbidden" }
```

- **Response 404 — Address not found:**
```json
{ "error": "Address not found" }
```

---

## 5. Data Model

### `Order` Entity

| Field                   | Type          | Nullable | Constraints |
|-------------------------|---------------|----------|-------------|
| `id`                    | Long          | No       | Primary key, auto-generated. |
| `userId`                | Long          | No       | Foreign key → `User.id`. Not null. |
| `status`                | Enum          | No       | `PAID` or `CANCELLED`. Set to `PAID` on creation. |
| `orderDate`             | LocalDateTime | No       | Server-assigned at time of order placement. |
| `basketTotal`           | BigDecimal    | No       | Sum of all order item line totals. |
| `deliveryCharge`        | BigDecimal    | No       | ₹0.00 or ₹50.00, per FEAT-07 BR-10 logic. |
| `totalAmount`           | BigDecimal    | No       | `basketTotal + deliveryCharge`. |
| `estimatedDeliveryDate` | String        | No       | `YYYY-MM-DD`, computed as `orderDate.toLocalDate() + 3` days. |
| `recipientName`         | String        | No       | Snapshot from `DeliveryAddress.recipientName`. |
| `phoneNumber`           | String        | No       | Snapshot from `DeliveryAddress.phoneNumber`. |
| `line1`                 | String        | No       | Snapshot from `DeliveryAddress.line1`. |
| `line2`                 | String        | Yes      | Snapshot from `DeliveryAddress.line2`. Nullable. |
| `city`                  | String        | No       | Snapshot from `DeliveryAddress.city`. |
| `state`                 | String        | No       | Snapshot from `DeliveryAddress.state`. |
| `pincode`               | String        | No       | Snapshot from `DeliveryAddress.pincode`. |

> **Note:** address fields are stored directly on `Order` (snapshot), not as a
> foreign key to `DeliveryAddress`. This ensures order history remains accurate
> even if the original address is later edited or deleted.

**Indexes:** index on `userId` to support order history queries.

---

### `OrderItem` Entity

| Field       | Type       | Nullable | Constraints |
|-------------|------------|----------|-------------|
| `id`        | Long       | No       | Primary key, auto-generated. |
| `orderId`   | Long       | No       | Foreign key → `Order.id`. Not null. |
| `bookId`    | Long       | No       | Reference to the book at time of order. Not a FK enforced constraint (book may be removed from catalogue later). |
| `title`     | String     | No       | Snapshot of `Book.title` at time of order. |
| `quantity`  | Integer    | No       | Number of copies ordered. ≥ 1. |
| `unitPrice` | BigDecimal | No       | Snapshot of `Book.price` at time of order. |
| `lineTotal` | BigDecimal | No       | `unitPrice × quantity`. |

**Indexes:** index on `orderId` to support item retrieval by order.

---

## 6. Out of Scope

- Real payment gateway integration (Stripe, Razorpay, etc.).
- Refunds or partial refunds.
- Gift point redemption — the `giftPointsToRedeem` field is accepted and stored
  for future integration but has no effect on pricing in FEAT-08 (covered by
  FEAT-09).
- Order cancellation (FEAT-12).
- Saving, tokenising, or vaulting card details.
- Expiry date currency validation (checking that the card has not expired
  relative to the current month — only the year bound is enforced).
- Luhn algorithm / card number checksum validation.
- Multiple payment methods (only credit card and debit card input shapes are
  supported; no PayPal, UPI, net banking, etc.).
- Partial basket checkout (all basket items are always included in a single
  order).
- Order splitting across multiple delivery addresses.

---

## 7. Acceptance Criteria

| ID    | Criterion |
|-------|-----------|
| AC-01 | `POST /api/orders` without a JWT returns 401. |
| AC-02 | `POST /api/orders` with a valid JWT but an empty basket returns 400 with message `"Basket is empty"`. |
| AC-03 | `POST /api/orders` with an `addressId` that does not exist returns 404. |
| AC-04 | `POST /api/orders` with an `addressId` that belongs to a different user returns 403. |
| AC-05 | `POST /api/orders` with a `cardNumber` that is not exactly 16 numeric digits returns 400. |
| AC-06 | `POST /api/orders` with an `expiryMonth` outside 1–12 returns 400. |
| AC-07 | `POST /api/orders` with an `expiryYear` less than the current calendar year returns 400. |
| AC-08 | `POST /api/orders` with a `cvv` that is not exactly 3 numeric digits returns 400. |
| AC-09 | `POST /api/orders` with a blank `cardholderName` returns 400. |
| AC-10 | `POST /api/orders` with a negative `giftPointsToRedeem` returns 400. |
| AC-11 | `POST /api/orders` with `cardNumber` = `"0000000000000000"` (all other fields valid) returns 402 with message `"Payment declined"`. No order is created and the basket remains unchanged. |
| AC-12 | `POST /api/orders` with a valid card number (not all zeros) and a non-empty basket returns 201 with status `"PAID"`. |
| AC-13 | The 201 response body contains all basket items with correct `bookId`, `title`, `quantity`, `unitPrice`, and `lineTotal`. |
| AC-14 | The 201 response body includes `basketTotal` equal to the sum of all item line totals. |
| AC-15 | The 201 response body includes `deliveryCharge` = `0.00` when `basketTotal ≥ 500`; `50.00` otherwise. |
| AC-16 | The 201 response body includes `totalAmount` equal to `basketTotal + deliveryCharge`. |
| AC-17 | The 201 response body includes `estimatedDeliveryDate` equal to the order date plus exactly 3 calendar days, in `YYYY-MM-DD` format. |
| AC-18 | The 201 response body includes a `deliveryAddress` object containing the snapshotted address fields matching the supplied `addressId`. |
| AC-19 | After a successful payment, the user's basket is empty. |
| AC-20 | After a successful payment, each ordered book's stock quantity is decremented by the corresponding ordered quantity. |
| AC-21 | If any basket item has insufficient stock at payment time, the request returns 400 with message `"Insufficient stock for: {title}"`. No order is created and the basket is not cleared. |
| AC-22 | The `Order` record persisted to the database contains a full address snapshot; updating or deleting the original `DeliveryAddress` does not alter the stored order. |
| AC-23 | Card details are not stored in the database after the request completes. |
| AC-24 | `giftPointsToRedeem` (when supplied and ≥ 0) is accepted without error and has no effect on `totalAmount` in FEAT-08. |
| AC-25 | Two consecutive successful orders each produce a distinct `orderId` and accurate `orderDate` timestamps. |
