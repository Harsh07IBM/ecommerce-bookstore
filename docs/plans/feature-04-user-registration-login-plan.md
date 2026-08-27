# Implementation Plan — FEAT-04: User Registration & Login

| Field           | Value                                        |
|-----------------|----------------------------------------------|
| **Feature ID**  | FEAT-04                                      |
| **Spec**        | docs/specs/feature-04-user-registration-login.md |
| **Status**      | Draft — Awaiting Developer Approval          |
| **Depends On**  | FEAT-01 (existing API structure)             |

---

## 1. Summary

FEAT-04 adds Spring Security, JWT authentication, and a `User` entity to the project. It is implemented in **6 phases**, each small enough to compile and reason about independently before moving to the next.

---

## 2. New Dependencies (pom.xml)

Two libraries must be added before any code is written:

| Library | Artifact ID | Why |
|---------|------------|-----|
| Spring Security | `spring-boot-starter-security` | Provides `SecurityFilterChain`, `BCryptPasswordEncoder`, authentication infrastructure |
| JJWT (Java JWT) | `jjwt-api`, `jjwt-impl`, `jjwt-jackson` | Builds, signs, and parses JWT tokens (HMAC-SHA256) |

**JJWT version: `0.12.6`** — the latest stable release. This version uses the fluent builder API (`Jwts.builder()...`), which differs from the older `0.9.x` API still shown in many tutorials. The plan uses the 0.12.x API throughout.

---

## 3. New Application Property

```properties
bookstore.jwt.secret=<at-least-32-character-random-string>
```

Added to `application.properties`. The secret must be ≥ 256 bits (32 ASCII characters) for HMAC-SHA256. A safe placeholder default is provided for local development with a `# TODO` note warning it must be replaced in any real deployment.

---

## 4. Implementation Phases

### Phase 1 — `User` entity + `UserRepository`

**New files:**
- `entity/User.java`
- `repository/UserRepository.java`

**`User` entity fields:**

| Field | Column | Type | Constraints |
|-------|--------|------|-------------|
| `id` | `id` | `Long` | PK, auto-increment |
| `firstName` | `first_name` | `String` | NOT NULL, max 100 |
| `lastName` | `last_name` | `String` | NOT NULL, max 100 |
| `email` | `email` | `String` | NOT NULL, UNIQUE, max 255, stored lower-case |
| `passwordHash` | `password_hash` | `String` | NOT NULL |
| `createdAt` | `created_at` | `LocalDateTime` | NOT NULL, set by `@PrePersist`, not updatable |

Note: the field is named `passwordHash` (not `password`) to make it impossible to accidentally return the raw value — any DTO that forgets to exclude it will expose a hash, not a password. Even so, it is never included in any response DTO.

**`UserRepository`:**
```java
interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
```

---

### Phase 2 — DTOs

**New files:**
- `dto/RegisterRequest.java` — inbound: `firstName`, `lastName`, `email`, `password` (with Bean Validation annotations)
- `dto/LoginRequest.java` — inbound: `email`, `password`
- `dto/UserDto.java` — outbound: `id`, `firstName`, `lastName`, `email` (no password field)
- `dto/LoginResponse.java` — outbound: `token` (String) + `user` (UserDto)

Bean Validation annotations on `RegisterRequest`:

| Field | Annotations |
|-------|-------------|
| `firstName` | `@NotBlank`, `@Size(max = 100)` |
| `lastName` | `@NotBlank`, `@Size(max = 100)` |
| `email` | `@NotBlank`, `@Email`, `@Size(max = 255)` |
| `password` | `@NotBlank`, `@Size(min = 8)` |

`LoginRequest` has only `@NotBlank` on both fields — detailed validation is intentionally skipped to avoid revealing whether email format is the issue vs credentials.

---

### Phase 3 — `JwtService`

**New file:** `service/JwtService.java`

Responsibilities:
- `generateToken(User user)` — builds and signs a JWT with claims: `sub` = user id (as String), `email`, `iat`, `exp` (iat + 24h).
- `extractUserId(String token)` — parses the token and returns the `sub` claim as a `Long`.
- `isTokenValid(String token)` — returns `true` if the token parses cleanly and is not expired.

The secret key is injected via `@Value("${bookstore.jwt.secret}")` and converted to a `SecretKey` using `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`.

This class has no Spring Security dependency — it is a plain `@Service`. This makes it easy to unit-test without spinning up a security context.

---

### Phase 4 — `UserService`

**New file:** `service/UserService.java`

Responsibilities:
- `register(RegisterRequest)` → `UserDto`
  1. Check `existsByEmailIgnoreCase` → throw `EmailAlreadyExistsException` if true.
  2. Hash the password with `BCryptPasswordEncoder`.
  3. Build and save a `User` entity.
  4. Map to `UserDto` and return.
- `login(LoginRequest)` → `LoginResponse`
  1. Look up user by email → throw `InvalidCredentialsException` if not found.
  2. Verify password with `BCryptPasswordEncoder.matches()` → throw `InvalidCredentialsException` if wrong.
  3. Generate JWT via `JwtService.generateToken()`.
  4. Return `LoginResponse` containing the token and a `UserDto`.

