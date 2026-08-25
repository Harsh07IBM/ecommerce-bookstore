package com.harsh.bookstore.entity;

/*
 * ------------------------------------------------------------------
 * IMPORTS
 * ------------------------------------------------------------------
 * The `jakarta.persistence.*` package is the JPA (Jakarta Persistence
 * API) — Java's standard for mapping ordinary classes to database tables.
 *
 * Note the package name is `jakarta.*`, NOT `javax.*` — Spring Boot 3.x
 * moved to Jakarta EE. Tutorials written for Spring Boot 2.x show
 * `javax.persistence.*`; if you copy code from an old tutorial and see
 * import errors, this is why. Same annotations, different package prefix.
 */
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Book — represents a single book available for sale in the store.
 *
 * WHAT THIS CLASS IS (in plain English):
 *   A JPA "entity" — an ordinary Java class that Hibernate (the ORM
 *   library Spring Data JPA uses under the hood) automatically maps to
 *   a database table. Every time we save one of these to the repository,
 *   Hibernate composes and runs the appropriate INSERT / UPDATE statement
 *   for us. When we call `findAll()`, it composes the SELECT and turns
 *   each row back into a `Book` object.
 *
 *   Concretely, this class defines the SHAPE of the `book` SQL table:
 *     - each field on the class    → a column in the table
 *     - the @Entity annotation     → "map this class to a table"
 *     - the @Id annotation         → "this field is the primary key"
 *     - the @Column annotations    → "these constraints apply to the column"
 *
 * WHY WE MODEL THINGS THIS WAY:
 *   See docs/designs/feature-01-browse-catalogue-design.md §6 and §7.
 *   The fields here match what the Python seed script produces (isbn,
 *   title, authors, description, cover URL, ...) plus two DB-managed
 *   fields (id and createdAt).
 */
@Entity                    // Mark this class as a JPA entity — Hibernate maps it to a table.
@Table(name = "book")      // The table's name in SQL. Without this, it would default to "Book" — case-sensitive on some databases.
public class Book {

    // ==================================================================
    // FIELDS
    //   Each field corresponds to one column in the `book` table.
    // ==================================================================

    /**
     * Internal database identifier. Never shown to end users (they see
     * ISBN instead), but used to look up books via the API path
     * `/api/books/{id}`. Is `null` for a fresh, unsaved Book — the DB
     * assigns a value automatically when we save.
     */
    @Id                                                    // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)    // let the DB pick the value (H2 uses auto-increment)
    private Long id;

    /**
     * ISBN — International Standard Book Number. 13 digits (modern) or 10
     * (legacy). Marked unique so the same book can't appear twice with
     * different rows — the database enforces this constraint.
     */
    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    /**
     * The book's title. length = 500 because some real titles are
     * surprisingly long (subtitles, edition marks, etc.).
     */
    @Column(nullable = false, length = 500)
    private String title;

    /**
     * The book's authors — a list of names.
     *
     * WHY @ElementCollection (and not a separate @Entity):
     *   For FEAT-01 we only need author names — no biographies, no
     *   birth dates. @ElementCollection is the JPA way to say "store
     *   this list of simple values in a small side table that links
     *   back to me". Hibernate creates the table `book_authors` with:
     *       book_id  — foreign key back to book.id
     *       author   — the name string
     *   We do NOT get a separate `Author` Java class. If we later need
     *   author pages / search-by-author, we'll refactor to a @ManyToMany
     *   with a proper Author entity. Deferred until actually needed.
     *
     * WHY FetchType.EAGER:
     *   Whenever we load a Book, we always want its authors alongside
     *   (they appear on both the list card and the detail page). EAGER
     *   tells JPA "load these at the same time". The alternative, LAZY,
     *   would defer the query until someone actually calls getAuthors()
     *   — better for huge collections, but overkill for typically-1-or-2
     *   author lists that we always display.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "book_authors",
        joinColumns = @JoinColumn(name = "book_id")
    )
    @Column(name = "author", nullable = false)
    private List<String> authors = new ArrayList<>();

    /**
     * A long-form description of the book.
     *
     * @Lob = "Large Object". It tells Hibernate to use a column type
     * designed for long text — CLOB on H2, TEXT on Postgres/MySQL. Regular
     * VARCHAR columns have length limits (typically a few thousand chars)
     * that book descriptions can exceed.
     */
    @Lob
    @Column(nullable = false)
    private String description;

    /**
     * URL of the book's cover image, hosted by Open Library.
     */
    @Column(name = "cover_image_url", nullable = false, length = 1000)
    private String coverImageUrl;

