package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.dto.BookFilter;
import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.repository.BookRepository;
import com.harsh.bookstore.repository.BookSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;


/**
 * BookService — the business-logic layer for books.
 *
 * WHAT THIS CLASS IS (in plain English):
 *   The "service" is the layer that sits BETWEEN the controller (which
 *   speaks HTTP) and the repository (which speaks SQL). It owns the
 *   business rules — things like:
 *     - "Books are ordered newest-first by default"
 *     - "Stock quantity is never exposed; expose IN_STOCK / OUT_OF_STOCK"
 *     - "If you ask for a book that doesn't exist, throw a specific exception"
 *
 * WHY WE HAVE A SERVICE AT ALL (why not call the repo directly from the controller):
 *   For FEAT-01 the service is thin — it just paginates and maps. It could
 *   look redundant. But putting rules in a service pays off in three ways:
 *
 *     1. SINGLE RESPONSIBILITY — the controller only knows HTTP; the repo
 *        only knows SQL; the service is where "business logic" lives.
 *
 *     2. TESTABILITY — we can unit-test the service by passing in a mock
 *        BookRepository, no HTTP layer, no database. See Phase 9.
 *
 *     3. REUSABILITY — later, another entry point (say a scheduled job or
 *        a message-queue handler) may need the same book-listing logic.
 *        A service can be called from anywhere; a controller cannot.
 *
 *   The pattern will pay off HARD as soon as we get to Phase 6+ features
 *   (basket, orders) where business rules multiply.
 *
 * WHY @Service (and not @Component):
 *   Functionally they're the same — both mark the class as a Spring bean
 *   Spring will manage. @Service is a specialisation of @Component whose
 *   ONLY difference is intent: it documents "this is a service-layer bean".
 *   Some tools (Spring's diagnostics, IDE search) use the distinction.
 */
@Service
public class BookService {

    /**
     * The book repository. `final` because we assign it once in the
     * constructor and never touch it again — a good habit for dependencies.
     */
    private final BookRepository bookRepository;


    /**
     * CONSTRUCTOR INJECTION — the preferred way to give Spring beans their
     * dependencies.
     *
     * When Spring creates a BookService (because of @Service), it looks at
     * this constructor and asks: "What arguments does it need? A
     * BookRepository. I have one — let me pass it in." That's dependency
     * injection: we NEVER do `new BookRepository(...)` ourselves.
     *
     * Notice there's no @Autowired here. Since Spring 4.3, when a class
     * has EXACTLY ONE constructor, Spring auto-wires it. Adding @Autowired
     * is legal but redundant, and modern Spring style omits it.
     */
    private final CategoryService categoryService;

    public BookService(BookRepository bookRepository, CategoryService categoryService) {
        this.bookRepository = bookRepository;
        this.categoryService = categoryService;
    }


    /**
     * Return one page of books, ordered by createdAt DESC (newest first).
     *
     * @param page  zero-based page index (0 = first page)
     * @param size  number of books per page
     * @return Page&lt;BookDto&gt; containing the requested page of DTOs plus
     *         pagination metadata (total pages, hasNext, etc.)
     */
    public Page<BookDto> listBooks(int page, int size) {
        PageRequest pageRequest = PageRequest.of(
            page, size, Sort.by("createdAt").descending()
        );
        return bookRepository.findAll(pageRequest).map(this::toDto);
    }


    /**
     * FEAT-03: unified search + filter entry point.
     * Builds a dynamic Specification from the BookFilter and delegates to
     * BookRepository.findAll(Specification, Pageable).
     * When all filter fields are null/false, behaviour is identical to listBooks().
     */
    public Page<BookDto> listBooks(BookFilter filter, int page, int size) {
        Specification<Book> spec = Specification
            .where(BookSpecification.hasKeyword(filter.getQ()))
            .and(BookSpecification.hasCategory(filter.getCategorySlug()))
            .and(BookSpecification.hasPriceAtLeast(filter.getMinPrice()))
            .and(BookSpecification.hasPriceAtMost(filter.getMaxPrice()));

        if (filter.isAvailableOnly()) {
            spec = spec.and(BookSpecification.isAvailable());
        }

        PageRequest pageRequest = PageRequest.of(page, size, resolveSort(filter.getSort()));
        return bookRepository.findAll(spec, pageRequest).map(this::toDto);
    }

