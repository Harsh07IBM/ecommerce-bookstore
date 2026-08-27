# FEAT-04 — Postman Testing Guide: User Registration & Login

> **Before you start:** `cd backend` then `mvn spring-boot:run` and wait for
> `Started BookstoreApplication` in the logs. Server runs on `http://localhost:8080`.

---

## Setup — one-time Postman steps

1. Open Postman → create a new **Collection** called `Bookstore Auth`.
2. Inside the collection, add a **Collection Variable**:
   - Name: `baseUrl`  Value: `http://localhost:8080`
3. Add a second **Collection Variable** (leave value blank for now):
   - Name: `token`  Value: *(empty)*
4. For every request below, set **Content-Type** header to `application/json`.

---

## Test 1 — Register a new account (happy path)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/register` |
| **Expected status** | `201 Created` |

**Request body (raw JSON):**
```json
{
  "firstName": "Harsh",
  "lastName":  "Sharma",
  "email":     "harsh@example.com",
  "password":  "secret123"
}
```

**What to check in the response:**
- Status is **201 Created**
- Body contains `id`, `firstName`, `lastName`, `email`
- `password` and `passwordHash` fields are **absent** — the password must never appear
- Copy the `id` value — you'll use it later

**Example response:**
```json
{
  "id": 1,
  "firstName": "Harsh",
  "lastName": "Sharma",
  "email": "harsh@example.com"
}
```

---

## Test 2 — Register the same email again (duplicate check)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/register` |
| **Expected status** | `409 Conflict` |

**Request body:** same JSON as Test 1.

**What to check:**
- Status is **409 Conflict**
- `status` = 409, `error` = "Conflict"
- `message` = "An account with this email address already exists"

---

## Test 3 — Register with an invalid email format (validation)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/register` |
| **Expected status** | `400 Bad Request` |

**Request body:**
```json
{
  "firstName": "Test",
  "lastName":  "User",
  "email":     "not-an-email",
  "password":  "secret123"
}
```

**What to check:**
- Status is **400 Bad Request**
- `message` = "email must be a valid email address"

---

## Test 4 — Register with a short password (validation)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/register` |
| **Expected status** | `400 Bad Request` |

**Request body:**
```json
{
  "firstName": "Test",
  "lastName":  "User",
  "email":     "test2@example.com",
  "password":  "short"
}
```

**What to check:**
- Status is **400 Bad Request**
- `message` = "password must be at least 8 characters"

---

## Test 5 — Login with correct credentials (happy path)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/login` |
| **Expected status** | `200 OK` |

**Request body:**
```json
{
  "email":    "harsh@example.com",
  "password": "secret123"
}
```

**What to check:**
- Status is **200 OK**
- Response has a `token` field — a long string starting with `eyJ`
- Response has a `user` object with `id`, `firstName`, `lastName`, `email`
- `user.passwordHash` and `user.password` are **absent**

**After verifying — save the token:**
1. Copy the full value of the `token` field
2. Go to your Postman Collection → **Variables** tab
3. Paste it into the `token` variable's **Current Value** column
4. Click **Save**

**Example response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc...",
  "user": {
    "id": 1,
    "firstName": "Harsh",
    "lastName": "Sharma",
    "email": "harsh@example.com"
  }
}
```

---

## Test 6 — Login with wrong password (anti-enumeration)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/login` |
| **Expected status** | `401 Unauthorized` |

**Request body:**
```json
{
  "email":    "harsh@example.com",
  "password": "wrongpassword"
}
```

**What to check:**
- Status is **401 Unauthorized**
- `message` = "Invalid email or password"

---

## Test 7 — Login with unknown email (anti-enumeration)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/login` |
| **Expected status** | `401 Unauthorized` |

**Request body:**
```json
{
  "email":    "nobody@example.com",
  "password": "secret123"
}
```

**What to check:**
- Status is **401 Unauthorized**
- `message` = "Invalid email or password"
- **The message is identical to Test 6** — this is intentional.
  The API never reveals whether the email exists or the password is wrong.

---

## Test 8 — Login with blank fields (validation)

| | |
|---|---|
| **Method** | POST |
| **URL** | `{{baseUrl}}/api/auth/login` |
| **Expected status** | `400 Bad Request` |

**Request body (blank email):**
```json
{
  "email":    "",
  "password": "secret123"
}
```

**What to check:**
- Status is **400 Bad Request**
- `message` = "email is required"

Repeat with a blank password:
```json
{
  "email":    "harsh@example.com",
  "password": ""
}
```
- `message` = "password is required"

---

## Test 9 — Access a protected endpoint WITHOUT a token

> This verifies that `anyRequest().authenticated()` in SecurityConfig is working.

| | |
|---|---|
| **Method** | GET |
| **URL** | `{{baseUrl}}/api/protected-test` |
| **Expected status** | `401 Unauthorized` |

**No Authorization header. No body.**

**What to check:**
- Status is **401 Unauthorized**
- This endpoint doesn't exist — but the *absence* of auth is what produces the 401,
  not the missing route. This proves Security is active on unknown routes.

---

## Test 10 — Public catalogue endpoints still work WITHOUT a token

> Regression check — existing FEAT-01/02/03 endpoints must stay public.

| | |
|---|---|
| **Method** | GET |
| **URL** | `{{baseUrl}}/api/books` |
| **Expected status** | `200 OK` |

**No Authorization header. No body.**

**What to check:**
- Status is **200 OK** — no token needed
- Books are returned normally

Also verify:
- `GET {{baseUrl}}/api/categories` → **200 OK** (no token)
- `GET {{baseUrl}}/api/books/1` → **200 OK** (no token)

---

## Test 11 — Access a future protected endpoint WITH a valid token

> This confirms the JWT is accepted by the filter.
> Use any endpoint that will require auth in a future feature.
> For now, we use a non-existent path to observe the difference.

| | |
|---|---|
| **Method** | GET |
| **URL** | `{{baseUrl}}/api/protected-test` |
| **Authorization** | Type: **Bearer Token** → paste the token from Test 5 |
| **Expected status** | `403` or `404` (NOT `401`) |

**What to check:**
- Status is **NOT 401** — the filter accepted the token
- You'll likely see a 403 or 404 because the path doesn't exist,
  but the important thing is that `401 Unauthorized` is gone.
  A `401` means "I don't know who you are". A `403` or `404` means
  "I know who you are, but this resource doesn't exist / isn't allowed."

---

## Sign-off checklist

| # | Test | Expected | Pass? |
|---|---|---|---|
| 1 | Register new account | 201 + UserDto (no password field) | ☐ |
| 2 | Register same email again | 409 + "already exists" message | ☐ |
| 3 | Register invalid email format | 400 + "must be a valid email address" | ☐ |
| 4 | Register password too short | 400 + "at least 8 characters" | ☐ |
| 5 | Login correct credentials | 200 + token + user (no password field) | ☐ |
| 6 | Login wrong password | 401 + "Invalid email or password" | ☐ |
| 7 | Login unknown email | 401 + **same** message as Test 6 | ☐ |
| 8 | Login blank fields | 400 + "is required" | ☐ |
| 9 | No token on protected route | 401 Unauthorized | ☐ |
| 10 | Public routes work without token | 200 OK | ☐ |
| 11 | Valid token accepted on protected route | NOT 401 | ☐ |

---

## Error response shape (same for all errors)

Every error uses this consistent JSON structure:

```json
{
  "status":    409,
  "error":     "Conflict",
  "message":   "An account with this email address already exists",
  "path":      "/api/auth/register",
  "timestamp": "2026-08-27T10:00:00.123"
}
```
