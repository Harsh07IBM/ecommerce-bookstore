# Implementation Plan: FEAT-08 — Payment

## 1. Overview

This plan describes every concrete step required to implement the Payment
feature as specified in `docs/specs/feature-08-payment.md`.

The feature adds one endpoint — `POST /api/orders` — that accepts card details
and an address ID, runs a simulated payment, and on success creates a persisted
`Order` with a full address snapshot, decrements book stock, and clears the
user's basket.

Payment is fully simulated: card number `0000000000000000` always fails (402);
any other validly-formatted card number always succeeds (201). No real gateway
is involved and card details are never persisted.

All endpoints require a valid JWT. Guest access is not supported.

---

## 2. Key Design Decisions

### OrderStatus as a String-backed enum
`OrderStatus` is a Java enum with values `PAID` and `CANCELLED`.
It is stored in the database using `@Enumerated(EnumType.STRING)` so that the
column value is the readable string `"PAID"` or `"CANCELLED"`, not an ordinal
integer. Ordinal storage is fragile — adding a value changes existing row
semantics if the enum declaration order changes.

### Address snapshot (no FK to DeliveryAddress)
The seven address fields (`recipientName`, `phoneNumber`, `line1`, `line2`,
`city`, `state`, `pincode`) are stored directly on the `Order` row, not as a
foreign key to `DeliveryAddress`. This matches spec BR-16: if a user later
edits or deletes the address record, order history remains accurate.

### Title snapshot on OrderItem (no FK to Book)
`OrderItem.title` stores the book title as a snapshot at order time.
`OrderItem.bookId` is a plain `Long` column — no `@ManyToOne` to `Book`.
If a book is later removed from the catalogue, order history still shows the
correct title and price.

### Delivery charge and date computed in OrderService (not delegated to CheckoutService)
`OrderService` replicates the delivery charge rule
(`basketTotal >= 500 → ₹0; else ₹50`) and estimated delivery date
(`LocalDate.now().plusDays(3)`) directly, rather than calling `CheckoutService`.
This keeps `OrderService` self-contained and independently testable without
needing to mock `CheckoutService`.

### Atomicity: check stock → decrement → clear basket → save order
All steps inside `placeOrder` run within a single `@Transactional` method.
The order is:
1. Validate all stock (throw before any mutation if any item is short)
2. Decrement all stock
3. Save the order (cascade saves items)
4. Clear the basket

If the transaction rolls back (e.g. on an unexpected exception after stock
decrement), no partial state is committed to the database.

### expiryYear validated in service, not with a static annotation
`@Min` annotations require a compile-time constant. The current calendar year
is not a constant, so `expiryYear` validation is performed at service entry:
```
if (req.getExpiryYear() < LocalDate.now().getYear()) → throw IllegalArgumentException
```
The existing `IllegalArgumentException` handler in `GlobalExceptionHandler`
returns 400 — no new exception class needed for this case.

### Card details never persisted
`PaymentRequest` fields are used only in-memory for format validation and the
decline check. No card entity, no card column, no card table exists.

### No SecurityConfig changes needed
`/api/orders` falls under the existing `anyRequest().authenticated()` catch-all
in `SecurityConfig`. No new permit rules are required.

### No new Maven dependencies
All required types (`@Transactional`, `BigDecimal`, `LocalDate`, Bean
Validation, Spring Data JPA) are already on the classpath.

---

## 3. New Files

| Layer | File | Purpose |
|-------|------|---------|
| entity | `OrderStatus.java` | Enum: `PAID`, `CANCELLED` |
| entity | `Order.java` | Order row — address snapshot, totals, status, items |
| entity | `OrderItem.java` | One book line item inside an order |
| repository | `OrderRepository.java` | `findAllByUserId` for future FEAT-10 order history |
| service | `OrderService.java` | Full `placeOrder` orchestration |
| controller | `OrderController.java` | `POST /api/orders` → 201 |
| dto | `PaymentRequest.java` | Request body with all Bean Validation annotations |
| dto | `OrderItemResponse.java` | One line item in the order response |
| dto | `OrderAddressSnapshot.java` | The `deliveryAddress` object in the response |
| dto | `OrderResponse.java` | Full 201 response body |
| exception | `PaymentDeclinedException.java` | Card `0000000000000000` → 402 |
| exception | `InsufficientStockException.java` | Insufficient stock at payment time → 400 |

