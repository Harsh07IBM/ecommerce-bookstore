# Feature Roadmap — E-Commerce Bookstore

**Document:** Feature Development Roadmap
**Project:** AI-Assisted E-Commerce Bookstore
**Status:** Active — updated as features are completed
**Traceability:** Every feature traces back to [business-requirements.md](business-requirements.md)

---

## How to Use This Document

- Every feature follows the full lifecycle: **Spec → Plan → Design → Code → Test**.
- When a feature is complete, check it off and move to the next one.
- Before starting a feature marked ⚠️, resolve its listed open questions first.

> **⚠️ Execution order change (2026-08-27):**
> The original roadmap placed the Storefront Frontend (Tier 3) before the remaining
> backend features (Tiers 4–6). After review, we decided to **finish all backend
> features first**, then build the frontend once every API exists and is tested.
> The tier numbers are preserved for traceability but the actual build order is:
> **Tier 0 → 1 → 2 → 4 → 5 → 6 → 3 (frontend last).**

---

## Progress Summary

| Build Order | Tier | Name | Features | Status |
|---|---|---|---|---|
| 1 | ✅ Tier 0 | Foundation | FEAT-01 | Complete |
| 2 | ✅ Tier 1 | Catalogue Completion | FEAT-02, FEAT-03 | Complete |
| 3 | ✅ Tier 2 | User Identity | FEAT-04 | Complete |
| 4 | 🟡 Tier 4 | Shopping Backend | FEAT-06, FEAT-07, FEAT-08, FEAT-09 | **In Progress (2/4)** |
| 5 | ⚫ Tier 5 | Post-Purchase Backend | FEAT-10, FEAT-11, FEAT-12, FEAT-13 | Not Started |
| 6 | 🟣 Tier 6 | Intelligence Backend | FEAT-14, FEAT-15 | Not Started |
| 7 | 🟠 Tier 3 | Storefront Frontend | FEAT-05 | Not Started — built last |

---

## ✅ Tier 0 — Foundation

> **Status: COMPLETE**
> All features in this tier are done. Move to Tier 1.

---

### [x] FEAT-01 — Browse Book Catalogue

| Field | Value |
|---|---|
| **Status** | ✅ Complete |
| **Spec** | [docs/specs/feature-01-browse-catalogue.md](specs/feature-01-browse-catalogue.md) |
| **Plan** | [docs/plans/feature-01-browse-catalogue-plan.md](plans/feature-01-browse-catalogue-plan.md) |
| **Design** | [docs/designs/feature-01-browse-catalogue-design.md](designs/feature-01-browse-catalogue-design.md) |
| **Business Requirements** | §7.1, §7.4, §7.5, §8, §5.2 |

**What it delivers:**
- Paginated list of all books (12 per page, newest first)
- Book detail page with full information
- Availability indicator (IN_STOCK / OUT_OF_STOCK)
- Seed pipeline: Open Library → `books.json` → H2 database on startup
- REST API: `GET /api/books` and `GET /api/books/{id}`

---

---

## ✅ Tier 1 — Catalogue Completion

> **Prerequisite:** Tier 0 complete ✅
> **Status: COMPLETE ✅**
> No authentication required. Pure backend extensions of the existing Book catalogue.

---

### [x] FEAT-02 — Category Browsing

| Field | Value |
|---|---|
| **Status** | ✅ Complete |
| **Spec** | [docs/specs/feature-02-category-browsing.md](specs/feature-02-category-browsing.md) |
| **Plan** | [docs/plans/feature-02-category-browsing-plan.md](plans/feature-02-category-browsing-plan.md) |
| **Design** | [docs/designs/feature-02-category-browsing-design.md](designs/feature-02-category-browsing-design.md) |
| **Business Requirements** | §7.2, BR-002 |
| **Depends On** | FEAT-01 |
| **Blocks** | FEAT-03 (filter by category), FEAT-05 (category navigation in UI) |

