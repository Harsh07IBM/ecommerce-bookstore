# Feature Specification: Browse Book Catalogue

| Field | Value |
|---|---|
| **Feature ID** | FEAT-01 |
| **Title** | Browse Book Catalogue |
| **Status** | Approved — 2026-08-24 |
| **Author** | AI Assistant (drafted for review) |
| **Depends On** | None (this is the foundational feature) |
| **Blocks** | FEAT-02 (Category Browsing), FEAT-03 (Search & Filter), all shopping features |

---

## 1. Purpose

Allow any visitor (logged in or not) to view the list of books available in the store, and to view the full details of any single book by selecting it.

This is the read-only foundation of the storefront. Every downstream feature (basket, order, buy-again, recommendations) depends on the `Book` concept defined here.

This specification describes **what** the feature does. Technical decisions (framework, database, API shape, class design) are deferred to the Plan and Design stages.

---

## 2. Traceability to Business Requirements

This feature is derived from the following items in [business-requirements.md](../business-requirements.md):

| Business Requirement | Reference |
|---|---|
| Guest can view available books | §5.2 |
| Guest can browse products | §5.2 |
| Guest can view product details | §5.2 |
| Registered user can access the product catalogue | §6.2 |
| Registered user can select a product | §6.2 |
| Catalogue access | §7.1 |
| Product selection | §7.4 |
| Product availability shown in catalogue | §7.5 |
| Sufficient product information for selection decision | §8 |

Confirmed classification (per §26 of business requirements): **Confirmed** — supported by capstone requirements and wireframes.

---

## 3. In Scope

The following are included in this feature:

1. Displaying a **list** of books available in the store.
2. Displaying the **details** of a single book when selected.
3. Showing an **availability indicator** (in stock / out of stock) on both list and detail views.
4. Basic **pagination** of the book list (finite page size, ability to move between pages).
5. A **default ordering** of the list (e.g. newest books first).
6. Seeding the store with real book data from a public source (Google Books API) via a one-time script.

---

## 4. Out of Scope (for this feature)

The following are recognised as valid future work but are **not** part of FEAT-01:

