# Technical Design: FEAT-06 — Shopping Basket

## 1. Overview

This document describes the concrete technical design for the Shopping Basket
feature. It bridges the approved plan (`feature-06-shopping-basket-plan.md`)
and the actual implementation, specifying every class, field, method signature,
validation annotation, and exception mapping that will be coded in Stage 4.

---

## 2. Database Schema

Two new tables. Hibernate generates DDL from the entity annotations.

### `basket`

| Column       | Type         | Constraints                     |
|--------------|--------------|----------------------------------|
| `id`         | BIGINT       | PK, auto-increment               |
| `user_id`    | BIGINT       | nullable (null for guests)       |
| `session_id` | VARCHAR(128) | nullable (null for auth users)   |
| `created_at` | TIMESTAMP    | NOT NULL, set by `@PrePersist`   |

**Invariant:** exactly one of `user_id` / `session_id` is non-null per row.

### `basket_item`

| Column      | Type   | Constraints                        |
|-------------|--------|-------------------------------------|
| `id`        | BIGINT | PK, auto-increment                  |
| `basket_id` | BIGINT | FK → `basket(id)`, NOT NULL         |
| `book_id`   | BIGINT | FK → `book(id)`, NOT NULL           |
| `quantity`  | INT    | NOT NULL                            |

---

## 3. Entities

### `Basket`
**Package:** `com.harsh.bookstore.entity`  
**Table:** `basket`

```
@Id @GeneratedValue(IDENTITY)  Long id
@Column("user_id")             Long userId          // null for guests
@Column("session_id", 128)     String sessionId     // null for auth users
@Column("created_at")          LocalDateTime createdAt  // set by @PrePersist
@OneToMany(mappedBy="basket", cascade=ALL, orphanRemoval=true)
                               List<BasketItem> items  // initialised to ArrayList
```

- `@PrePersist onCreate()` — sets `createdAt = LocalDateTime.now()` if null.
- `equals` / `hashCode` — id-based; `hashCode` returns `getClass().hashCode()`.

### `BasketItem`
**Package:** `com.harsh.bookstore.entity`  
**Table:** `basket_item`

```
@Id @GeneratedValue(IDENTITY)  Long id
@ManyToOne(LAZY) @JoinColumn("basket_id") Basket basket
@ManyToOne(EAGER) @JoinColumn("book_id")  Book book
@Column                                   int quantity
```

- `book` is `EAGER` — every response read needs title, price, coverImageUrl.
  Eager fetch collapses N+1 selects into one JOIN.
- `basket` is `LAZY` — never navigated from item → parent.
- `equals` / `hashCode` — id-based; `hashCode` returns `getClass().hashCode()`.

---

## 4. Repository

### `BasketRepository`
**Package:** `com.harsh.bookstore.repository`  
Extends `JpaRepository<Basket, Long>`. All queries are Spring Data derived queries.

| Method | Derived query |
|--------|--------------|
| `Optional<Basket> findByUserId(Long userId)` | `WHERE user_id = ?` |
| `Optional<Basket> findBySessionId(String sessionId)` | `WHERE session_id = ?` |
| `void deleteBySessionId(String sessionId)` | `DELETE WHERE session_id = ?` |

---

## 5. Exceptions

Three new domain exceptions, all in `com.harsh.bookstore.exception`:

| Class | Constructor | Message |
|-------|-------------|---------|
| `BasketItemNotFoundException` | `(Long bookId)` | `"Book {bookId} is not in your basket"` |
| `OutOfStockException` | `()` | `"This book is currently out of stock"` |
| `MaxQuantityExceededException` | `()` | `"Maximum quantity per book is 7"` |

All extend `RuntimeException` and call `super(message)`.

### GlobalExceptionHandler additions

Three new `@ExceptionHandler` methods added to
`GlobalExceptionHandler` under a `// FEAT-06 handlers` section:

| Exception | HTTP status |
|-----------|------------|
| `BasketItemNotFoundException` | 404 Not Found |
| `OutOfStockException` | 400 Bad Request |
| `MaxQuantityExceededException` | 400 Bad Request |

All follow the same pattern as existing handlers: construct `ErrorResponse`,
return `ResponseEntity.status(...).body(body)`.

---

## 6. DTOs

All in `com.harsh.bookstore.dto`.

### `BasketItemDto`

| Field | Type | Notes |
|-------|------|-------|
| `bookId` | `Long` | |
| `title` | `String` | |
| `author` | `String` | First element of `book.getAuthors()`; `""` if list is null/empty |
| `coverImageUrl` | `String` | |
| `unitPrice` | `BigDecimal` | `book.getPrice()` |
| `quantity` | `int` | |
| `lineTotal` | `BigDecimal` | `unitPrice × quantity` (BigDecimal multiply) |

### `BasketResponse`

| Field | Type | Notes |
|-------|------|-------|
| `items` | `List<BasketItemDto>` | Empty list for an empty basket |
| `totalItems` | `int` | Sum of all `quantity` values |
| `basketTotal` | `BigDecimal` | Sum of all `lineTotal` values |

### `AddItemRequest`

| Field | Type | Validation |
|-------|------|------------|
| `bookId` | `Long` | `@NotNull(message = "bookId is required")` |
| `quantity` | `int` | `@Min(1) @Max(7)`, default value `1` |

### `UpdateItemRequest`

| Field | Type | Validation |
|-------|------|------------|
| `quantity` | `int` | `@Min(0) @Max(7)` — `0` means remove |

---

## 7. Service

### `BasketService`
**Package:** `com.harsh.bookstore.service`  
**Constructor injection:** `BasketRepository`, `BookRepository`

#### 7.1 Public methods

**`getBasket(Long userId, String sessionId) → BasketResponse`**  
1. `resolveBasket(userId, sessionId)` — find or create basket.  
2. Return `toResponse(basket)`.

---

**`addItem(Long userId, String sessionId, AddItemRequest req) → BasketResponse`**  
1. `resolveBasket(...)`.  
2. `bookRepository.findById(req.getBookId())` → `orElseThrow(BookNotFoundException)`.  
3. If `book.getStockQuantity() == 0` → throw `OutOfStockException`.  
4. Search `basket.getItems()` for existing item with matching `bookId`.  
   - **Found:** `newQty = existing.getQuantity() + req.getQuantity()`. If `newQty > 7` → throw `MaxQuantityExceededException`. Else `existing.setQuantity(newQty)`.  
   - **Not found:** create new `BasketItem`, set `basket`, `book`, `quantity`; add to `basket.getItems()`.  
5. `basketRepository.save(basket)`.  
6. Return `toResponse(basket)`.

---

**`updateItem(Long userId, String sessionId, Long bookId, int quantity) → BasketResponse`**  
1. `resolveBasket(...)`.  
2. Find item by `bookId` → `orElseThrow(BasketItemNotFoundException)`.  
3. If `quantity == 0` → `basket.getItems().remove(item)` (orphanRemoval deletes the row). Else `item.setQuantity(quantity)`.  
4. `basketRepository.save(basket)`.  
5. Return `toResponse(basket)`.

*Note: `@Max(7)` on `UpdateItemRequest` prevents `quantity > 7` from reaching the service.*

---

**`removeItem(Long userId, String sessionId, Long bookId) → BasketResponse`**  
1. `resolveBasket(...)`.  
2. Find item → `orElseThrow(BasketItemNotFoundException)`.  
3. `basket.getItems().remove(item)`.  
4. `basketRepository.save(basket)`.  
5. Return `toResponse(basket)`.

---

**`clearBasket(Long userId, String sessionId) → BasketResponse`**  
1. `resolveBasket(...)`.  
2. `basket.getItems().clear()`.  
3. `basketRepository.save(basket)`.  
4. Return `toResponse(basket)`.

