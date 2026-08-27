# Implementation Plan: Search & Filter

| Field | Value |
|---|---|
| **Feature ID** | FEAT-03 |
| **Corresponds To Spec** | [feature-03-search-and-filter.md](../specs/feature-03-search-and-filter.md) |
| **Status** | Approved — 2026-08-24 |
| **Author** | AI Assistant (drafted for review) |

---

## 1. Purpose of This Document

The Specification defines **what** the feature must do.
The Design (next stage) will define **how each class, method, and query looks in detail**.

This **Plan** sits between them. Its job is to:

- Confirm the technical approach for dynamic query construction.
- Break the work into an **ordered sequence of phases**, each producing a verifiable state.
- Identify every **file that will be created or modified**.
- List the **decisions the Design stage will lock down**.
- Surface **risks** early.

Class names, method signatures, exact JPQL/SQL, and DTO shapes are **not** decided here — those belong to Design.

---

## 2. Technical Direction

The stack is unchanged from FEAT-01 and FEAT-02. One meaningful new technique is introduced: **JPA Specifications** for dynamic query construction.

| Concern | Choice | Notes |
|---|---|---|
| **Language / Framework** | Java 21, Spring Boot 3.4.x, Maven | Unchanged |
| **Database** | H2 in-memory, `create-drop` | Unchanged |
| **ORM** | Spring Data JPA (Hibernate) | Extended with `JpaSpecificationExecutor` |
| **Dynamic query approach** | **JPA Specifications** (`Specification<Book>`) | See rationale below |
| **API style** | REST JSON — extend existing `GET /api/books` | No new endpoint, only new optional params |
| **Testing** | JUnit 5, Mockito, `@DataJpaTest`, `@WebMvcTest` | Same three-layer strategy |

### Why JPA Specifications (not `@Query` or query-by-example)

The core challenge of FEAT-03 is **dynamic queries** — any combination of `q`, `category`, `minPrice`, `maxPrice`, `available`, and `sort` can be active at once. That is 2⁵ = 32 possible filter combinations for the boolean flags alone, and infinitely more when ranges are involved.

Three approaches exist:

| Approach | What it does | Why we reject it |
|---|---|---|
| Hardcoded `@Query` per combination | Write 32+ JPQL strings | Unmaintainable — adding one filter means rewriting every permutation |
| Query-by-Example (QBE) | Spring Data matches non-null fields | Cannot handle `LIKE`, ranges, or `JOIN` across `Category` — too limited |
| **JPA Specifications** | Build a `WHERE` clause programmatically from predicate objects | Handles all combinations cleanly. Each filter is one small `Predicate` method. They are AND-joined at runtime. This is the standard Spring Data pattern for dynamic queries. |

**Decision: JPA Specifications.**

This requires one small addition to `BookRepository`: it must extend `JpaSpecificationExecutor<Book>` in addition to `JpaRepository<Book, Long>`. That is a one-line change and does not break any existing method.

---

## 3. What Changes vs What Is New

| File | New or Modified | Why |
|---|---|---|
| `repository/BookRepository.java` | **Modified** | Add `JpaSpecificationExecutor<Book>` to the interface |
| `repository/BookSpecification.java` | **New** | Static factory methods — one per filter — returning `Specification<Book>` predicates |
| `service/BookService.java` | **Modified** | Replace `listBooks(page, size)` and `listBooksByCategory(slug, page, size)` with a single unified `listBooks(BookFilter, page, size)` method that builds the Specification |
| `dto/BookFilter.java` | **New** | A plain value-object carrying all the optional filter parameters from the HTTP request — keeps the service method signature clean |
| `controller/BookController.java` | **Modified** | `listBooks()` reads all new `@RequestParam`s, validates them, builds a `BookFilter`, passes it to the service |

No new entities, no new DTOs for the response (still `PagedResponse<BookDto>`), no new exceptions beyond what already exists. The error response for bad parameters uses the existing `GlobalExceptionHandler` → `IllegalArgumentException` → 400 path from FEAT-01.

---

## 4. Implementation Phases

The feature is built in **5 phases**. Each phase produces a verifiable outcome.

---

### Phase 1 — `BookFilter` value object

Create `BookFilter` — a plain Java class (no Spring annotations, no JPA) that holds all the optional search and filter fields:
`q`, `categorySlug`, `minPrice`, `maxPrice`, `availableOnly`, `sort`.

**What to verify:** `BookFilter` compiles with sensible field types and a no-arg constructor. Nothing runs yet.

**Teaches:**
- Why separating "what the user asked for" (a filter object) from "how we query for it" (a Specification) keeps each class focused on one thing
- Value objects as a pattern — a class whose only job is to carry data, with no behaviour

