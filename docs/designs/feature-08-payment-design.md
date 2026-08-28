# Technical Design: FEAT-08 — Payment

## 1. Overview

This document records every concrete technical decision needed to implement
`POST /api/orders` as specified in `docs/specs/feature-08-payment.md` and
planned in `docs/plans/feature-08-payment-plan.md`.

It is the authoritative reference for the coding and testing phases. Nothing
in the implementation may deviate from this document without a design update.

---

## 2. Design Decisions

### D-01 — `OrderStatus` stored as `STRING`, not ordinal

`@Enumerated(EnumType.STRING)` is used on `Order.status`. Ordinal storage
(`EnumType.ORDINAL`) is fragile: inserting a new value before `PAID` in the
enum declaration would silently corrupt every existing row. String storage
(`"PAID"`, `"CANCELLED"`) is stable regardless of declaration order.

### D-02 — Address snapshot: 7 columns on `Order`, no FK to `DeliveryAddress`

The seven address fields (`recipientName`, `phoneNumber`, `line1`, `line2`,
`city`, `state`, `pincode`) are copied from the `DeliveryAddress` row into
columns on `Order` at payment time. There is no `@ManyToOne` to
`DeliveryAddress`.

Rationale: spec BR-16 requires that subsequent edits or deletions of the
original address record do not alter stored order history.

### D-03 — `OrderItem.bookId` is a plain `Long` column, no `@ManyToOne` to `Book`

`OrderItem.title` stores the book title as a snapshot. `OrderItem.bookId`
stores the book's ID as a plain `Long` with no `@ManyToOne`. If a book is
later removed from the catalogue, order history still shows the correct title
and price. This mirrors the address-snapshot reasoning in D-02.

### D-04 — `orderDate` set via `@PrePersist`, not in the service

`Order.orderDate` is set inside a `@PrePersist` callback rather than in
`OrderService`. This keeps the timestamp logic inside the entity (single
responsibility) and ensures the column is always populated regardless of
how the entity is saved — no risk of forgetting to set it in the service.

### D-05 — Delivery charge and estimated delivery date computed in `OrderService`

`OrderService` replicates the delivery charge rule
(`basketTotal >= 500 → BigDecimal.ZERO; else new BigDecimal("50.00")`) and
the delivery date rule (`LocalDate.now().plusDays(3).toString()`) directly,
rather than delegating to `CheckoutService`. This keeps `OrderService`
independently testable with no dependency on `CheckoutService`.

### D-06 — `expiryYear` validated in service, not with a static Bean Validation annotation

`@Min` requires a compile-time constant. The current calendar year is not a
constant. Validation is performed at service entry:

```java
if (req.getExpiryYear() < LocalDate.now().getYear()) {
    throw new IllegalArgumentException("expiryYear must be the current year or later");
}
```

The existing `IllegalArgumentException` handler in `GlobalExceptionHandler`
already returns 400 — no new exception class is needed.

### D-07 — Stock check pass before any mutation

All stock levels are validated in a first pass over the basket items before
any decrement is applied. This ensures that if item N lacks stock, no stock
has already been decremented for items 1..N-1. The order of operations inside
`placeOrder` is:

1. Validate all inputs (year, empty basket, address, card decline)
2. Check all stock levels (throw `InsufficientStockException` on first failure)
3. Decrement all stock
4. Save `Order` (cascade saves `OrderItem` rows)
5. Clear basket

### D-08 — `BasketService.clearBasket(userId, null)` called after order save

`clearBasket` is called with `(userId, null)` — the user is always
authenticated when placing an order (no guest checkout). The `null` session
argument is consistent with all other authenticated-user calls to
`BasketService` already in the codebase (e.g. `CheckoutService`).

### D-09 — Card details never persisted

`PaymentRequest` is a plain DTO used only within the request thread. No card
entity, no card table, and no card column exists anywhere in the schema. After
the method returns, the DTO is garbage-collected. Spec BR-17 is satisfied by
design.

### D-10 — `giftPointsToRedeem` accepted and ignored in FEAT-08