    private Sort resolveSort(String sortParam) {
        if ("price_asc".equals(sortParam))  return Sort.by("price").ascending();
        if ("price_desc".equals(sortParam)) return Sort.by("price").descending();
        return Sort.by("createdAt").descending(); // default: newest
    }


    /**
     * Return a single book's DTO by its id.
     *
     * @param id  the internal database id of the book
     * @return the book as a DTO
     * @throws BookNotFoundException if no book has that id
     */
    public BookDto getBookById(Long id) {
        // findById returns Optional<Book> — a container that might be
        // empty. Optional forces us to think about the "not found" case
        // (which is why Optional exists — to make missing values obvious
        // in the type system, rather than relying on null).
        //
        // .orElseThrow(supplier) — if the Optional is present, return the
        // value; if empty, run the supplier and throw its return value.
        // The `() -> new BookNotFoundException(id)` is a lambda that
        // constructs the exception LAZILY — only if we actually need it.
        Book book = bookRepository.findById(id)
            .orElseThrow(() -> new BookNotFoundException(id));

        return toDto(book);
    }


    /**
     * Return a paginated page of books belonging to the given category slug.
     * Ordered newest first, page size as requested.
     *
     * @throws CategoryNotFoundException if the slug does not match any category.
     */
    public Page<BookDto> listBooksByCategory(String slug, int page, int size) {
        Category category = categoryService.getCategoryBySlug(slug); // throws if not found
        PageRequest pageRequest = PageRequest.of(
            page, size, Sort.by("createdAt").descending()
        );
        return bookRepository.findByCategory(category, pageRequest).map(this::toDto);
    }


    // ==================================================================
    // PRIVATE HELPERS
    // ==================================================================

    /**
     * Convert an entity (Book) into its outward-facing DTO shape.
     *
     * WHY THIS BELONGS TO THE SERVICE (and not the entity or the DTO):
     *   - Putting it on the entity would tempt code all over the app to
     *     use it, mixing "internal entity" concerns with "outward shape".
     *   - Putting it on the DTO would put logic in a class that's supposed
     *     to be a passive data holder.
     *   - The service owns this translation because that's exactly where
     *     the boundary between "internal model" and "external contract"
     *     lives.
     *
     *   Later, if the mapping gets complex (say we compute discount
     *   pricing, or format authors specially for display), we'll extract
     *   this into a dedicated BookMapper class. For now, a private helper
     *   is the simplest possible answer.
     */
    private BookDto toDto(Book book) {
        BookDto dto = new BookDto();

        // ---- Straight-across field copies ----
        dto.setId(book.getId());
        dto.setIsbn(book.getIsbn());
        dto.setTitle(book.getTitle());
        dto.setAuthors(book.getAuthors());
        dto.setDescription(book.getDescription());
        dto.setCoverImageUrl(book.getCoverImageUrl());
        dto.setPublisher(book.getPublisher());
        dto.setPublishedDate(book.getPublishedDate());
        dto.setPageCount(book.getPageCount());
        dto.setLanguage(book.getLanguage());
        // Expose the category's display name — DTO shape is unchanged,
        // clients still see "category": "Fiction" as a string.
        dto.setCategory(book.getCategory().getName());
        dto.setPrice(book.getPrice());

        // ---- Derived field: availability ----
        // stockQuantity itself is NOT copied over. Instead we compute a
        // customer-safe indicator. Note: no `< 0` case because the
        // stock_quantity column is NOT NULL and defaults to 0 — the DB
        // won't let us end up with a negative stock through this app.
        dto.setAvailability(
            book.getStockQuantity() > 0 ? "IN_STOCK" : "OUT_OF_STOCK"
        );

        // NOT copied: createdAt (internal sort key only)

        return dto;
    }
}