**Delivered:**
- `Category` entity (`id`, `name`, `slug`) with `@ManyToOne` on `Book`
- `BookSeedLoader` rewritten to seed categories first, then books
- `GET /api/categories` — returns all categories with book count
- `GET /api/books?category={slug}` — filtered catalogue (routed via `BookFilter`)
- `CategoryNotFoundException` → 404 via `GlobalExceptionHandler`

**Lifecycle checklist:**
- [x] Spec written and approved
- [x] Plan written and approved
- [x] Design written and approved
- [x] Code complete
- [x] Tests passing

---

### [x] FEAT-03 — Search & Filter

| Field | Value |
|---|---|
| **Status** | ✅ Complete |
| **Spec** | [docs/specs/feature-03-search-and-filter.md](specs/feature-03-search-and-filter.md) |
| **Plan** | [docs/plans/feature-03-search-and-filter-plan.md](plans/feature-03-search-and-filter-plan.md) |
| **Design** | [docs/designs/feature-03-search-and-filter-design.md](designs/feature-03-search-and-filter-design.md) |
| **Business Requirements** | §9.1, §9.2 |
| **Depends On** | FEAT-01, FEAT-02 |
| **Blocks** | FEAT-05 (search bar in UI) |

**Delivered:**
- `BookFilter` value object (`q`, `categorySlug`, `minPrice`, `maxPrice`, `availableOnly`, `sort`)
- `BookSpecification` predicate factory (JPA Criteria API)
- `BookService.listBooks(BookFilter, page, size)` — unified search+filter+sort
- `BookController` updated — all `@RequestParam`s, input validation, `hasFilters` branch
- Sorting: `newest` (default), `price_asc`, `price_desc`

**Lifecycle checklist:**
- [x] Spec written and approved
- [x] Plan written and approved
- [x] Design written and approved
- [x] Code complete
- [x] Tests passing

---

> ✅ **Tier 1 complete.** FEAT-02 and FEAT-03 both shipped and all 31 tests pass.
> ➡️ **Next:** Move to Tier 2 — User Identity.

---

---

## ✅ Tier 2 — User Identity

> **Prerequisite:** Tier 1 complete
> The single most important feature in the roadmap.
> Everything in Tier 4, 5, and 6 depends on a `User` existing in the system.

> ⚠️ **Open Questions to resolve BEFORE writing the spec:**
> - Can a guest add to the basket / checkout / purchase without registering? (§5.3, Q7–10)
> - Is authentication mandatory before checkout? (§20, Q10)

---

### [x] FEAT-04 — User Registration & Login

| Field | Value |
|---|---|
| **Status** | ✅ Complete |
| **Spec** | [docs/specs/feature-04-user-registration-login.md](specs/feature-04-user-registration-login.md) |
| **Plan** | [docs/plans/feature-04-user-registration-login-plan.md](plans/feature-04-user-registration-login-plan.md) |
| **Design** | [docs/designs/feature-04-user-registration-login-design.md](designs/feature-04-user-registration-login-design.md) |
| **Business Requirements** | §6.1, §4, BR-001 |
| **Depends On** | FEAT-01 |
| **Blocks** | FEAT-06 (basket), FEAT-08 (payment), FEAT-10 (order history), FEAT-14 (recommendations) |

**Delivered:**
- `User` entity (`id`, `firstName`, `lastName`, `email`, `passwordHash`, `createdAt`) — table `users`
- `POST /api/auth/register` — creates account, returns 201 + `UserDto` (no password)
- `POST /api/auth/login` — verifies credentials, returns 200 + JWT + `UserDto`
- BCrypt password hashing (never stored or returned as plaintext)
- JWT signed with HMAC-SHA256, 24-hour expiry, no refresh tokens
- `JwtAuthFilter` — validates `Authorization: Bearer <token>` on every request
- `SecurityConfig` — `GET /api/books/**`, `GET /api/categories`, `POST /api/auth/**` all public; everything else requires auth
- Anti-enumeration: wrong email and wrong password return identical 401 response
- `EmailAlreadyExistsException` → 409 Conflict
- `InvalidCredentialsException` → 401 Unauthorized
- `MethodArgumentNotValidException` handler → 400 with Bean Validation field message

