# Feature Specification: FEAT-14 — Recommendations

## 1. Overview

Return a personalised list of book recommendations for the authenticated user,
based on the categories of books they have previously ordered. The endpoint is
used by the frontend to surface relevant books during the shopping experience.

---

## 2. Business Rules

| # | Rule |
|---|------|
| BR-01 | Recommendations are derived from the categories of the user's past purchases. |
| BR-02 | Books the user has already ordered are excluded from results. |
| BR-03 | At most **6** recommendations are returned, sorted by title ascending. |
| BR-04 | The endpoint requires authentication — guests receive 401. |
| BR-05 | If the user has no past orders, an empty list is returned (not an error). |
| BR-06 | If fewer than 6 qualifying books exist, all qualifying books are returned. |

---

## 3. REST API Contract

### GET /api/recommendations

- **Auth:** required (JWT).
- **Response 200:**
```json
[
  {
    "id": 5,
    "isbn": "9780132350884",
    "title": "Clean Code",
    "authors": ["Robert C. Martin"],
    "description": "...",
    "coverImageUrl": "https://...",
    "publisher": "Prentice Hall",
    "publishedDate": "2008",
    "pageCount": 431,
    "language": "en",
    "category": "Technology",
    "price": 599.00,
    "availability": "IN_STOCK"
  }
]
```
- Returns `[]` if user has no past orders or no qualifying books exist.
- **Response 401:** no JWT present.

---

## 4. Out of Scope

- Guest recommendations (no session-based history).
- Collaborative filtering or ML-based recommendations.
- Recommendations based on browsing history (only purchase history is used).
- Caching or pre-computation of recommendation sets.

---

## 5. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-01 | `GET /api/recommendations` with valid JWT returns 200 with a JSON array. |
| AC-02 | Returned books are from the same categories as the user's past orders. |
| AC-03 | Books already purchased by the user do not appear in results. |
| AC-04 | At most 6 books are returned; results are sorted by title ascending. |
| AC-05 | A user with no past orders receives an empty array `[]`. |
| AC-06 | `GET /api/recommendations` without a JWT returns 401. |
