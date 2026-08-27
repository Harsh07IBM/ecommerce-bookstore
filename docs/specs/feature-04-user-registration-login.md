# Feature Specification — FEAT-04: User Registration & Login

| Field             | Value                                              |
|-------------------|----------------------------------------------------|
| **Feature ID**    | FEAT-04                                            |
| **Feature Name**  | User Registration & Login                          |
| **Tier**          | 2 — User Identity                                  |
| **Status**        | Draft — Awaiting Developer Approval                |
| **Author**        | Bob (AI assistant)                                 |
| **Depends On**    | FEAT-01 (existing API foundation)                  |
| **Blocks**        | FEAT-06 (basket), FEAT-08 (payment), FEAT-10 (order history), FEAT-14 (recommendations) |
| **Business Requirements** | §4, §5, §6.1, BR-001                      |
| **Open Questions Resolved** | Q7, Q8, Q9, Q10 (see §8 below)           |

---

## 1. Overview

FEAT-04 introduces identity to the bookstore. It delivers two capabilities:

1. **Registration** — a new visitor creates an account with name, email, and password.
2. **Login** — a registered user authenticates and receives a JWT that grants access to protected resources.

All catalogue endpoints (browse, search, filter) remain publicly accessible to guests. Basket operations are also open to guests (resolved Q8). Checkout and payment will be protected in later features — FEAT-04 establishes the authentication infrastructure they depend on.

---

## 2. Resolved Open Questions

| Question | Decision |
|----------|----------|
| Q7 — What can a Guest User do? | Browse catalogue, search, filter, view book detail. No basket in FEAT-04 scope. |
| Q8 — Can a guest add to basket? | Yes — basket is accessible without login. Checkout/payment require login. |
| Q9 — Can a guest purchase? | No — checkout and payment require a registered, authenticated user. |
| Q10 — Is authentication mandatory before checkout? | Yes — login is required before checkout proceeds. |

---

## 3. User Stories

### US-04-01 — Register
> As a new visitor, I want to create an account with my name, email, and password so that I can later log in and make purchases.

**Acceptance criteria:**
- I can submit `firstName`, `lastName`, `email`, and `password`.
- If the email is not already registered, my account is created and I receive a `201 Created` response with my user profile (no password field).
- If the email is already registered, I receive `409 Conflict` with a clear message.
- Passwords are never returned in any API response.
- Passwords are stored as a BCrypt hash — never in plaintext.

### US-04-02 — Login
> As a registered user, I want to log in with my email and password so that I can access my basket, order history, and checkout.

**Acceptance criteria:**
- I can submit `email` and `password`.
- If credentials are correct, I receive a `200 OK` response containing a signed JWT and my user profile.
- If credentials are wrong (wrong password or unknown email), I receive `401 Unauthorized` with a generic message. The message must NOT reveal whether the email exists (prevents account enumeration).
- The JWT is valid for **24 hours** from issue time.
- After 24 hours the token expires and I must log in again.

### US-04-03 — Access protected endpoints (infrastructure only)
> As a registered user, I want endpoints that require authentication to reject requests that have no valid JWT, so that my data is protected.

**Acceptance criteria:**
- Any endpoint marked as protected (future features) returns `401 Unauthorized` if no `Authorization: Bearer <token>` header is present.
- Any endpoint marked as protected returns `401 Unauthorized` if the JWT is expired or has an invalid signature.
- All existing catalogue endpoints (`GET /api/books`, `GET /api/books/{id}`, `GET /api/categories`) remain publicly accessible — no token required.

---

## 4. Functional Requirements

| ID     | Requirement |
|--------|-------------|
| FR-01  | `POST /api/auth/register` — creates a new user account. |
| FR-02  | `POST /api/auth/login` — authenticates credentials and returns a JWT. |
| FR-03  | `firstName` and `lastName` are required strings, max 100 characters each. |
| FR-04  | `email` is required, must be a valid email format, max 255 characters, stored in lower-case. |
| FR-05  | `password` is required, minimum 8 characters. No maximum enforced. |
| FR-06  | Passwords must be stored using BCrypt hashing. Plaintext passwords must never be persisted or logged. |
| FR-07  | Email addresses must be unique across all users. Duplicate registration returns 409. |
| FR-08  | Login error responses must use the same generic message regardless of whether the email exists or the password is wrong. |
| FR-09  | The JWT payload must contain: `sub` (user id as string), `email`, `iat` (issued-at), `exp` (expires-at = iat + 24h). |
| FR-10  | The JWT must be signed with HMAC-SHA256 (HS256) using a secret key loaded from an environment variable / application property. |
| FR-11  | All existing public endpoints must continue to function without a token. |
| FR-12  | Spring Security must be configured so that protected endpoints (to be declared in future features) reject requests with missing, expired, or invalid JWTs with a `401` response. |

