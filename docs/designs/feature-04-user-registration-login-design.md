# Technical Design — FEAT-04: User Registration & Login

| Field | Value |
|---|---|
| **Feature ID** | FEAT-04 |
| **Spec** | [docs/specs/feature-04-user-registration-login.md](../specs/feature-04-user-registration-login.md) |
| **Plan** | [docs/plans/feature-04-user-registration-login-plan.md](../plans/feature-04-user-registration-login-plan.md) |
| **Status** | Draft — Awaiting Developer Approval |

---

## 1. Purpose

This document translates the approved plan into a **code-ready design**. Every class, method signature, annotation, and field is specified here. The coding phase should be mechanical — no architectural decisions left to make.

---

## 2. Architecture — Request Flow

```mermaid
%%{init: {'theme':'default'}}%%
flowchart LR
    Client([Client])
    JWTFilter[JwtAuthFilter\nconfig/]
    Security[SecurityConfig\nconfig/]
    Auth[AuthController\ncontroller/]
    US[UserService\nservice/]
    JS[JwtService\nservice/]
    UR[(UserRepository\nrepository/)]
    DB[(H2 Database)]
    EH[GlobalExceptionHandler\nexception/]

    Client -->|POST /api/auth/register\nPOST /api/auth/login| JWTFilter
    JWTFilter -->|permitted - no token check| Auth
    Auth -->|@Valid RegisterRequest\n@Valid LoginRequest| US
    US -->|findByEmail\nexistsByEmail\nsave| UR
    UR --- DB
    US -->|generateToken| JS
    Auth -->|throws exception| EH
    US -->|throws exception| EH
    Security -.->|configures| JWTFilter
```

---

## 3. Layer Responsibilities

| Layer | Class | Responsibility |
|---|---|---|
| Entity | `User` | JPA-mapped row; holds `passwordHash`, never `password` |
| Repository | `UserRepository` | `findByEmailIgnoreCase`, `existsByEmailIgnoreCase` |
| Service | `UserService` | Registration logic, login logic, `UserDetailsService` impl |
| Service | `JwtService` | Token generation, validation, claim extraction |
| Controller | `AuthController` | HTTP translation — `@Valid`, 201/200 responses |
| Config | `SecurityConfig` | `SecurityFilterChain` bean, permit rules, BCrypt bean |
| Config | `JwtAuthFilter` | Reads `Authorization` header, sets `SecurityContext` |
| Exception | `EmailAlreadyExistsException` | → 409 |
| Exception | `InvalidCredentialsException` | → 401 |
| Exception | `GlobalExceptionHandler` | 3 new handlers (409, 401, 400 Bean Validation) |

---

## 4. pom.xml Changes

Add inside the `<dependencies>` block, after the existing `spring-boot-starter-validation` entry:

```xml
<!-- Spring Security — filter chain, BCryptPasswordEncoder, auth manager -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JJWT — build, sign, and parse JWT tokens (HMAC-SHA256) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

Add inside the `<dependencies>` block, after `spring-boot-starter-test`, for the security test helper:

```xml
<!-- Spring Security Test — SecurityMockMvcRequestPostProcessors for @WebMvcTest -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 5. application.properties Addition

```properties
# ------------------------------------------------------------------
# JWT
# ------------------------------------------------------------------
# Secret key for signing HMAC-SHA256 tokens. Must be >= 32 characters
# (256 bits). Replace this placeholder before deploying anywhere public.
# TODO: move to an environment variable in any real deployment.
bookstore.jwt.secret=bookstore-dev-secret-key-change-in-production-min32c
bookstore.jwt.expiration-ms=86400000
```

`86400000` ms = 24 hours.

---

## 6. Phase 1 — `User` Entity + `UserRepository`

### 6.1 `entity/User.java`

```java
@Entity
@Table(name = "users")   // "user" is a reserved word in H2/SQL — use "users"
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;                  // stored lower-case by UserService

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;           // BCrypt hash — NEVER the raw password

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // no-arg constructor + getters + setters + equals/hashCode/toString
    // equals: id-based (same pattern as Book)
    // hashCode: getClass().hashCode()
    // toString: "User{id=..., email='...'}"
}
```

**Why `"users"` not `"user"`:**
`USER` is an SQL reserved keyword in H2 (and most databases). Using `@Table(name = "users")` avoids a startup error with zero workarounds needed.

**Why `passwordHash` not `password`:**
The field name makes it structurally obvious this is a hash. Any code that accidentally tries to write `user.getPassword()` into a response won't compile — `getPassword()` doesn't exist.

### 6.2 `repository/UserRepository.java`

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
```

Both methods use `IgnoreCase` so `Harsh@Example.COM` and `harsh@example.com` resolve to the same account.

---

## 7. Phase 2 — DTOs

### 7.1 `dto/RegisterRequest.java`

```java
public class RegisterRequest {

    @NotBlank(message = "firstName is required")
    @Size(max = 100, message = "firstName must be 100 characters or fewer")
    private String firstName;

    @NotBlank(message = "lastName is required")
    @Size(max = 100, message = "lastName must be 100 characters or fewer")
    private String lastName;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Size(max = 255, message = "email must be 255 characters or fewer")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;

