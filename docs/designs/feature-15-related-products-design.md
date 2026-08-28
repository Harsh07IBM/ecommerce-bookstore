# Technical Design: FEAT-15 — Related Products

## 1. Overview

Three surgical changes to existing files: one new repository method, one new
service method, one new controller endpoint. No new files, no schema changes,
no security changes.

---

## 2. No New Database Schema

Uses existing `book` and `category` tables. No migrations required.

---

## 3. Repository: `BookRepository` addition

```java
/**
 * FEAT-15: books in the same category, excluding the given book id.
 * Spring Data derives: SELECT * FROM book WHERE category_id = ? AND id <> ?
 * ORDER BY title ASC LIMIT ?
 */
List<Book> findByCategoryAndIdNot(Category category, Long excludeId, Pageable pageable);
```

Called with `PageRequest.of(0, 5, Sort.by("title").ascending())`.

`Book.category` is `FetchType.EAGER` — the category JOIN is already part of
every book SELECT, so no extra query is needed.

---

## 4. Service: `BookService` addition

```java
public List<BookDto> getRelatedBooks(Long bookId) {
    Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new BookNotFoundException(bookId));
    Pageable pageable = PageRequest.of(0, 5, Sort.by("title").ascending());
    return bookRepository.findByCategoryAndIdNot(book.getCategory(), bookId, pageable)
            .stream()
            .map(this::toDto)
            .toList();
}
```

`toDto` is the existing private method — no visibility change needed.
`BookNotFoundException` is the existing exception — already handled by
`GlobalExceptionHandler` → 404.

---

## 5. Controller: `BookController` addition

```java
@GetMapping("/{id}/related")
public List<BookDto> getRelatedBooks(@PathVariable Long id) {
    return bookService.getRelatedBooks(id);
}
```

No `Authentication` parameter — the endpoint is public (`GET /api/books/**`
is already `permitAll()` in `SecurityConfig`).

---

## 6. Error Mapping

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Book not found | `BookNotFoundException` | 404 |
| No related books | — (empty list returned) | 200 |

---

## 7. Test Designs

### Service tests

**`getRelatedBooks_returnsUpTo5BooksInSameCategory`**
- Stub `bookRepository.findById(1L)` → book with `category = Fiction`.
- Stub `findByCategoryAndIdNot(fiction, 1L, pageable)` → list of 3 books.
- Assert: returned list has 3 DTOs.

**`getRelatedBooks_excludesSelf`**
- Verify `findByCategoryAndIdNot` is called with `excludeId = 1L` (the book's own id).

**`getRelatedBooks_returnsEmpty_whenNoRelatedBooks`**
- Stub repository returns empty list → result is `[]`.

**`getRelatedBooks_throws404_bookNotFound`**
- Stub `bookRepository.findById(99L)` → empty → `BookNotFoundException`.

### Controller tests

**`getRelatedBooks_returns200`**
- `when(bookService.getRelatedBooks(1L)).thenReturn(List.of(bookDto()))` → 200.

**`getRelatedBooks_returns404_notFound`**
- `when(bookService.getRelatedBooks(99L)).thenThrow(new BookNotFoundException(99L))` → 404.
