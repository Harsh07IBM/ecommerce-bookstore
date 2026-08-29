# Frontend UI Design — Shopping Basket & Post-Basket Flow

| Field | Value |
|---|---|
| **Scope** | `Basket.jsx` · `Checkout.jsx` · `OrderConfirmation.jsx` · `Orders.jsx` |
| **Stack** | React 18 · Vite · Tailwind CSS · Context API |
| **Theme** | Dark (`gray-950` base) · `brand` purple accent · `Playfair Display` serif headings |
| **Status** | Draft — Awaiting Developer Approval |

---

## 1. Purpose

This document specifies the **visual and structural design** for the four pages that
form the basket-to-order flow. It translates the approved backend design
(`feature-06-shopping-basket-design.md`) into a code-ready UI blueprint. Every
component, layout region, interaction state, and conditional render is specified here.
No UI decisions should be needed during implementation.

---

## 2. Design Principles

| Principle | Application |
|-----------|-------------|
| **Consistent dark theme** | All pages use `bg-gray-950` body, `bg-gray-900 border-gray-800` cards |
| **Serif headings** | `font-serif` (`Playfair Display`) for all `<h1>` and `<h2>` page titles |
| **Brand purple CTAs** | `btn-primary` class for all primary actions (checkout, place order) |
| **Sticky sidebar** | Order summary always visible while the user works through the left column |
| **Loading / disabled states** | Every async button shows a spinner or ellipsis and is `disabled` during inflight |
| **Empty states** | All list pages have a centred, illustrated empty state with a CTA |
| **No emojis in SVG areas** | Inline SVG icons preferred over emoji; emoji only in small badge-style chips |

---

## 3. Shared Layout Conventions

All pages wrap content in:

```
<div className="min-h-screen bg-gray-950">
  {/* optional page header band */}
  <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    ...
  </div>
</div>
```

Card primitive: `bg-gray-900 border border-gray-800 rounded-2xl` (matches `.card` utility class from `index.css`).

---

## 4. Basket.jsx — Design (as-built reference)

`Basket.jsx` has been fully implemented. This section documents the agreed design
so it serves as the baseline for the checkout and orders pages.

### 4.1 Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Page header band (border-b border-gray-800 bg-gray-900/50)  │
│  "Shopping Basket"  ·  N items              [Clear basket]   │
└─────────────────────────────────────────────────────────────┘
┌──────────────────────────────┐  ┌──────────────────────────┐
│  Item list (flex-1)          │  │  Order Summary (sticky)  │
│  ┌────────────────────────┐  │  │  Line items (scrollable) │
│  │ [Cover] Title / Author │  │  │  Subtotal / Delivery     │
│  │         [−/qty/+]  ₹XX │  │  │  Free delivery nudge     │
│  └────────────────────────┘  │  │  ──────────────────────  │
│  …                           │  │  Total (bold)            │
│  ← Continue Shopping         │  │  [Checkout] or [Sign in] │
└──────────────────────────────┘  │  Trust badges            │
                                   └──────────────────────────┘
