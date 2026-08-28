package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.entity.Order;
import com.harsh.bookstore.entity.OrderItem;
import com.harsh.bookstore.repository.BookRepository;
import com.harsh.bookstore.repository.OrderRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * RecommendationService — returns personalised book recommendations (FEAT-14).
 *
 * ALGORITHM:
 *   1. Load all orders for the user via OrderRepository.findAllByUserId.
 *   2. If no orders exist, return an empty list immediately.
 *   3. Collect bookIds from every OrderItem across all orders.
 *   4. Load those Book entities to resolve their categories.
 *   5. Scan the full catalogue (bookRepository.findAll()), keeping only books
 *      whose category is in the set of purchased categories AND whose id is NOT
 *      in the set of already-ordered book ids.
 *   6. Sort by title ascending, cap at 6, map to BookDto, and return.
 *
 * WHY findAll() INSTEAD OF A PER-CATEGORY QUERY:
 *   A user may have ordered books across several categories, which would require
 *   one SELECT per category. A single findAll() followed by an in-memory filter
 *   is simpler and acceptable for a development-scale catalogue.
 *
 * WHY toDto() IS DUPLICATED HERE (not reused from BookService):
 *   BookService.toDto() is private. Making it package-private or public solely
 *   to serve an unrelated service would widen its scope unnecessarily. The
 *   mapping is 15 lines of straight field copies — duplication is the lesser harm.
 */
@Service
public class RecommendationService {

    private final OrderRepository orderRepository;
    private final BookRepository  bookRepository;

    public RecommendationService(OrderRepository orderRepository,
                                 BookRepository  bookRepository) {
        this.orderRepository = orderRepository;
        this.bookRepository  = bookRepository;
    }


    /**
     * Return up to 6 recommended books for the given user.
     *
     * @param userId the authenticated user's ID
     * @return list of up to 6 BookDto, sorted by title ascending; empty if no orders
     */
    public List<BookDto> getRecommendations(Long userId) {
        List<Order> orders = orderRepository.findAllByUserId(userId);
        if (orders.isEmpty()) {
            return List.of();
        }

        // Collect all book ids the user has previously ordered
        Set<Long> orderedBookIds = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .map(OrderItem::getBookId)
                .collect(Collectors.toSet());

        // Resolve those books to find their categories
        List<Book> orderedBooks = bookRepository.findAllById(orderedBookIds);
        Set<Category> purchasedCategories = orderedBooks.stream()
                .map(Book::getCategory)
                .collect(Collectors.toSet());

        // Find candidates: same category, not yet ordered, up to 6, sorted by title
        return bookRepository.findAll().stream()
                .filter(b -> purchasedCategories.contains(b.getCategory()))
                .filter(b -> !orderedBookIds.contains(b.getId()))
                .sorted(Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(6)
                .map(this::toDto)
                .toList();
    }


    // ==================================================================
    // PRIVATE HELPERS
    // ==================================================================

    private BookDto toDto(Book book) {
        BookDto dto = new BookDto();
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
        dto.setCategory(book.getCategory().getName());
        dto.setPrice(book.getPrice());
        dto.setAvailability(book.getStockQuantity() > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
        return dto;
    }
}