The field is present on `PaymentRequest` with `@Min(0)`. It is read and
validated (negative values → 400) but never passed to `OrderService`'s
calculation. No storage column is needed in FEAT-08; it will be wired up in
FEAT-09.

### D-11 — `OrderResponse` fields serialised as `String` to avoid Jackson config issues

`orderDate` is returned as `order.getOrderDate().toString()` (a `String`).
`status` is returned as `order.getStatus().name()` (a `String`). This avoids
any dependency on Jackson date or enum serialisation configuration — matching
the pattern established by `CheckoutSummaryResponse.estimatedDeliveryDate` in
FEAT-07.

---

## 3. File Inventory

### 3.1 New production files

| # | Path | Role |
|---|------|------|
| 1 | `entity/OrderStatus.java` | Enum `PAID` / `CANCELLED` |
| 2 | `entity/Order.java` | `@Entity` — `orders` table |
| 3 | `entity/OrderItem.java` | `@Entity` — `order_item` table |
| 4 | `repository/OrderRepository.java` | `findAllByUserId` |
| 5 | `exception/PaymentDeclinedException.java` | → 402 |
| 6 | `exception/InsufficientStockException.java` | → 400 |
| 7 | `dto/PaymentRequest.java` | Request body |
| 8 | `dto/OrderItemResponse.java` | One line item in response |
| 9 | `dto/OrderAddressSnapshot.java` | `deliveryAddress` object in response |
| 10 | `dto/OrderResponse.java` | Full 201 response body |
| 11 | `service/OrderService.java` | `placeOrder` orchestration |
| 12 | `controller/OrderController.java` | `POST /api/orders` |

### 3.2 Modified production files

| File | Change |
|------|--------|
| `exception/GlobalExceptionHandler.java` | Add `// FEAT-08 handlers` section with two `@ExceptionHandler` methods |

### 3.3 New test files

| # | Path |
|---|------|
| 1 | `test/repository/OrderRepositoryTest.java` |
| 2 | `test/service/OrderServiceTest.java` |
| 3 | `test/controller/OrderControllerTest.java` |

---

## 4. Entity Design

### 4.1 `OrderStatus` enum

```
package: com.harsh.bookstore.entity
values:  PAID, CANCELLED
```

No fields, no constructor. Two-value enum.

---

### 4.2 `Order` entity

```
table:   orders          (NOT "order" — SQL reserved word)
indexes: idx_order_user_id ON (user_id)
```

| Java field | Column | Type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | `Long` | No | `@Id @GeneratedValue(IDENTITY)` |
| `userId` | `user_id` | `Long` | No | `@Column(nullable=false)` |
| `status` | `status` | `OrderStatus` | No | `@Enumerated(STRING)`, `length=20` |
| `orderDate` | `order_date` | `LocalDateTime` | No | `updatable=false`; set by `@PrePersist` |
| `basketTotal` | `basket_total` | `BigDecimal` | No | `precision=10, scale=2` |
| `deliveryCharge` | `delivery_charge` | `BigDecimal` | No | `precision=10, scale=2` |
| `totalAmount` | `total_amount` | `BigDecimal` | No | `precision=10, scale=2` |
| `estimatedDeliveryDate` | `estimated_delivery_date` | `String` | No | `length=10`, `YYYY-MM-DD` |
| `recipientName` | `recipient_name` | `String` | No | `length=100` |
| `phoneNumber` | `phone_number` | `String` | No | `length=10` |
| `line1` | `line1` | `String` | No | `length=200` |
| `line2` | `line2` | `String` | Yes | `length=200`; nullable |
| `city` | `city` | `String` | No | `length=100` |
| `state` | `state` | `String` | No | `length=100` |
| `pincode` | `pincode` | `String` | No | `length=6` |
| `items` | — | `List<OrderItem>` | — | `@OneToMany(mappedBy="order", cascade=ALL, orphanRemoval=true)` |

