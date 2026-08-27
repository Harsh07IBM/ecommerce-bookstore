# Implementation Plan: Category Browsing

| Field | Value |
|---|---|
| **Feature ID** | FEAT-02 |
| **Corresponds To Spec** | [feature-02-category-browsing.md](../specs/feature-02-category-browsing.md) |
| **Status** | Approved — 2026-08-24 |
| **Author** | AI Assistant (drafted for review) |

---

## 1. Purpose of This Document

The Specification defines **what** the feature must do.
The Design (next stage) will define **how each class, endpoint, and table looks in detail**.

This **Plan** sits between them. Its job is to:

- Confirm the technical approach for this feature.
- Break the work into an **ordered sequence of phases**, each producing a working, verifiable state.
- Identify every **file that will be created or modified**.
- List the **decisions the Design stage will lock down**.
- Surface **risks** early.

Class names, method signatures, exact SQL column definitions, and DTO shapes are **not** decided here — those belong to Design.

---

## 2. Technical Direction

The stack is already established from FEAT-01. No new dependencies are needed for FEAT-02.

| Concern | Choice | Notes |
|---|---|---|
| **Language / Framework** | Java 21, Spring Boot 3.4.x, Maven | Unchanged from FEAT-01 |
| **Database** | H2 in-memory, `create-drop` | Unchanged — schema is regenerated from entities on every restart |
| **ORM** | Spring Data JPA (Hibernate) | `@ManyToOne` relationship added to `Book` entity |
| **Query approach** | Derived query method (`findByCategory_Slug`) | No raw SQL needed for this feature |
| **API style** | REST JSON over HTTP | Two new endpoints added to the existing pattern |
| **Testing** | JUnit 5, Mockito, `@DataJpaTest`, `@WebMvcTest` | Same three-layer test strategy as FEAT-01 |

### Key technical decision (resolved here, not in Design)

The spec's OQ-01 asked whether category filtering should live at:
- `GET /api/books?category={slug}` — filter param on the existing books endpoint, OR
- `GET /api/categories/{slug}/books` — a sub-resource on a new categories endpoint

**Decision: `GET /api/books?category={slug}`.**
Rationale: keeps the books endpoint as the single place to get book lists. FEAT-03 will add more filter params to the same endpoint — this is the cleaner foundation for that.

---

## 3. What Changes vs What Is New

This feature touches existing FEAT-01 files AND creates new ones. Understanding which is which matters before coding begins.

| File | New or Modified | Why |
|---|---|---|
| `entity/Book.java` | **Modified** | Replace `String category` field with `@ManyToOne Category category` |
| `entity/Category.java` | **New** | The `Category` entity — maps to the new `category` table |
| `repository/CategoryRepository.java` | **New** | Spring Data interface for `Category` lookups |
| `repository/BookRepository.java` | **Modified** | Add `findByCategory_Slug(String slug, Pageable p)` method |
| `service/BookService.java` | **Modified** | Add `listBooksByCategory(slug, page, size)` method |
| `service/CategoryService.java` | **New** | Business logic for listing all categories with book counts |
| `controller/BookController.java` | **Modified** | Add optional `?category` query param to `listBooks()` |
| `controller/CategoryController.java` | **New** | `GET /api/categories` endpoint |
| `dto/CategoryDto.java` | **New** | Outward-facing shape of a category (id, name, slug, bookCount) |
| `exception/CategoryNotFoundException.java` | **New** | Thrown when a slug resolves to no category |
| `exception/GlobalExceptionHandler.java` | **Modified** | Add `@ExceptionHandler` for `CategoryNotFoundException` → 404 |
| `config/BookSeedLoader.java` | **Modified** | Seed categories from `books.json` before seeding books |

---

## 4. Implementation Phases

The feature is built in **6 phases**. Each phase produces a verifiable outcome. We pause between phases.

---

### Phase 1 — `Category` entity and repository

Create the `Category` JPA entity (`id`, `name`, `slug`) and its `CategoryRepository` extending `JpaRepository<Category, Long>`.

**What to verify:** Start the app and confirm via H2 console (`http://localhost:8080/h2-console`) that Hibernate created a `category` table with the correct columns.

**Teaches:**
- `@ManyToOne` relationship direction (Category is the "one" side)
- Why we keep Category as a separate entity rather than an enum — categories come from data, not code

---

### Phase 2 — Upgrade `Book` entity to use `@ManyToOne Category`

