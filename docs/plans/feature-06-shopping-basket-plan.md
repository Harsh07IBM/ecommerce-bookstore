# Implementation Plan: FEAT-06 — Shopping Basket

## 1. Overview

This plan describes every concrete step required to implement the Shopping
Basket feature as specified in `docs/specs/feature-06-shopping-basket.md`.

---

## 2. Key Design Decision — Session Strategy

The current `SecurityConfig` sets `SessionCreationPolicy.STATELESS` — Spring
Security never creates or reads an `HttpSession`. That is correct for JWT-only
endpoints, but the basket spec requires a session cookie to identify **guest**
baskets.

**Resolution:**  
Change session policy to `SessionCreationPolicy.IF_REQUIRED`. Spring will only
create a session when one is actually needed (i.e., when a guest calls a basket
endpoint). Authenticated requests still carry a JWT and will not create a
session. This is the minimal change that satisfies both concerns.

The guest basket is identified by the `HttpSession` ID and stored in the DB.
The auth user basket is identified by the `userId` extracted from the JWT.

---

## 3. New Files

| Layer | File | Purpose |
|-------|------|---------|
| entity | `Basket.java` | Root basket entity — owns the `items` collection |
| entity | `BasketItem.java` | One item (book + quantity) inside a basket |
| repository | `BasketRepository.java` | JPA repository for `Basket` |
| service | `BasketService.java` | All basket business logic |
| controller | `BasketController.java` | 5 REST endpoints |
| dto | `BasketItemDto.java` | One line in the basket response |
| dto | `BasketResponse.java` | Full basket response body |
| dto | `AddItemRequest.java` | Request body for POST /api/basket/items |
| dto | `UpdateItemRequest.java` | Request body for PUT /api/basket/items/{bookId} |
| exception | `BasketItemNotFoundException.java` | Thrown when item not in basket (→ 404) |
| exception | `OutOfStockException.java` | Thrown when book stock = 0 (→ 400) |
| exception | `MaxQuantityExceededException.java` | Thrown when quantity > 7 (→ 400) |

---

## 4. Modified Files

| File | Change |
|------|--------|
| `SecurityConfig.java` | Change `STATELESS` → `IF_REQUIRED`; add `GET/POST/PUT/DELETE /api/basket/**` to `permitAll()` (basket is accessible to guests) |
| `GlobalExceptionHandler.java` | Add `@ExceptionHandler` for `BasketItemNotFoundException` (404), `OutOfStockException` (400), `MaxQuantityExceededException` (400) |

---

## 5. Step-by-Step Implementation Order

### Step 1 — Entities

**`Basket`**
- Fields: `id` (Long PK), `userId` (Long, nullable), `sessionId` (String, nullable), `createdAt` (LocalDateTime)
- `userId` is set for auth users; `sessionId` is set for guests. Exactly one of them is non-null per row.
- `@OneToMany(mappedBy = "basket", cascade = ALL, orphanRemoval = true)` → `List<BasketItem> items`
- DB table: `basket`

**`BasketItem`**
- Fields: `id` (Long PK), `basket` (ManyToOne), `book` (ManyToOne, FetchType.EAGER), `quantity` (int)
- DB table: `basket_item`

---

### Step 2 — Repository

**`BasketRepository`** extends `JpaRepository<Basket, Long>`
- `Optional<Basket> findByUserId(Long userId)`
- `Optional<Basket> findBySessionId(String sessionId)`
- `void deleteBySessionId(String sessionId)`

---

### Step 3 — Exceptions

Three new domain exceptions:

- `BasketItemNotFoundException(Long bookId)` — message: `"Book {bookId} is not in your basket"`
- `OutOfStockException()` — message: `"This book is currently out of stock"`
- `MaxQuantityExceededException()` — message: `"Maximum quantity per book is 7"`

Add handlers to `GlobalExceptionHandler`:
- `BasketItemNotFoundException` → 404
- `OutOfStockException` → 400
- `MaxQuantityExceededException` → 400

---

### Step 4 — DTOs

**`BasketItemDto`** — fields: `bookId`, `title`, `author` (first author), `coverImageUrl`, `unitPrice` (BigDecimal), `quantity`, `lineTotal` (BigDecimal)

**`BasketResponse`** — fields: `List<BasketItemDto> items`, `int totalItems`, `BigDecimal basketTotal`

**`AddItemRequest`** — fields: `Long bookId` (`@NotNull`), `int quantity` (default 1, `@Min(1) @Max(7)`)

**`UpdateItemRequest`** — fields: `int quantity` (`@Min(0) @Max(7)`)

---

### Step 5 — Service

**`BasketService`** — constructor-injected: `BasketRepository`, `BookRepository`

**Private helper:** `resolveBasket(Long userId, String sessionId)` — finds or creates the right basket:
- If `userId` != null → `findByUserId` or create new with `userId`
- Else → `findBySessionId` or create new with `sessionId`

**Public methods:**

| Method | Logic |
|--------|-------|
| `getBasket(Long userId, String sessionId)` | `resolveBasket` → `toResponse` |
| `addItem(Long userId, String sessionId, AddItemRequest req)` | Resolve basket → load book (404 if missing) → check stock (400 if 0) → find existing item or create new → check resulting quantity ≤ 7 (400 if not) → save → return response |
| `updateItem(Long userId, String sessionId, Long bookId, int quantity)` | Resolve basket → find item (404 if missing) → if quantity = 0, remove item; else set quantity and check ≤ 7 → save → return response |
| `removeItem(Long userId, String sessionId, Long bookId)` | Resolve basket → find item (404 if missing) → remove → save → return response |
| `clearBasket(Long userId, String sessionId)` | Resolve basket → clear all items → save → return empty response |

