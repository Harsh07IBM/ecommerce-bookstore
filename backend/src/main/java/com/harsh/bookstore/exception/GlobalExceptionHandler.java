package com.harsh.bookstore.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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


    /**
     * Handle "book not found" specifically. Returns HTTP 404.
     *
     * `HttpServletRequest` gives us access to details of the current
     * request — most importantly, the URI that was called, so we can echo
     * it back in the ErrorResponse's `path` field. Spring injects it
     * automatically when we declare it as a parameter.
     */
    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            BookNotFoundException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),          // 404
            HttpStatus.NOT_FOUND.getReasonPhrase(),// "Not Found"
            ex.getMessage(),                       // "Book with id 999 was not found"
            request.getRequestURI()                // "/api/books/999"
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