- Searching books by keyword → deferred to FEAT-03
- Filtering by category, price, language, etc. → deferred to FEAT-03
- Category-based navigation (Categories page, category tree) → deferred to FEAT-02
- Brand browsing → deferred (also depends on §17.5 Q2: what "brand" means for a book)
- Related products on the detail page → deferred (§7.6, requires related-product logic)
- Adding to basket → deferred to a later feature
- Recommendations on any page → deferred to later feature
- In-application admin capabilities (add/edit/delete books, categories, users, orders) → **out of scope for the project** per [business-requirements §3.2](../business-requirements.md#32-out-of-scope-unless-explicitly-approved). Catalogue maintenance is handled by re-running the offline seed script.
- Book reviews / ratings → not in scope for this project (no confirmed requirement).
- Multiple images per book (only the cover is shown) → deferred

Nothing in this list may be silently added during Plan, Design, or Coding without a new spec.

---

## 5. User Stories

### US-01 — Anyone can view the book list

> As a **visitor** (guest or logged in),
> I want to **see a list of books available in the store**,
> so that I can **discover what I might want to read or buy**.

**Preconditions:** The store has at least one book in its catalogue.
**Trigger:** Visitor opens the store's catalogue page.
**Expected result:** A list of books is displayed, each showing at minimum the cover image, title, author(s), price, and availability.

---

### US-02 — Anyone can view a book's full details

> As a **visitor**,
> I want to **click a book in the list and see its full details**,
> so that I can **decide whether the book is what I'm looking for**.

**Preconditions:** The book exists in the catalogue.
**Trigger:** Visitor selects a book from the list.
**Expected result:** A detail view is shown containing the full description, publisher, publication date, ISBN, page count, language, category, price, and availability.

---

### US-03 — Availability is visible

> As a **visitor**,
> I want to **see clearly whether a book is currently available**,
> so that I don't waste time trying to buy something out of stock.

**Preconditions:** The book has a stock quantity recorded.
**Expected result:** If `stockQuantity > 0`, the book is shown as **Available**. Otherwise it is shown as **Out of stock**. The exact stock number is **not** shown to the customer.

---

### US-04 — The list is paginated

> As a **visitor** browsing a large catalogue,
> I want the **list to be paginated**,
> so that the page loads quickly and I can navigate through all books.

**Preconditions:** The catalogue may contain many books.
**Expected result:** The list is broken into pages of a fixed size (proposed: **12 books per page**). The visitor can move to the next / previous page, and can see which page they are on.

---

## 6. The `Book` Concept — Business View

A **Book** in this system is a physical book offered for sale. Every book has the following business-level attributes:

| Attribute | Type / Format | Required? | Description | Source |
|---|---|---|---|---|
| `id` | Unique identifier | Yes | Internal identifier assigned by the system. | Generated by the store |
| `isbn` | 10 or 13 characters | Yes | International Standard Book Number. Must be unique across the catalogue. | Google Books |
| `title` | Text | Yes | The book's title. | Google Books |
| `authors` | List of names | Yes | One or more author names. Order preserved. | Google Books |
| `description` | Text (long) | Yes | Short summary shown on the details page. | Google Books |
| `coverImageUrl` | URL | Yes | Address of the book's cover image. | Google Books |
| `publisher` | Text | No | Publisher name. Some old books may lack this. | Google Books |
| `publishedDate` | Year, or full date | No | When the book was first published. Format may vary. | Google Books |
| `pageCount` | Whole number ≥ 1 | No | Number of pages. Not always available. | Google Books |
| `language` | Text (e.g. `en`, `hi`) | Yes | ISO language code. | Google Books |
| `category` | Text | Yes | A single primary category (e.g. `Fiction`, `Technology`, `History`). | Google Books (first category returned) |
| `price` | Money, in ₹ (INR) | Yes | Selling price. Must be > 0. | Generated by the store |
| `stockQuantity` | Whole number ≥ 0 | Yes | Units on hand. Not shown to the customer directly. | Generated by the store |
| `createdAt` | Timestamp | Yes | When the book was added to the catalogue. Used for default ordering. | System |

**Notes for the reviewer:**

- **Multiple authors** are modelled as a simple list of names at this stage. A dedicated Author entity (with author bios etc.) is deferred until a feature actually needs it.
- **Single category** per book. A book-to-many-categories model is deferred until it's actually needed by FEAT-02.
- **Price is in Indian Rupees (₹)**. If a different currency is preferred, flag it now.
- **Stock quantity is not displayed** to customers — only the derived Available / Out of stock indicator is. This is intentional: exposing exact stock invites screen-scraping and is not useful information for the customer.

---

## 7. Functional Requirements

| ID | Requirement |
|---|---|
| **FR-01** | The system MUST display a paginated list of all books in the catalogue. |
| **FR-02** | The list view MUST show, for each book: cover image, title, author(s), price, availability indicator. |
| **FR-03** | The list view MUST default to ordering books by `createdAt` descending (newest first). |
| **FR-04** | The list view MUST support pagination with 12 books per page (proposed — reviewer to confirm). |
| **FR-05** | Selecting a book from the list MUST navigate the visitor to that book's detail view. |
| **FR-06** | The detail view MUST show the fields listed in §6, except `stockQuantity` (which is used to derive the Available / Out of stock indicator only) and `createdAt` (internal use). |
| **FR-07** | A book with `stockQuantity == 0` MUST be shown as "Out of stock" but MUST still be visible in the list and MUST still have a working detail page. |
| **FR-08** | Both list and detail views MUST be accessible without logging in (guest access). |
| **FR-09** | If a requested book does not exist, the detail view MUST return a clear "not found" response. |
| **FR-10** | Cover images that fail to load (broken URL) MUST fall back to a placeholder image (visual behavior; exact placeholder to be decided in Design). |

---

## 8. Acceptance Criteria

The feature is considered **complete** when all of the following can be demonstrated:

1. `data/seed/books.json` contains at least **50 real books** across at least **5 distinct categories**, populated from Google Books.
2. Opening the catalogue page shows a paginated list of books with cover, title, authors, price, and availability.
3. Pagination controls allow navigating forward and backward through pages, and stop correctly at the first and last pages.
4. Selecting any book navigates to a detail page showing all fields listed in §6 except stock quantity and createdAt.
5. At least one seeded book has `stockQuantity == 0` (manually set) and is shown as "Out of stock" in both list and detail views.
6. Requesting a non-existent book returns a clear "not found" response.
7. A visitor who is not logged in can perform all of the above without any authentication step.
8. The default order of the catalogue list is newest-first (by `createdAt`).

---

## 9. Assumptions

The following assumptions underpin this specification and must be validated during review:

| ID | Assumption |
|---|---|
| **A-01** | Physical books only (confirmed by developer). No eBooks in this feature. |
| **A-02** | Currency is Indian Rupees (₹, INR). |
| **A-03** | Prices are stored as fixed selling prices per book. No discounts, no dynamic pricing in this feature. |
| **A-04** | Google Books API is a suitable public source for book metadata. |
| **A-05** | The catalogue is populated exclusively by running the offline seed script. Any change to the catalogue (new books, price updates, stock changes) is done by editing the seed source and re-running the script — there is no runtime admin interface. |
| **A-06** | Page size of 12 books per page is acceptable. |
| **A-07** | A single primary category per book is acceptable for this feature. Books with multiple Google Books categories will use the first one returned. |

---

## 10. Open Questions — Resolved

Resolved on approval 2026-08-24.

| ID | Question | Resolution |
|---|---|---|
| **OQ-01** | Currency? | ₹ (INR) confirmed. |
| **OQ-02** | Page size? | 12 books per page confirmed. |
| **OQ-03** | Books without cover images? | Use a placeholder image. Do not skip the book. |
| **OQ-04** | "Out of stock" visual? | Deferred to the Design stage (UI concern). |
| **OQ-05** | Out-of-stock ordering? | Intermixed with in-stock books by `createdAt` — do not bury them at the bottom. Simplifies logic and matches most real bookstores. |

---

## 11. Non-Goals

Explicit reminders of what this feature does NOT do, to prevent scope creep:

- Does not implement a shopping basket.
- Does not implement search, filter, or sort by anything other than the default `createdAt`.
- Does not implement categories as a browsable concept (Category pages, `/categories` navigation).
- Does not implement user accounts or login (deferred to a later feature).
- Does not implement recommendations, related products, or "Buy Again".
- Does not perform any live call to Google Books at runtime. Google Books is used only in the offline seed script.

---

## 12. Review Checklist for the Developer

Before approving this specification, please confirm:

- [x] The scope in §3 matches your intent for the first feature.
- [x] The out-of-scope list in §4 does not accidentally exclude something you wanted.
- [x] The `Book` fields in §6 include everything you want and nothing extra.
- [x] The functional requirements in §7 make sense.
- [x] The acceptance criteria in §8 are what you'd want to see as "done".
- [x] The assumptions in §9 are correct.
- [x] All open questions in §10 have your answers.

Once approved, this spec becomes the input to the **Plan stage**, where we'll break the work into ordered implementation steps.