**`@PrePersist` callback** (`protected void onCreate()`):
```java
if (orderDate == null) { orderDate = LocalDateTime.now(); }
```

**`equals` / `hashCode`**: id-based equality (`id != null && id.equals(that.id)`);
`hashCode()` returns `getClass().hashCode()`.

**No-arg constructor** required by JPA.

---

### 4.3 `OrderItem` entity

```
table:   order_item
indexes: idx_order_item_order_id ON (order_id)
```

| Java field | Column | Type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | `Long` | No | `@Id @GeneratedValue(IDENTITY)` |
| `order` | `order_id` | `Order` | No | `@ManyToOne(fetch=LAZY) @JoinColumn(name="order_id", nullable=false)` |
| `bookId` | `book_id` | `Long` | No | Plain `@Column` — no `@ManyToOne` to `Book` (D-03) |
| `title` | `title` | `String` | No | `length=500`; snapshot |
| `quantity` | `quantity` | `int` | No | `@Column(nullable=false)` |
| `unitPrice` | `unit_price` | `BigDecimal` | No | `precision=10, scale=2` |
| `lineTotal` | `line_total` | `BigDecimal` | No | `precision=10, scale=2` |

**`equals` / `hashCode`**: same id-based pattern as `Order`.

**No-arg constructor** required by JPA.

---

## 5. Repository Design

### `OrderRepository`

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByUserId(Long userId);
}
```

`findAllByUserId` is derived from the method name — no `@Query` needed.
Used by FEAT-10 (order history). No other custom methods needed in FEAT-08.

---

## 6. Exception Design

### `PaymentDeclinedException`

```java
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException() {
        super("Payment declined");
    }
}
```

Handled in `GlobalExceptionHandler` → **402 Payment Required**.

---

### `InsufficientStockException`

```java
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String title) {
        super("Insufficient stock for: " + title);
    }
}
```

Handled in `GlobalExceptionHandler` → **400 Bad Request**.

---

## 7. DTO Design

### `PaymentRequest`

```
Fields and Bean Validation annotations:
```

| Field | Type | Annotations |
|---|---|---|
| `addressId` | `Long` | `@NotNull` |
| `cardNumber` | `String` | `@NotBlank @Pattern(regexp="\\d{16}", message="cardNumber must be exactly 16 numeric digits")` |
| `expiryMonth` | `int` | `@Min(value=1, message="expiryMonth must be between 1 and 12") @Max(value=12, message="expiryMonth must be between 1 and 12")` |
| `expiryYear` | `int` | none — validated in service (D-06) |
| `cvv` | `String` | `@NotBlank @Pattern(regexp="\\d{3}", message="cvv must be exactly 3 numeric digits")` |
| `cardholderName` | `String` | `@NotBlank(message="cardholderName must not be blank")` |
| `giftPointsToRedeem` | `int` | `@Min(value=0, message="giftPointsToRedeem must be non-negative")` |

Default value for `giftPointsToRedeem`: `0` (Java primitive default).

---

### `OrderItemResponse`

| Field | Type |
|---|---|
| `bookId` | `Long` |
| `title` | `String` |
| `quantity` | `int` |
| `unitPrice` | `BigDecimal` |
| `lineTotal` | `BigDecimal` |

No-arg constructor + explicit getters/setters. No validation annotations.

---

### `OrderAddressSnapshot`

| Field | Type |
|---|---|
| `recipientName` | `String` |
| `phoneNumber` | `String` |
| `line1` | `String` |
| `line2` | `String` (nullable) |
| `city` | `String` |
| `state` | `String` |
| `pincode` | `String` |

No-arg constructor + explicit getters/setters. No validation annotations.

---

### `OrderResponse`

| Field | Type | Notes |
|---|---|---|
| `orderId` | `Long` | |
| `status` | `String` | `"PAID"` — enum `.name()` |
| `orderDate` | `String` | `LocalDateTime.toString()` e.g. `"2025-08-21T14:30:00"` |
| `items` | `List<OrderItemResponse>` | |
| `basketTotal` | `BigDecimal` | |
| `deliveryCharge` | `BigDecimal` | |
| `totalAmount` | `BigDecimal` | |
| `estimatedDeliveryDate` | `String` | `YYYY-MM-DD` |
| `deliveryAddress` | `OrderAddressSnapshot` | |

No-arg constructor + explicit getters/setters.

---

## 8. Service Design

### `OrderService`

```
@Service
Constructor-injected:
  - OrderRepository          orderRepository
  - BasketService            basketService
  - DeliveryAddressRepository addressRepository
  - BookRepository           bookRepository