---

### Phase 2 — `BookSpecification` predicates

Create `BookSpecification` as a class of **static factory methods**, each returning a `Specification<Book>`:

- `hasKeyword(String q)` — LIKE match on title, authors (via join), description, and isbn
- `hasCategory(String slug)` — JOIN to category, match on slug (case-insensitive)
- `hasPriceAtLeast(BigDecimal min)` — `price >= min`
- `hasPriceAtMost(BigDecimal max)` — `price <= max`
- `isAvailable()` — `stockQuantity > 0`

Each method returns `null` when its input is absent — Spring Data JPA treats a null Specification as "no constraint", so the AND-combination logic becomes trivially simple.

**What to verify:** `@DataJpaTest` — seed a small set of books across two categories with varying prices and stock. Call each Specification individually and verify it returns only the expected subset.

**Teaches:**
- What a `Specification<T>` is: a functional interface wrapping a JPA `CriteriaQuery` predicate
- Why returning `null` for "no filter" is idiomatic Spring Data — `Specification.where(null)` is a no-op
- The JPA Criteria API just enough to understand what the generated SQL will look like

---

### Phase 3 — Extend `BookRepository` with `JpaSpecificationExecutor`

Add `JpaSpecificationExecutor<Book>` to `BookRepository`'s extends clause. This gives the repository a new method: `findAll(Specification<Book> spec, Pageable pageable)`.

That is the only change to this file.

**What to verify:** The app still starts and `mvn test` still passes green — the existing FEAT-01 and FEAT-02 tests must not be broken by this one-line change.

**Teaches:**
- How Spring Data interfaces compose — you can extend multiple typed interfaces on one repository
- That `JpaSpecificationExecutor` adds methods without removing or changing any existing ones

---

### Phase 4 — Unify `BookService` with `BookFilter`

Refactor `BookService` to have a single `listBooks(BookFilter filter, int page, int size)` method that:

1. Reads each field from `BookFilter`
2. Builds the individual Specification predicates (calls `BookSpecification.hasKeyword(...)` etc.)
3. AND-combines them with `Specification.where(...).and(...)`
4. Applies the correct `Sort` based on `filter.getSort()` (`newest` / `price_asc` / `price_desc`)
5. Calls `bookRepository.findAll(combinedSpec, pageRequest)`
6. Maps the result to `Page<BookDto>` using the existing `toDto()` method

The old `listBooks(int page, int size)` and `listBooksByCategory(String slug, int page, int size)` are **replaced** by this single method. A `BookFilter` with all-null fields produces the same query as the old `listBooks()` — full catalogue, newest first.

**What to verify:** Mockito unit tests in `BookServiceTest`:
- A `BookFilter` with no fields set → `findAll` called with an all-null Specification (i.e. no WHERE clause) and `createdAt DESC` sort
- A `BookFilter` with `q="tolkien"` → `findAll` called with a Specification that includes the keyword predicate
- A `BookFilter` with `availableOnly=true` → Specification includes the availability predicate
- All predicates combined → single `findAll` call with the combined AND Specification

**Teaches:**
- `Specification.where(s1).and(s2).and(s3)` — the fluent builder pattern for combining predicates
- Why having one service method instead of many is better: less duplication, the code path for "no filters" is just a degenerate case of "some filters"

---

### Phase 5 — Update `BookController` + validation + automated tests

**Controller changes:**

Add `@RequestParam`s for `q`, `category`, `minPrice`, `maxPrice`, `available`, `sort` — all with `required = false`.

Add validation logic (before building `BookFilter`):
- `q` present but blank → throw `IllegalArgumentException` (→ 400)
- `minPrice` present but < 0 → throw `IllegalArgumentException` (→ 400)
- `maxPrice` present but < 0 → throw `IllegalArgumentException` (→ 400)
- Both `minPrice` and `maxPrice` present and `minPrice > maxPrice` → throw `IllegalArgumentException` (→ 400)
- `sort` present but not one of `newest`, `price_asc`, `price_desc` → throw `IllegalArgumentException` (→ 400)

After validation, build a `BookFilter`, call `bookService.listBooks(filter, page, size)`, wrap in `PagedResponse.from(...)`.

