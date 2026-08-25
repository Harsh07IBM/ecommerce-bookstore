# Implementation Plan: Browse Book Catalogue

| Field | Value |
|---|---|
| **Feature ID** | FEAT-01 |
| **Corresponds To Spec** | [feature-01-browse-catalogue.md](../specs/feature-01-browse-catalogue.md) |
| **Status** | Revised — Awaiting Developer Approval (v2: layer-based packaging) |
| **Author** | AI Assistant (drafted for review) |

---

## 1. Purpose of This Document

The Specification defines **what** the feature must do.
The Design (next stage) will define **how each class, endpoint, and database table looks in detail**.

This **Plan** sits between them. Its job is to:

- Set the **overall technical direction** (what tech, what approach).
- Break the work into an **ordered sequence of phases** that each teach one concept.
- Identify the **critical files** to be created.
- List **decisions the Design stage will need to lock down**.
- Surface **risks** early.

This document is intentionally high-level. Class names, method signatures, exact REST paths, and DB column definitions are **not** decided here — those belong to Design.

---

## 2. Technical Direction

The following are the proposed technology choices for this feature and the whole project going forward. All are subject to your approval — they are defaults, not decisions I've made unilaterally.

| Concern | Proposed Choice | Why |
|---|---|---|
| **Language** | Java 21 (LTS) | Latest long-term-support version. Required by modern Spring Boot. |
| **Framework** | Spring Boot 3.x | Industry-standard Java web framework. What you asked to learn. |
| **Build tool** | Maven | More tutorial coverage than Gradle. Beginner-friendly XML config. |
| **ORM** | Spring Data JPA (Hibernate under the hood) | Standard way to talk to a database in Spring. |
| **Database (development)** | H2 in-memory | Zero setup. Ships as a dependency. Data resets on restart — which matches our seed-on-startup model. |
| **Database (later)** | PostgreSQL or MySQL | Swappable via configuration when we need persistence. **Not in scope for FEAT-01.** |
| **JSON library** | Jackson | Bundled with Spring Boot Web. Nothing to add. |
| **Testing** | JUnit 5 + Spring Boot Test | Bundled with Spring Boot's test starter. |
| **API style** | REST (JSON over HTTP) | Standard, testable with a browser/Postman, decoupled from frontend. |
| **Frontend for FEAT-01** | **None** — REST endpoints only | Deferred to a later feature. Verifying via browser/Postman is enough for FEAT-01's acceptance criteria. |
| **Seed script language** | Python 3 | Simple, universal, no compilation. One file. Runs once to produce `books.json`. |

### Beginner-friendly rationale (worth reading)

- **H2 in-memory** means you don't have to install a database on Day 1. When we later need real persistence (starting probably around FEAT-06 — user accounts), we'll swap to PostgreSQL by changing a few lines in `application.properties`. That swap is itself a learning moment.
- **REST-only (no frontend yet)** keeps our attention on the Spring Boot layered architecture — the actual thing you asked to learn. Once you're comfortable reading JSON from `GET /api/books`, adding a frontend later is straightforward.
- **Maven over Gradle**: not because Maven is better, but because every Spring Boot tutorial you'll find is written for it. Familiarity beats elegance while learning.

---

## 3. Prerequisites — What You'll Install Once

Before Phase 1 begins, please install the following. This is **not per-feature work** — it's one-time setup for the whole project.

| Tool | Version | Verification command |
|---|---|---|
| **JDK** | 21 (LTS) | `java --version` |
| **Maven** | 3.9+ | `mvn --version` |
| **Python** | 3.10+ | `python --version` |
| **Git** | Any recent | `git --version` |
| **A REST client** | Postman, Insomnia, or `curl` | (visual) |

**Note:** If you already have JDK 17 installed and don't want to reinstall, we can use 17 instead — it's still supported by Spring Boot 3.x. Flag this in your review.

---

## 4. Implementation Phases

The feature will be built in **9 phases**. Each phase teaches one focused Spring Boot concept and produces a working, testable state.

