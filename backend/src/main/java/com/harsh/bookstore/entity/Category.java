package com.harsh.bookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Category — represents a named subject classification for books.
 *
 * WHAT THIS CLASS IS:
 *   A JPA entity that maps to the `category` table. Every Book belongs
 *   to exactly one Category via a @ManyToOne relationship defined on Book.
 *
 *   We do NOT put a @OneToMany books list here — we never navigate from
 *   a Category to all its books directly. Queries that need "books in a
 *   category" go through BookRepository.findByCategory(...).
 *
 * WHY SLUG:
 *   The `slug` is a URL-safe identifier (e.g. "self-help", "fiction").
 *   Using slugs in API URLs keeps them readable and stable regardless of
 *   internal IDs. Slugs are derived from the category name at seed time.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name — e.g. "Fiction", "Self-Help", "Technology". Unique across all categories. */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** URL-safe identifier — e.g. "fiction", "self-help", "technology". Unique across all categories. */
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    public Category() {}

    // --- Getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    // --- equals / hashCode / toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category)) return false;
        Category that = (Category) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }

    @Override
    public String toString() {
        return "Category{id=" + id + ", slug='" + slug + "'}";
    }
}