**What to verify (manual, via Postman or browser):**
Walk through every acceptance criterion in [spec §8](../specs/feature-03-search-and-filter.md#8-acceptance-criteria) — all 13 criteria.

**Automated tests (`@WebMvcTest`):**
- Each valid parameter combination returns 200 with the right structure
- `?q=` (blank) → 400
- `?minPrice=500&maxPrice=100` → 400
- `?sort=invalid` → 400
- `GET /api/books` (no params) → 200, identical to FEAT-01 behaviour — **regression guard**
- `GET /api/books?category=fiction` → identical to FEAT-02 behaviour — **regression guard**

`mvn test` must be green before this phase is considered done.

**Teaches:**
- How to write validation logic that accumulates multiple checks and reports the first failure cleanly
- That `@RequestParam(required = false)` paired with Java `Optional` or null-checks is the standard Spring pattern for optional filters
- Regression testing as a discipline — always verify existing behaviour is intact when modifying a method

---

## 5. Files — Complete List

```
backend/src/main/java/com/harsh/bookstore/
│
├── repository/
│   ├── BookRepository.java               MODIFIED  (add JpaSpecificationExecutor<Book>)
│   └── BookSpecification.java            NEW
│
├── service/
│   └── BookService.java                  MODIFIED  (unified listBooks with BookFilter)
│
├── dto/
│   └── BookFilter.java                   NEW
│
└── controller/
    └── BookController.java               MODIFIED  (new @RequestParams + validation)

backend/src/test/java/com/harsh/bookstore/
│
├── repository/
│   ├── BookRepositoryTest.java           MODIFIED  (add Specification-based query tests)
│   └── BookSpecificationTest.java        NEW
│
├── service/
│   └── BookServiceTest.java              MODIFIED  (replace old method tests, add filter tests)
│
└── controller/
    └── BookControllerTest.java           MODIFIED  (add filter param tests + regression guards)
```

---

## 6. Decisions for the Design Stage

| # | Decision |
|---|---|
| **D-01** | Exact JPQL / Criteria API expression for the keyword `LIKE` across `title`, `description`, `isbn`, and the `book_authors` join table — the join strategy needs to be explicit to avoid N+1 issues |
| **D-02** | Whether `BookFilter.sort` is a `String` or a dedicated `SortOption` enum — enum is safer (compile-time exhaustiveness), string is simpler |
| **D-03** | Whether `BookFilter` is a plain class with getters/setters, a Java `record`, or a class with a builder — the Design will choose the cleanest option |
| **D-04** | Whether `BookSpecification.hasCategory` looks up the category by slug directly via a JOIN on the `category` table, or first resolves the slug to a `Category` object and then matches by entity — affects how `CategoryNotFoundException` is raised for unknown slugs in the filter context |
| **D-05** | How the `authors` field is handled in the keyword LIKE — `authors` is in a separate `book_authors` join table, so the Criteria query needs an explicit `JOIN` to search it |

---

## 7. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Keyword search across `book_authors` join table causes a Cartesian join, returning duplicate books | Medium | High — incorrect result counts and pagination totals | Use `CriteriaQuery.distinct(true)` in the Specification when the authors join is active. Design stage will specify this explicitly. |
| Refactoring `BookService.listBooks()` signature breaks `BookControllerTest` mock setup | Medium | Small | Update mock setup in the same PR. The service method signature change is the only breaking change — tests are updated in Phase 5. |
| `JpaSpecificationExecutor` interacts poorly with existing `@DataJpaTest` tests | Low | Small | `JpaSpecificationExecutor` is purely additive — adds methods, changes nothing. Existing tests call `findAll(Pageable)` which is on `JpaRepository`, unchanged. |
| FEAT-02 regression — `?category=fiction` stops working after the service refactor | Medium | High | Phase 4 test explicitly covers `BookFilter` with only `categorySlug` set. Phase 5 controller test has an explicit FEAT-02 regression guard. |

---

## 8. Verification Approach

The feature is "Plan complete" when:

1. All 5 phases are done in order.
2. Every acceptance criterion in [spec §8](../specs/feature-03-search-and-filter.md#8-acceptance-criteria) is demonstrably met (Phase 5 manual verification, all 13 criteria).
3. All automated tests are green (`mvn test`) — no failures, no new warnings.
4. The FEAT-01 regression test (`GET /api/books` with no params) passes.
5. The FEAT-02 regression test (`GET /api/books?category=fiction`) passes.

---

## 9. Review Checklist for the Developer

Before approving this plan, please confirm:

- [ ] The JPA Specifications approach (§2) makes sense — you understand why it is chosen over `@Query` or query-by-example.
- [ ] The 5-phase sequence in §4 makes sense as a build order.
- [ ] The file list in §5 covers everything you expect to be touched — notably that `Category` entity and `BookSeedLoader` are **not** modified by FEAT-03 (they were handled in FEAT-02).
- [ ] The Design-stage decisions in §6 are understood — they will be answered in the next document.
- [ ] The risks in §7 (especially the duplicate-book risk from the authors join) are acceptable.

Once approved, this plan becomes the input to the **Design stage** for FEAT-03.
