# Implementation Plan: FEAT-07 — Checkout & Delivery Address

## 1. Overview

This plan describes every concrete step required to implement the Checkout &
Delivery Address feature as specified in
`docs/specs/feature-07-checkout-delivery.md`.

The feature adds two areas of functionality:

1. **Address management** — authenticated users can save, list, update, and
   delete delivery addresses. Exactly one address per user may be the default.
2. **Checkout summary** — a read-only endpoint combines the user's current
   basket (via `BasketService`) with a chosen delivery address, calculates the
   delivery charge, and returns an estimated delivery date. The payment step
   (FEAT-08) will consume this response.

Guest checkout is explicitly out of scope — all endpoints require a valid JWT.

---

## 2. Key Design Decisions

### User extraction from JWT
The `JwtAuthFilter` (FEAT-04) already places the `User` entity as the
`Authentication` principal on every authenticated request. Both new controllers
follow the same pattern as `BasketController`:

```java
User currentUser = (User) authentication.getPrincipal();
Long userId = currentUser.getId();
```

No changes to the JWT or auth infrastructure are needed.

### Ownership enforcement
`AddressService` enforces ownership on every address look-up. Attempts to
read, update, or delete an address belonging to another user throw
`AddressAccessForbiddenException` → 403. The pattern is:

```
address = repository.findById(addressId) → 404 if absent
if (!address.getUserId().equals(userId)) → throw AddressAccessForbiddenException
```

`findByIdAndUserId` is **not** used for the ownership check because it collapses
a missing address and a wrong-owner address into the same `Optional.empty()`,
making it impossible to return the correct status code (404 vs 403). Instead,
`findById` is used first, then the ownership check is applied explicitly.

### Default-demotion logic
When saving or updating an address with `isDefault = true`, the service:

1. Calls `repository.findByUserIdAndIsDefaultTrue(userId)`.
2. If a prior default exists **and** it is not the address being updated, sets
   its `isDefault` to `false` and saves it.
3. Saves the new/updated address with `isDefault = true`.

This ensures the at-most-one-default invariant (BR-04) is maintained
atomically within the same transaction.

### Default-address delete guard
Before deleting, the service checks `repository.countByUserId(userId)`. If the
count is greater than 1 **and** the address is the current default, it throws
`DefaultAddressDeleteException` → 400 (BR-08). If the address is the only one
(count == 1), deletion is allowed regardless of `isDefault` (BR-09).

### Checkout summary reads the basket via BasketService
`CheckoutService` calls `basketService.getBasket(userId, null)` — the `null`
session ID signals an authenticated user, consistent with how `BasketController`
already calls the same method. This reuses all existing basket logic without
duplication.

### No SecurityConfig changes needed
All new endpoints (`/api/addresses/**`, `/api/checkout/**`) require
authentication. The existing `anyRequest().authenticated()` catch-all in
`SecurityConfig` already covers them. **No change to `SecurityConfig.java` is
required.**

### No new Maven dependencies
All types used (`LocalDate`, `BigDecimal`, Bean Validation annotations,
Spring Data JPA, Spring Web) are already on the classpath.

---

## 3. New Files

| Layer | File | Purpose |
|-------|------|---------|
| entity | `DeliveryAddress.java` | Delivery address entity; maps to `delivery_address` table |
| repository | `DeliveryAddressRepository.java` | JPA repository with 4 query methods |
| service | `AddressService.java` | CRUD for addresses; enforces ownership and default logic |
| service | `CheckoutService.java` | Builds the read-only checkout summary |
| controller | `AddressController.java` | 4 address REST endpoints |
| controller | `CheckoutController.java` | 1 checkout summary endpoint |
| dto | `AddressRequest.java` | Request body for POST and PUT (all fields + validation) |
| dto | `AddressResponse.java` | Response shape for a single address |
| dto | `CheckoutSummaryResponse.java` | Full checkout summary response |
| dto | `DeliveryAddressDto.java` | Embedded address object inside `CheckoutSummaryResponse` |
| exception | `AddressNotFoundException.java` | Thrown when address ID does not exist → 404 |
| exception | `AddressAccessForbiddenException.java` | Thrown when address belongs to another user → 403 |
| exception | `DefaultAddressDeleteException.java` | Thrown when deleting the default while others exist → 400 |

