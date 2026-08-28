package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.entity.Order;
import com.harsh.bookstore.entity.OrderItem;
import com.harsh.bookstore.entity.OrderStatus;
import com.harsh.bookstore.repository.BookRepository;
import com.harsh.bookstore.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private BookRepository  bookRepository;

    private RecommendationService recommendationService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(orderRepository, bookRepository);
    }


    // ==================================================================
    // FEAT-14 — getRecommendations
    // ==================================================================

    @Test
    void getRecommendations_returnsEmpty_whenNoOrders() {
        when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        List<BookDto> result = recommendationService.getRecommendations(USER_ID);

        assertThat(result).isEmpty();
        verify(bookRepository, never()).findAll();
        verify(bookRepository, never()).findAllById(any());
    }

    @Test
    void getRecommendations_returnsUpTo6BooksFromPurchasedCategories() {
        // One order containing bookId=10 (Fiction)
        Order order = orderWithItem(10L);
        when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of(order));

        // Ordered book resolves to Fiction category
        Book orderedBook = book(10L, "Ordered Book", fiction());
        when(bookRepository.findAllById(any())).thenReturn(List.of(orderedBook));

        // Catalogue has 8 Fiction books (none with id=10)
        List<Book> catalogue = List.of(
                book(1L, "Alpha", fiction()), book(2L, "Beta", fiction()),
                book(3L, "Gamma", fiction()), book(4L, "Delta", fiction()),
                book(5L, "Epsilon", fiction()), book(6L, "Zeta", fiction()),
                book(7L, "Eta", fiction()), book(8L, "Theta", fiction())
        );
        when(bookRepository.findAll()).thenReturn(catalogue);

        List<BookDto> result = recommendationService.getRecommendations(USER_ID);

        assertThat(result).hasSize(6);
        assertThat(result).allMatch(dto -> "Fiction".equals(dto.getCategory()));
    }

    @Test
    void getRecommendations_excludesAlreadyOrderedBooks() {
        Order order = orderWithItem(10L);
        when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of(order));

        Book orderedBook = book(10L, "Ordered Book", fiction());
        when(bookRepository.findAllById(any())).thenReturn(List.of(orderedBook));

        // Catalogue includes the ordered book plus one new one
        when(bookRepository.findAll()).thenReturn(List.of(
                orderedBook,
                book(11L, "New Book", fiction())
        ));

        List<BookDto> result = recommendationService.getRecommendations(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(11L);
    }


    // ==================================================================
    // Private helpers
    // ==================================================================

    private Category fiction() {
        Category c = new Category();
        c.setId(1L);
        c.setName("Fiction");
        c.setSlug("fiction");
        return c;
    }

    private Book book(Long id, String title, Category category) {
        Book b = new Book();
        b.setId(id);
        b.setIsbn("9781234567890");
        b.setTitle(title);
        b.setAuthors(List.of("Author"));
        b.setDescription("Desc");
        b.setCoverImageUrl("https://example.com/cover.jpg");
        b.setLanguage("en");
        b.setCategory(category);
        b.setPrice(new BigDecimal("299.00"));
        b.setStockQuantity(5);
        b.setCreatedAt(LocalDateTime.now());
        return b;
    }

    private Order orderWithItem(Long bookId) {
        OrderItem item = new OrderItem();
        item.setBookId(bookId);
        item.setTitle("A Book");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("299.00"));
        item.setLineTotal(new BigDecimal("299.00"));

        Order order = new Order();
        order.setId(42L);
        order.setUserId(USER_ID);
        order.setStatus(OrderStatus.PAID);
        order.setBasketTotal(new BigDecimal("299.00"));
        order.setDeliveryCharge(BigDecimal.ZERO);
        order.setGiftPointsRedeemed(0);
        order.setTotalAmount(new BigDecimal("299.00"));
        order.setPointsAwarded(14);
        order.setEstimatedDeliveryDate("2025-09-04");
        order.getItems().add(item);
        item.setOrder(order);
        return order;
    }
}
