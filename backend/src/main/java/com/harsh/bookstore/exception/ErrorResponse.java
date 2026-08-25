package com.harsh.bookstore.exception;

import java.time.LocalDateTime;


/**
 * ErrorResponse — the JSON shape returned when the API responds with an
 * error (any HTTP 4xx or 5xx).
 *
 * WHY WE HAVE A DEDICATED CLASS FOR ERROR RESPONSES:
 *   Two reasons:
 *
 *     1. Every error looks the same to the client. Whether it's a 404, a
 *        400 for bad params, or a 500 for a server bug — same field set,
 *        same JSON key names. Consistent errors are much easier to consume.
 *
 *     2. Well-formed error bodies are a documented HTTP standard practice.
 *        Ours closely mirrors Spring Boot's own default error shape, so
 *        API consumers familiar with Spring apps feel at home.
 *
 * WHAT THE FIELDS MEAN (matches design §12.4):
 *
 *     {
 *       "timestamp": "2026-08-25T10:15:30.123",   ← when the error happened
 *       "status":    404,                          ← HTTP status number
 *       "error":     "Not Found",                  ← the status's human name
 *       "message":   "Book with id 999 was not found",  ← WHAT went wrong
 *       "path":      "/api/books/999"              ← the URL that was called
 *     }
 *
 * WHERE IT'S USED:
 *   Only inside GlobalExceptionHandler. Nothing else constructs one.
 */
public class ErrorResponse {

    // ==================================================================
    // FIELDS
    // ==================================================================

    /**
     * When the error occurred. Set automatically by the constructor.
     */
    private LocalDateTime timestamp;

    /**
     * The HTTP status code as a number — 404, 400, 500, etc. Handy for
     * clients that want to dispatch on the number rather than parse the
     * error text.
     */
    private int status;

    /**
     * The HTTP status name — "Not Found", "Bad Request", etc. Redundant
     * with `status` but included for readability in the response body.
     */
    private String error;

    /**
     * The human-readable explanation. For a BookNotFoundException, this is
     * "Book with id 999 was not found". For a validation error, "size must
     * be between 1 and 100". Never contains internal implementation
     * details — those go to the server log only.
     */
    private String message;

    /**
     * The request path that produced the error. Useful when you have logs
     * across many endpoints and want to spot which one is misbehaving.
     */
    private String path;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    /**
     * The only way to construct an ErrorResponse. The `timestamp` is set
     * automatically to "now" here — callers don't need to think about it.
     */
    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }


    // ==================================================================
    // GETTERS AND SETTERS
    //   Jackson uses these to emit each field as a JSON key. Setters
    //   included for symmetry and in case Jackson ever needs them.
    // ==================================================================

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