---

## 4. Modified Files

| File | Change |
|------|--------|
| `GlobalExceptionHandler.java` | Add `@ExceptionHandler` methods for `AddressNotFoundException` (404), `AddressAccessForbiddenException` (403), `DefaultAddressDeleteException` (400) |
| `SecurityConfig.java` | **No change required.** `/api/addresses/**` and `/api/checkout/**` are already covered by the existing `anyRequest().authenticated()` catch-all. |

---

## 5. Step-by-Step Implementation Order

### Step 1 — Entity

**`DeliveryAddress`**
- Annotations: `@Entity`, `@Table(name = "delivery_address")`
- Fields:
  - `id` — `Long`, `@Id @GeneratedValue(strategy = IDENTITY)`
  - `userId` — `Long`, `@Column(name = "user_id", nullable = false)`
  - `recipientName` — `String`, `@Column(nullable = false, length = 100)`
  - `phoneNumber` — `String`, `@Column(name = "phone_number", nullable = false, length = 10)`
  - `line1` — `String`, `@Column(nullable = false, length = 200)`
  - `line2` — `String`, `@Column(length = 200)` (nullable — no `nullable = false`)
  - `city` — `String`, `@Column(nullable = false, length = 100)`
  - `state` — `String`, `@Column(nullable = false, length = 100)`
  - `pincode` — `String`, `@Column(nullable = false, length = 6)`
  - `isDefault` — `boolean`, `@Column(name = "is_default", nullable = false)`, Java default `false`
- Indexes (declared via `@Table(indexes = {...})`):
  - `(user_id)` — supports list queries
  - `(user_id, is_default)` — supports fast default look-up (BR-04)
- Standard no-arg constructor, full getters/setters, id-based `equals`/`hashCode`, `toString`.

---

### Step 2 — Repository

**`DeliveryAddressRepository`** extends `JpaRepository<DeliveryAddress, Long>`

| Method signature | Purpose |
|------------------|---------|
| `List<DeliveryAddress> findAllByUserId(Long userId)` | Retrieve all addresses for the list endpoint |
| `Optional<DeliveryAddress> findByIdAndUserId(Long id, Long userId)` | Available for potential future use; ownership check in service uses `findById` + explicit comparison |
| `Optional<DeliveryAddress> findByUserIdAndIsDefaultTrue(Long userId)` | Locate the current default before demoting it |
| `long countByUserId(Long userId)` | Count user's addresses to enforce BR-08/BR-09 delete guard |

---

### Step 3 — Exceptions

Three new domain exceptions (all extend `RuntimeException`):

- **`AddressNotFoundException(Long addressId)`**
  - Message: `"Address not found: " + addressId`
  - Maps to → **404 Not Found**

- **`AddressAccessForbiddenException()`**
  - Message: `"You do not have permission to access this address"`
  - Maps to → **403 Forbidden**

- **`DefaultAddressDeleteException()`**
  - Message: `"Cannot delete the default address while other addresses exist"`
  - Maps to → **400 Bad Request**

---

### Step 4 — DTOs

**`AddressRequest`** (used for both POST and PUT)
- Fields with Bean Validation annotations:
  - `recipientName` — `String`, `@NotBlank`
  - `phoneNumber` — `String`, `@NotBlank @Pattern(regexp = "\\d{10}", message = "phoneNumber must be exactly 10 numeric digits")`
  - `line1` — `String`, `@NotBlank`
  - `line2` — `String` (no `@NotBlank` — nullable)
  - `city` — `String`, `@NotBlank`
  - `state` — `String`, `@NotBlank`
  - `pincode` — `String`, `@NotBlank @Pattern(regexp = "\\d{6}", message = "pincode must be exactly 6 numeric digits")`
  - `isDefault` — `boolean`, defaults to `false` when omitted from JSON

**`AddressResponse`**
- Fields (no validation annotations — outbound only):
  - `id` — `Long`
  - `userId` — `Long`
  - `recipientName` — `String`
  - `phoneNumber` — `String`
  - `line1` — `String`
  - `line2` — `String` (nullable)
  - `city` — `String`
  - `state` — `String`
  - `pincode` — `String`
  - `isDefault` — `boolean`