`UserService` also implements `UserDetailsService` (required by Spring Security) so the security filter chain can load users by email during JWT validation.

---

### Phase 5 — Spring Security configuration

**New files:**
- `config/SecurityConfig.java`
- `config/JwtAuthFilter.java`

**`JwtAuthFilter`** — a `OncePerRequestFilter`:
1. Reads the `Authorization` header.
2. If it starts with `"Bearer "`, extracts the token.
3. Calls `JwtService.isTokenValid()`.
4. If valid, loads the user via `UserService` and sets a `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`.
5. If the header is absent or the token is invalid, does nothing — Spring Security's own filter handles the 401 downstream.

**`SecurityConfig`** — a `@Configuration` class:
- Declares the `SecurityFilterChain` bean.
- Disables CSRF (stateless JWT API — no cookies, no CSRF risk).
- Sets session management to `STATELESS`.
- Permits all requests to:
  - `GET /api/books/**`
  - `GET /api/categories`
  - `POST /api/auth/register`
  - `POST /api/auth/login`
- Requires authentication for all other requests.
- Registers `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`.
- Declares a `BCryptPasswordEncoder` bean (used by `UserService`).
- Declares an `AuthenticationManager` bean (wired from `AuthenticationConfiguration`).

---

### Phase 6 — `AuthController` + exception handlers

**New file:** `controller/AuthController.java`

```
POST /api/auth/register  →  UserService.register()   →  201 Created  + UserDto
POST /api/auth/login     →  UserService.login()       →  200 OK       + LoginResponse
```

Both methods carry `@Valid` on the request body so Bean Validation fires before the service is called.

**New exceptions:**
- `exception/EmailAlreadyExistsException.java` → handled as 409 Conflict
- `exception/InvalidCredentialsException.java` → handled as 401 Unauthorized

**`GlobalExceptionHandler` additions:**
- `handleEmailAlreadyExists` → 409, message: "An account with this email address already exists"
- `handleInvalidCredentials` → 401, message: "Invalid email or password"
- `handleMethodArgumentNotValid` → 400, extracts the first field error message from `BindingResult` (Bean Validation failures)

---

## 5. New File Checklist

### Main source

```
entity/
  User.java                       NEW — Phase 1
repository/
  UserRepository.java             NEW — Phase 1
dto/
  RegisterRequest.java            NEW — Phase 2
  LoginRequest.java               NEW — Phase 2
  UserDto.java                    NEW — Phase 2
  LoginResponse.java              NEW — Phase 2
service/
  JwtService.java                 NEW — Phase 3
  UserService.java                NEW — Phase 4
config/
  SecurityConfig.java             NEW — Phase 5
  JwtAuthFilter.java              NEW — Phase 5
controller/
  AuthController.java             NEW — Phase 6
exception/
  EmailAlreadyExistsException.java  NEW — Phase 6
  InvalidCredentialsException.java  NEW — Phase 6
exception/GlobalExceptionHandler.java  MODIFIED — Phase 6 (3 new handlers)
```

### Modified files

```
backend/pom.xml                          ADD 3 new dependencies (Phase 0)
backend/src/main/resources/
  application.properties                 ADD bookstore.jwt.secret property (Phase 0)
```

### Test source

```
repository/
  UserRepositoryTest.java          NEW — Phase 1 tests
service/
  JwtServiceTest.java              NEW — Phase 3 tests
  UserServiceTest.java             NEW — Phase 4 tests
controller/
  AuthControllerTest.java          NEW — Phase 6 tests
```

---

## 6. Testing Plan

| Test Class | Type | Key cases |
|------------|------|-----------|
| `UserRepositoryTest` | `@DataJpaTest` | save user, findByEmail found/not-found, duplicate email throws constraint |
| `JwtServiceTest` | Unit (`@ExtendWith(MockitoExtension.class)`) | generateToken produces parseable JWT, isTokenValid true/false, extractUserId correct |
| `UserServiceTest` | Unit | register happy path, duplicate email → exception, login happy path, wrong password → exception, unknown email → exception |
| `AuthControllerTest` | `@WebMvcTest` | POST /register 201, POST /register 409, POST /register 400 (validation), POST /login 200, POST /login 401 |

**Regression guard:** existing `BookControllerTest` and `CategoryControllerTest` must still pass with Security active. The `@WebMvcTest` slice loads `SecurityConfig` — the permit-all rules for `GET /api/books/**` and `GET /api/categories` ensure no new 401s appear on existing tests.

---

## 7. Phase Order & Rationale

The phases are ordered so that each one can compile cleanly before the next begins:

1. **Entity + Repository** first — everything else depends on having a `User` in the database layer.
2. **DTOs** second — service and controller both need them; defining them early avoids circular dependency.
3. **JwtService** third — pure utility with no dependencies on other new classes; easy to verify in isolation.
4. **UserService** fourth — depends on repository, DTOs, and JwtService — all already exist.
5. **SecurityConfig** fifth — depends on UserService (for `UserDetailsService`) and JwtService; must exist before the controller is wired up.
6. **Controller + exception handlers** last — depends on everything above; adding it last means the app compiles and the security config is correct before the HTTP layer is wired.