Between phases, we pause. Each pause is a chance for you to (a) verify the phase works as expected, (b) ask questions about the concept, and (c) approve moving to the next phase.

### Phase 1 — Environment sanity check

Confirm JDK 21, Maven, and Python are installed and working.

**Deliverable:** all four `--version` commands succeed.
**Teaches:** nothing new — just prepares the ground.

---

### Phase 2 — Seed data acquisition

Write a Python script that fetches ~50–100 books from the Google Books API across several subjects, transforms them into the schema defined in [spec §6](../specs/feature-01-browse-catalogue.md#6-the-book-concept--business-view), generates prices and stock quantities, and writes `data/seed/books.json`.

**Deliverable:** `scripts/fetch_books.py` and a populated `data/seed/books.json` (≥50 books, ≥5 categories, at least one with `stockQuantity == 0`).
**Teaches:** how Spring Boot's seed data will enter the system (via a JSON file), what the "shape of a Book" looks like as data.

---

### Phase 3 — Spring Boot project scaffold

Bootstrap a Spring Boot project inside `backend/` using Spring Initializr (either the web UI at start.spring.io or the CLI). Dependencies to include:

- Spring Web
- Spring Data JPA
- H2 Database
- Validation
- Spring Boot DevTools (auto-reload while developing)
- Lombok (optional — reduces boilerplate on entities)

Verify by running `mvn spring-boot:run` and hitting the empty app on `http://localhost:8080`.

**Deliverable:** a runnable, empty Spring Boot app.
**Teaches:**
- Spring Boot project layout (`src/main/java`, `src/main/resources`, `application.properties`, `pom.xml`)
- What a "starter" dependency is
- How `@SpringBootApplication` bootstraps everything
- What "embedded Tomcat" means

---

### Phase 4 — The `Book` entity + repository

Create a `Book` JPA entity with the fields from [spec §6](../specs/feature-01-browse-catalogue.md#6-the-book-concept--business-view), and a `BookRepository` interface extending Spring Data's `JpaRepository`.

Verify by starting the app and observing that Hibernate creates the `book` table in H2 automatically. Use the built-in H2 console (`http://localhost:8080/h2-console`) to see the empty table.

**Deliverable:** `Book` entity class, `BookRepository` interface, H2 console showing an empty `book` table.
**Teaches:**
- What a JPA entity is (`@Entity`, `@Id`, `@GeneratedValue`)
- How Hibernate maps a Java class to a database table
- Why Spring Data JPA lets you write a repository *without writing SQL*
- How to model a list field (multiple authors) — `@ElementCollection` vs. separate entity vs. comma-joined string. **This is a Design-stage decision.**

---

### Phase 5 — Seed data loader

Create a `CommandLineRunner` bean (or `ApplicationRunner`) that runs at startup, reads `data/seed/books.json`, deserialises with Jackson, and calls `BookRepository.saveAll(...)`. Make it **idempotent**: only load if the table is empty.

Verify by starting the app and using the H2 console to see all seeded books.

**Deliverable:** a data loader class, and the H2 console showing seeded rows.
**Teaches:**
- Spring bean lifecycle (`@Component`, `CommandLineRunner`)
- Dependency injection (`@Autowired` / constructor injection)
- Reading files from `src/main/resources` vs. project root — **Design decision:** where does `books.json` live?
- Jackson JSON → Java object mapping
- Why idempotency matters for seed logic

---

### Phase 6 — The service layer

Create a `BookService` with two operations:

- **List books** — paginated, ordered by `createdAt` desc, page size 12.
- **Get one book by id** — returns the book, or a "not found" outcome.

The service is a thin wrapper over the repository in this feature, but its existence sets up the pattern for every future feature — business logic goes here, not in controllers.

**Deliverable:** `BookService` class with two methods.
**Teaches:**
- Why separate service from controller (single-responsibility, testability, reusability)
- `@Service` annotation
- Spring's `Pageable` / `Page` types
- Custom exceptions for "not found" (introduces the concept of exception handling)

---

### Phase 7 — The API layer

Create a `BookController` exposing two REST endpoints:

- `GET /api/books?page=0&size=12` → paginated list
- `GET /api/books/{id}` → single book

Return a **DTO** rather than the entity directly (so the API isn't accidentally coupled to database internals — an important Spring Boot pattern).

Add a global exception handler so "not found" returns HTTP 404 with a clean JSON body.

**Deliverable:** working endpoints returning JSON. Verifiable in a browser and in Postman.
**Teaches:**
- `@RestController`, `@GetMapping`, `@PathVariable`, `@RequestParam`
- Why DTOs exist and how they differ from entities
- `ResponseEntity` and HTTP status codes
- `@ControllerAdvice` + `@ExceptionHandler` for centralised error handling

---

### Phase 8 — Manual verification against the spec

Systematically walk through every acceptance criterion in [spec §8](../specs/feature-01-browse-catalogue.md#8-acceptance-criteria). Confirm each one passes.

**Deliverable:** a filled-in checklist against §8 of the spec.
**Teaches:** the discipline of verifying against a written spec — the "Verify" stage of your AGENTS.md lifecycle.

---

### Phase 9 — Automated tests

Write focused tests at three layers:

- **Repository test** (`@DataJpaTest`) — verify the repository saves and paginates correctly.
- **Service test** — verify the service returns the right page and handles "not found".
- **Controller test** (`@WebMvcTest` + `MockMvc`) — verify the endpoints return the correct status codes and JSON shape.

**Deliverable:** a green test suite (`mvn test`).
**Teaches:**
- Spring Boot testing slices (`@DataJpaTest`, `@WebMvcTest`, `@SpringBootTest`)
- Mocking with Mockito
- Test-driven confidence — the tests catch future regressions when you add features 2, 3, 4...

---

## 5. Critical Files (Anticipated)

The following files will be created. Exact names and paths are finalised in Design, but this is the anticipated shape.

Project uses **layer-based (MVC-style) packaging** — each folder corresponds to one architectural layer. This is the convention you'll see in every Spring Boot tutorial and blog post, which keeps external learning material easy to map onto our code.

```text
ecommerce-bookstore/
├── scripts/
│   └── fetch_books.py                          (Phase 2)
├── data/seed/
│   └── books.json                              (Phase 2 output)
├── backend/
│   ├── pom.xml                                 (Phase 3)
│   ├── src/main/java/.../
│   │   ├── BookstoreApplication.java           (Phase 3)
│   │   ├── controller/
│   │   │   └── BookController.java             (Phase 7)
│   │   ├── service/
│   │   │   └── BookService.java                (Phase 6)
│   │   ├── repository/
│   │   │   └── BookRepository.java             (Phase 4)
│   │   ├── entity/
│   │   │   └── Book.java                       (Phase 4)
│   │   ├── dto/
│   │   │   └── BookDto.java                    (Phase 7)
│   │   ├── exception/
│   │   │   ├── BookNotFoundException.java      (Phase 6)
│   │   │   └── GlobalExceptionHandler.java     (Phase 7)
│   │   └── config/
│   │       └── BookSeedLoader.java             (Phase 5)
│   ├── src/main/resources/
│   │   └── application.properties              (Phase 3)
│   └── src/test/java/.../
│       ├── repository/
│       │   └── BookRepositoryTest.java         (Phase 9)
│       ├── service/
│       │   └── BookServiceTest.java            (Phase 9)
│       └── controller/
│           └── BookControllerTest.java         (Phase 9)
```

### Layer meanings (for reference)

| Folder | Role |
|---|---|
| `controller/` | HTTP entry points. Translates HTTP ⇄ Java calls. No business logic. |
| `service/` | Business logic. Orchestrates repositories. Feature-agnostic operations. |
| `repository/` | Talks to the database. Extends Spring Data interfaces. |
| `entity/` | JPA classes — one Java class per DB table. |
| `dto/` | Data-transfer objects — the shape of data sent over the wire. Decoupled from DB. |
| `exception/` | Custom exceptions + the global `@ControllerAdvice` handler. |
| `config/` | App-startup bits — beans, runners, cross-cutting configuration. |

---

## 6. Decisions the Design Stage Will Lock Down

These are decisions I'm **not** making in the plan — they belong in the next stage. Listing them so you know what's coming:

| # | Decision |
|---|---|
| **D-01** | Exact package name (`com.harsh.bookstore`? something else?) |
| **D-02** | How to model the `authors` list — `@ElementCollection`, comma-joined string, or a separate `Author` entity |
| **D-03** | Where `books.json` lives — `data/seed/books.json` at repo root, or `backend/src/main/resources/seed/books.json` |
| **D-04** | Exact DB column types, especially for `price` (BigDecimal vs. long-storing-paise vs. double) |
| **D-05** | REST DTO shape — flat vs. nested, what to include vs. omit |
| **D-06** | Error response body shape (RFC 7807 Problem Details or a custom shape) |
| **D-07** | Whether to include Lombok — reduces boilerplate but hides some Java behavior. Recommendation: **no Lombok for the first feature** so you see the boilerplate and understand what it does; introduce Lombok later once you're comfortable. |
| **D-08** | Whether pagination controls include `total`, `totalPages`, `hasNext`, etc. (Spring's default `Page` has all of these — probably keep them all) |

---

## 7. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Google Books returns fewer than 50 books for a subject, or duplicates across subjects | Medium | Small — we'd have a smaller catalogue | Fetch across 7–8 subjects, deduplicate by ISBN, top up manually if short |
| Google Books rate-limits during fetch | Low | Small — script fails, retry | Script retries with backoff; runs once, results are cached in `books.json` forever after |
| Cover image URLs from Google Books go stale later | Low | Small — placeholder fallback (per OQ-03) | Fall back to a placeholder on broken URLs |
| Beginner unfamiliarity with Maven causes phase 3 to stall | Medium | Medium — blocks everything downstream | I'll walk through the Initializr step interactively; we can pause on any concept |
| H2 in-memory means data resets on restart | Guaranteed | None for FEAT-01 (seed reloads on startup) | Not a bug — it's the model. Swap DB later. |
| `authors` modelling choice ripples into later features | Medium | Small — we can refactor when needed | Deliberate: start simple. Refactor when a feature actually needs it. |

---

## 8. Verification Approach

The feature is considered "Plan complete" when:

1. All 9 phases are done.
2. Every acceptance criterion in [spec §8](../specs/feature-01-browse-catalogue.md#8-acceptance-criteria) is demonstrably met (Phase 8).
3. All automated tests are green (Phase 9).
4. `git status` on the `backend/` and `scripts/` directories shows only intended changes.

---

## 9. What Comes After This Feature

The natural next steps, in order:

1. **FEAT-02 — Category Browsing** — introduces relationships between entities (a `Category` entity, `@ManyToOne`).
2. **FEAT-03 — Search & Filter** — introduces JPA Specifications or `@Query`.
3. **FEAT-04 — Storefront Frontend** — first React (or Thymeleaf) work, consuming the REST API built here.
4. **FEAT-05 — User Registration & Login** — introduces Spring Security.
5. **FEAT-06 — Shopping Basket** — first stateful, user-scoped feature.

These are foreshadowing, not commitments. Each will have its own spec, plan, and design. **There is no admin-CRUD feature planned** — catalogue changes are made by editing the seed source and re-running the seed script, per [business-requirements §3.2](../business-requirements.md#32-out-of-scope-unless-explicitly-approved).

---

## 10. Review Checklist for the Developer

Before approving this plan, please confirm:

- [ ] The technical direction in §2 is agreeable (Java 21, Spring Boot, Maven, H2, no frontend yet).
- [ ] The prerequisites in §3 match what you have installed (or you're happy to install what's missing).
- [ ] The 9-phase sequence in §4 makes sense as a learning path.
- [ ] The anticipated file structure in §5 is reasonable (feature-based packaging).
- [ ] You accept that the decisions in §6 will be finalised in the Design stage, not now.
- [ ] The risks in §7 are acceptable.

Once approved, this plan becomes the input to the **Design stage**, where every decision from §6 gets a specific, code-ready answer.
