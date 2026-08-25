package com.harsh.bookstore.dto;

import org.springframework.data.domain.Page;

import java.util.List;


/**
 * PagedResponse — a clean pagination wrapper we return from the API.
 *
 * WHY THIS EXISTS (why not return Spring's own Page directly):
 *   Spring Data JPA's Page&lt;T&gt; type has a Java-native, verbose JSON shape:
 *   it includes fields like `pageable.sort.sorted`, `pageable.offset`,
 *   `sort.unsorted`, and other internals that reflect Spring's own class
 *   design. Real problems that causes:
 *
 *     1. NOISE — API consumers see dozens of fields they don't need.
 *     2. COUPLING — the shape depends on Spring's internal types. If Spring
 *        renames a field in a later version, our public API breaks.
 *     3. DEPRECATED — Spring itself deprecated the direct-Page serialisation
 *        pattern and recommends wrapping.
 *
 *   PagedResponse is a hand-designed wrapper with only the fields a client
 *   actually needs: content + pagination metadata. Stable, small, portable.
 *
 * WHY IT'S GENERIC (the &lt;T&gt;):
 *   The wrapper doesn't care what shape of item it contains — BookDto today,
 *   OrderDto next month, whatever. Making it PagedResponse&lt;T&gt; means we
 *   reuse the class across every endpoint that paginates, and the compiler
 *   still knows "PagedResponse&lt;BookDto&gt; contains BookDto items".
 */
public class PagedResponse<T> {

    // ==================================================================
    // FIELDS
    // ==================================================================

    /**
     * The list of items on this page. For a book listing, each item is a BookDto.
     */
    private List<T> content;

    /**
     * The current page number, zero-based. Page 0 is the first page.
     */
    private int page;

    /**
     * The requested page size — how many items this page holds at most.
     */
    private int size;

    /**
     * The TOTAL number of items across ALL pages. `long` (not int) because
     * a large database might exceed 2 billion rows. Overkill for a
     * bookstore, but the Spring convention is `long`, so we match.
     */
    private long totalElements;

    /**
     * The total number of pages. Derived: ceil(totalElements / size).
     */
    private int totalPages;

    /**
     * True if there's at least one more page after this one.
     */
    private boolean hasNext;

    /**
     * True if there's at least one page BEFORE this one (i.e. page > 0
     * and totalPages > 0).
     */
    private boolean hasPrevious;


    // ==================================================================
    // CONSTRUCTORS
    // ==================================================================

    /**
     * No-arg constructor. Jackson uses this to deserialize (if we ever
     * needed to accept a PagedResponse in a request body — not today).
     * Also used internally by the static factory below.
     */
    public PagedResponse() {
    }


    // ==================================================================
    // STATIC FACTORY
    // ==================================================================

    /**
     * Build a PagedResponse from Spring's Page&lt;T&gt;. This is the standard
     * "adapter" — the ONE place we translate from Spring's shape to ours.
     *
     * WHY STATIC FACTORY (and not a constructor):
     *   A static method reads more clearly at the call site:
     *       PagedResponse.from(pageResult)
     *   than:
     *       new PagedResponse&lt;&gt;(pageResult)
     *   Also, this pattern lets us have MULTIPLE named factory methods in
     *   the future (like `fromWithTotal(list, total)`) without constructor
     *   overload ambiguity.
     *
     * WHAT THE &lt;T&gt; BEFORE PagedResponse MEANS:
     *   It's a generic-method type parameter declaration. It says: "this
     *   method has a type parameter T; the return type is PagedResponse&lt;T&gt;;
     *   Java infers T from the caller's Page&lt;T&gt;". Called as
     *   `PagedResponse.from(page)` — no explicit type needed.
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        PagedResponse<T> response = new PagedResponse<>();
        response.setContent(page.getContent());
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setHasNext(page.hasNext());
        response.setHasPrevious(page.hasPrevious());
        return response;
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }

    public boolean isHasPrevious() { return hasPrevious; }
    public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
}