```

### 4.2 Key decisions

- Qty stepper: border `border-gray-700 rounded-xl`; pressing `−` when qty=1 triggers `remove`, not `update(0)`.
- Remove button: trash SVG, `hover:text-red-400 hover:bg-red-950/30`.
- Guest CTA: "Sign in to Checkout" + "No account? Create one free" link.
- Free delivery threshold: ₹500. Delivery charge: ₹50 if below threshold.
- Empty state: centred cart icon SVG, serif heading, "Discover Books" CTA.

---

## 5. Checkout.jsx — Design

### 5.1 Layout

Two-column grid on `lg` breakpoint: `grid lg:grid-cols-3 gap-8`.
Left column (`lg:col-span-2`): step content. Right column: sticky order summary.

```
┌─────────────────────────────────────────────────────────────┐
│  (no separate page header band — heading is inline)          │
│  "Checkout"  h1 font-serif                                   │
│                                                              │
│  Step indicator:  ① Delivery Address ──── ② Payment         │
└─────────────────────────────────────────────────────────────┘
┌────────────────────────────────────┐  ┌─────────────────────┐
│  Step 1: Delivery Address          │  │  Order Summary       │
│  ┌────────────────────────────┐    │  │  (same card style    │
│  │ ○  Recipient Name          │    │  │   as Basket.jsx)     │
│  │    123 Main St, City       │    │  │                      │
│  │    State — 400001          │    │  │  Item ×N     ₹XX     │
│  │    +91 99999 99999 [Default]│   │  │  ──────────────────  │
│  └────────────────────────────┘    │  │  Subtotal    ₹XX     │
│  [+ Add New Address]               │  │  Delivery    FREE    │
│  [Continue to Payment →]           │  │  Gift Points -₹XX    │
│                                    │  │  ──────────────────  │
│  ── OR ──                          │  │  Total       ₹XX     │
│                                    │  │  Est. delivery date  │
│  Step 2: Payment Details           │  └─────────────────────┘
│  ← Back  "Payment Details"         │
│  ┌────────────────────────────┐    │
│  │  Card Information          │    │
│  │  Cardholder Name           │    │
│  │  Card Number (mono)        │    │
│  │  MM  |  YYYY  |  CVV       │    │
│  └────────────────────────────┘    │
│  🎁 Gift Points card (if > 0)      │
│  [Place Order — ₹XX.XX]            │
│  hint text                         │
└────────────────────────────────────┘
```

### 5.2 Step Indicator

```jsx
// Three visual states per step circle:
// completed  → bg-green-600 text-white  "✓"
// active     → bg-brand-600 text-white  "1"
// pending    → bg-gray-800  text-gray-500 "2"
```

Connector line between step 1 and step 2: `w-12 h-0.5 bg-gray-700 mx-1`.

### 5.3 Address Cards

Each saved address renders as:

```
<label className={`card flex gap-4 p-4 cursor-pointer
  ${selected ? 'border-brand-500' : 'border-gray-800'}`}>
  <input type="radio" className="mt-1 accent-brand-500" />
  <div>
    <p className="font-semibold text-white">{recipientName}</p>
    <p className="text-sm text-gray-400">{line1[, line2]}</p>
    <p className="text-sm text-gray-400">{city}, {state} — {pincode}</p>
    <p className="text-sm text-gray-400">{phoneNumber}</p>
    {isDefault && <span className="badge bg-brand-900 text-brand-300">Default</span>}
  </div>
</label>
```

### 5.4 New Address Form

Shown inline below the address list when `showNew === true`.
Slides in via `animate-slide-up`.
Fields in order: `recipientName`, `phoneNumber`, `line1`, `line2` (optional),
`city`, `state`, `pincode`.
Each field uses the global `.input` class.
Buttons: `[Save Address]` (btn-primary, sm) + `[Cancel]` (btn-outline, sm).

### 5.5 Payment Card

Card section header: `text-sm font-semibold text-gray-400 uppercase tracking-wider` — "Card Information".

Fields:
- Cardholder Name — full width `.input`
- Card Number — full width `.input font-mono`, auto-formatted with spaces every 4 digits, `maxLength={19}`
- Expiry Month / Expiry Year / CVV — `grid grid-cols-3 gap-3`, each `.input`

### 5.6 Gift Points Section

Shown only when `giftPoints > 0`:

```
bg-gray-900 border border-gray-800 rounded-2xl p-5
  "🎁 Gift Points (N available = ₹N)"  text-sm font-semibold text-gray-300
  <input type="number" min=0 max={giftPoints} />  (className="input text-sm")
```

### 5.7 Place Order Button

```jsx
<button type="submit" disabled={placing} className="btn-primary w-full py-3 text-base">
  {placing ? <Spinner size="sm" /> + ' Processing…' : `Place Order — ₹${total}`}
</button>
<p className="text-xs text-gray-600 text-center">
  Use card number 0000000000000000 to simulate a declined payment.