    // no-arg constructor, getters, setters
}
```

### 7.2 `dto/LoginRequest.java`

```java
public class LoginRequest {

    @NotBlank(message = "email is required")
    private String email;

    @NotBlank(message = "password is required")
    private String password;

    // no-arg constructor, getters, setters
}
```

No `@Email` on `LoginRequest.email` — we deliberately avoid revealing whether it's an email format issue vs bad credentials.

### 7.3 `dto/UserDto.java`

```java
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;

    // no-arg constructor, getters, setters
}
```

No `passwordHash` field. If it were accidentally added, any response serialisation would expose a BCrypt hash — this design makes that impossible by omission.

### 7.4 `dto/LoginResponse.java`

```java
public class LoginResponse {

    private String token;
    private UserDto user;

    // no-arg constructor, getters, setters
}
```

---

## 8. Phase 3 — `JwtService`

```java
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${bookstore.jwt.secret}") String secret,
            @Value("${bookstore.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key)
            .compact();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);   // throws if expired or bad signature
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

**Key points:**
- `Keys.hmacShaKeyFor(bytes)` validates the key length at startup — a short secret causes an `IllegalArgumentException` immediately, not silently at runtime.
- `isTokenValid` catches `JwtException` (covers `ExpiredJwtException`, `MalformedJwtException`, `SignatureException`) and returns `false` — never throws to callers.
- `parseClaims` is private — only used internally; external callers use the three public methods.

---

## 9. Phase 4 — `UserService`

```java
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtService jwtService) { ... }

    // ── Registration ──────────────────────────────────────────
    public UserDto register(RegisterRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw new EmailAlreadyExistsException();
        }
        User user = new User();
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setEmail(req.getEmail().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        User saved = userRepository.save(user);
        return toDto(saved);
    }

    // ── Login ─────────────────────────────────────────────────
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.getEmail())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtService.generateToken(user);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUser(toDto(user));
        return resp;
    }

    // ── UserDetailsService (required by Spring Security) ──────
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
            .map(u -> org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .roles("USER")
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // ── Helper ────────────────────────────────────────────────
    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        return dto;
    }
}
```

**Why `InvalidCredentialsException` is thrown for both wrong-email and wrong-password:**
Using the same exception from a single code path ensures the response is identical regardless of which check failed — no timing attack or response-body enumeration possible.

**Why `Locale.ROOT` for `toLowerCase`:**
`Locale.ROOT` applies no locale-specific rules (e.g. Turkish `I` → `ı` surprises). It is the correct locale for normalising identifiers.

---

## 10. Phase 5 — Security Configuration

### 10.1 `config/JwtAuthFilter.java`

```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) { ... }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);   // no token — pass through
            return;
        }

        String token = header.substring(7);

        if (jwtService.isTokenValid(token)) {
            Long userId = jwtService.extractUserId(token);
            userRepository.findById(userId).ifPresent(user -> {
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                auth.setDetails(new WebAuthenticationDetailsSource()
                    .buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        // invalid token → don't set auth → Security rejects protected endpoints
        filterChain.doFilter(request, response);
    }
}
```

**Why `UserRepository` (not `UserDetailsService`) in the filter:**
The filter needs a `User` entity (not a Spring `UserDetails`) so that future services (basket, orders) can call `getAuthentication().getPrincipal()` and cast directly to `User`. Using `UserRepository.findById` (primary key lookup by token `sub`) is a single indexed lookup — always O(1).

