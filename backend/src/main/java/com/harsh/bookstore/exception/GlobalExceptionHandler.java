package com.harsh.bookstore.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;



/**
 * GlobalExceptionHandler — one place that decides how every uncaught
 * exception becomes an HTTP response.
 *
 * WHAT @RestControllerAdvice IS (in plain English):
 *   It's Spring's mechanism for CROSS-CUTTING behaviour that applies to
 *   every controller. "Advice" here means "extra logic that wraps the
 *   controllers". When a controller method throws an exception, Spring
 *   pauses, looks through @RestControllerAdvice classes for a matching
 *   @ExceptionHandler, and calls that instead of letting the exception
 *   escape into Spring's default handler.
 *
 *   Why it's brilliant: without it, we'd have to write try/catch blocks
 *   in every controller method:
 *
 *     @GetMapping("/{id}")
 *     public BookDto getBookById(@PathVariable Long id) {
 *         try {
 *             return bookService.getBookById(id);
 *         } catch (BookNotFoundException e) {
 *             // build an ErrorResponse, wrap in ResponseEntity, return 404
 *             ...
 *         }
 *     }
 *
 *   Repeated in EVERY controller method. That's not just ugly — it's
 *   error-prone, because different developers will handle it differently.
 *   With @RestControllerAdvice, the try/catch is CENTRALISED and the
 *   controllers stay clean.
 *
 * WHY "RestControllerAdvice" AND NOT PLAIN "ControllerAdvice":
 *   The prefix "Rest" means the return values are automatically serialised
 *   as JSON (like @RestController). Plain @ControllerAdvice would look up
 *   views, which we don't have — we only serve JSON.
 *
 * WHAT WE CATCH HERE:
 *   1. BookNotFoundException     → 404 Not Found
 *   2. IllegalArgumentException  → 400 Bad Request  (invalid pagination params)
 *   3. Any other Exception       → 500 Internal Server Error (fallback)
 *
 * A NOTE ON THE 500 HANDLER — WHY WE DON'T LEAK THE INNER EXCEPTION:
 *   For unexpected exceptions, the client sees a generic message ("An
 *   unexpected error occurred"). The full stack trace is LOGGED on the
 *   server. This is the standard security posture: developers can debug
 *   from logs; attackers don't learn about our internals from HTTP
 *   responses (like class names, SQL details, file paths).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    // ==================================================================
    // FEAT-04 handlers
    // ==================================================================

    /**
     * 409 Conflict — email address already registered.
     * Thrown by UserService.register() when existsByEmailIgnoreCase returns true.
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }


    /**
     * 401 Unauthorized — wrong email or wrong password on login.
     * Thrown by UserService.login(). Same message for both failure modes
     * — see InvalidCredentialsException Javadoc for the security rationale.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }


    /**
     * 400 Bad Request — Bean Validation failure on a @RequestBody.
     *
     * WHEN THIS FIRES (vs the IllegalArgumentException handler below):
     *   This handler catches MethodArgumentNotValidException, which Spring
     *   throws when @Valid fails on a @RequestBody parameter — e.g. a blank
     *   firstName or a malformed email in RegisterRequest.
     *
     *   The existing IllegalArgumentException handler catches manual throws
     *   from controller code (e.g. "page must be >= 0" in BookController).
     *   Both produce 400 responses — but Spring's exception type differs
     *   depending on where the validation happens.
     *
     * EXTRACTING THE MESSAGE:
     *   MethodArgumentNotValidException holds a BindingResult containing one
     *   FieldError per failed constraint. We take the FIRST one and use its
     *   defaultMessage — which is the custom message= string we wrote on
     *   the annotation (e.g. "email must be a valid email address").
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Validation failed");

        ErrorResponse body = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            message,
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }



    /**
     * Handle "book not found" specifically. Returns HTTP 404.
     *
     * `HttpServletRequest` gives us access to details of the current
     * request — most importantly, the URI that was called, so we can echo
     * it back in the ErrorResponse's `path` field. Spring injects it
     * automatically when we declare it as a parameter.
     */
    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookNotFound(
            BookNotFoundException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }


    /**
     * Handle "category not found" — returns HTTP 404.
     * Triggered when BookController receives ?category=unknown-slug.
     */
    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(
            CategoryNotFoundException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }


    /**
     * Handle invalid input. Returns HTTP 400.
     *
     * BookController throws IllegalArgumentException when it detects bad
     * pagination parameters (page &lt; 0, or size out of range). Anything
     * else that throws IllegalArgumentException in our app also lands here.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),          // 400
            HttpStatus.BAD_REQUEST.getReasonPhrase(),// "Bad Request"
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }


    /**
     * The last-resort handler — catches anything else that escapes.
     * Returns HTTP 500 with a generic message.
     *
     * We LOG the full stack trace so we (the developers) can investigate
     * later. But we never send the stack trace or exception class name
     * back to the client — that would leak internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);

        ErrorResponse body = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),           // 500
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), // "Internal Server Error"
            "An unexpected error occurred",                     // no details for the client
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
