# Feature Specification: Search & Filter

| Field | Value |
|---|---|
| **Feature ID** | FEAT-03 |
| **Title** | Search & Filter |
| **Status** | Approved — 2026-08-24 |
| **Author** | AI Assistant (drafted for review) |
| **Depends On** | FEAT-01 (Browse Book Catalogue — `Book` entity and catalogue API), FEAT-02 (Category Browsing — `Category` entity and category model) |
| **Blocks** | FEAT-05 (Storefront Frontend — search bar and filter panel in the UI) |

---

## 1. Purpose

Allow any visitor (guest or registered) to **find specific books** in the catalogue by typing a keyword and/or applying one or more filters (category, price range, language, availability).

FEAT-01 delivers a flat paginated list of all books.
FEAT-02 delivers category-scoped browsing.
This feature adds **intent-driven discovery** — when a visitor knows what they want, they can search for it directly rather than browsing page by page.

This specification describes **what** the feature does. Technical implementation details (query construction, indexing strategy, endpoint design) are deferred to the Plan and Design stages.

---

## 2. Traceability to Business Requirements

| Business Requirement | Reference |
|---|---|
| Customer should be able to search the bookstore catalogue | §9.1 |
| Catalogue should support filtering capabilities | §9.2 |
| Search and filter are confirmed features per the capstone workflow | §9 (preamble) |
| Guest user can browse and view products | §5.2 |
| Registered user can browse products within categories | §6.2 |

**Requirement classification (per §21 of business-requirements.md):** **Confirmed** — search and filtering are explicitly required by §9.1 and §9.2. The exact filter attributes are to be finalised here (§9.2 deferred this decision to specification time).

---

## 3. In Scope

The following are included in this feature:

1. **Keyword search** — searching the catalogue by a text query that is matched against book title, author name(s), and description.
2. **Category filter** — restricting results to a single category (builds on the `Category` model from FEAT-02).
3. **Price range filter** — restricting results to books whose price falls within a specified minimum and/or maximum.
4. **Availability filter** — restricting results to books that are currently in stock.
5. **Sorting** — choosing the order of results: newest first (default), price low-to-high, price high-to-low.
6. **Combining** — any combination of keyword + filters + sort applied together in a single request.
7. **Pagination** of search/filter results with the same page size (12) as the main catalogue.
8. The existing `GET /api/books` endpoint from FEAT-01 is **extended** to accept the new query parameters — no new separate search endpoint is introduced.

---

## 4. Out of Scope (for this feature)

| Excluded | Reason / Deferred To |
|---|---|
| Full-text search with relevance ranking (Elasticsearch, Solr) | Out of scope for this project — simple SQL `LIKE`/`ILIKE` matching is sufficient |
| Fuzzy / typo-tolerant search | Not required by the business requirements |
| Search suggestions / autocomplete | Not confirmed in business requirements |
| Saved searches or search history | Not confirmed in business requirements |
| Filtering by publisher, publication date, or page count | Not confirmed as required filters — can be added if approved |
| Filtering by language | See OQ-01 — proposed as in scope but awaits developer confirmation |
| Brand browsing / filtering (§7.3) | Separate feature — "brand" for a book is an open question (§20 Q2) |
| Related products | FEAT-15 |
| Frontend search bar and filter UI | FEAT-05 |
| Admin-managed filter facets | Out of scope for the whole project (§3.2) |

Nothing in this list may be silently added during Plan, Design, or Coding without a new spec.

---

## 5. User Stories

### US-01 — Anyone can search by keyword

> As a **visitor**,
> I want to **type a keyword and see all books whose title, author, or description contains that word**,
> so that I can **quickly find a specific book or author I have in mind**.

**Preconditions:** The catalogue has at least one book.
**Trigger:** Visitor submits a search query.
**Expected result:** A paginated list of books whose title, author(s), or description contains the keyword (case-insensitive). If no books match, a clear empty-results response is returned (not an error).

---

### US-02 — Anyone can filter by category

> As a **visitor**,
> I want to **filter the catalogue by category**,
> so that I can **see only books from a subject area I care about**.

**Preconditions:** At least one category and one book in that category exist.
**Trigger:** Visitor applies a category filter.
**Expected result:** Only books belonging to that category are shown, paginated.

---

### US-03 — Anyone can filter by price range

> As a **visitor**,
> I want to **set a minimum and/or maximum price**,
> so that I can **find books within my budget**.

**Preconditions:** The catalogue has books with varying prices.
**Trigger:** Visitor applies a price filter.
**Expected result:** Only books with a price within the specified range are shown. Either bound (min or max) is optional — omitting one means "no lower/upper limit".

---

### US-04 — Anyone can filter by availability

> As a **visitor**,
> I want to **filter to only in-stock books**,
> so that I can **avoid wasting time on books I cannot currently buy**.

**Preconditions:** The catalogue has at least one in-stock book.
**Trigger:** Visitor enables the "available only" filter.
**Expected result:** Only books with `stockQuantity > 0` are shown.