#### 7.2 Private helpers

**`resolveBasket(Long userId, String sessionId) → Basket`**  
```
if userId != null:
    basketRepository.findByUserId(userId)
        .orElseGet(() -> { b = new Basket(); b.setUserId(userId); return save(b); })
else:
    basketRepository.findBySessionId(sessionId)
        .orElseGet(() -> { b = new Basket(); b.setSessionId(sessionId); return save(b); })
```

**`toResponse(Basket basket) → BasketResponse`**  
- Stream `basket.getItems()` → map each to `BasketItemDto`:
  - `author` = `book.getAuthors().get(0)` if list non-null and non-empty, else `""`.
  - `lineTotal` = `book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))`.
- `totalItems` = `items.stream().mapToInt(BasketItemDto::getQuantity).sum()`.
- `basketTotal` = `items.stream().map(BasketItemDto::getLineTotal).reduce(ZERO, add)`.

---

## 8. Controller

### `BasketController`
**Package:** `com.harsh.bookstore.controller`  
**Mapping:** `@RequestMapping("/api/basket")`  
**Constructor injection:** `BasketService`

All five handlers accept `Authentication authentication` (nullable — Spring
passes `null` for unauthenticated requests) and `HttpSession session`
(Spring creates one on first guest request with `IF_REQUIRED` policy).

| Method | Annotation | Path | Delegates to |
|--------|-----------|------|--------------|
| `getBasket` | `@GetMapping` | `/api/basket` | `basketService.getBasket(userId, sessionId)` |
| `addItem` | `@PostMapping("/items")` | `/api/basket/items` | `basketService.addItem(userId, sessionId, req)` |
| `updateItem` | `@PutMapping("/items/{bookId}")` | `/api/basket/items/{bookId}` | `basketService.updateItem(userId, sessionId, bookId, req.getQuantity())` |
| `removeItem` | `@DeleteMapping("/items/{bookId}")` | `/api/basket/items/{bookId}` | `basketService.removeItem(userId, sessionId, bookId)` |
| `clearBasket` | `@DeleteMapping` | `/api/basket` | `basketService.clearBasket(userId, sessionId)` |

`addItem` and `updateItem` annotate `@RequestBody` with `@Valid`.

#### Private helper: `resolveIdentity`

```java
private Object[] resolveIdentity(Authentication auth, HttpSession session) {
    if (auth != null && auth.getPrincipal() instanceof User user) {
        return new Object[]{ user.getId(), null };
    }
    return new Object[]{ null, session.getId() };
}
```

Returns `[userId, sessionId]` — exactly one non-null. Every handler calls this
and unpacks to pass into the service.

---

## 9. Security Config Changes

Two changes to `SecurityConfig.securityFilterChain`:

1. **Session policy** — `SessionCreationPolicy.STATELESS` → `SessionCreationPolicy.IF_REQUIRED`.  
   Spring creates a session only when one is actually needed (first guest basket
   request). JWT-authenticated requests never create a session.

2. **Permit rule** — add before `anyRequest().authenticated()`:
   ```java
   .requestMatchers("/api/basket/**").permitAll()
   ```
   This allows both guests and authenticated users to reach basket endpoints
   without a JWT being mandatory.

---

## 10. Error Mapping Summary

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Book not found | `BookNotFoundException` | 404 |
| Book out of stock | `OutOfStockException` | 400 |
| Resulting quantity > 7 | `MaxQuantityExceededException` | 400 |
| Item not in basket | `BasketItemNotFoundException` | 404 |
| Bean validation failure | `MethodArgumentNotValidException` | 400 |

---

## 11. No New Dependencies

No new entries in `pom.xml`. `HttpSession` is provided by
`jakarta.servlet-api` (transitive via `spring-boot-starter-web`). Spring
Session (JDBC/Redis) is not used — the session is in-memory on the server,
which is sufficient for a single-node development setup.