**Lifecycle checklist:**
- [x] Open questions resolved (Q7–Q10 — guest capabilities, token strategy, fields, expiry)
- [x] Spec written and approved
- [x] Plan written and approved
- [x] Design written and approved
- [x] Code complete
- [x] Tests passing (64 total, 0 failures)

---

> ✅ **Tier 2 complete.** FEAT-04 shipped — 64 tests passing.
> ➡️ **Next:** Move to Tier 3 — Storefront Frontend.

---

---

## 🟠 Tier 3 — Storefront Frontend *(built last — after all backend is complete)*

> **Original prerequisite:** Tier 1 + Tier 2 complete.
> **Revised execution:** This tier is now intentionally built AFTER Tiers 4, 5, and 6.
> All backend APIs will exist and be Postman-tested before the first line of frontend code is written.
> This gives the frontend a complete, stable API contract to build against.

---

### [ ] FEAT-05 — Storefront Frontend

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §2.1, §3.1, §5.2, §7.1–7.4 |
| **Depends On** | FEAT-01, FEAT-02, FEAT-03, FEAT-04 |
| **Blocks** | FEAT-06 (basket UI), FEAT-07 (checkout UI) |

**What it will deliver:**
- Catalogue page: book grid with pagination
- Category navigation sidebar / tabs
- Search bar and filter panel
- Book detail page
- Login and registration pages
- Broken cover image fallback to placeholder
- Responsive layout

**Key design work needed:**
- Frontend framework decision (React vs Thymeleaf — to be decided at spec time)
- API integration with all existing REST endpoints
- Placeholder image for missing covers (spec FR-10)

**Lifecycle checklist:**
- [ ] Frontend framework decision made
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

> ✅ **Tier 3 complete when:** FEAT-05 is checked off above.
> 🎉 **This is the final tier — project is feature-complete.**

---

---

## 🟡 Tier 4 — Shopping Backend *(build order: 4th)*

> **Prerequisite:** Tier 2 complete (users and JWT auth exist). ✅
> **Note:** Frontend (Tier 3) is built AFTER this tier, not before.
> The core commercial flow — basket through payment, all as REST APIs.

> ⚠️ **Open Questions to resolve BEFORE writing specs for remaining features:**
> - FEAT-08: Is payment simulated or real gateway? What happens on failure? (§20, Q18–21)
> - FEAT-09: How are gift points earned? What is their value? Do they expire? (§20, Q22–26)

---

### [x] FEAT-06 — Shopping Basket

| Field | Value |
|---|---|
| **Status** | ✅ Complete |
| **Spec** | [docs/specs/feature-06-shopping-basket.md](specs/feature-06-shopping-basket.md) |
| **Plan** | [docs/plans/feature-06-shopping-basket-plan.md](plans/feature-06-shopping-basket-plan.md) |
| **Design** | [docs/designs/feature-06-shopping-basket-design.md](designs/feature-06-shopping-basket-design.md) |
| **Business Requirements** | §10.1, §10.2, §10.3, BR-005 |
| **Depends On** | FEAT-04 (user) |
| **Blocks** | FEAT-07 (checkout), FEAT-08 (payment) |

**What it delivers:**
- Guest and authenticated basket via session cookie / JWT
- Add, update quantity, remove item, clear basket
- Per-book max quantity of 7 enforced; out-of-stock guard
- Basket total and per-line totals computed server-side
- Basket persists across requests for registered users (DB-backed)

**Lifecycle checklist:**
- [x] Guest basket open question resolved
- [x] Spec written and approved
- [x] Plan written and approved
- [x] Design written and approved
- [x] Code complete
- [x] Tests passing (27 new tests — 91 total, 0 failures)

---