```

#### `@Transactional public OrderResponse placeOrder(Long userId, PaymentRequest req)`

```
Step 1 — expiryYear runtime validation (D-06)
    if req.getExpiryYear() < LocalDate.now().getYear():
        throw new IllegalArgumentException("expiryYear must be the current year or later")

Step 2 — empty basket guard
    BasketResponse basket = basketService.getBasket(userId, null)
    if basket.getItems().isEmpty():
        throw new IllegalArgumentException("Basket is empty")

Step 3 — address ownership check (mirrors AddressService pattern)
    DeliveryAddress address = addressRepository.findById(req.getAddressId())
        .orElseThrow(() -> new AddressNotFoundException(req.getAddressId()))
    if (!address.getUserId().equals(userId)):
        throw new AddressAccessForbiddenException()

Step 4 — simulated card decline (BR-10)
    if "0000000000000000".equals(req.getCardNumber()):
        throw new PaymentDeclinedException()

Step 5 — compute charges (D-05)
    BigDecimal deliveryCharge =
        basket.getBasketTotal().compareTo(new BigDecimal("500")) >= 0
            ? BigDecimal.ZERO
            : new BigDecimal("50.00")
    BigDecimal totalAmount = basket.getBasketTotal().add(deliveryCharge)
    String estimatedDeliveryDate = LocalDate.now().plusDays(3).toString()

Step 6 — stock validation pass (D-07, first pass — no mutations yet)
    for each item in basket.getItems():
        Book book = bookRepository.findById(item.getBookId())
            .orElseThrow(() -> new BookNotFoundException(item.getBookId()))
        if book.getStockQuantity() < item.getQuantity():
            throw new InsufficientStockException(item.getTitle())

Step 7 — stock decrement pass (D-07, second pass — all validations passed)
    for each item in basket.getItems():
        Book book = bookRepository.findById(item.getBookId()).get()
        book.setStockQuantity(book.getStockQuantity() - item.getQuantity())
        bookRepository.save(book)

Step 8 — build and save Order
    Order order = new Order()
    order.setUserId(userId)
    order.setStatus(OrderStatus.PAID)
    order.setBasketTotal(basket.getBasketTotal())
    order.setDeliveryCharge(deliveryCharge)
    order.setTotalAmount(totalAmount)
    order.setEstimatedDeliveryDate(estimatedDeliveryDate)
    // address snapshot
    order.setRecipientName(address.getRecipientName())
    order.setPhoneNumber(address.getPhoneNumber())
    order.setLine1(address.getLine1())
    order.setLine2(address.getLine2())
    order.setCity(address.getCity())
    order.setState(address.getState())
    order.setPincode(address.getPincode())
    // order items
    for each BasketItemDto item in basket.getItems():
        OrderItem oi = new OrderItem()
        oi.setOrder(order)
        oi.setBookId(item.getBookId())
        oi.setTitle(item.getTitle())
        oi.setQuantity(item.getQuantity())
        oi.setUnitPrice(item.getUnitPrice())
        oi.setLineTotal(item.getLineTotal())
        order.getItems().add(oi)

    Order saved = orderRepository.save(order)   // cascade saves OrderItems

Step 9 — clear basket (D-08)
    basketService.clearBasket(userId, null)

Step 10 — build and return response
    return toResponse(saved)