Replace the `String category` field on `Book` with a `@ManyToOne Category category` field. Update the seed loader to:
1. Parse all distinct category names from `books.json`
2. Derive slugs (lowercase, spaces → hyphens)
3. Save all `Category` records first
4. When building each `Book`, look up its `Category` by name and set the relationship

**What to verify:** H2 console shows:
- `category` table has 8 rows (one per subject)
- `book` table has a `category_id` foreign key column pointing to `category.id`
- All 113 books have a non-null `category_id`

**Teaches:**
- Foreign key relationship in Hibernate
- Why seeding order matters (categories must exist before books reference them)
- `@ManyToOne(fetch = FetchType.EAGER)` — why we load the category alongside each book

---

### Phase 3 — `CategoryRepository` query + `CategoryService`

Add a `findBySlugIgnoreCase(String slug)` method to `CategoryRepository`. Create `CategoryService` with:
- `listAllCategories()` — returns all categories ordered alphabetically, each with its book count
- `getCategoryBySlug(String slug)` — returns the category or throws `CategoryNotFoundException`

**What to verify:** Write a quick `@DataJpaTest` to confirm `findBySlugIgnoreCase` finds "fiction" when the stored slug is "fiction", and returns empty for an unknown slug.

**Teaches:**
- Spring Data derived query naming convention (`findBy` + field name + `IgnoreCase`)
- How to count related entities using a `@Query` with `COUNT`
- Custom exception pattern (mirrors `BookNotFoundException` from FEAT-01)

---

### Phase 4 — Extend `BookRepository` and `BookService`

Add `findByCategory_Slug(String slug, Pageable pageable)` to `BookRepository`.

In `BookService`, add `listBooksByCategory(String slug, int page, int size)`:
- Validates the slug exists (calls CategoryService or repo directly) — throws `CategoryNotFoundException` if not
- Returns paginated `Page<BookDto>` filtered to that category

**What to verify:** `@DataJpaTest` — seed a few books across two categories, call `findByCategory_Slug`, confirm only the correct books come back.

**Teaches:**
- Spring Data's nested-field query syntax (`Category_Slug` means "navigate the `category` association, then match `slug`")
- Reusing existing `toDto()` mapping unchanged — the DTO shape doesn't change for books

---

### Phase 5 — API layer: `CategoryController` + update `BookController`

Create `CategoryController` with `GET /api/categories` → returns `List<CategoryDto>`.

Update `BookController.listBooks()` to accept an optional `?category` query parameter. When present, delegate to `bookService.listBooksByCategory()`; when absent, keep the existing `bookService.listBooks()` behaviour exactly.

Add `CategoryNotFoundException` handling to `GlobalExceptionHandler` — returns HTTP 404 with the same `ErrorResponse` shape as FEAT-01's `BookNotFoundException`.

**What to verify:**
- `GET /api/categories` returns JSON array of 8 categories, alphabetically ordered, each with a correct `bookCount`
- `GET /api/books?category=fiction` returns only Fiction books
- `GET /api/books?category=FICTION` (uppercase) returns the same results (case-insensitive)
- `GET /api/books?category=unknown-slug` returns 404 with structured error body
- `GET /api/books` (no category param) returns all books — **no regression from FEAT-01**

**Teaches:**
- Optional `@RequestParam` — `required = false`, default null
- How one controller method branches based on presence/absence of a parameter
- Extending `GlobalExceptionHandler` with a new exception type

---

### Phase 6 — Automated tests

Write focused tests at all three layers:

**Repository (`@DataJpaTest`):**
- `findByCategory_Slug` returns only books for the given category
- `findByCategory_Slug` with unknown slug returns empty page
- `findBySlugIgnoreCase` is case-insensitive

**Service (Mockito, no Spring context):**
- `listAllCategories()` returns categories sorted alphabetically with correct counts
- `listBooksByCategory()` returns correct page of DTOs for a valid slug
- `listBooksByCategory()` throws `CategoryNotFoundException` for an unknown slug

**Controller (`@WebMvcTest`):**
- `GET /api/categories` → 200 with correct JSON array
- `GET /api/books?category=fiction` → 200 with filtered book list
- `GET /api/books?category=nope` → 404 with error body
- `GET /api/books` (no param) → 200, same as FEAT-01 — regression guard

**What to verify:** `mvn test` passes green with no failures.