### 10.2 `config/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) { ... }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,  "/api/books/**").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/categories").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/h2-console/**").permitAll()   // dev only
                .anyRequest().authenticated()
            )
            .headers(headers ->
                headers.frameOptions(fo -> fo.disable()))  // needed for H2 console iframes
            .addFilterBefore(jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

**Why H2 console is permitted:**
H2 console uses `<iframe>` tags, which `X-Frame-Options: DENY` (Spring Security default) blocks. `frameOptions().disable()` keeps the console usable in development. This is safe because `permitAll()` on `/h2-console/**` is already in the existing `application.properties`-controlled path.

---

## 11. Phase 6 — `AuthController`, Exceptions, Handler Additions

### 11.1 `exception/EmailAlreadyExistsException.java`

```java
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("An account with this email address already exists");
    }
}
```

### 11.2 `exception/InvalidCredentialsException.java`

```java
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
```

Both are `RuntimeException` subclasses — same pattern as `BookNotFoundException`.

### 11.3 `GlobalExceptionHandler` — 3 new handlers

```java
// 409 — duplicate email
@ExceptionHandler(EmailAlreadyExistsException.class)
public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
        EmailAlreadyExistsException ex, HttpServletRequest request) {
    ErrorResponse body = new ErrorResponse(
        HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
        ex.getMessage(), request.getRequestURI());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
}

// 401 — bad credentials
@ExceptionHandler(InvalidCredentialsException.class)
public ResponseEntity<ErrorResponse> handleInvalidCredentials(
        InvalidCredentialsException ex, HttpServletRequest request) {
    ErrorResponse body = new ErrorResponse(
        HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
        ex.getMessage(), request.getRequestURI());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
}

// 400 — Bean Validation failures (@Valid on request body)
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(fe -> fe.getDefaultMessage())
        .orElse("Validation failed");
    ErrorResponse body = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
        message, request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
}
```

**Why `MethodArgumentNotValidException` instead of `IllegalArgumentException` for validation:**
When `@Valid` fails on a request body, Spring throws `MethodArgumentNotValidException` (not `IllegalArgumentException`). The existing `IllegalArgumentException` handler covers controller-level manual checks (like `page < 0`). This new handler covers Bean Validation on `@RequestBody`.

### 11.4 `controller/AuthController.java`

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) { ... }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)          // 201
    public UserDto register(@Valid @RequestBody RegisterRequest req) {
        return userService.register(req);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(userService.login(req));
    }
}
```

`register` uses `@ResponseStatus(HttpStatus.CREATED)` (201). `login` uses `ResponseEntity.ok()` (200). Both carry `@Valid` so Bean Validation fires before the service is ever called.

---

## 12. Test Designs

### 12.1 `UserRepositoryTest` (`@DataJpaTest`)

| Test | What it verifies |
|------|-----------------|
| `save_assignsId` | Saved user gets a non-null id and `createdAt` |
| `findByEmailIgnoreCase_found` | Returns user for matching email (case-insensitive) |
| `findByEmailIgnoreCase_notFound` | Returns empty Optional for unknown email |
| `existsByEmailIgnoreCase_true` | Returns true when email exists |
| `existsByEmailIgnoreCase_false` | Returns false when email absent |
| `save_duplicateEmail_throwsConstraintViolation` | H2 enforces the UNIQUE constraint |

### 12.2 `JwtServiceTest` (unit, `@ExtendWith(MockitoExtension.class)`)

| Test | What it verifies |
|------|-----------------|
| `generateToken_producesNonBlankToken` | Token is not null/blank |
| `isTokenValid_returnsTrueForFreshToken` | Fresh token is valid |
| `isTokenValid_returnsFalseForExpiredToken` | Token with 0ms expiry is invalid |
| `isTokenValid_returnsFalseForGarbageString` | Random string is invalid |
| `extractUserId_returnsCorrectId` | Sub claim round-trips correctly |

For the expired-token test: construct a `JwtService` with `expirationMs = 0` (or `-1`) and generate a token — it will already be expired.

### 12.3 `UserServiceTest` (unit, `@ExtendWith(MockitoExtension.class)`)

| Test | What it verifies |
|------|-----------------|
| `register_success` | Saves user, returns DTO without password, hashed password stored |
| `register_duplicateEmail_throwsEmailAlreadyExists` | `existsByEmail` returns true → exception |
| `register_emailStoredLowerCase` | `user.getEmail()` is lower-case regardless of input case |
| `login_success` | Returns `LoginResponse` with token and correct user DTO |
| `login_unknownEmail_throwsInvalidCredentials` | `findByEmail` empty → exception |
| `login_wrongPassword_throwsInvalidCredentials` | `passwordEncoder.matches` false → exception |

### 12.4 `AuthControllerTest` (`@WebMvcTest(AuthController.class)`)

| Test | What it verifies |
|------|-----------------|
| `register_returns201_withUserDto` | 201, body contains id/name/email, no passwordHash |
| `register_returns409_whenEmailTaken` | Service throws `EmailAlreadyExistsException` → 409 correct body |
| `register_returns400_whenEmailInvalid` | Bean Validation fires → 400, correct message |
| `register_returns400_whenPasswordTooShort` | Bean Validation fires → 400, correct message |
| `login_returns200_withTokenAndUser` | 200, body contains `token` and `user` |
| `login_returns401_whenCredentialsWrong` | Service throws `InvalidCredentialsException` → 401 correct body |

**Regression note:** `BookControllerTest` and `CategoryControllerTest` use `@WebMvcTest` which loads `SecurityConfig`. They test `GET` endpoints that are `permitAll()` — no change in behaviour expected. Run `mvn test` after Phase 5 (before Phase 6) to confirm the security config doesn't break existing tests.

---

## 13. Design Decisions

| ID | Decision | Rationale |
|----|----------|-----------|
| D-01 | Table name `"users"` not `"user"` | `USER` is reserved in H2/SQL |
| D-02 | Field named `passwordHash` not `password` | Structural impossibility of accidental exposure |
| D-03 | `JwtAuthFilter` injects `UserRepository`, not `UserDetailsService` | Returns a `User` entity as principal — required by later features (basket, orders) |
| D-04 | `isTokenValid` never throws | Filter logic is simpler; errors become `false` cleanly |
| D-05 | `InvalidCredentialsException` thrown from the same code regardless of whether email or password failed | Prevents account enumeration |
| D-06 | `MethodArgumentNotValidException` handler extracts first field error message | Matches spec §5 error shape; consistent with existing handler style |
| D-07 | H2 console permitted + `frameOptions` disabled | Dev convenience; acceptable because H2 console is already limited to localhost |
| D-08 | `email` stored as lower-case via `Locale.ROOT` | Prevents duplicate-account issues from mixed-case registration |
