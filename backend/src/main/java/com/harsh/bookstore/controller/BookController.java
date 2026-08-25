package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.dto.PagedResponse;
import com.harsh.bookstore.service.BookService;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * BookController — the HTTP entry point for the Browse Book Catalogue feature.
 *
 * WHAT A CONTROLLER IS (in plain English):
 *   A class that translates between the outside world's language (HTTP:
 *   URLs, query strings, HTTP status codes, JSON) and our internal language
 *   (Java method calls, Java objects). It knows NOTHING about the database
 *   or business rules — it just receives HTTP requests, delegates to a
 *   service, and returns the result.
 *
 * @RestController:
 *   Two-in-one annotation:
 *     1. @Controller  — marks this class as a Spring bean that handles
 *                       HTTP requests.
 *     2. @ResponseBody (applied to every method) — tells Spring "the
 *                       return value of these methods IS the response
 *                       body; serialise it as JSON via Jackson".
 *
 *   Without @ResponseBody, Spring would treat a return value like "hello"
 *   as a VIEW NAME (for server-side HTML templating) rather than a body.
 *   Since we don't serve HTML, @RestController is what we want.
 *
 * @RequestMapping("/api/books"):
 *   A URL PREFIX applied to every method in this class. So @GetMapping
 *   inside this class means "match GET on /api/books", and @GetMapping("/{id}")
 *   means "match GET on /api/books/{id}". Keeps the paths DRY.
 *
 * WHY PARAMETER VALIDATION HAPPENS HERE (not in BookService):
 *   The rules "page must be >= 0" and "size must be between 1 and 100"
 *   are HTTP-input concerns, not business-logic concerns. If some future
 *   caller of BookService (e.g. a scheduled job) has valid pagination
 *   from an internal source, we shouldn't force them through the same
 *   guards. Validate at the boundary; trust the internals.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;


    /**
     * Constructor injection — see BookService's constructor for the
     * detailed explanation of why this pattern.
     */
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }


    /**
     * GET /api/books?page=0&size=12 — paginated list of books.
     *
     * @RequestParam(defaultValue = "0")  — reads the ?page=X query string.
     *   If the client doesn't send `page`, defaults to 0.
     * @RequestParam(defaultValue = "12") — same for ?size=X. Defaults to 12
     *   (matches spec §7 FR-04).
     *
     * Bad values throw IllegalArgumentException, which our
     * GlobalExceptionHandler converts into a 400 Bad Request response.
     */
    @GetMapping
    public PagedResponse<BookDto> listBooks(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {

        // ---- Input validation ----
        // The service could throw its own error later — but a clear early
        // check gives the client a clean 400 with a helpful message.
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        // ---- Delegate + wrap for return ----
        Page<BookDto> pageResult = bookService.listBooks(page, size);
        return PagedResponse.from(pageResult);
    }


    /**
     * GET /api/books/{id} — single book by internal id.
     *
     * @PathVariable Long id — reads the {id} segment out of the URL and
     *   converts it to a Long automatically. If the client sends
     *   /api/books/abc, Spring throws its own conversion exception
     *   BEFORE this method is called, and Spring's default handling
     *   returns a 400.
     *
     * If the book doesn't exist, BookService throws BookNotFoundException,
     * which the GlobalExceptionHandler turns into a 404.
     *
     * Notice how thin this method is — one line. That's the point of a
     * layered architecture. Controllers are the "translation" layer; the
     * heavy lifting is elsewhere.
     */
    @GetMapping("/{id}")
    public BookDto getBookById(@PathVariable Long id) {
        return bookService.getBookById(id);
    }
}