    /**
     * Publisher name. Nullable — some old or obscure books on Open Library
     * have no publisher recorded.
     */
    @Column(length = 255)
    private String publisher;

    /**
     * When the book was first published. Stored as a String (not a
     * LocalDate) because Open Library returns inconsistent formats:
     * "2020", "2020-05", "2020-05-15". Trying to parse all of these into
     * LocalDate is fragile and offers no benefit here — we only display
     * the value, we don't sort or filter by it.
     */
    @Column(name = "published_date", length = 50)
    private String publishedDate;

    /**
     * Number of pages. Nullable — sometimes unknown. `Integer` (boxed)
     * rather than `int` (primitive) precisely BECAUSE it can be null —
     * `int` in Java can't hold null.
     */
    @Column(name = "page_count")
    private Integer pageCount;

    /**
     * ISO language code, e.g. "en", "hi", "fr".
     */
    @Column(nullable = false, length = 10)
    private String language;

    /**
     * A single primary category — "Fiction", "Technology", "History", etc.
     * We keep it as a plain String here (no separate Category entity) to
     * keep FEAT-01 simple. FEAT-02 will introduce a proper Category entity
     * and refactor this into a @ManyToOne relationship.
     */
    @Column(nullable = false, length = 100)
    private String category;

    /**
     * Selling price in Indian Rupees.
     *
     * WHY BigDecimal AND NOT double:
     *   `double` is a floating-point type. Floating-point has rounding
     *   errors that don't matter for physics but DO matter for money
     *   (0.1 + 0.2 does not equal 0.3 in doubles). BigDecimal stores
     *   the number exactly, no rounding surprises.
     *
     *   precision = 10, scale = 2 means: up to 10 total digits total, of
     *   which 2 are after the decimal point. Max value: 99,999,999.99.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * How many copies of this book are in stock. When 0, the API returns
     * availability = "OUT_OF_STOCK". The exact stock number is never
     * exposed via the public API — see BookService.toDto() in Phase 6.
     */
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    /**
     * When this row was inserted into the DB. Set once, never updated
     * (updatable = false enforces that even by mistake). Used as the
     * default sort key so newest books show up first in the catalogue.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    // ==================================================================
    // LIFECYCLE CALLBACK
    // ==================================================================

    /**
     * @PrePersist = "run this method just BEFORE Hibernate issues INSERT
     * for a new Book". Perfect place to stamp `createdAt` so calling code
     * doesn't have to remember to set it.
     *
     * `protected` visibility (not public) because Hibernate calls this
     * via reflection; nobody outside this class should call it directly.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }


    // ==================================================================
    // CONSTRUCTORS
    // ==================================================================

    /**
     * JPA requires a no-argument constructor so Hibernate can reflectively
     * create empty instances and populate their fields when reading rows.
     *
     * If we declared no constructors at all, Java would generate this one
     * automatically — but the moment we add ANY other constructor, that
     * automatic no-arg constructor disappears. Declaring it explicitly
     * makes the requirement obvious.
     */
    public Book() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    //   One pair for every field. Yes, it's a lot of boilerplate — this
    //   is exactly the code Lombok's @Data annotation would generate for
    //   us. We're writing it by hand for FEAT-01 so you can see what
    //   Lombok would have done. Once you're comfortable, we can adopt
    //   Lombok in a future feature. See design decision D-07.
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getPublishedDate() { return publishedDate; }
    public void setPublishedDate(String publishedDate) { this.publishedDate = publishedDate; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }


    // ==================================================================
    // equals / hashCode / toString
    // ==================================================================

    /**
     * Two Book objects are equal only when they share the same non-null
     * id. This is the standard "JPA entity equality" pattern.
     *
     * IMPORTANT SUBTLETY: two Book instances with `id == null` (both
     * fresh, unsaved) are NOT considered equal — they could be entirely
     * different books that just haven't been persisted yet. Hence the
     * explicit `id != null` check.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;                     // same object reference → equal
        if (!(other instanceof Book)) return false;         // different type → not equal
        Book that = (Book) other;
        return id != null && id.equals(that.id);
    }

    /**
     * hashCode returns the class's own hashCode — a constant per Book.
     *
     * WHY NOT hash on `id`:
     *   An entity's id starts as null (before persistence) and becomes
     *   non-null after save. If hashCode depended on id, the entity's hash
     *   would CHANGE when saved — which would break Set / Map lookups
     *   (an item can never be found again after its hash changes).
     *   The class-hash pattern avoids that trap. Recommended by JPA experts.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * For debugging and log lines. Keeps it short — the full description
     * would swamp any log message.
     */
    @Override
    public String toString() {
        return "Book{id=" + id + ", title='" + title + "'}";
    }
}