</p>
```

### 5.8 Order Summary Sidebar (Checkout)

Same card style as Basket.jsx. Shown only when `summary` is loaded.
Additional row: `📅 Estimated delivery: {estimatedDeliveryDate}` — `text-xs text-gray-500`.
Gift Points redemption row shown only when `redeemPoints > 0` — `text-brand-300`.

### 5.9 Interaction States

| Trigger | State change |
|---------|-------------|
| Select address | `selectedAddr` updates → `getCheckout(id)` refetches summary |
| Click "Continue to Payment" | disabled until `selectedAddr != null`; sets `step = 2` |
| Click "← Back" | sets `step = 1` |
| Submit payment form | `placing = true`, button disabled; on success navigate to confirmation |

---

## 6. OrderConfirmation.jsx — Design

### 6.1 Layout

Single centred column, `max-w-2xl mx-auto px-4 py-16`, `text-center` for header.
Card section below is `text-left`.

```
┌────────────────────────────────────────┐
│           ✅  (green ring icon)         │
│     "Order Confirmed!"   h1 serif       │
│     {confirmationMessage}  text-gray-400│
│                                        │
│  ┌──────────────────────────────────┐  │
│  │ Order #    | 12345               │  │
│  │ Status     | [PAID badge]        │  │
│  │ Est. Delivery | 2025-08-01       │  │
│  │ ─────────────────────────────── │  │
│  │ Book Title ×2           ₹59.98   │  │
│  │ ...                              │  │
│  │ ─────────────────────────────── │  │
│  │ Subtotal                ₹59.98   │  │
│  │ Delivery                 FREE    │  │
│  │ Gift Points             -₹10     │  │
│  │ Total Paid              ₹49.98   │  │
│  │ ─────────────────────────────── │  │
│  │ 🎁 You earned N gift points!    │  │
│  │ ─────────────────────────────── │  │
│  │ Delivering to:                   │  │
│  │  Name / Address                  │  │
│  └──────────────────────────────────┘  │
│                                        │
│  [View All Orders]   [Continue Shopping]│
└────────────────────────────────────────┘
```

### 6.2 Success Icon

```jsx
<div className="w-20 h-20 bg-green-900/40 border-2 border-green-500 rounded-full
     flex items-center justify-center mx-auto mb-6">
  {/* Inline SVG checkmark — no emoji */}
  <svg className="w-10 h-10 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
  </svg>
</div>
```

### 6.3 Status Badge

```jsx
<span className="badge bg-green-900/50 text-green-400">{order.status}</span>
// CANCELLED:
<span className="badge bg-red-900/50 text-red-400">{order.status}</span>
```

Dynamic colour mapping:

```js
const STATUS_BADGE = {
  PAID:      'bg-green-900/50 text-green-400',
  CANCELLED: 'bg-red-900/50 text-red-400',
};
```

### 6.4 Gift Points Earned Banner

Shown only when `order.pointsAwarded > 0`:

```
bg-brand-900/30 border border-brand-700/50 rounded-xl p-3 text-sm text-brand-300
  "🎁 You earned N gift points on this order!"
```

### 6.5 Delivery Address Block

```
<p className="font-medium text-gray-300 mb-1">Delivering to</p>
<p className="text-sm text-gray-400">{recipientName}</p>
<p className="text-sm text-gray-400">{line1[, line2]}</p>
<p className="text-sm text-gray-400">{city}, {state} — {pincode}</p>
```

### 6.6 Action Buttons

```jsx
<div className="flex gap-4 justify-center mt-8">
  <Link to="/orders" className="btn-outline">View All Orders</Link>
  <Link to="/books"  className="btn-primary">Continue Shopping</Link>
</div>
```

---

## 7. Orders.jsx — Design

### 7.1 Layout

Single column, `max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10`.

```
┌──────────────────────────────────────────────────┐
│  "My Orders"  h1 font-serif                       │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │  Order #12345           [PAID]  Details → │  │
│  │  2025-07-15                               │  │
│  │  ─────────────────────────────────────── │  │
│  │  Clean Code ×1                  ₹29.99   │  │
│  │  The Pragmatic Programmer ×2    ₹59.98   │  │
│  │  +1 more items                            │  │
│  │  ─────────────────────────────────────── │  │
│  │  Total: ₹89.97   [🔁 Buy Again] [Cancel] │  │
│  └────────────────────────────────────────┘  │
│  …                                            │
└──────────────────────────────────────────────┘
```

### 7.2 Order Card

```
bg-gray-900 border border-gray-800 rounded-2xl p-5 space-y-4
```

Header row: `flex flex-wrap items-start justify-between gap-3`
- Left: `text-xs text-gray-500` "Order #N" + `text-sm text-gray-400` date (`.split('T')[0]`)
- Right: status badge + "Details →" link (`text-sm text-brand-400 hover:text-brand-300`)

Items preview: `text-sm text-gray-400 divide-y divide-gray-800`
- Show up to 3 items: `{item.title} ×{item.quantity}` + `₹{lineTotal}`
- Overflow: `+N more items` in `text-gray-600`

Footer row: `flex flex-wrap items-center justify-between gap-3 pt-1 border-t border-gray-800`
- Left: `font-bold text-white` "Total: ₹N"
- Right: action buttons

### 7.3 Action Buttons

**Buy Again:**
```jsx
<button onClick={() => handleBuyAgain(o.orderId)}
  className="btn-outline text-sm py-1.5 px-4">
  {/* Refresh SVG icon */} Buy Again
