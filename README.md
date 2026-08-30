# 📚 Ink&Pages — E-Commerce Bookstore

[![Live Demo](https://img.shields.io/badge/Live%20Demo-ecommerce--bookstore--gamma.vercel.app-brightgreen?style=for-the-badge)](https://ecommerce-bookstore-gamma.vercel.app)
[![Backend API](https://img.shields.io/badge/Backend%20API-Railway-blueviolet?style=for-the-badge)](https://ecommerce-bookstore-production.up.railway.app/api/books)

A full-stack e-commerce bookstore built with **Spring Boot** and **React**, following a structured, spec-driven development process. Browse 113+ real books, manage a basket as a guest or signed-in user, place orders, and earn gift points on every purchase.

> 🌐 **Live at: https://ecommerce-bookstore-gamma.vercel.app**

---

## ✨ Features

| Feature | Status |
|---|---|
| Browse & paginate catalogue | ✅ |
| Search by title, author, description | ✅ |
| Filter by category, price, availability | ✅ |
| Sort by newest / price | ✅ |
| Guest shopping basket (session-based) | ✅ |
| User registration & login (JWT) | ✅ |
| Guest basket wiped on sign-in | ✅ |
| Checkout with delivery address | ✅ |
| Order placement & history | ✅ |
| 48-hour order cancellation window | ✅ |
| Gift points (5% back, redeemable at checkout) | ✅ |
| Book recommendations based on order history | ✅ |
| Related books on book detail page | ✅ |

---

## 🛠 Tech Stack

### Backend
| | |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.4.7 |
| Build Tool | Maven (with Maven Wrapper) |
| ORM | Spring Data JPA + Hibernate 6 |
| Database | PostgreSQL 17 |
| Security | Spring Security + JWT (JJWT 0.12.6) |
| Testing | JUnit 5 · Mockito · MockMvc · H2 (test scope) |

### Frontend
| | |
|---|---|
| Framework | React 18 + Vite |
| Routing | React Router v6 |
| Styling | Tailwind CSS |
| State | React Context API |
| HTTP | Fetch API |
| Notifications | react-hot-toast |

---

## 🗂 Repository Layout

```text
ecommerce-bookstore/
├── backend/                        # Spring Boot REST API
│   └── src/
│       ├── main/java/com/harsh/bookstore/
│       │   ├── config/             # Security, CORS, seed loader
│       │   ├── controller/         # REST controllers
│       │   ├── dto/                # Request / response shapes
│       │   ├── entity/             # JPA entities
│       │   ├── exception/          # Custom exceptions + global handler
│       │   ├── repository/         # Spring Data JPA repositories
│       │   └── service/            # Business logic
│       ├── main/resources/
│       │   └── application.properties
│       └── test/                   # 229 unit + integration tests
│
├── frontend/                       # React + Vite SPA
│   └── src/
│       ├── components/             # Navbar, BookCard, Footer, Spinner
│       ├── context/                # AuthContext, BasketContext
│       ├── pages/                  # Home, Books, BookDetail, Basket,
│       │                           # Checkout, Orders, Login, Register
│       └── api.js                  # All fetch calls to the backend
│
├── data/seed/
│   └── books.json                  # 113 books seeded from Open Library
├── scripts/
│   └── fetch_books.py              # Seed data fetcher (Python 3)
├── docs/                           # Specs, plans, designs, ADRs
└── AGENTS.md                       # AI-assisted development strategy
```

---

## ⚙️ Prerequisites

| Tool | Version |
|---|---|
| JDK | 21+ |
| Maven | Bundled via `mvnw` — no install needed |
| Node.js | 18+ |
| PostgreSQL | 17 |
| Python | 3.10+ *(only for regenerating seed data)* |

---

## 🚀 Getting Started

### 1 — PostgreSQL Setup

Create the database and user (run once in pgAdmin or psql):

```sql
CREATE USER bookstore_user WITH PASSWORD 'your-password';
CREATE DATABASE bookstore OWNER bookstore_user;
GRANT ALL PRIVILEGES ON DATABASE bookstore TO bookstore_user;
```

### 2 — Run the Backend

```powershell
cd backend

$env:DB_URL      = "jdbc:postgresql://localhost:5432/bookstore"
$env:DB_USERNAME = "bookstore_user"
$env:DB_PASSWORD = "your-password"

.\mvnw.cmd spring-boot:run
```

On first startup Hibernate creates all tables and the seed loader populates **113 books** and **8 categories** automatically:

```
Seeded 8 categories
Seeded 113 books from ..\data\seed\books.json
```

Backend runs on **http://localhost:8080**

### 3 — Run the Frontend

```powershell
cd frontend
npm install
npm run dev
```

Frontend runs on **http://localhost:5173**

---

## 🔑 Environment Variables

| Variable | Description | Default (dev only) |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/bookstore` |
| `DB_USERNAME` | Database username | `bookstore_user` |
| `DB_PASSWORD` | Database password | *(empty)* |
| `JWT_SECRET` | HMAC-SHA256 signing key (min 32 chars) | `bookstore-dev-secret-key-change-in-production-min32c` |

> ⚠️ **Never use the default `JWT_SECRET` in staging or production.** Always inject a strong secret via environment variable or a secrets manager.

---

## 🧪 Running Tests

```powershell
cd backend
.\mvnw.cmd test
```

Tests use **H2 in-memory** — no running PostgreSQL instance required.

```
Tests run: 229, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 📡 Key API Endpoints

### Books
```
GET  /api/books                          # Paginated catalogue
GET  /api/books?q=tolkien                # Search by title / author
GET  /api/books?category=fiction         # Filter by category
GET  /api/books?sort=price_asc           # Sort
GET  /api/books/{id}                     # Single book
GET  /api/books/{id}/related             # Related books
GET  /api/categories                     # All categories
```

### Auth
```
POST /api/auth/register                  # Create account
POST /api/auth/login                     # Sign in → JWT token
```

### Basket
```
GET    /api/basket                       # View basket (guest or user)
POST   /api/basket/items                 # Add item
PUT    /api/basket/items/{id}            # Update quantity
DELETE /api/basket/items/{id}            # Remove item
DELETE /api/basket                       # Clear basket
```

### Orders
```
GET  /api/orders                         # Order history (auth required)
POST /api/orders                         # Place order (auth required)
GET  /api/orders/{id}                    # Order detail
GET  /api/orders/{id}/confirmation       # Order confirmation
POST /api/orders/{id}/cancel             # Cancel within 48 hours
```

---

## 🌱 Regenerating Seed Data

The seed script fetches real book metadata from [Open Library](https://openlibrary.org) and writes it to `data/seed/books.json`:

```bash
python scripts/fetch_books.py
```

The app reads this file at startup — no live network calls from the application itself.

---

## 🏗 Development Workflow

Every feature follows a strict lifecycle before a single line of code is written:

```
Think → Specify → Plan → Design → Code → Test → Verify
```

Each stage produces a reviewed document under `docs/`. See [`AGENTS.md`](AGENTS.md) for the full AI-assisted development strategy.

---

## 📋 Out of Scope

- No admin UI — catalogue changes are made by editing the seed source and re-running the seed script
- No payment gateway integration — payments are simulated
- No refresh tokens — JWT tokens expire after 24 hours

---

## 📄 License

MIT
