# Feature Specification: Category Browsing

| Field | Value |
|---|---|
| **Feature ID** | FEAT-02 |
| **Title** | Category Browsing |
| **Status** | Approved — 2026-08-24 |
| **Author** | AI Assistant (drafted for review) |
| **Depends On** | FEAT-01 (Browse Book Catalogue — `Book` entity and catalogue API must exist) |
| **Blocks** | FEAT-03 (Search & Filter — filter-by-category depends on the Category model), FEAT-05 (Storefront Frontend — category navigation in the UI) |

---

## 1. Purpose

Allow any visitor (guest or registered) to browse the book catalogue **organised by category** — selecting a category and seeing only the books that belong to it.

FEAT-01 already delivers a flat, undivided list of all books. This feature layers a **category dimension** on top of that, so customers can narrow their browsing to a subject area they care about (e.g. "Technology", "History", "Fiction") without needing a search box.

This specification describes **what** the feature does. All technical decisions — how `Category` is modelled in the database, what the API endpoints look like, how the relationship between `Book` and `Category` is implemented — are deferred to the Plan and Design stages.

---

## 2. Traceability to Business Requirements

| Business Requirement | Reference |
|---|---|
| Customer can select a product category | §7.2 |
| Customer can access the catalogue for a category | §7.2 |
| Customer can browse products within a category | §7.2 |
| Category-based catalogue is a confirmed business rule | BR-002 |
| Guest user can browse categories | §5.2 |
| Registered user can select product categories | §6.2 |
| Registered user can browse products within categories | §6.2 |

**Requirement classification (per §21 of business-requirements.md):** **Confirmed** — category browsing is explicitly required by §7.2 and BR-002.

---

## 3. In Scope

The following are included in this feature:

1. Displaying a **list of all available categories** in the store.
2. Selecting a category and seeing a **filtered, paginated list** of books belonging to that category.
3. The category filter carrying the same **availability indicator**, **pagination**, and **default ordering** (newest first) as the main catalogue (FEAT-01).
4. Each category knowing its **book count** — how many books belong to it — so it can be displayed alongside the category name.
5. **Upgrading the `Book` data model** so that category is a proper first-class concept rather than a plain string on the `Book` record.
6. Seeding the category data automatically from the existing `books.json` seed file — no separate data input required.

---

## 4. Out of Scope (for this feature)

| Excluded | Deferred To |
|---|---|
| Keyword search within a category | FEAT-03 |
| Filtering by price, language, or availability within a category | FEAT-03 |
| Nested / hierarchical categories (sub-categories) | Not planned — flag if needed |
| Brand browsing (§7.3) | Separate feature — the meaning of "brand" for a book is an open question (§20 Q2) |
| Creating, editing, or deleting categories through the application | Out of scope for the whole project (§3.2) — category maintenance is done by re-running the seed script |
| Frontend category navigation UI | FEAT-05 |
| Related products grouped by category | FEAT-15 |

Nothing in this list may be silently added during Plan, Design, or Coding without a new spec.

---

## 5. User Stories

### US-01 — Anyone can see the list of categories

> As a **visitor** (guest or logged in),
> I want to **see a list of the categories available in the store**,
> so that I can **navigate to the subject area I'm interested in**.

**Preconditions:** The store has at least one book in its catalogue.
**Trigger:** Visitor opens or navigates to the categories list.
**Expected result:** A list of all categories is displayed. Each entry shows the category name and the number of books in that category.

---

### US-02 — Anyone can browse books in a category

> As a **visitor**,
> I want to **select a category and see only the books in that category**,
> so that I can **focus my browsing on a subject I care about**.

**Preconditions:** The selected category exists and has at least one book.
**Trigger:** Visitor selects a category from the list.
**Expected result:** A paginated list of books belonging to that category is displayed, with the same book information as the main catalogue list (cover, title, author(s), price, availability).

---

### US-03 — Empty category is handled gracefully

> As a **visitor**,
> I want to **see a clear message if a category has no books**,
> so that I **do not think the page is broken**.

**Preconditions:** A category exists but no books are currently assigned to it. (Unlikely in the seeded catalogue, but must be handled correctly.)
**Trigger:** Visitor selects an empty category.
**Expected result:** The category page is shown with an appropriate "no books available" message. It is not an error state.

---

### US-04 — Requesting a non-existent category is handled

> As a **visitor** (or API consumer) who requests a category that does not exist,
> I want to receive a **clear "not found" response**,
> so that I **understand the category doesn't exist rather than seeing a confusing error**.

**Preconditions:** None.
**Trigger:** A request is made for a category slug or ID that is not in the catalogue.
**Expected result:** A clear "not found" response. In the REST API this is HTTP 404 with a structured error body, consistent with the error format established in FEAT-01.

---

## 6. The `Category` Concept — Business View

A **Category** in this system is a named subject classification for books. Every book belongs to exactly one category.

| Attribute | Type | Required? | Description | Source |
|---|---|---|---|---|
| `id` | Unique identifier | Yes | Internal identifier assigned by the system. | Generated by the store |
| `name` | Text | Yes | The category's display name (e.g. "Fiction", "Technology", "History"). Must be unique across all categories. | Derived from `books.json` seed data |
| `slug` | Text (URL-safe) | Yes | A lowercase, hyphen-separated version of the name used in URLs (e.g. `"self-help"`, `"fiction"`). Must be unique. | Derived from `name` at seed time |
| `bookCount` | Whole number ≥ 0 | Yes | How many books currently belong to this category. Shown on the category list. | Computed from books in the catalogue |

**Notes for the reviewer:**

