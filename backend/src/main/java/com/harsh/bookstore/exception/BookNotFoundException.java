package com.harsh.bookstore.exception;


/**
 * BookNotFoundException — thrown by BookService when the caller asks for
 * a book by an id that doesn't exist in the database.
 *
 * WHY IT EXTENDS RuntimeException (and not Exception):
 *   Java has two flavours of exceptions:
 *
 *     - CHECKED exceptions (extend Exception) — callers are FORCED by the
 *       compiler to either catch them or declare `throws` on their method
 *       signature. Every method up the call stack has to mention them.
 *
 *     - UNCHECKED exceptions (extend RuntimeException) — no such
 *       requirement. They can be thrown anywhere without polluting method
 *       signatures with `throws BookNotFoundException`.
 *
 *   For a "programmer-intended" flow like "the requested book wasn't found",
 *   an unchecked exception is the right fit. The controller's global
 *   exception handler (added in Phase 7) will catch it centrally and turn
 *   it into an HTTP 404 response. No method in between has to know or care.
 *
 * WHERE IT LIVES IN THE ARCHITECTURE:
 *
 *     BookRepository.findById(id)          returns Optional.empty()
 *                    │
 *                    ▼
 *     BookService.getBookById(id)          throws BookNotFoundException
 *                    │  (unchecked — passes through the call stack)
 *                    ▼
 *     BookController.getBookById(id)       does not catch — just lets it fly
 *                    │
 *                    ▼
 *     GlobalExceptionHandler               catches, translates to 404 JSON
 *
 *   This is the "controller advice" pattern — one place decides how errors
 *   become HTTP responses. Details in Phase 7.
 */
public class BookNotFoundException extends RuntimeException {

    /**
     * Constructor. Produces a message like: "Book with id 42 was not found".
     * The GlobalExceptionHandler in Phase 7 will surface this message in
     * the JSON error response.
     *
     * @param id  the id that was looked up but not found
     */
    public BookNotFoundException(Long id) {
        super("Book with id " + id + " was not found");
    }
}