```

#### `private OrderResponse toResponse(Order order)`

Maps every field of the saved `Order` to `OrderResponse`:
- `orderId` ← `order.getId()`
- `status` ← `order.getStatus().name()`
- `orderDate` ← `order.getOrderDate().toString()`
- `items` ← map each `OrderItem` to `OrderItemResponse`
- `basketTotal`, `deliveryCharge`, `totalAmount`, `estimatedDeliveryDate` ← direct
- `deliveryAddress` ← new `OrderAddressSnapshot` with the 7 snapshot fields

---

## 9. Controller Design

### `OrderController`

```
@RestController
@RequestMapping("/api/orders")
Constructor-injected: OrderService orderService
```

| Method | HTTP verb | Path | Response status | Auth |
|---|---|---|---|---|
| `placeOrder` | POST | `/api/orders` | 201 Created | Required (JWT) |

**`placeOrder` signature:**

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public OrderResponse placeOrder(
        @Valid @RequestBody PaymentRequest req,
        Authentication authentication) {

    User user = (User) authentication.getPrincipal();
    return orderService.placeOrder(user.getId(), req);
}
```

- `@Valid` triggers Bean Validation on `PaymentRequest`.
- `Authentication` is non-null — Spring Security rejects unauthenticated
  requests before the method is reached (401 via `HttpStatusEntryPoint`).
- `userId` is extracted from the `User` principal via `.getId()` — consistent
  with `AddressController` and `CheckoutController`.

---

## 10. `GlobalExceptionHandler` additions

Insert a `// FEAT-08 handlers` section (before the `// FEAT-07 handlers`
section) with two new `@ExceptionHandler` methods:

```
PaymentDeclinedException  → 402 Payment Required
InsufficientStockException → 400 Bad Request
```

Pattern is identical to every existing handler: build `ErrorResponse`,
return `ResponseEntity.status(...).body(body)`.

---

## 11. HTTP Status Mapping Summary

| Scenario | Exception thrown | HTTP status |
|---|---|---|
| No JWT | — (Spring Security) | 401 |
| Empty basket | `IllegalArgumentException` | 400 |
| `addressId` not found | `AddressNotFoundException` | 404 |
| `addressId` wrong owner | `AddressAccessForbiddenException` | 403 |
| Bean Validation failure | `MethodArgumentNotValidException` | 400 |
| `expiryYear` < current year | `IllegalArgumentException` | 400 |
| Card `0000000000000000` | `PaymentDeclinedException` | 402 |
| Insufficient stock | `InsufficientStockException` | 400 |
| Success | — | 201 |

---

## 12. No SecurityConfig changes

`/api/orders` falls under the existing `anyRequest().authenticated()` rule
added in FEAT-06. No modifications to `SecurityConfig.java` are needed.

---

## 13. No new Maven dependencies

All required types are already on the classpath from existing dependencies:

| Type | Source |
|---|---|
| `@Transactional` | `spring-boot-starter-data-jpa` |
| `LocalDate`, `LocalDateTime` | JDK `java.time` |
| `BigDecimal` | JDK `java.math` |
| Bean Validation annotations | `spring-boot-starter-validation` |
| `JpaRepository` | `spring-boot-starter-data-jpa` |

---

## 14. Sequence Diagram

```
Client          OrderController      OrderService        BasketService
  |                   |                   |                    |
  | POST /api/orders  |                   |                    |
  |------------------>|                   |                    |
  |                   | placeOrder(userId, req)                |
  |                   |------------------>|                    |
  |                   |                   | getBasket(userId, null)
  |                   |                   |------------------->|
  |                   |                   |<-------------------|
  |                   |                   | findById(addressId)
  |                   |                   |---> AddressRepository
  |                   |                   | findById(bookId) × N
  |                   |                   |---> BookRepository (check)
  |                   |                   | save(book) × N
  |                   |                   |---> BookRepository (decrement)
  |                   |                   | save(order)
  |                   |                   |---> OrderRepository
  |                   |                   | clearBasket(userId, null)
  |                   |                   |------------------->|
  |                   |                   |<-------------------|
  |                   |<------------------|
  | 201 OrderResponse |
  |<------------------|
```
