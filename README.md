# E-Commerce Bookstore

An AI-assisted, spec-driven learning project — a Java + Spring Boot backend for an ecommerce bookstore.

## Status

Under active development. Currently on **FEAT-01: Browse Book Catalogue**.

- ✅ Business requirements
- ✅ Feature spec, plan, and technical design
- 🚧 Coding — Phase 5 of 9 complete (Book entity + Spring Data JPA repository + seed loader)

See [`AGENTS.md`](AGENTS.md) for the development strategy and [`docs/`](docs/) for spec / plan / design documents.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.4.7 |
| Build tool | Maven |
| ORM | Spring Data JPA (Hibernate) |
| Database (dev) | H2 in-memory |
| Testing | JUnit 5, Mockito, MockMvc |
| Seed pipeline | Python 3 (standard library only) |
| Seed data source | [Open Library](https://openlibrary.org) |

## Repository Layout

```text
ecommerce-bookstore/
├── AGENTS.md                   # AI-assisted development strategy
├── backend/                    # Spring Boot backend
│   └── src/main/java/com/harsh/bookstore/
│       ├── entity/             # JPA entities
│       ├── repository/         # Spring Data JPA repositories
│       ├── service/            # Business logic (planned)
│       ├── controller/         # REST controllers (planned)
│       ├── dto/                # Data-transfer objects (planned)
│       ├── exception/          # Custom exceptions + global handler (planned)
│       └── config/             # Startup runners (BookSeedLoader)
├── data/seed/
│   └── books.json              # 113 books fetched from Open Library
├── scripts/
│   └── fetch_books.py          # Offline seed data fetcher
├── docs/
│   ├── business-requirements.md
│   ├── specs/                  # Feature specifications
│   ├── plans/                  # Implementation plans
│   └── designs/                # Technical designs
└── frontend/                   # Reserved (not started)
```

## Prerequisites

- JDK 21
- Maven 3.9+
- Python 3.10+ (only needed if regenerating seed data)

## Running the Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080`.

**H2 web console (dev only):** `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:bookstore`
- User: `sa`
- Password: (empty)

After startup, the app auto-seeds 113 books from `data/seed/books.json`. Query them:

```sql
SELECT COUNT(*) FROM book;
```

## Regenerating Seed Data

The seed script pulls real book metadata from Open Library and writes it to `data/seed/books.json`. Run once (or whenever you want to refresh the catalogue):

```bash
python scripts/fetch_books.py
```

The Spring Boot app reads the file at startup — no live network calls from the app itself.

## Development Workflow

Every feature follows a strict lifecycle:

> **Think → Specify → Plan → Design → Code → Test → Verify**

Each stage produces a document that the developer reviews before the next stage begins. Feature artefacts live under `docs/specs/`, `docs/plans/`, and `docs/designs/`. See [`AGENTS.md`](AGENTS.md) for the full description of the strategy and the developer's role.

## Scope

**In scope:** browse catalogue, category browsing (planned), search & filter (planned), user registration (planned), basket, orders, gift-point redemption, 48-hour order cancellation, recommendations based on order history.

**Explicitly out of scope:** any in-application admin interface. Catalogue changes are made by editing the seed source and re-running the seed script.

Full scope statement: [`docs/business-requirements.md`](docs/business-requirements.md) §3.

## License

TBD.