### [x] FEAT-07 — Checkout & Delivery Address

| Field | Value |
|---|---|
| **Status** | ✅ Complete |
| **Spec** | [docs/specs/feature-07-checkout-delivery.md](specs/feature-07-checkout-delivery.md) |
| **Plan** | [docs/plans/feature-07-checkout-delivery-plan.md](plans/feature-07-checkout-delivery-plan.md) |
| **Design** | [docs/designs/feature-07-checkout-delivery-design.md](designs/feature-07-checkout-delivery-design.md) |
| **Business Requirements** | §12.1, §12.2, BR-009 |
| **Depends On** | FEAT-06 (basket) |
| **Blocks** | FEAT-08 (payment) |

**What it delivers:**
- Multiple saved delivery addresses per user (add, update, delete)
- Default address management with auto-demotion (BR-04)
- Ownership enforced — users cannot access each other's addresses (403)
- Checkout summary: basket items + chosen address + delivery charge + estimated delivery date
- Delivery charge: free above ₹500, ₹50 below (BR-10)
- Estimated delivery: today + 3 calendar days, ISO-8601 (BR-11)

**Lifecycle checklist:**
- [x] Multiple addresses open question resolved (Q17 — multiple saved addresses supported)
- [x] Delivery date calculation rule decided (Q15 — today + 3 calendar days)
- [x] Delivery charges decision made (Q16 — free ≥ ₹500, ₹50 below)
- [x] Spec written and approved
- [x] Plan written and approved
- [x] Design written and approved
- [x] Code complete
- [x] Tests passing (44 new tests — 135 total, 0 failures)

---

### [ ] FEAT-08 — Payment

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §13.1, §13.2, §13.3, §13.4, §13.5, BR-010, BR-012, A-004 |
| **Depends On** | FEAT-07 (checkout + delivery address) |
| **Blocks** | FEAT-09 (gift points), FEAT-10 (order creation), FEAT-13 (confirmation) |

**What it will deliver:**
- Payment initiation from basket/checkout
- Credit card and debit card payment methods (§13.2)
- Payment completion flow
- Payment confirmation response
- Simulated payment processing (real gateway is out of scope per §3.2 unless approved)

**Lifecycle checklist:**
- [ ] Simulated vs real payment decision made
- [ ] Payment failure behaviour defined
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

### [ ] FEAT-09 — Gift Points Redemption

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §14.1, §14.2, BR-011 |
| **Depends On** | FEAT-08 (payment flow) |
| **Blocks** | FEAT-12 (cancellation — what happens to redeemed points?) |

**What it will deliver:**
- Display of available gift points at payment
- Option to redeem eligible points against the order total
- Points deducted from user balance on successful payment

**Key open questions (ALL must be resolved before spec):**
- How are gift points earned? (§20, Q22)
- What is the monetary value of one point? (§20, Q23)
- Maximum redeemable per order? (§20, Q24)
- Do points expire? (§20, Q25)
- What happens to redeemed points if order is cancelled? (§20, Q26)

**Lifecycle checklist:**
- [ ] All gift point business rules decided
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

> ✅ **Tier 4 complete when:** FEAT-06, FEAT-07, FEAT-08, and FEAT-09 are all checked off.
> ➡️ **Next:** Move to Tier 5 — Post-Purchase Backend.

---

---

## ⚫ Tier 5 — Post-Purchase Backend *(build order: 5th)*

> **Prerequisite:** Tier 4 complete (payment and orders exist).
> Features that operate on completed orders — all as REST APIs.

> ⚠️ **Open Questions to resolve BEFORE writing specs:**
> - FEAT-12: Does the 48-hour window start from order creation? (§20, Q11)
> - FEAT-12: Which order statuses allow cancellation? (§20, Q12)
> - FEAT-12: What happens to payment on cancellation? (§20, Q13–14)

---

