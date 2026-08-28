# Implementation Plan: FEAT-15 — Related Products

## 1. Overview

Add `GET /api/books/{id}/related` to the existing `BookController`. Returns up
to 5 books in the same category, excluding the book itself, sorted by title.
All changes are additive except the three existing files modified below.

---

## 2. New Files

None.

---

## 3. Modified Files

| File | Change |
|------|--------|
| `BookRepository.java` | Add `findByCategoryAndIdNot(Category, Long, Pageable)` derived query |
| `BookService.java` | Add `getRelatedBooks(Long bookId)` method |
| `BookController.java` | Add `GET /api/books/{id}/related` endpoint |

No security config changes — `GET /api/books/**` is already `permitAll()`.  
No new exceptions — `BookNotFoundException` already exists and is handled.

---

## 4. Step-by-Step Implementation

### Step 1 — Repository

Add to `BookRepository`:

```java
List<Book> findByCategoryAndIdNot(Category category, Long excludeId, Pageable pageable);
```

Spring Data derives:
`SELECT * FROM book WHERE category_id = ? AND id <> ? ORDER BY title ASC LIMIT 5`

Called with `PageRequest.of(0, 5, Sort.by("title").ascending())`.

### Step 2 — Service

Add to `BookService`:

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

`toDto` is already private in `BookService` — no visibility change needed.

### Step 3 — Controller

Add to `BookController`:

```java
@GetMapping("/{id}/related")
public List<BookDto> getRelatedBooks(@PathVariable Long id) {
    return bookService.getRelatedBooks(id);
}
```

---

## 5. Test Plan

### 5.1 Service Tests — `@ExtendWith(MockitoExtension.class)`

| Test | What it verifies |
|------|-----------------|
| `getRelatedBooks_returnsUpTo5BooksInSameCategory` | Book found; repository called with correct category and exclusion id; results mapped |
| `getRelatedBooks_excludesSelf` | The requested book id is passed as `excludeId` to repository |
| `getRelatedBooks_returnsEmpty_whenNoRelatedBooks` | Repository returns empty list → `[]` returned |
| `getRelatedBooks_throws404_bookNotFound` | `bookRepository.findById` returns empty → `BookNotFoundException` |

### 5.2 Controller Tests — `@WebMvcTest`

| Test | What it verifies |
|------|-----------------|
| `getRelatedBooks_returns200` | Valid book id → 200 with JSON array |
| `getRelatedBooks_returns404_notFound` | Service throws `BookNotFoundException` → 404 |
