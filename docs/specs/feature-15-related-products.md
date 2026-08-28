# Feature Specification: FEAT-15 — Related Products

## 1. Overview

Display a list of related books on the book detail page. "Related" is defined
as books in the same category as the requested book. This helps users discover
more books in a genre or subject area they are already interested in.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | Related books are books that share the **same category** as the requested book. |
| BR-02 | The requested book itself is **excluded** from results. |
| BR-03 | At most **5** related books are returned, sorted by title ascending. |
| BR-04 | The endpoint is **public** — no authentication required (consistent with `GET /api/books/**`). |
| BR-05 | If the book does not exist, the endpoint returns 404. |
| BR-06 | If fewer than 5 related books exist, all qualifying books are returned. |
| BR-07 | If no related books exist (sole book in its category), an empty list is returned. |

---

## 3. REST API Contract

### GET /api/books/{id}/related

- **Auth:** not required.
- **Path parameter:** `id` — the database ID of the book.
- **Response 200:**
```json
[
  {
    "id": 7,
    "isbn": "9780201633610",
    "title": "Design Patterns",
    "authors": ["Erich Gamma", "Richard Helm"],
    "description": "...",
    "coverImageUrl": "https://...",
    "publisher": "Addison-Wesley",
    "publishedDate": "1994",
    "pageCount": 395,
    "language": "en",
    "category": "Technology",
    "price": 799.00,
    "availability": "IN_STOCK"
  }
]
```
- Returns `[]` if no related books exist.
- **Response 404:**
```json
{ "status": 404, "error": "Not Found", "message": "Book not found", "path": "/api/books/999/related" }
```

---

## 4. Out of Scope

- Multi-category books (current model: one book belongs to exactly one category).
- Author-based relatedness.
- Collaborative-filtering or purchase-based relatedness.

---

## 5. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `GET /api/books/{id}/related` returns 200 with books from the same category. |
| AC-02 | The book with the requested `{id}` does not appear in the results. |
| AC-03 | At most 5 books are returned, sorted by title ascending. |
| AC-04 | `GET /api/books/{id}/related` returns 404 when the book does not exist. |
| AC-05 | Returns `[]` when the book is the only one in its category. |
| AC-06 | No JWT is required — the endpoint is publicly accessible. |
