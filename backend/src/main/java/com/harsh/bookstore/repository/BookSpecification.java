package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Book;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * BookSpecification — static factory methods that each return one Specification<Book> predicate.
 *
 * Each method returns null when the input is absent.
 * Specification.where(null) produces no WHERE clause, so null predicates are silently skipped
 * when combined — this is the idiomatic Spring Data pattern for optional filters.
 *
 * The combined query in BookService looks like:
 *   Specification.where(hasKeyword(q))
 *                .and(hasCategory(slug))
 *                .and(hasPriceAtLeast(min))
 *                .and(hasPriceAtMost(max))
 *                .and(isAvailable())   // only when availableOnly=true
 */
public class BookSpecification {

    private BookSpecification() {} // utility class — no instances

    /**
     * Keyword match across title, isbn, description, and authors (case-insensitive substring).
     *
     * WHY query.distinct(true):
     *   The authors JOIN is one-to-many (one book → many author rows in book_authors).
     *   Without distinct, a book with 3 authors matching the keyword returns 3 duplicate rows.
     *   distinct(true) adds SQL DISTINCT, giving exactly one row per book.
     */
    public static Specification<Book> hasKeyword(String q) {
        if (q == null || q.isBlank()) return null;
        final String pattern = "%" + q.toLowerCase() + "%";

        return (root, query, cb) -> {
            query.distinct(true);
            // authors is a List<String> @ElementCollection.
            // Joining it gives a path whose value IS the element string directly —
            // there is no sub-attribute to navigate into. Cast to Expression<String>
            // so cb.lower() accepts it.
            Join<Object, Object> authorsJoin = root.join("authors", JoinType.LEFT);
            Expression<String> authorExpr = authorsJoin.as(String.class);

            return cb.or(
                cb.like(cb.lower(root.get("title")),       pattern),
                cb.like(cb.lower(root.get("isbn")),        pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(authorExpr),              pattern)
            );
        };
    }

    /**
     * Category slug match (case-insensitive).
     * Navigates the @ManyToOne relationship: root.get("category").get("slug").
     * If the slug matches no category, this predicate returns zero results (not a 404).
     */
    public static Specification<Book> hasCategory(String slug) {
        if (slug == null || slug.isBlank()) return null;

        return (root, query, cb) ->
            cb.equal(
                cb.lower(root.get("category").get("slug")),
                slug.toLowerCase()
            );
    }

    /**
     * Minimum price filter (price >= min, inclusive).
     */
    public static Specification<Book> hasPriceAtLeast(BigDecimal min) {
        if (min == null) return null;
        return (root, query, cb) ->
            cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    /**
     * Maximum price filter (price <= max, inclusive).
     */
    public static Specification<Book> hasPriceAtMost(BigDecimal max) {
        if (max == null) return null;
        return (root, query, cb) ->
            cb.lessThanOrEqualTo(root.get("price"), max);
    }

    /**
     * Availability filter — only books with stockQuantity > 0.
     */
    public static Specification<Book> isAvailable() {
        return (root, query, cb) ->
            cb.greaterThan(root.get("stockQuantity"), 0);
    }
}