**`DeliveryAddressDto`** (embedded in `CheckoutSummaryResponse`)
- Fields: `id`, `recipientName`, `phoneNumber`, `line1`, `line2`, `city`, `state`, `pincode`
- No `userId` or `isDefault` — only the fields the checkout consumer needs.

**`CheckoutSummaryResponse`**
- Fields:
  - `items` — `List<BasketItemDto>` (reuses the existing DTO from FEAT-06)
  - `basketTotal` — `BigDecimal`
  - `deliveryCharge` — `BigDecimal` (`0.00` or `50.00`)
  - `estimatedDeliveryDate` — `String` (ISO-8601, e.g. `"2025-08-18"`)
  - `deliveryAddress` — `DeliveryAddressDto`

---

### Step 5 — AddressService

**`AddressService`** — `@Service`, constructor-injected: `DeliveryAddressRepository`

#### `listAddresses(Long userId)` → `List<AddressResponse>`
1. Call `repository.findAllByUserId(userId)`.
2. Map each `DeliveryAddress` to `AddressResponse`.
3. Return the list (empty list if the user has no addresses).

#### `saveAddress(Long userId, AddressRequest req)` → `AddressResponse`
1. If `req.isDefault()` is `true`:
   a. Call `repository.findByUserIdAndIsDefaultTrue(userId)`.
   b. If present, set `isDefault = false` and save the old default.
2. Build a new `DeliveryAddress` from `req`; set `userId` on it.
3. Save and return as `AddressResponse`.

#### `updateAddress(Long userId, Long addressId, AddressRequest req)` → `AddressResponse`
1. Call `repository.findById(addressId)` → throw `AddressNotFoundException` if absent.
2. If `address.getUserId()` does not equal `userId` → throw `AddressAccessForbiddenException`.
3. If `req.isDefault()` is `true`:
   a. Call `repository.findByUserIdAndIsDefaultTrue(userId)`.
   b. If present **and** its `id` differs from `addressId`, set `isDefault = false` and save it.
4. Update all fields of the address from `req`.
5. Save and return as `AddressResponse`.

#### `deleteAddress(Long userId, Long addressId)`
1. Call `repository.findById(addressId)` → throw `AddressNotFoundException` if absent.
2. If `address.getUserId()` does not equal `userId` → throw `AddressAccessForbiddenException`.
3. Call `repository.countByUserId(userId)`.
4. If `count > 1` and `address.isDefault()` is `true` → throw `DefaultAddressDeleteException`.
5. Call `repository.delete(address)`.

**Private helper:** `toResponse(DeliveryAddress address)` → `AddressResponse`  
Maps all fields from entity to DTO. Used by all returning methods.

---

### Step 6 — CheckoutService

**`CheckoutService`** — `@Service`, constructor-injected: `BasketService`, `DeliveryAddressRepository`

#### `getCheckoutSummary(Long userId, Long addressId)` → `CheckoutSummaryResponse`
1. Call `basketService.getBasket(userId, null)` → `BasketResponse`.
2. If `basketResponse.getItems()` is empty → throw `IllegalArgumentException("Basket is empty")`.
   *(Caught by the existing `IllegalArgumentException` handler in `GlobalExceptionHandler` → 400.)*
3. Call `repository.findById(addressId)` → throw `AddressNotFoundException` if absent.
4. If `address.getUserId()` does not equal `userId` → throw `AddressAccessForbiddenException`.
5. Compute `deliveryCharge`:
   - `basketResponse.getBasketTotal().compareTo(new BigDecimal("500")) >= 0` → `BigDecimal.ZERO`
   - otherwise → `new BigDecimal("50.00")`
6. Compute `estimatedDeliveryDate`: `LocalDate.now().plusDays(3).toString()`
7. Build and return `CheckoutSummaryResponse` with all fields populated.

---

### Step 7 — Controllers

**`AddressController`** — `@RestController`, `@RequestMapping("/api/addresses")`,  
constructor-injected: `AddressService`

| Method | HTTP | Path | Status | Delegation |
|--------|------|------|--------|------------|
| `listAddresses` | GET | `/api/addresses` | 200 | `addressService.listAddresses(userId)` |
| `saveAddress` | POST | `/api/addresses` | 201 | `addressService.saveAddress(userId, req)` |
| `updateAddress` | PUT | `/api/addresses/{id}` | 200 | `addressService.updateAddress(userId, id, req)` |
| `deleteAddress` | DELETE | `/api/addresses/{id}` | 204 | `addressService.deleteAddress(userId, id)` |

