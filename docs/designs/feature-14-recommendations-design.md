# Technical Design: FEAT-14 — Recommendations

## 1. Overview

Two new files: `RecommendationService` and `RecommendationController`.
No schema changes, no new exceptions, no security config changes.

---

## 2. No New Database Schema

All data comes from existing tables: `orders`, `order_item`, `book`, `category`.
No migrations required.

---

## 3. Service: `RecommendationService`

**Package:** `com.harsh.bookstore.service`  
**Constructor injection:** `OrderRepository`, `BookRepository`

### `getRecommendations(Long userId) → List<BookDto>`

```
1. orders = orderRepository.findAllByUserId(userId)
2. if orders.isEmpty() → return List.of()

3. orderedBookIds = orders.stream()
       .flatMap(o -> o.getItems().stream())
       .map(OrderItem::getBookId)
       .collect(toSet())

4. orderedBooks = bookRepository.findAllById(orderedBookIds)   // resolves categories
   purchasedCategories = orderedBooks.stream()
       .map(Book::getCategory)
       .collect(toSet())

5. candidates = bookRepository.findAll().stream()
       .filter(b -> purchasedCategories.contains(b.getCategory()))
       .filter(b -> !orderedBookIds.contains(b.getId()))
       .sorted(Comparator.comparing(Book::getTitle))
       .limit(6)
       .map(this::toDto)
       .toList()

6. return candidates
```

**`toDto(Book book) → BookDto`** — private helper, identical mapping to
`BookService.toDto()`:
- All fields copied directly.
- `category` = `book.getCategory().getName()`.
- `availability` = `book.getStockQuantity() > 0 ? "IN_STOCK" : "OUT_OF_STOCK"`.

*Why duplicate `toDto` instead of reusing `BookService.toDto()`?*  
`BookService.toDto()` is `private`. Making it package-private or public to
serve an unrelated service would violate encapsulation. The mapping is 15 lines
of straight field copies — duplication is the lesser harm here.

---

## 4. Controller: `RecommendationController`

**Package:** `com.harsh.bookstore.controller`  
**Mapping:** `@RequestMapping("/api/recommendations")`  
**Constructor injection:** `RecommendationService`

```java
@GetMapping
public List<BookDto> getRecommendations(Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    return recommendationService.getRecommendations(user.getId());
}
```

`authentication` is guaranteed non-null — `anyRequest().authenticated()` in
`SecurityConfig` rejects unauthenticated requests before this method is reached.

---

## 5. Error Mapping

| Scenario | Behaviour |
|----------|-----------|
| No JWT | 401 — Spring Security rejects before controller is called |
| No past orders | 200 with `[]` — not an error |
| All category books already ordered | 200 with `[]` |

---

## 6. Test Designs

### Service tests — `@ExtendWith(MockitoExtension.class)`

**`getRecommendations_returnsUpTo6BooksFromPurchasedCategories`**
- Stub: `orderRepository.findAllByUserId(1L)` → one order with one item (bookId=10).
- Stub: `bookRepository.findAllById([10L])` → Book with `category=Fiction`.
- Stub: `bookRepository.findAll()` → 8 Fiction books (none with id=10).
- Assert: result has 6 books, all `category = "Fiction"`.

**`getRecommendations_excludesAlreadyOrderedBooks`**
- Same setup; assert result contains no book with `id == 10`.

**`getRecommendations_returnsEmpty_whenNoOrders`**
- Stub: `orderRepository.findAllByUserId(1L)` → `List.of()`.
- Assert: result is empty; `bookRepository` is **never called**.

### Controller tests — `@WebMvcTest(RecommendationController.class)`

**`getRecommendations_returns200_authenticated`**
- `when(recommendationService.getRecommendations(1L)).thenReturn(List.of(bookDto()))`.
- `.with(authentication(userAuth()))` → expect 200 + JSON array.

**`getRecommendations_returns401_noJwt`**
- No auth → expect 401.
