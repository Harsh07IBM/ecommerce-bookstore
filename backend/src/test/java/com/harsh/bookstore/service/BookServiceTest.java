package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.exception.CategoryNotFoundException;
import com.harsh.bookstore.repository.BookRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for BookService.
 *
 * ANNOTATIONS EXPLAINED:
 *
 *   @ExtendWith(MockitoExtension.class) — enables Mockito's annotations
 *     (@Mock, @InjectMocks). This is NOT a Spring test — no Spring
 *     context, no database, no Tomcat. Just plain Java + Mockito.
 *     Result: very fast. Tests here run in milliseconds.
 *
 *   @Mock — Mockito creates a fake BookRepository. All methods return
 *     Mockito's default values (null / empty / false) unless we
 *     configure them with when(...).thenReturn(...).
 *
 *   @InjectMocks — Mockito creates a real BookService and looks for a
 *     constructor whose parameter types match our @Mocks. It passes the
 *     fake BookRepository into BookService. So `bookService.bookRepository`
 *     inside the class IS our mock.
 *
 * WHY WE DON'T USE @SpringBootTest OR @DataJpaTest HERE:
 *   BookService is a plain Java class — nothing about it requires Spring
 *   to be wired up to test its logic. Booting the full context (DB,
 *   Tomcat, seed loader) just to test a pair of methods is wasteful.
 *   Pure Mockito is roughly 100× faster than a Spring context boot.
 *
 * TESTING PHILOSOPHY:
 *   These are UNIT tests — one class in isolation. Its dependencies
 *   (the repository) are mocked. If we later replace BookRepository with
 *   an entirely different implementation, these tests still pass, because
 *   they only check BookService's own logic.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private BookService bookService;


    // ==================================================================
    // listBooks(...)
    // ==================================================================

    @Test
    void listBooks_returnsPageOfDtos_mappedFromEntities() {
        Book book = sampleBook(1L, "The Great Gatsby", 10);
        when(bookRepository.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(book)));

        Page<BookDto> result = bookService.listBooks(0, 12);

        assertThat(result.getContent()).hasSize(1);
        BookDto dto = result.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("The Great Gatsby");
        assertThat(dto.getAvailability()).isEqualTo("IN_STOCK");
        assertThat(dto.getCategory()).isEqualTo("Fiction");
    }


    @Test
    void listBooks_passesCorrectPageAndSort_toRepository() {
        // GIVEN: mock returns an empty page — we're testing the CALL,
        // not the return value here.
        when(bookRepository.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        // WHEN: we ask for page 2, size 7
        bookService.listBooks(2, 7);

        // THEN: capture the Pageable the service passed to the repo, and
        // verify page number, size, and sort direction.
        //
        // ArgumentCaptor is Mockito's tool for "I called a method with
        // something — let me see EXACTLY what I called it with". This is
        // stronger than just `verify(repo).findAll(any())`.
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(captor.capture());

        Pageable actual = captor.getValue();
        assertThat(actual.getPageNumber()).isEqualTo(2);
        assertThat(actual.getPageSize()).isEqualTo(7);
        assertThat(actual.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(actual.getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }


    // ==================================================================
    // getBookById(...)
    // ==================================================================

    @Test
    void getBookById_returnsDto_whenBookExists() {
        // GIVEN
        Book book = sampleBook(42L, "Some Book", 10);
        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));

        // WHEN
        BookDto dto = bookService.getBookById(42L);

        // THEN
        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getTitle()).isEqualTo("Some Book");
    }


    @Test
    void getBookById_throwsBookNotFoundException_whenBookDoesNotExist() {
        // GIVEN: the mock returns empty for any lookup of id 99
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        // WHEN + THEN: assertThatThrownBy is AssertJ's fluent way of
        // asserting an exception. Reads like English.
        assertThatThrownBy(() -> bookService.getBookById(99L))
            .isInstanceOf(BookNotFoundException.class)
            .hasMessageContaining("99");
    }


    // ==================================================================
    // Availability derivation — checked indirectly via getBookById
    // ==================================================================

    @Test
    void getBookById_dtoAvailabilityIsInStock_whenStockPositive() {
        Book book = sampleBook(1L, "Book", 5);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookDto dto = bookService.getBookById(1L);

        assertThat(dto.getAvailability()).isEqualTo("IN_STOCK");
    }


    @Test
    void getBookById_dtoAvailabilityIsOutOfStock_whenStockZero() {
        Book book = sampleBook(2L, "Book", 0);
        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));

        BookDto dto = bookService.getBookById(2L);

        assertThat(dto.getAvailability()).isEqualTo("OUT_OF_STOCK");
    }


    // ==================================================================
    // Test helpers
    // ==================================================================

    /**
     * Build a Book that has every non-nullable field set. We give it an
     * id up front because entities coming out of the repo (mocked here)
     * would already have one.
     */
    private Book sampleBook(Long id, String title, int stockQuantity) {
        Category cat = new Category();
        cat.setId(1L);
        cat.setName("Fiction");
        cat.setSlug("fiction");

        Book b = new Book();
        b.setId(id);
        b.setIsbn("9781234567890");
        b.setTitle(title);
        b.setAuthors(List.of("Author"));
        b.setDescription("Description");
        b.setCoverImageUrl("https://example.com/cover.jpg");
        b.setLanguage("en");
        b.setCategory(cat);
        b.setPrice(new BigDecimal("299.00"));
        b.setStockQuantity(stockQuantity);
        b.setCreatedAt(LocalDateTime.now());
        return b;
    }
}