- Every method accepts `Authentication authentication` (non-null — all endpoints require JWT).
- `userId` is extracted via `((User) authentication.getPrincipal()).getId()`.
- `saveAddress` is annotated `@ResponseStatus(HttpStatus.CREATED)`.
- `deleteAddress` is annotated `@ResponseStatus(HttpStatus.NO_CONTENT)` and returns `void`.
- `@Valid @RequestBody AddressRequest req` on `saveAddress` and `updateAddress` triggers Bean Validation → 400 on failure.

**`CheckoutController`** — `@RestController`, `@RequestMapping("/api/checkout")`,  
constructor-injected: `CheckoutService`

| Method | HTTP | Path | Status | Delegation |
|--------|------|------|--------|------------|
| `getCheckoutSummary` | GET | `/api/checkout/summary` | 200 | `checkoutService.getCheckoutSummary(userId, addressId)` |

- Accepts `Authentication authentication` and `@RequestParam Long addressId`.
- `addressId` is declared `required = true` (Spring default) — omitting it returns 400 automatically.
- `userId` extracted the same way as in `AddressController`.

---

### Step 8 — GlobalExceptionHandler additions

Add three new `@ExceptionHandler` methods in the `// FEAT-07 handlers` section:

```
AddressNotFoundException      → 404 Not Found
AddressAccessForbiddenException → 403 Forbidden
DefaultAddressDeleteException  → 400 Bad Request
```

Each handler follows the identical pattern as the existing FEAT-06 handlers:
construct an `ErrorResponse` with `status`, `error`, `ex.getMessage()`, and
`request.getRequestURI()`, then return `ResponseEntity.status(...).body(body)`.

---

## 6. No New Dependencies

No new Maven dependencies are required. All types used in this feature are
already on the classpath:

- `LocalDate` — `java.time` (JDK)
- `BigDecimal` — `java.math` (JDK)
- Bean Validation annotations (`@NotBlank`, `@Pattern`) — `spring-boot-starter-validation`
- Spring Data JPA — `spring-boot-starter-data-jpa`
- Spring Web (`@RestController`, `@RequestParam`, etc.) — `spring-boot-starter-web`

---

## 7. Test Plan (to be executed in Stage 5)

### 7.1 Repository Tests — `@DataJpaTest`

**`AddressRepositoryTest`**

| Test | What it verifies |
|------|-----------------|
| `findAllByUserId_returnsCorrectAddresses` | Returns only the addresses belonging to the given `userId`; other users' addresses are excluded |
| `findByUserIdAndIsDefaultTrue_returnsDefault` | Returns the one address with `isDefault = true` for the user |
| `findByUserIdAndIsDefaultTrue_empty_whenNoDefault` | Returns `Optional.empty()` when no default is set |
| `countByUserId_returnsCorrectCount` | Returns the exact count of addresses for the user |

---

### 7.2 Service Tests — `@ExtendWith(MockitoExtension.class)`

**`AddressServiceTest`**

| Test | What it verifies |
|------|-----------------|
| `listAddresses_returnsUserAddresses` | Delegates to repo and maps entities to `AddressResponse` |
| `saveAddress_success` | Address saved with correct fields; `AddressResponse` returned |
| `saveAddress_demotesExistingDefault` | When `isDefault=true`, prior default is fetched and saved with `isDefault=false` before the new one is saved |
| `updateAddress_success` | Fields updated, saved, and `AddressResponse` returned |
| `updateAddress_forbidden` | `repository.findById` returns address with different `userId` → throws `AddressAccessForbiddenException` |
| `updateAddress_notFound` | `repository.findById` returns empty → throws `AddressNotFoundException` |
| `deleteAddress_success` | Address found, count > 1 but not default → `repository.delete` called |
| `deleteAddress_forbidden` | Wrong owner → throws `AddressAccessForbiddenException` |
| `deleteAddress_notFound` | Not found → throws `AddressNotFoundException` |
| `deleteAddress_defaultWithOthersPresent_throws` | `isDefault=true` and `count > 1` → throws `DefaultAddressDeleteException` |
| `deleteAddress_onlyAddress_succeeds` | `isDefault=true` but `count == 1` → deletion proceeds, no exception |