**Private helper:** `toResponse(Basket basket)` — maps basket to `BasketResponse`:
- For each item: build `BasketItemDto` with `lineTotal = unitPrice × quantity`
- `totalItems = sum of quantities`
- `basketTotal = sum of lineTotals`

---

### Step 6 — Controller

**`BasketController`** — `@RestController`, `@RequestMapping("/api/basket")`, constructor-injected: `BasketService`

**Private helper:** `resolveIdentity(Authentication auth, HttpSession session)` — extracts `userId` (from `User` principal if auth is not null) or `sessionId` (from `session.getId()`).

| Method | Endpoint | Delegation |
|--------|----------|------------|
| `getBasket` | `GET /api/basket` | `basketService.getBasket(...)` |
| `addItem` | `POST /api/basket/items` | `basketService.addItem(...)` |
| `updateItem` | `PUT /api/basket/items/{bookId}` | `basketService.updateItem(...)` |
| `removeItem` | `DELETE /api/basket/items/{bookId}` | `basketService.removeItem(...)` |
| `clearBasket` | `DELETE /api/basket` | `basketService.clearBasket(...)` |

All 5 methods accept `Authentication authentication` (nullable for guests) and
`HttpSession session` (Spring injects it automatically, creating one if needed).

---

### Step 7 — Security Config Update

In `SecurityConfig.securityFilterChain`:
1. Change `SessionCreationPolicy.STATELESS` → `SessionCreationPolicy.IF_REQUIRED`
2. Add to `permitAll()`:
   ```
   .requestMatchers("/api/basket/**").permitAll()
   ```
   (basket endpoints are open to both guests and authenticated users)

---

## 6. No New Dependencies

No new Maven dependencies are required. `HttpSession` is part of the
`jakarta.servlet` API already on the classpath via `spring-boot-starter-web`.
Spring Session (JDBC/Redis) is **not** used — the session is in-memory on the
server, which is sufficient for a dev/single-node setup.

---

## 7. Test Plan (to be executed in Stage 5)

### 7.1 Repository Tests — `@DataJpaTest`
| Test | What it verifies |
|------|-----------------|
| `findByUserId_returnsBasket` | Finds a basket by userId |
| `findBySessionId_returnsBasket` | Finds a basket by sessionId |
| `findByUserId_returnsEmpty_whenNotFound` | Empty Optional for unknown userId |

### 7.2 Service Tests — `@ExtendWith(MockitoExtension.class)`
| Test | What it verifies |
|------|-----------------|
| `getBasket_emptyBasket` | Returns empty response for new basket |
| `addItem_success` | Item added, response correct |
| `addItem_sameBookIncrementsQuantity` | Re-adding same book sums quantities |
| `addItem_outOfStock_throws` | OutOfStockException when stock = 0 |
| `addItem_exceedsMaxQuantity_throws` | MaxQuantityExceededException when total > 7 |
| `addItem_bookNotFound_throws` | BookNotFoundException when book missing |
| `updateItem_success` | Quantity updated, response correct |
| `updateItem_zeroQuantity_removesItem` | Item removed when quantity = 0 |
| `updateItem_itemNotFound_throws` | BasketItemNotFoundException |
| `removeItem_success` | Item removed, response correct |
| `removeItem_notFound_throws` | BasketItemNotFoundException |
| `clearBasket_success` | All items removed, empty response |

### 7.3 Controller Tests — `@WebMvcTest`
| Test | What it verifies |
|------|-----------------|
| `GET /api/basket` → 200 | Returns basket response |
| `POST /api/basket/items` → 200 | Adds item, returns basket |
| `POST /api/basket/items` → 400 (out of stock) | OutOfStockException → 400 |
| `POST /api/basket/items` → 400 (max qty) | MaxQuantityExceededException → 400 |
| `POST /api/basket/items` → 404 (book missing) | BookNotFoundException → 404 |
| `PUT /api/basket/items/{bookId}` → 200 | Updates quantity |
| `PUT /api/basket/items/{bookId}` → 404 | Item not in basket |
| `DELETE /api/basket/items/{bookId}` → 200 | Removes item |
| `DELETE /api/basket/items/{bookId}` → 404 | Item not in basket |
| `DELETE /api/basket` → 200 | Clears basket |

---

## 8. Acceptance Criteria Traceability

| AC | Covered by |
|----|-----------|
| AC-01 | Service test `getBasket_emptyBasket` + Controller test `GET 200` |
| AC-02 | Service test `addItem_success` |
| AC-03 | Service test `addItem_sameBookIncrementsQuantity` |
| AC-04 | Service test `addItem_outOfStock_throws` + Controller test `POST 400` |
| AC-05 | Service test `addItem_exceedsMaxQuantity_throws` + Controller test `POST 400` |
| AC-06 | Service test `updateItem_zeroQuantity_removesItem` |
| AC-07 | Service test `removeItem_notFound_throws` + Controller test `DELETE 404` |
| AC-08 | Service test `clearBasket_success` + Controller test `DELETE /api/basket 200` |
| AC-09 | Service test `getBasket_emptyBasket` + `addItem_success` (assert lineTotals / basketTotal) |
| AC-10 | Repository test `findByUserId_returnsBasket` |
| AC-11 | Repository test `findBySessionId_returnsBasket` |
