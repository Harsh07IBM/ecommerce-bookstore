package com.harsh.bookstore.dto;

import java.math.BigDecimal;
import java.util.List;


/**
 * BookDto — the outward-facing shape of a Book as sent to API clients.
 *
 * WHAT A DTO IS (in plain English):
 *   "DTO" stands for Data Transfer Object. It's an ordinary Java class
 *   whose only job is to carry data across a boundary — in our case,
 *   between our service layer and the HTTP world.
 *
 * WHY WE NEED A SEPARATE CLASS FROM Book (the @Entity):
 *   Book is bound to a database table via JPA annotations. If we returned
 *   Book directly from the controller, three bad things would happen:
 *
 *     1. INTERNAL FIELDS LEAK. Fields like `stockQuantity` and `createdAt`
 *        would show up in the JSON response, exposing more than we intend.
 *
 *     2. THE API AND DB SCHEMA COUPLE. Rename a database column → break the
 *        API contract. Move a field to a new entity → break the API contract.
 *        DTOs act as a firewall between the two.
 *
 *     3. LAZY-LOADING SURPRISES. JPA can lazily load related data. If Jackson
 *        tries to serialise an entity outside a transaction, it may hit a
 *        LazyInitializationException at runtime — nasty bug. Serialising a
 *        DTO (plain Java data) never has this problem.
 *
 * DIFFERENCES FROM Book:
 *   - `stockQuantity` is OMITTED — replaced by the derived `availability`
 *     string ("IN_STOCK" / "OUT_OF_STOCK"). Exact stock counts are internal.
 *   - `createdAt` is OMITTED — it's used internally to sort the list, but
 *     the client doesn't need to see it.
 *
 * WHY THIS CLASS HAS NO JPA / JACKSON ANNOTATIONS:
 *   By default, Jackson (the JSON library Spring uses) auto-serialises any
 *   public getter as a JSON property. `getTitle()` becomes `"title": "..."`
 *   in the output. So a plain-old Java object with getters is enough —
 *   nothing extra to declare.
 */
public class BookDto {

    // ==================================================================
    // FIELDS
    //   Each field becomes one property in the JSON response.
    //   The order below is the order Jackson emits them by default.
    // ==================================================================

    private Long id;
    private String isbn;
    private String title;
    private List<String> authors;
    private String description;
    private String coverImageUrl;
    private String publisher;
    private String publishedDate;
    private Integer pageCount;
    private String language;
    private String category;
    private BigDecimal price;

    /**
     * A human-readable stock indicator — either "IN_STOCK" or "OUT_OF_STOCK".
     * Derived from the entity's stockQuantity by BookService — the raw number
     * never leaves the service layer.
     */
    private String availability;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    /**
     * No-arg constructor. Both Jackson (when deserialising JSON into a DTO
     * for write endpoints later) and our own BookService's toDto() rely on
     * it. Even though we don't use it explicitly for FEAT-01 (we only
     * write DTOs OUT, not read them IN), it's a convention worth keeping.
     */
    public BookDto() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    //   Same reasoning as on Book — writing these by hand instead of using
    //   Lombok so you can see the boilerplate Lombok would generate.
    //   See design decision D-07.
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

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }


    // ==================================================================
    // toString
    // ==================================================================

    /**
     * Compact debug string. Full field dump would be noisy in logs.
     */
    @Override
    public String toString() {
        return "BookDto{id=" + id + ", title='" + title + "', availability=" + availability + "}";
    }
}