---

## 4. Modified Files

| File | Change |
|------|--------|
| `GlobalExceptionHandler.java` | Add `@ExceptionHandler` for `PaymentDeclinedException` (402) and `InsufficientStockException` (400) |
| `SecurityConfig.java` | **No change required.** `/api/orders` is already covered by `anyRequest().authenticated()`. |

---

## 5. Step-by-Step Implementation Order

### Step 1 — Enum + Entities

**`OrderStatus.java`**
```java
public enum OrderStatus {
    PAID,
    CANCELLED
}
```

**`Order`**
- Annotations: `@Entity`, `@Table(name = "orders")` — `"order"` is an SQL reserved word
- Fields:
  - `id` — `Long`, `@Id @GeneratedValue(IDENTITY)`
  - `userId` — `Long`, `@Column(name = "user_id", nullable = false)`
  - `status` — `OrderStatus`, `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)`
  - `orderDate` — `LocalDateTime`, `@Column(name = "order_date", nullable = false, updatable = false)` — set via `@PrePersist`
  - `basketTotal` — `BigDecimal`, `@Column(name = "basket_total", nullable = false, precision = 10, scale = 2)`
  - `deliveryCharge` — `BigDecimal`, `@Column(name = "delivery_charge", nullable = false, precision = 10, scale = 2)`
  - `totalAmount` — `BigDecimal`, `@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)`
  - `estimatedDeliveryDate` — `String`, `@Column(name = "estimated_delivery_date", nullable = false, length = 10)` — stored as `YYYY-MM-DD`
  - `recipientName` — `String`, `@Column(name = "recipient_name", nullable = false, length = 100)`
  - `phoneNumber` — `String`, `@Column(name = "phone_number", nullable = false, length = 10)`
  - `line1` — `String`, `@Column(nullable = false, length = 200)`
  - `line2` — `String`, `@Column(length = 200)` — nullable
  - `city` — `String`, `@Column(nullable = false, length = 100)`
  - `state` — `String`, `@Column(nullable = false, length = 100)`
  - `pincode` — `String`, `@Column(nullable = false, length = 6)`
  - `items` — `List<OrderItem>`, `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)`
- `@Table` index: `@Index(name = "idx_order_user_id", columnList = "user_id")`
- `@PrePersist` sets `orderDate = LocalDateTime.now()` if null

**`OrderItem`**
- Annotations: `@Entity`, `@Table(name = "order_item", indexes = {@Index(name = "idx_order_item_order_id", columnList = "order_id")})`
- Fields:
  - `id` — `Long`, `@Id @GeneratedValue(IDENTITY)`
  - `order` — `Order`, `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false)`
  - `bookId` — `Long`, `@Column(name = "book_id", nullable = false)` — plain column, no `@ManyToOne`
  - `title` — `String`, `@Column(nullable = false, length = 500)` — snapshot
  - `quantity` — `int`, `@Column(nullable = false)`
  - `unitPrice` — `BigDecimal`, `@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)`
  - `lineTotal` — `BigDecimal`, `@Column(name = "line_total", nullable = false, precision = 10, scale = 2)`

---

### Step 2 — Repository

**`OrderRepository`** extends `JpaRepository<Order, Long>`

| Method | Purpose |
|--------|---------|
| `List<Order> findAllByUserId(Long userId)` | Retrieve all orders for a user — used by FEAT-10 (order history) |

---

### Step 3 — Exceptions

- **`PaymentDeclinedException()`**
  - Message: `"Payment declined"`
  - Maps to → **402 Payment Required**

- **`InsufficientStockException(String title)`**
  - Message: `"Insufficient stock for: " + title`
  - Maps to → **400 Bad Request**

---

### Step 4 — DTOs

**`PaymentRequest`** — request body for `POST /api/orders`

| Field | Type | Validation |
|-------|------|------------|
| `addressId` | `Long` | `@NotNull` |
| `cardNumber` | `String` | `@NotBlank @Pattern(regexp = "\\d{16}", message = "cardNumber must be exactly 16 numeric digits")` |
| `expiryMonth` | `int` | `@Min(1) @Max(12)` with message `"expiryMonth must be between 1 and 12"` |
| `expiryYear` | `int` | No static annotation — validated in service (see design decision) |
| `cvv` | `String` | `@NotBlank @Pattern(regexp = "\\d{3}", message = "cvv must be exactly 3 numeric digits")` |
| `cardholderName` | `String` | `@NotBlank(message = "cardholderName must not be blank")` |
| `giftPointsToRedeem` | `int` | `@Min(value = 0, message = "giftPointsToRedeem must be non-negative")`, default `0` |