---

### 7.3 Service Tests — `@ExtendWith(MockitoExtension.class)`

**`CheckoutServiceTest`**

| Test | What it verifies |
|------|-----------------|
| `getCheckoutSummary_freeDelivery` | `basketTotal >= 500` → `deliveryCharge = 0.00` |
| `getCheckoutSummary_paidDelivery` | `basketTotal < 500` → `deliveryCharge = 50.00` |
| `getCheckoutSummary_emptyBasket_throws` | Empty items list → throws `IllegalArgumentException("Basket is empty")` |
| `getCheckoutSummary_addressNotFound_throws` | `repository.findById` returns empty → throws `AddressNotFoundException` |
| `getCheckoutSummary_addressForbidden_throws` | Address belongs to different user → throws `AddressAccessForbiddenException` |
| `getCheckoutSummary_estimatedDeliveryDate` | `estimatedDeliveryDate` equals `LocalDate.now().plusDays(3).toString()` |
| `getCheckoutSummary_itemsAndTotals` | Items list and `basketTotal` from `BasketService` are passed through unchanged |

---

### 7.4 Controller Tests — `@WebMvcTest`

**`AddressControllerTest`**

| Test | What it verifies |
|------|-----------------|
| `GET /api/addresses` → 200 | Returns list of addresses for authenticated user |
| `GET /api/addresses` → 401 | No JWT → Spring Security returns 401 before controller is reached |
| `GET /api/addresses` → 200 empty | Empty list when user has no addresses |
| `POST /api/addresses` → 201 | Valid body → address created, `Location` / response body returned |
| `POST /api/addresses` → 400 (missing field) | `@NotBlank` violation → 400 with validation message |
| `POST /api/addresses` → 400 (invalid pincode) | `@Pattern` violation on `pincode` → 400 |
| `POST /api/addresses` → 400 (invalid phone) | `@Pattern` violation on `phoneNumber` → 400 |
| `PUT /api/addresses/{id}` → 200 | Valid body + owner → address updated |
| `PUT /api/addresses/{id}` → 400 | Validation failure → 400 |
| `PUT /api/addresses/{id}` → 403 | `AddressAccessForbiddenException` → 403 |
| `PUT /api/addresses/{id}` → 404 | `AddressNotFoundException` → 404 |
| `DELETE /api/addresses/{id}` → 204 | Address deleted, no body |
| `DELETE /api/addresses/{id}` → 400 | `DefaultAddressDeleteException` → 400 |
| `DELETE /api/addresses/{id}` → 403 | `AddressAccessForbiddenException` → 403 |
| `DELETE /api/addresses/{id}` → 404 | `AddressNotFoundException` → 404 |

**`CheckoutControllerTest`**

| Test | What it verifies |
|------|-----------------|
| `GET /api/checkout/summary?addressId=1` → 200 | Returns full `CheckoutSummaryResponse` |
| `GET /api/checkout/summary` (no addressId) → 400 | Missing required query param → 400 |
| `GET /api/checkout/summary?addressId=1` → 400 (empty basket) | `IllegalArgumentException("Basket is empty")` → 400 |
| `GET /api/checkout/summary?addressId=1` → 403 | `AddressAccessForbiddenException` → 403 |
| `GET /api/checkout/summary?addressId=1` → 404 | `AddressNotFoundException` → 404 |
| `GET /api/checkout/summary?addressId=1` → 401 | No JWT → 401 |

---

## 8. Acceptance Criteria Traceability