### [ ] FEAT-10 — Order Management & History

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §11.1, §11.2, BR-006 |
| **Depends On** | FEAT-08 (payment creates an order) |
| **Blocks** | FEAT-11 (buy again), FEAT-12 (cancellation), FEAT-14 (recommendations) |

**What it will deliver:**
- Order created automatically on successful payment
- Order detail view (items, quantities, prices, status, delivery address)
- Order history list for the authenticated user
- Order status tracking (e.g. Placed → Shipped → Delivered)

**Lifecycle checklist:**
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

### [ ] FEAT-11 — Buy Again

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §11.3, BR-007 |
| **Depends On** | FEAT-10 (order history) |
| **Blocks** | Nothing — standalone convenience feature |

**What it will deliver:**
- "Buy Again" action on a previous order or individual order item
- Re-adds the selected book(s) to the current basket in one click
- Checks current availability before adding

**Lifecycle checklist:**
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

### [ ] FEAT-12 — Order Cancellation

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §11.4, BR-014 |
| **Depends On** | FEAT-10 (order exists), FEAT-08 (payment exists) |
| **Blocks** | Nothing — but must coordinate with gift-point reversal (FEAT-09) |

**What it will deliver:**
- Cancel order action available within 48 hours of order creation
- Order status transitions to Cancelled
- Payment reversal / refund initiation (rules to be decided)
- Gift point restoration if redeemed on cancelled order

**Key open questions (ALL must be resolved before spec):**
- 48-hour window measured from when exactly? (§20, Q11)
- Which statuses permit cancellation? Can a shipped order be cancelled? (§20, Q12)
- What happens to payment? Is refund automatic? (§20, Q13–14)

**Lifecycle checklist:**
- [ ] All cancellation business rules decided
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

### [ ] FEAT-13 — Purchase Confirmation

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §13.5, §16, BR-013 |
| **Depends On** | FEAT-08 (payment), FEAT-10 (order) |
| **Blocks** | Nothing |

**What it will deliver:**
- Confirmation screen shown immediately after successful payment
- Displays: order summary, items purchased, total paid, delivery address, tentative delivery date
- Clear success message as described in the wireframe (§16)

**Lifecycle checklist:**
- [ ] Confirmation screen content agreed (wireframe review)
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

> ✅ **Tier 5 complete when:** FEAT-10, FEAT-11, FEAT-12, and FEAT-13 are all checked off.
> ➡️ **Next:** Move to Tier 6 — Intelligence Backend.

---

---

## 🟣 Tier 6 — Intelligence Backend *(build order: 6th)*

> **Prerequisite:** Tier 5 complete (order history exists and is meaningful).
> Features that use accumulated data to improve the shopping experience — all as REST APIs.
> After this tier, ALL backend is complete and the frontend (Tier 3) begins.

> ⚠️ **Open Questions to resolve BEFORE writing specs:**
> - FEAT-14: What should recommendations be based on exactly? (§20, Q27)
> - FEAT-14: How many recommendations to show? (§20, Q28)
> - FEAT-14: Is a simple rule-based algorithm acceptable? (§20, Q29)
> - FEAT-15: What makes a product "related"? Same category, same author, both? (§20, Q6)

---

### [ ] FEAT-14 — Recommendations

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §6.5, §15.1, §15.2, BR-008, A-005 |
| **Depends On** | FEAT-10 (order history to base recommendations on) |
| **Blocks** | Nothing |

**What it will deliver:**
- Recommendations displayed during the shopping experience
- Recommendations displayed within the basket (§10.4)
- Based on the customer's previous order history (BR-008)
- Simple rule-based algorithm (e.g. books in the same categories as past purchases)

**Lifecycle checklist:**
- [ ] Recommendation algorithm decided
- [ ] Display contexts decided (catalogue page? basket? product detail?)
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

### [ ] FEAT-15 — Related Products

| Field | Value |
|---|---|
| **Status** | 🔲 Not Started |
| **Business Requirements** | §7.6, BR-004 |
| **Depends On** | FEAT-01 (book detail page), FEAT-02 (category model) |
| **Blocks** | Nothing |

