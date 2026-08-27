package com.harsh.bookstore.dto;

import java.math.BigDecimal;

/**
 * BookFilter — carries all optional search and filter parameters from an HTTP request.
 *
 * All fields are nullable (null = "no constraint for this field").
 * BookController builds this from @RequestParams and passes it to BookService.listBooks().
 * BookSpecification reads it to build the dynamic Specification<Book> WHERE clause.
 */
public class BookFilter {

    /** Keyword matched against title, authors, description, isbn. Null = no keyword filter. */
    private String q;

    /** Category slug to filter by. Null = all categories. */
    private String categorySlug;

    /** Minimum price inclusive. Null = no lower bound. */
    private BigDecimal minPrice;

    /** Maximum price inclusive. Null = no upper bound. */
    private BigDecimal maxPrice;

    /** When true, only books with stockQuantity > 0 are returned. */
    private boolean availableOnly;

    /**
     * Sort order. Allowed values: "newest" (default), "price_asc", "price_desc".
     * Null is treated as "newest".
     */
    private String sort;

    public BookFilter() {}

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }

    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public boolean isAvailableOnly() { return availableOnly; }
    public void setAvailableOnly(boolean availableOnly) { this.availableOnly = availableOnly; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