| AC | Criterion (summary) | Covered by |
|----|---------------------|-----------|
| AC-01 | `GET /api/addresses` without JWT → 401 | `AddressControllerTest`: `GET → 401` |
| AC-02 | `GET /api/addresses` with no saved addresses → 200 `[]` | `AddressServiceTest`: `listAddresses_returnsUserAddresses`; `AddressControllerTest`: `GET 200 empty` |
| AC-03 | `GET /api/addresses` returns only the authenticated user's addresses | `AddressRepositoryTest`: `findAllByUserId_returnsCorrectAddresses`; `AddressServiceTest`: `listAddresses_returnsUserAddresses` |
| AC-04 | `POST /api/addresses` valid → 201 with persisted object and `id` | `AddressServiceTest`: `saveAddress_success`; `AddressControllerTest`: `POST → 201` |
| AC-05 | `POST /api/addresses` missing required field → 400 | `AddressControllerTest`: `POST → 400 (missing field)` |
| AC-06 | `POST /api/addresses` invalid `pincode` → 400 | `AddressControllerTest`: `POST → 400 (invalid pincode)` |
| AC-07 | `POST /api/addresses` invalid `phoneNumber` → 400 | `AddressControllerTest`: `POST → 400 (invalid phone)` |
| AC-08 | Saving with `isDefault: true` demotes previous default | `AddressServiceTest`: `saveAddress_demotesExistingDefault` |
| AC-09 | `PUT /api/addresses/{id}` updates all fields → 200 | `AddressServiceTest`: `updateAddress_success`; `AddressControllerTest`: `PUT → 200` |
| AC-10 | `PUT /api/addresses/{id}` wrong owner → 403 | `AddressServiceTest`: `updateAddress_forbidden`; `AddressControllerTest`: `PUT → 403` |
| AC-11 | `PUT /api/addresses/{id}` not found → 404 | `AddressServiceTest`: `updateAddress_notFound`; `AddressControllerTest`: `PUT → 404` |
| AC-12 | `DELETE /api/addresses/{id}` → 204 | `AddressServiceTest`: `deleteAddress_success`; `AddressControllerTest`: `DELETE → 204` |
| AC-13 | `DELETE /api/addresses/{id}` wrong owner → 403 | `AddressServiceTest`: `deleteAddress_forbidden`; `AddressControllerTest`: `DELETE → 403` |
| AC-14 | `DELETE /api/addresses/{id}` default with others present → 400 | `AddressServiceTest`: `deleteAddress_defaultWithOthersPresent_throws`; `AddressControllerTest`: `DELETE → 400` |
| AC-15 | `DELETE /api/addresses/{id}` only address (even if default) → 204 | `AddressServiceTest`: `deleteAddress_onlyAddress_succeeds` |
| AC-16 | `GET /api/checkout/summary` without JWT → 401 | `CheckoutControllerTest`: `GET → 401` |
| AC-17 | `GET /api/checkout/summary` without `addressId` → 400 | `CheckoutControllerTest`: `GET (no addressId) → 400` |
| AC-18 | `GET /api/checkout/summary` empty basket → 400 `"Basket is empty"` | `CheckoutServiceTest`: `getCheckoutSummary_emptyBasket_throws`; `CheckoutControllerTest`: `GET → 400 (empty basket)` |
| AC-19 | `GET /api/checkout/summary` `addressId` belongs to another user → 403 | `CheckoutServiceTest`: `getCheckoutSummary_addressForbidden_throws`; `CheckoutControllerTest`: `GET → 403` |
| AC-20 | `GET /api/checkout/summary` non-existent `addressId` → 404 | `CheckoutServiceTest`: `getCheckoutSummary_addressNotFound_throws`; `CheckoutControllerTest`: `GET → 404` |
| AC-21 | Basket total ≥ ₹500 → `deliveryCharge: 0.00` | `CheckoutServiceTest`: `getCheckoutSummary_freeDelivery` |
| AC-22 | Basket total < ₹500 → `deliveryCharge: 50.00` | `CheckoutServiceTest`: `getCheckoutSummary_paidDelivery` |
| AC-23 | `estimatedDeliveryDate` = today + 3 days, `YYYY-MM-DD` | `CheckoutServiceTest`: `getCheckoutSummary_estimatedDeliveryDate` |
| AC-24 | Response includes all basket items with correct totals | `CheckoutServiceTest`: `getCheckoutSummary_itemsAndTotals` |
| AC-25 | Response includes full delivery address matching `addressId` | `CheckoutServiceTest`: `getCheckoutSummary_freeDelivery` / `getCheckoutSummary_paidDelivery` (assert `deliveryAddress` fields) |
| AC-26 | Calling summary does not alter basket, addresses, or stock | `CheckoutServiceTest`: all tests — `addressService.save` and `basketService` mutations are never called; verified with Mockito `verify(..., never())` |
