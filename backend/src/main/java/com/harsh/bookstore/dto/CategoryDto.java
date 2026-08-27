package com.harsh.bookstore.dto;

/**
 * CategoryDto — outward-facing shape of a Category as returned by the API.
 *
 * Fields:
 *   id        — internal DB id (included for clients that prefer stable numeric keys)
 *   name      — display name e.g. "Fiction", "Self-Help"
 *   slug      — URL-safe identifier e.g. "fiction", "self-help"
 *   bookCount — number of books currently in this category (computed at query time)
 */
public class CategoryDto {

    private Long id;
    private String name;
    private String slug;
    private long bookCount;

    public CategoryDto() {}

    // --- Getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public long getBookCount() { return bookCount; }
    public void setBookCount(long bookCount) { this.bookCount = bookCount; }
}