**`OrderItemResponse`** — fields: `bookId` (Long), `title` (String), `quantity` (int), `unitPrice` (BigDecimal), `lineTotal` (BigDecimal)

**`OrderAddressSnapshot`** — fields: `recipientName`, `phoneNumber`, `line1`, `line2` (nullable), `city`, `state`, `pincode`

**`OrderResponse`** — fields:
- `orderId` (Long)
- `status` (String — `"PAID"`)
- `orderDate` (String — `LocalDateTime.toString()`, e.g. `"2025-08-21T14:30:00"`)
- `items` (List\<OrderItemResponse\>)
- `basketTotal` (BigDecimal)
- `deliveryCharge` (BigDecimal)
- `totalAmount` (BigDecimal)
- `estimatedDeliveryDate` (String)
- `deliveryAddress` (OrderAddressSnapshot)

---

### Step 5 — OrderService

**`OrderService`** — `@Service`, constructor-injected: `OrderRepository`, `BasketService`, `DeliveryAddressRepository`, `BookRepository`

#### `@Transactional placeOrder(Long userId, PaymentRequest req)` → `OrderResponse`

```
1.  if req.getExpiryYear() < LocalDate.now().getYear():
        throw new IllegalArgumentException("expiryYear must be the current year or later")

2.  BasketResponse basket = basketService.getBasket(userId, null)
    if basket.getItems().isEmpty():
        throw new IllegalArgumentException("Basket is empty")

3.  DeliveryAddress address = addressRepository.findById(req.getAddressId())
        .orElseThrow(() -> new AddressNotFoundException(req.getAddressId()))
    if !address.getUserId().equals(userId):
        throw new AddressAccessForbiddenException()

4.  if req.getCardNumber().equals("0000000000000000"):
        throw new PaymentDeclinedException()

5.  BigDecimal deliveryCharge =
        basket.getBasketTotal().compareTo(new BigDecimal("500")) >= 0
            ? BigDecimal.ZERO : new BigDecimal("50.00")
    BigDecimal totalAmount = basket.getBasketTotal().add(deliveryCharge)
    String estimatedDeliveryDate = LocalDate.now().plusDays(3).toString()

6.  For each item in basket.getItems():
        Book book = bookRepository.findById(item.getBookId())
                .orElseThrow(() -> new BookNotFoundException(item.getBookId()))
        if book.getStockQuantity() < item.getQuantity():
            throw new InsufficientStockException(item.getTitle())

7.  For each item in basket.getItems():
        Book book = bookRepository.findById(item.getBookId()).get()
        book.setStockQuantity(book.getStockQuantity() - item.getQuantity())
        bookRepository.save(book)

8.  Build Order:
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
        for each basket item:
            OrderItem oi = new OrderItem()
            oi.setOrder(order)
            oi.setBookId(item.getBookId())
            oi.setTitle(item.getTitle())
            oi.setQuantity(item.getQuantity())
            oi.setUnitPrice(item.getUnitPrice())
            oi.setLineTotal(item.getLineTotal())
            order.getItems().add(oi)

9.  Order saved = orderRepository.save(order)  // cascade saves OrderItems

10. basketService.clearBasket(userId, null)

11. return toResponse(saved)
```

**Private helper `toResponse(Order order)`** → `OrderResponse`:
Maps all fields. `orderDate` → `order.getOrderDate().toString()`. `status` → `order.getStatus().name()`. For items: map each `OrderItem` to `OrderItemResponse`. For address: map snapshot fields to `OrderAddressSnapshot`.

---

### Step 6 — Controller

**`OrderController`** — `@RestController`, `@RequestMapping("/api/orders")`, constructor-injected: `OrderService`

| Method | HTTP | Path | Status | Delegation |
|--------|------|------|--------|------------|
| `placeOrder` | POST | `/api/orders` | 201 | `orderService.placeOrder(userId, req)` |

- Accepts `Authentication authentication` (non-null — JWT required)
- `@ResponseStatus(HttpStatus.CREATED)` on the method
- `@Valid @RequestBody PaymentRequest req` — triggers Bean Validation
- `userId` extracted via `((User) authentication.getPrincipal()).getId()`