---

### US-05 — Anyone can sort search results

> As a **visitor** looking at a list of results,
> I want to **choose the order** — newest first, cheapest first, or most expensive first —
> so that I can **scan results in the order most useful to me**.

**Preconditions:** A result set exists (from a search, filter, or the full catalogue).
**Trigger:** Visitor selects a sort order.
**Expected result:** Results are reordered accordingly and pagination resets to page 0.

---

### US-06 — Filters and search can be combined

> As a **visitor**,
> I want to **search with a keyword AND apply one or more filters at the same time**,
> so that I can **narrow results as precisely as I need**.

**Preconditions:** The catalogue has books.
**Trigger:** Visitor enters a keyword and applies a category and/or price filter simultaneously.
**Expected result:** Results match ALL active constraints — only books that satisfy the keyword AND the category AND the price range are shown.

---

### US-07 — No results is not an error

> As a **visitor** whose search or filter combination matches nothing,
> I want to see a **clear "no results" response**,
> so that I **understand no books match, rather than thinking the system is broken**.

**Preconditions:** None.
**Trigger:** Visitor submits a combination that matches zero books.
**Expected result:** An empty results page is returned (total = 0, empty content list). HTTP 200, not 404.

---

## 6. Search & Filter Parameters — Business View

The following parameters can be applied to the book catalogue. All are optional. When none are supplied, the full catalogue is returned (identical behaviour to FEAT-01).

| Parameter | Type | Description | Example |
|---|---|---|---|
| `q` | Text | Keyword to match against title, author(s), and description. Case-insensitive. Partial match (substring). | `q=tolkien` |
| `category` | Category slug | Restrict to books in this category. | `category=fiction` |
| `minPrice` | Decimal ≥ 0 | Minimum price (inclusive). | `minPrice=200` |
| `maxPrice` | Decimal > 0 | Maximum price (inclusive). Must be ≥ minPrice if both are provided. | `maxPrice=500` |
| `available` | Boolean | When `true`, only in-stock books are returned. When `false` or absent, all books are returned. | `available=true` |
| `sort` | Enum | Ordering of results. Allowed values: `newest` (default), `price_asc`, `price_desc`. | `sort=price_asc` |
| `page` | Integer ≥ 0 | Zero-based page index. Default: 0. | `page=2` |
| `size` | Integer 1–100 | Page size. Default: 12. | `size=12` |

**Notes for the reviewer:**

- **`q` is a substring match** — `q=art` would match "The Art of War", "Restart", "Martin Luther King". No relevance ranking. Simple and sufficient for a bookstore of this size.
- **`category` uses slug** — consistent with FEAT-02's URL design (e.g. `"self-help"`, `"fiction"`). Case-insensitive.
- **`sort=newest`** matches the default ordering from FEAT-01 (by `createdAt` descending), so the unfiltered response is identical when no sort is specified.
- **`available=false`** (or omitting `available`) returns all books including out-of-stock ones. This is intentional — out-of-stock books are still visible (FEAT-01 FR-07).
- **Language filter** is listed as OQ-01 — see §10. Proposed as in scope but awaiting developer confirmation.

---

## 7. Functional Requirements

| ID | Requirement |
|---|---|
| **FR-01** | The `GET /api/books` endpoint MUST accept all parameters listed in §6 as optional query parameters. |
| **FR-02** | When `q` is provided, results MUST include only books where title, at least one author name, OR description contains the keyword (case-insensitive, substring). |
| **FR-03** | When `category` is provided, results MUST include only books belonging to that category (case-insensitive slug match). |
| **FR-04** | When `minPrice` is provided, results MUST include only books with price ≥ minPrice. |
| **FR-05** | When `maxPrice` is provided, results MUST include only books with price ≤ maxPrice. |
| **FR-06** | When both `minPrice` and `maxPrice` are provided, `minPrice` MUST be less than or equal to `maxPrice`. If not, the system MUST return a 400 Bad Request. |
| **FR-07** | When `available=true` is provided, results MUST include only books with `stockQuantity > 0`. |
| **FR-08** | When `sort=price_asc` is provided, results MUST be ordered by price ascending. |
| **FR-09** | When `sort=price_desc` is provided, results MUST be ordered by price descending. |
| **FR-10** | When `sort` is absent or `sort=newest`, results MUST be ordered by `createdAt` descending (identical to FEAT-01 default). |
| **FR-11** | An unrecognised `sort` value MUST return a 400 Bad Request. |
| **FR-12** | All active parameters MUST be combined with AND logic — a book must satisfy every active constraint to appear in results. |
| **FR-13** | When no parameters are provided, the endpoint MUST behave identically to the FEAT-01 implementation (full catalogue, newest first, page size 12). — **No regression.** |
| **FR-14** | A combination that matches zero books MUST return HTTP 200 with an empty page (zero `totalElements`), not a 404. |
| **FR-15** | All search and filter functionality MUST be accessible without logging in (guest access). |
| **FR-16** | The `q` parameter MUST be validated: if provided, it MUST NOT be an empty string or contain only whitespace. Such a request MUST return a 400 Bad Request. |