**Teaches:**
- Testing a modified controller method with an optional parameter — both the "param present" and "param absent" code paths need coverage
- How `@DataJpaTest` isolates the JPA layer — seed loader does not run, tests build their own data

---

## 5. Files — Complete List

```
backend/src/main/java/com/harsh/bookstore/
│
├── entity/
│   ├── Book.java                         MODIFIED  (String category → @ManyToOne Category)
│   └── Category.java                     NEW
│
├── repository/
│   ├── BookRepository.java               MODIFIED  (add findByCategory_Slug)
│   └── CategoryRepository.java           NEW
│
├── service/
│   ├── BookService.java                  MODIFIED  (add listBooksByCategory)
│   └── CategoryService.java              NEW
│
├── controller/
│   ├── BookController.java               MODIFIED  (add optional ?category param)
│   └── CategoryController.java           NEW
│
├── dto/
│   └── CategoryDto.java                  NEW
│
├── exception/
│   ├── CategoryNotFoundException.java    NEW
│   └── GlobalExceptionHandler.java       MODIFIED  (add CategoryNotFoundException handler)
│
└── config/
    └── BookSeedLoader.java               MODIFIED  (seed categories first, then books)

backend/src/test/java/com/harsh/bookstore/
│
├── repository/
│   ├── BookRepositoryTest.java           MODIFIED  (add category-filter tests)
│   └── CategoryRepositoryTest.java       NEW
│
├── service/
│   ├── BookServiceTest.java              MODIFIED  (add listBooksByCategory tests)
│   └── CategoryServiceTest.java          NEW
│
└── controller/
    ├── BookControllerTest.java           MODIFIED  (add ?category param tests + regression)
    └── CategoryControllerTest.java       NEW
```

---

## 6. Decisions for the Design Stage

The Design document will lock down the following — they are intentionally left open here:

| # | Decision |
|---|---|
| **D-01** | Exact `@ManyToOne` fetch strategy on `Book.category` — `EAGER` (proposed, same rationale as authors) vs `LAZY` |
| **D-02** | How `bookCount` is computed in `CategoryService` — a `@Query` with `COUNT`, a `@Formula`, or a derived `countByCategory(Category c)` call |
| **D-03** | Exact `CategoryDto` shape — include `id`? just `name` + `slug` + `bookCount`? |
| **D-04** | Slug derivation logic — exact algorithm for converting category name to slug (e.g. "Self-Help" → `"self-help"`) — implemented in `BookSeedLoader` |
| **D-05** | Whether `BookController.listBooks()` uses a single merged method (with nullable category param) or delegates to two separate private methods internally |

---

## 7. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `Book` entity change breaks existing FEAT-01 tests | Medium | Small | All FEAT-01 tests use a `sampleBook()` helper — update the helper to set a `Category` object instead of a string. One change, all tests fixed. |
| `BookSeedLoader` seeding order — books inserted before categories exist | Medium | High — foreign key violation at startup | Phase 2 explicitly seeds categories first, books second. Verified in the H2 console before Phase 3 begins. |
| `findByCategory_Slug` with H2 case sensitivity | Low | Small | Use `IgnoreCase` suffix on the repository method — Spring Data generates `LOWER()` SQL, which works on H2 and Postgres alike. |
| `BookController` regression — existing `GET /api/books` breaks | Low | High — FEAT-01 acceptance criteria violated | Phase 5 explicitly includes a regression test. `mvn test` must be green before Phase 6 is considered done. |

---

## 8. Verification Approach

The feature is "Plan complete" when:

1. All 6 phases are done in order.
2. Every acceptance criterion in [spec §8](../specs/feature-02-category-browsing.md#8-acceptance-criteria) is demonstrably met (Phase 5 manual verification).
3. All automated tests are green (`mvn test`) — Phase 6.
4. The existing FEAT-01 regression test (`GET /api/books` with no params) still passes.

---

## 9. Review Checklist for the Developer

Before approving this plan, please confirm:

- [ ] The 6-phase sequence in §4 makes sense as a build order.
- [ ] The decision to use `GET /api/books?category={slug}` (not a sub-resource path) is confirmed.
- [ ] The file list in §5 covers everything you expect to be created or modified.
- [ ] The Design-stage decisions in §6 are understood — they will be answered in the next document.
- [ ] The risks in §7 are acceptable.

Once approved, this plan becomes the input to the **Design stage** for FEAT-02.