---

### Step 7 — GlobalExceptionHandler additions

Two new `@ExceptionHandler` methods in the `// FEAT-08 handlers` section:

```
PaymentDeclinedException      → 402 Payment Required
InsufficientStockException    → 400 Bad Request
```

Each follows the identical pattern as existing handlers: construct `ErrorResponse`, return `ResponseEntity.status(...).body(body)`.

---

### Step 8 — No SecurityConfig change

`/api/orders` is already covered by the `anyRequest().authenticated()` catch-all.
No modifications to `SecurityConfig.java` are needed.

---

## 6. No New Dependencies

| Type | Source |
|------|--------|
| `@Transactional` | `spring-boot-starter-data-jpa` |
| `LocalDate`, `LocalDateTime` | `java.time` (JDK) |
| `BigDecimal` | `java.math` (JDK) |
| Bean Validation annotations | `spring-boot-starter-validation` |
| `JpaRepository` | `spring-boot-starter-data-jpa` |

---

## 7. Test Plan (to be executed in Stage 5)

### 7.1 Repository Tests — `@DataJpaTest`

**`OrderRepositoryTest`**

| Test | What it verifies |
|------|-----------------|
| `findAllByUserId_returnsOrders` | Save 2 orders for userId=1L and 1 for userId=2L; `findAllByUserId(1L)` returns exactly 2 |
| `findAllByUserId_returnsEmpty_whenNone` | `findAllByUserId(999L)` on empty table returns empty list |

---

### 7.2 Service Tests — `@ExtendWith(MockitoExtension.class)`

**`OrderServiceTest`**

| Test | What it verifies |
|------|-----------------|
| `placeOrder_success_paidStatus` | Returned `OrderResponse` has `status = "PAID"` |
| `placeOrder_success_deliveryChargeFree` | `basketTotal >= 500` → `deliveryCharge = 0.00` |
| `placeOrder_success_deliveryChargePaid` | `basketTotal < 500` → `deliveryCharge = 50.00` |
| `placeOrder_success_totalAmountCorrect` | `totalAmount = basketTotal + deliveryCharge` |
| `placeOrder_success_basketCleared` | `basketService.clearBasket(userId, null)` is called after save |
| `placeOrder_success_stockDecremented` | `bookRepository.save(book)` called with `stockQuantity` reduced by ordered qty |
| `placeOrder_success_addressSnapshot` | `Order.recipientName`, `city`, `pincode` match the address entity |
| `placeOrder_emptyBasket_throws` | Empty basket → `IllegalArgumentException("Basket is empty")` |
| `placeOrder_addressNotFound_throws` | `addressRepository.findById` empty → `AddressNotFoundException` |
| `placeOrder_addressForbidden_throws` | Address owned by different user → `AddressAccessForbiddenException` |
| `placeOrder_cardDeclined_throws` | Card `0000000000000000` → `PaymentDeclinedException` |
| `placeOrder_insufficientStock_throws` | Book stock < ordered qty → `InsufficientStockException` with book title in message |
| `placeOrder_expiredYear_throws` | `expiryYear` < current year → `IllegalArgumentException` |

---

### 7.3 Controller Tests — `@WebMvcTest`

**`OrderControllerTest`**

| Test | Endpoint | Expected |
|------|----------|---------|
| `placeOrder_returns201` | `POST /api/orders` valid body (with JWT) | 201 + `$.status == "PAID"`, `$.orderId` present |
| `placeOrder_returns400_emptyBasket` | service throws `IllegalArgumentException("Basket is empty")` | 400 + `$.message == "Basket is empty"` |
| `placeOrder_returns400_invalidCardNumber` | `cardNumber = "123"` (not 16 digits) | 400 (Bean Validation) |
| `placeOrder_returns400_invalidExpiryMonth` | `expiryMonth = 13` | 400 (Bean Validation) |
| `placeOrder_returns400_invalidCvv` | `cvv = "12AB"` | 400 (Bean Validation) |
| `placeOrder_returns400_blankCardholderName` | `cardholderName = ""` | 400 (Bean Validation) |
| `placeOrder_returns400_negativeGiftPoints` | `giftPointsToRedeem = -1` | 400 (Bean Validation) |
| `placeOrder_returns401_noJwt` | No Authorization header | 401 |
| `placeOrder_returns402_cardDeclined` | service throws `PaymentDeclinedException` | 402 + `$.message == "Payment declined"` |
| `placeOrder_returns403_addressForbidden` | service throws `AddressAccessForbiddenException` | 403 |
| `placeOrder_returns404_addressNotFound` | service throws `AddressNotFoundException(1L)` | 404 |