**What it will deliver:**
- Related books shown on the book detail page
- Rule-based logic (e.g. same category, same author)
- Clicking a related book navigates to that book's detail page

**Lifecycle checklist:**
- [ ] "Related" definition agreed (same category? same author? both?)
- [ ] Maximum number of related items decided
- [ ] Spec written and approved
- [ ] Plan written and approved
- [ ] Design written and approved
- [ ] Code complete
- [ ] Tests passing

---

> ✅ **Tier 6 complete when:** FEAT-14 and FEAT-15 are both checked off.
> ➡️ **Next:** Move to Tier 3 — Storefront Frontend (final tier).

---

---

## Open Questions Master List

These questions must be resolved before the corresponding feature's spec can be written.
Track resolutions here as they are decided.

| # | Question | Blocks | Status |
|---|---|---|---|
| Q1 | What exact filters must be supported in search? (§9.2) | FEAT-03 | ✅ Resolved — title, author, description, category, price range, availability |
| Q2 | Can a guest add to basket / checkout / purchase? (§5.3) | FEAT-06 | ✅ Resolved — guests can add to basket; checkout requires login |
| Q3 | Is authentication mandatory before checkout? (§20 Q10) | FEAT-06 | ✅ Resolved — yes, login required before checkout |
| Q4 | Can customers maintain multiple delivery addresses? (§20 Q17) | FEAT-07 | ❓ Open |
| Q5 | How is the tentative delivery date calculated? (§20 Q15) | FEAT-07 | ❓ Open |
| Q6 | Are delivery charges applicable? (§20 Q16) | FEAT-07 | ❓ Open |
| Q7 | Is payment simulated or real gateway? (§20 Q18, A-004) | FEAT-08 | ❓ Open |
| Q8 | What happens when payment fails? (§20 Q20) | FEAT-08 | ❓ Open |
| Q9 | How are gift points earned? (§14.2, §20 Q22) | FEAT-09 | ❓ Open |
| Q10 | What is the monetary value of one gift point? (§20 Q23) | FEAT-09 | ❓ Open |
| Q11 | What are the gift point redemption limits? (§20 Q24) | FEAT-09 | ❓ Open |
| Q12 | Do gift points expire? (§20 Q25) | FEAT-09 | ❓ Open |
| Q13 | What happens to redeemed points if order is cancelled? (§20 Q26) | FEAT-09, FEAT-12 | ❓ Open |
| Q14 | Does the 48-hour cancellation window start from order creation? (§20 Q11) | FEAT-12 | ❓ Open |
| Q15 | Which order statuses permit cancellation? (§20 Q12) | FEAT-12 | ❓ Open |
| Q16 | What happens to payment on cancellation — automatic refund? (§20 Q13–14) | FEAT-12 | ❓ Open |
| Q17 | What exactly determines a "related" product? (§20 Q6) | FEAT-15 | ❓ Open |
| Q18 | What should recommendations be based on exactly? (§20 Q27) | FEAT-14 | ❓ Open |
| Q19 | Is a simple rule-based recommendation algorithm acceptable? (§20 Q29) | FEAT-14 | ❓ Open |

**Already resolved:**

| # | Question | Resolution |
|---|---|---|
| R1 | Physical books or eBooks? | Physical books only (2026-08-24) |
| R2 | Where does the initial catalogue come from? | Open Library via offline Python seed script (2026-08-24) |
| R3 | Who maintains the catalogue? | No in-application admin — re-run the seed script (2026-08-24) |
| R4 | Are external metadata sources permitted? | Yes — Open Library (2026-08-24) |
| R5 | What can a guest do? | Browse catalogue + add to basket; checkout/payment requires login (2026-08-27) |
| R6 | What token strategy? | JWT, HMAC-SHA256, 24-hour expiry, no refresh tokens (2026-08-27) |
| R7 | What are the user registration fields? | firstName, lastName, email, password (2026-08-27) |