---

## 5. API Contract

### 5.1 POST /api/auth/register

**Request body:**
```json
{
  "firstName": "Harsh",
  "lastName":  "Sharma",
  "email":     "harsh@example.com",
  "password":  "secret123"
}
```

**Success — 201 Created:**
```json
{
  "id":        1,
  "firstName": "Harsh",
  "lastName":  "Sharma",
  "email":     "harsh@example.com"
}
```

**Failure — 400 Bad Request** (validation):
```json
{
  "status":  400,
  "error":   "Bad Request",
  "message": "email must be a valid email address",
  "path":    "/api/auth/register",
  "timestamp": "..."
}
```

**Failure — 409 Conflict** (email already registered):
```json
{
  "status":  409,
  "error":   "Conflict",
  "message": "An account with this email address already exists",
  "path":    "/api/auth/register",
  "timestamp": "..."
}
```

---

### 5.2 POST /api/auth/login

**Request body:**
```json
{
  "email":    "harsh@example.com",
  "password": "secret123"
}
```

**Success — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id":        1,
    "firstName": "Harsh",
    "lastName":  "Sharma",
    "email":     "harsh@example.com"
  }
}
```

**Failure — 401 Unauthorized** (wrong email or wrong password — same message for both):
```json
{
  "status":  401,
  "error":   "Unauthorized",
  "message": "Invalid email or password",
  "path":    "/api/auth/login",
  "timestamp": "..."
}
```

---

## 6. Validation Rules

| Field       | Rules |
|-------------|-------|
| `firstName` | Required, not blank, max 100 characters |
| `lastName`  | Required, not blank, max 100 characters |
| `email`     | Required, valid email format, max 255 characters |
| `password`  | Required, minimum 8 characters |

Validation errors return **400 Bad Request** using the same `ErrorResponse` shape as existing endpoints. When multiple fields fail, the message describes the first failing field (Bean Validation default behaviour).

---

## 7. Security Requirements

| Requirement | Detail |
|-------------|--------|
| Password storage | BCrypt via Spring Security's `BCryptPasswordEncoder`. Work factor: default (10 rounds). |
| Token algorithm | HMAC-SHA256 (HS256). |
| Token expiry | 24 hours from issue. |
| Secret key | Loaded from `bookstore.jwt.secret` application property. Must be at least 256 bits (32 characters). Never hardcoded. |
| Anti-enumeration | Login endpoint returns the same message and same HTTP status for wrong email and wrong password. |
| Password in logs | Passwords must never appear in any log statement. |
| Password in response | Password (hashed or otherwise) must never appear in any API response. |
| Existing endpoints | `GET /api/books`, `GET /api/books/{id}`, `GET /api/categories` remain public (no token required). |

---

## 8. Out of Scope for FEAT-04

The following are explicitly **not** part of this feature:

- Email verification (send confirmation email after registration)
- Password reset / forgot password flow
- Refresh tokens or token rotation
- Role-based access control (no admin role — §3.2 of business requirements)
- Logout endpoint (JWT is stateless; client discards the token)
- Guest basket persistence (handled in FEAT-06)
- Any frontend UI (backend REST API only)

---

## 9. Error Response Shape

All error responses use the same `ErrorResponse` structure already established by FEAT-01:

```json
{
  "status":    <HTTP status code>,
  "error":     "<HTTP reason phrase>",
  "message":   "<human-readable explanation>",
  "path":      "<request URI>",
  "timestamp": "<ISO-8601 datetime>"
}
```

Two new HTTP status codes are introduced by this feature:

| Status | When |
|--------|------|
| 409 Conflict | Duplicate email on registration |
| 401 Unauthorized | Invalid credentials on login, or missing/invalid JWT on a protected endpoint |

---

## 10. Non-Functional Requirements

| Area | Requirement |
|------|-------------|
| Performance | Registration and login must complete within 2 seconds under normal load (BCrypt hashing is intentionally slow — this is acceptable). |
| Test coverage | Unit tests for service layer; `@WebMvcTest` for controller layer; integration test for repository layer. All happy paths and key error paths covered. |
| No plaintext secrets | JWT secret must not be hardcoded in source code. Use `application.properties` (which is in `.gitignore` for production — for this project, a default value in properties is acceptable with a `TODO` note). |