---

## 8. Acceptance Criteria Traceability

| AC | Criterion (summary) | Covered by |
|----|---------------------|-----------|
| AC-01 | No JWT → 401 | `OrderControllerTest`: `placeOrder_returns401_noJwt` |
| AC-02 | Empty basket → 400 `"Basket is empty"` | `OrderServiceTest`: `placeOrder_emptyBasket_throws`; `OrderControllerTest`: `placeOrder_returns400_emptyBasket` |
| AC-03 | `addressId` not found → 404 | `OrderServiceTest`: `placeOrder_addressNotFound_throws`; `OrderControllerTest`: `placeOrder_returns404_addressNotFound` |
| AC-04 | `addressId` wrong owner → 403 | `OrderServiceTest`: `placeOrder_addressForbidden_throws`; `OrderControllerTest`: `placeOrder_returns403_addressForbidden` |
| AC-05 | Invalid `cardNumber` (not 16 digits) → 400 | `OrderControllerTest`: `placeOrder_returns400_invalidCardNumber` |
| AC-06 | `expiryMonth` outside 1–12 → 400 | `OrderControllerTest`: `placeOrder_returns400_invalidExpiryMonth` |
| AC-07 | `expiryYear` < current year → 400 | `OrderServiceTest`: `placeOrder_expiredYear_throws` |
| AC-08 | Invalid `cvv` → 400 | `OrderControllerTest`: `placeOrder_returns400_invalidCvv` |
| AC-09 | Blank `cardholderName` → 400 | `OrderControllerTest`: `placeOrder_returns400_blankCardholderName` |
| AC-10 | Negative `giftPointsToRedeem` → 400 | `OrderControllerTest`: `placeOrder_returns400_negativeGiftPoints` |
| AC-11 | Card `0000000000000000` → 402, no order, basket unchanged | `OrderServiceTest`: `placeOrder_cardDeclined_throws`; `OrderControllerTest`: `placeOrder_returns402_cardDeclined` |
| AC-12 | Valid card → 201 with `"PAID"` | `OrderServiceTest`: `placeOrder_success_paidStatus`; `OrderControllerTest`: `placeOrder_returns201` |
| AC-13 | Response items correct | `OrderServiceTest`: `placeOrder_success_paidStatus` (assert items) |
| AC-14 | `basketTotal` = sum of line totals | `OrderServiceTest`: `placeOrder_success_deliveryChargeFree` (assert basketTotal) |
| AC-15 | `deliveryCharge` = 0 / 50 based on total | `OrderServiceTest`: `placeOrder_success_deliveryChargeFree` + `_deliveryChargePaid` |
| AC-16 | `totalAmount = basketTotal + deliveryCharge` | `OrderServiceTest`: `placeOrder_success_totalAmountCorrect` |
| AC-17 | `estimatedDeliveryDate` = orderDate + 3 days | `OrderServiceTest`: `placeOrder_success_paidStatus` (assert date) |
| AC-18 | `deliveryAddress` matches supplied `addressId` | `OrderServiceTest`: `placeOrder_success_addressSnapshot` |
| AC-19 | Basket cleared after success | `OrderServiceTest`: `placeOrder_success_basketCleared` |
| AC-20 | Stock decremented per ordered qty | `OrderServiceTest`: `placeOrder_success_stockDecremented` |
| AC-21 | Insufficient stock → 400, no order created, basket unchanged | `OrderServiceTest`: `placeOrder_insufficientStock_throws` |
| AC-22 | Address snapshot survives address edit/delete | `OrderServiceTest`: `placeOrder_success_addressSnapshot` (snapshot fields stored on Order) |
| AC-23 | Card details not persisted | Design — no card column/entity exists in the codebase |
| AC-24 | `giftPointsToRedeem` ≥ 0 accepted, no price effect | `OrderServiceTest`: `placeOrder_success_totalAmountCorrect` (total unchanged by gift points) |
| AC-25 | Two orders produce distinct `orderId` | `OrderRepositoryTest`: `findAllByUserId_returnsOrders` (two rows, distinct ids) |