---

## 8. Acceptance Criteria

The feature is considered **complete** when all of the following can be demonstrated:

1. `GET /api/books?q=fiction` returns only books whose title, author(s), or description contains "fiction" (case-insensitive).
2. `GET /api/books?category=technology` returns only books in the Technology category.
3. `GET /api/books?minPrice=200&maxPrice=500` returns only books priced between ₹200 and ₹500 inclusive.
4. `GET /api/books?available=true` returns only books with stock > 0.
5. `GET /api/books?sort=price_asc` returns books ordered from cheapest to most expensive.
6. `GET /api/books?sort=price_desc` returns books ordered from most expensive to cheapest.
7. `GET /api/books?q=history&category=history&minPrice=300&available=true&sort=price_asc` returns books matching ALL of those constraints simultaneously.
8. A search that matches nothing returns HTTP 200 with `totalElements: 0` and an empty `content` array.
9. `GET /api/books?minPrice=500&maxPrice=100` returns HTTP 400.
10. `GET /api/books?sort=invalid` returns HTTP 400.
11. `GET /api/books?q=` (empty keyword) returns HTTP 400.
12. `GET /api/books` (no parameters) returns the same response as before FEAT-03 was built — full catalogue, newest first, 12 per page. No regression.
13. All of the above work without authentication.

---

## 9. Assumptions

| ID | Assumption |
|---|---|
| **A-01** | Simple `LIKE`/`ILIKE` SQL substring matching is sufficient for this catalogue size. No full-text search engine is required. |
| **A-02** | Search is case-insensitive for all text parameters (`q`, `category`). |
| **A-03** | Keyword search matches against `title`, `authors`, and `description` only — not against ISBN, publisher, or other fields. |
| **A-04** | Prices are in Indian Rupees (₹, INR) — consistent with FEAT-01 assumption A-02. |
| **A-05** | The `available` filter applies the same rule as the availability indicator in FEAT-01: a book is available when `stockQuantity > 0`. |
| **A-06** | Sorting is applied to the full filtered result set before pagination, so the sort is globally consistent across all pages. |
| **A-07** | The `GET /api/books` endpoint is the single entry point for all listing, filtering, and searching — no parallel `/api/search` endpoint is introduced. |

---

## 10. Open Questions — To Resolve Before Plan

| ID | Question | Impact |
|---|---|---|
| **OQ-01** | Should a **language filter** (`?language=en`) be included? The `Book` entity already has a `language` field (FEAT-01 design). | If yes: add `language` to the parameter table in §6 and add a corresponding FR. If no: language stays a non-filterable display field. **Proposed: include it — it's a natural catalogue filter and the data already exists.** |
| **OQ-02** | Should the `q` keyword search include **ISBN**? A customer who knows the exact ISBN could search for it. | Low impact — just an additional field in the LIKE clause. **Proposed: yes, include ISBN in the search scope.** |
| **OQ-03** | Should **search scope** (`q`) be configurable per request (e.g. `searchIn=title,authors`) or always fixed to title + authors + description? | Adds complexity. **Proposed: fix the scope to title + authors + description (+ ISBN per OQ-02). No per-request configuration.** |

---

## 11. Non-Goals

Explicit reminders of what this feature does NOT do:

- Does not implement autocomplete, suggestions, or search history.
- Does not implement relevance ranking or full-text search scoring.
- Does not implement filters beyond those listed in §6.
- Does not implement brand or publisher browsing.
- Does not implement any frontend UI (search bar, filter panel) — that is FEAT-05.
- Does not change any behaviour of `GET /api/categories` (FEAT-02 endpoint).
- Does not implement sorting within a specific category listing beyond what is specified here.

---

## 12. Review Checklist for the Developer

Before approving this specification, please confirm:

- [ ] The scope in §3 is correct — keyword + category + price + availability + sort is what you want.
- [ ] The out-of-scope list in §4 does not accidentally exclude something you wanted in this feature.
- [ ] The search/filter parameters in §6 are the right set (nothing missing, nothing extra).
- [ ] The `q` matching against title + author + description (A-03) is acceptable.
- [ ] Simple `LIKE` matching (A-01) is sufficient — no need for Elasticsearch or similar.
- [ ] The functional requirements in §7 make sense, especially the AND combination logic (FR-12).
- [ ] The acceptance criteria in §8 are what you'd want to see as "done".
- [ ] **OQ-01 answered:** include language filter — yes or no?
- [ ] **OQ-02 answered:** include ISBN in search scope — yes or no?
- [ ] **OQ-03 answered:** fixed search scope (proposed) is acceptable — yes or no?

Once approved, this spec becomes the input to the **Plan stage** for FEAT-03.