- **One category per book.** A book belongs to exactly one category. Multi-category support is deferred — it is not needed by any feature through Tier 4.
- **`slug` is for URLs.** Rather than exposing internal numeric IDs in URLs like `/api/books?categoryId=3`, we use the slug: `/api/books?category=technology`. Slugs are stable, readable, and won't change if the database is rebuilt.
- **`bookCount` is computed.** It is not stored as a column — it is derived at query time from the number of associated books. This keeps the data consistent automatically.
- **Categories come from the seed.** The set of categories is determined by the `category` values in `books.json`. Running the seed script again regenerates the category list from scratch.

---

## 7. Functional Requirements

| ID | Requirement |
|---|---|
| **FR-01** | The system MUST provide an endpoint that returns the list of all categories, each with its name, slug, and book count. |
| **FR-02** | The category list MUST be ordered alphabetically by name. |
| **FR-03** | The system MUST provide an endpoint that returns a paginated list of books filtered to a single category, identified by its slug. |
| **FR-04** | The filtered book list MUST apply the same default ordering (newest first by `createdAt`) and the same page size (12) as the main catalogue list (FEAT-01). |
| **FR-05** | The filtered book list MUST show the same book information as the main catalogue list: cover image, title, author(s), price, and availability indicator. |
| **FR-06** | If the visitor requests a category slug that does not exist, the system MUST return a clear "not found" response. |
| **FR-07** | If a valid category has zero books, the system MUST return an empty page (not an error). |
| **FR-08** | Both category endpoints MUST be accessible without logging in (guest access). |
| **FR-09** | The `Book` data model MUST be upgraded so that `category` is a proper relationship to a `Category` record, not a plain string. This is a **breaking internal change** — the Plan stage must account for the migration of existing seeded data. |
| **FR-10** | Category names MUST be matched in a **case-insensitive** manner when filtering (e.g. `?category=fiction` and `?category=Fiction` return the same results). |

---

## 8. Acceptance Criteria

The feature is considered **complete** when all of the following can be demonstrated:

1. Calling the categories endpoint returns a list of all categories present in `books.json`, each with a correct book count.
2. The category list is ordered alphabetically.
3. Selecting any category returns a paginated list of books belonging only to that category.
4. Pagination, page size (12), and newest-first ordering all work correctly on the category-filtered list.
5. Each book in the filtered list shows cover, title, author(s), price, and availability — identical to the main catalogue.
6. Requesting a category that does not exist returns a structured 404 response, consistent with the FEAT-01 error format.
7. Requesting a valid category with no books returns an empty page (zero results, no error).
8. All of the above work without authentication (guest access).
9. The existing `GET /api/books` endpoint from FEAT-01 continues to work correctly and returns **all books regardless of category** (no regression).
10. The `book` table's category data has been migrated from a plain string to a foreign key to the `category` table, confirmed via the H2 console.

---

## 9. Assumptions

| ID | Assumption |
|---|---|
| **A-01** | A book belongs to exactly one category. No multi-category support is needed for this feature. |
| **A-02** | Categories are derived entirely from the `books.json` seed file. There is no separate category configuration. |
| **A-03** | The set of categories is fixed for the lifetime of a seed run. Changing categories requires re-running the seed script. |
| **A-04** | Slugs are automatically derived from category names by lowercasing and replacing spaces with hyphens (e.g. "Self-Help" → `"self-help"`). No manual slug entry is required. |
| **A-05** | The eight categories currently in `books.json` (Fiction, Technology, History, Business, Self-Help, Science, Biography, Philosophy) are representative of what will exist at runtime. |
| **A-06** | Book count per category is computed at query time — it does not need to be cached or pre-computed for FEAT-02's scale. |

---

## 10. Open Questions — To Resolve Before Plan

| ID | Question | Impact |
|---|---|---|
| **OQ-01** | Should the `GET /api/books` endpoint accept `?category={slug}` as an optional filter parameter, OR should there be a separate endpoint like `GET /api/categories/{slug}/books`? | Affects API design. Both are valid; the Plan/Design stage will choose one. Proposed: use `GET /api/books?category={slug}` so the main books endpoint stays the single entry point for listing. |
| **OQ-02** | Should book count per category be a live count from the database, or is it acceptable to return it as part of the category query? | Performance question. For our scale, a live count join is perfectly fine. Flag if this changes. |
| **OQ-03** | What should happen to the `Book.category` plain-string column that exists from FEAT-01 during the migration to a foreign key? | Implementation detail — to be decided in Design. The seed data will drive re-creation; since we use `create-drop` with H2, there is no migration in the traditional sense. |

---

## 11. Non-Goals

Explicit reminders of what this feature does NOT do:

- Does not implement keyword search (FEAT-03).
- Does not implement any filtering other than by category.
- Does not implement sub-categories or hierarchical classification.
- Does not implement "brand" browsing (a separate concept, open question §20 Q2).
- Does not expose any way to add, edit, or delete categories through the application.
- Does not implement a category page in the frontend (FEAT-05).

---

## 12. Review Checklist for the Developer

Before approving this specification, please confirm:

- [ ] The scope in §3 matches your intent for FEAT-02.
- [ ] The out-of-scope list in §4 does not accidentally exclude something you wanted.
- [ ] The `Category` concept in §6 (name, slug, bookCount) is the right shape.
- [ ] The assumption of one category per book (A-01) is acceptable.
- [ ] The functional requirements in §7 make sense.
- [ ] The acceptance criteria in §8 are what you'd want to see as "done".
- [ ] Open question OQ-01 is answered: `GET /api/books?category={slug}` vs `GET /api/categories/{slug}/books`.
- [ ] The assumptions in §9 are correct.

Once approved, this spec becomes the input to the **Plan stage** for FEAT-02.