</button>
```

**Cancel** (only when `o.status === 'PAID'`):
```jsx
<button onClick={() => handleCancel(o.orderId)}
  className="text-sm text-red-400 hover:text-red-300
    border border-red-900 hover:border-red-600
    px-4 py-1.5 rounded-xl transition-all">
  Cancel
</button>
```

### 7.4 Status Badge Colours

```js
const STATUS_COLORS = {
  PAID:      'bg-green-900/50 text-green-400',
  CANCELLED: 'bg-red-900/50 text-red-400',
};
// fallback: 'bg-gray-800 text-gray-400'
```

### 7.5 Empty State

```
<div className="text-center py-24">
  {/* Inline SVG package/box icon */}
  <h2 className="font-serif text-2xl font-bold text-white mb-2">No orders yet</h2>
  <p className="text-gray-400 mb-8">Your order history will appear here.</p>
  <Link to="/books" className="btn-primary">Start Shopping</Link>
</div>
```

---

## 8. Interaction & State Summary

| Page | Key state | Async operations |
|------|-----------|-----------------|
| Basket | `removing`, `updating` (bookId or null) | `remove(bookId)`, `update(bookId, qty)`, `clear()` |
| Checkout | `step` (1/2), `selectedAddr`, `redeemPoints`, `placing` | `getAddresses()`, `getGiftPoints()`, `getCheckout(addressId)`, `addAddress()`, `placeOrder()` |
| OrderConfirmation | `order`, `loading` | `getConfirmation(orderId)` |
| Orders | `orders`, `loading` | `getOrders()`, `cancelOrder(id)`, `buyAgain(id)` |

---

## 9. API Dependencies

All API calls go through `src/api.js`. Functions used per page:

| Page | API functions |
|------|--------------|
| Basket | `useBasket()` context (`update`, `remove`, `clear`) |
| Checkout | `getAddresses`, `addAddress`, `getCheckout`, `getGiftPoints`, `placeOrder` |
| OrderConfirmation | `getConfirmation` |
| Orders | `getOrders`, `cancelOrder`, `buyAgain` |

---

## 10. Acceptance Criteria (Frontend)

| ID | Criterion |
|----|-----------|
| UI-01 | Basket page shows cover thumbnail, qty stepper, line total, and remove button per item |
| UI-02 | Qty stepper is disabled during inflight update; pressing − at qty=1 removes the item |
| UI-03 | Order summary sidebar updates reactively from BasketContext |
| UI-04 | Free delivery nudge shows when basket total < ₹500 |
| UI-05 | Guest sees "Sign in to Checkout" CTA; authenticated user sees "Checkout" |
| UI-06 | Checkout step indicator accurately reflects current step with colour feedback |
| UI-07 | Selecting an address refetches the checkout summary |
| UI-08 | New address form slides in inline below existing addresses |
| UI-09 | Place Order button is disabled and shows spinner during submission |
| UI-10 | Order confirmation shows a green SVG checkmark, order details, and gift points earned |
| UI-11 | Orders list shows up to 3 items per order with "+N more" overflow |
| UI-12 | Cancel button only appears for PAID orders |
| UI-13 | Buy Again re-adds items to basket and shows a toast notification |
| UI-14 | All empty states include an SVG icon, serif heading, and a CTA link |
