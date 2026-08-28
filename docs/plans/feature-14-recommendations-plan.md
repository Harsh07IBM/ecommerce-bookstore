# Implementation Plan: FEAT-14 — Recommendations

## 1. Overview

Implement `GET /api/recommendations` — a personalised book recommendation
endpoint based on the authenticated user's purchase history. All changes are
additive; no existing files are modified except the roadmap.

---

## 2. New Files

| Layer | File | Purpose |
|-------|------|---------|
| service | `RecommendationService.java` | Business logic — find books in purchased categories, exclude already-ordered |
| controller | `RecommendationController.java` | `GET /api/recommendations` |

---

## 3. Modified Files

| File | Change |
|------|--------|
| `feature-roadmap.md` | Mark FEAT-14 complete |

No security config changes are needed — `anyRequest().authenticated()` already
protects this endpoint. No new exceptions are needed — an empty result is valid.

---

## 4. Step-by-Step Implementation

### Step 1 — Service: `RecommendationService`

Constructor injection: `OrderRepository`, `BookRepository`.

**`getRecommendations(Long userId) → List<BookDto>`**

1. `orders = orderRepository.findAllByUserId(userId)`.
2. If `orders` is empty → return `List.of()` immediately.
3. Collect ordered `bookIds`: `Set<Long>` from all `order.getItems().stream().map(OrderItem::getBookId)`.
4. Look up those books: `bookRepository.findAllById(orderedBookIds)` → extract the set of `Category` objects.
5. Load all books in those categories: `bookRepository.findAll()` — filter in-memory:
   - `book.getCategory()` is in the categories set.
   - `book.getId()` is NOT in `orderedBookIds`.
6. Sort by `title` ascending, limit to 6.
7. Map each `Book` to `BookDto` using the same field-by-field mapping as `BookService.toDto()`.
8. Return the list.

*Note: `BookRepository` has no `findByCategoryIn()` method and we should not
add one for this feature — an in-memory filter over the full catalogue is
acceptable for a development-scale dataset.*

### Step 2 — Controller: `RecommendationController`

- `@RestController`, `@RequestMapping("/api/recommendations")`
- Constructor injection: `RecommendationService`
- One endpoint: `@GetMapping` — extract `user.getId()` from `Authentication`, delegate to service.

---

## 5. Test Plan

### 5.1 Service Tests — `@ExtendWith(MockitoExtension.class)`

| Test | What it verifies |
|------|-----------------|
| `getRecommendations_returnsUpTo6BooksFromPurchasedCategories` | Orders exist; candidate books in same categories returned; at most 6 |
| `getRecommendations_excludesAlreadyOrderedBooks` | Books whose IDs appear in past orders are absent from results |
| `getRecommendations_returnsEmpty_whenNoOrders` | `orderRepository.findAllByUserId` returns empty list → `List.of()` returned |

### 5.2 Controller Tests — `@WebMvcTest`

| Test | What it verifies |
|------|-----------------|
| `getRecommendations_returns200_authenticated` | Valid JWT → 200 with JSON array |
| `getRecommendations_returns401_noJwt` | No JWT → 401 |
