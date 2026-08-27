package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.dto.BookFilter;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.exception.CategoryNotFoundException;
import com.harsh.bookstore.service.BookService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for BookController.
 *
 * ANNOTATIONS EXPLAINED:
 *
 *   @WebMvcTest(BookController.class) — Spring's "web slice" testing
 *     annotation. It loads:
 *       - The specified controller (BookController)
 *       - Spring MVC infrastructure (dispatcher, converters, Jackson)
 *       - All @RestControllerAdvice beans — so our GlobalExceptionHandler
 *         is active, meaning our tests can verify the real 404/400 JSON
 *     It does NOT load:
 *       - @Service beans (we provide BookService as a @MockBean below)
 *       - @Repository beans, no database, no seed loader.
 *     Startup is ~5× faster than a full @SpringBootTest.
 *
 *   @MockBean — hybrid of @Mock and Spring's bean registration. The
 *     mocked BookService is registered as a Spring bean, so the auto-
 *     wired BookController receives it transparently.
 *
 *     Note: @MockBean is deprecated as of Spring Boot 3.4 in favour of
 *     @MockitoBean (org.springframework.test.context.bean.override.mockito).
 *     Both work identically for our purposes — sticking with @MockBean
 *     because tutorials/blog posts still overwhelmingly use it. Swap to
 *     @MockitoBean once you're comfortable.
 *
 *   MockMvc — a fake HTTP client that hits our controllers directly (no
 *     real network). Extremely fast — no port to bind, no socket to open.
 *
 * WHAT WE'RE TESTING:
 *   The HTTP CONTRACT — status codes, JSON shape, error format. Business
 *   logic is covered by BookServiceTest, entity mapping by BookRepositoryTest.
 */
@WebMvcTest(value = BookController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(com.harsh.bookstore.config.SecurityConfig.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    // JwtAuthFilter is a @Component and gets picked up by @WebMvcTest.
    // It needs JwtService + UserRepository to construct — we mock them here.
    @MockBean
    private com.harsh.bookstore.service.JwtService jwtService;

    @MockBean
    private com.harsh.bookstore.repository.UserRepository userRepository;


    // ==================================================================
    // GET /api/books
    // ==================================================================

    @Test
    void listBooks_returns200_withPagedResponseJson() throws Exception {
        BookDto dto = sampleDto(1L, "Sample Book");
        Page<BookDto> page = new PageImpl<>(List.of(dto));
        when(bookService.listBooks(eq(0), eq(12))).thenReturn(page);

        mockMvc.perform(get("/api/books"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Sample Book"))
            .andExpect(jsonPath("$.content[0].availability").value("IN_STOCK"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].stockQuantity").doesNotExist())
            .andExpect(jsonPath("$.content[0].createdAt").doesNotExist());
    }


    // FEAT-02 regression + category filter tests

    @Test
    void listBooks_returns200_withCategoryFilter() throws Exception {
        // ?category=fiction is a non-null filter param → controller routes through
        // BookFilter / listBooks(BookFilter, page, size), NOT listBooksByCategory.
        BookDto dto = sampleDto(1L, "Fiction Book");
        Page<BookDto> page = new PageImpl<>(List.of(dto));
        when(bookService.listBooks(any(BookFilter.class), eq(0), eq(12))).thenReturn(page);

        mockMvc.perform(get("/api/books?category=fiction"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("Fiction Book"));
    }

    @Test
    void listBooks_returns404_whenCategoryUnknown() throws Exception {
        // ?category=nope → BookFilter path → CategoryNotFoundException → 404.
        when(bookService.listBooks(any(BookFilter.class), eq(0), eq(12)))
            .thenThrow(new CategoryNotFoundException("nope"));

        mockMvc.perform(get("/api/books?category=nope"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Category with slug 'nope' was not found"));
    }

    @Test
    void listBooks_noCategory_callsListBooksNotListBooksByCategory() throws Exception {
        // Regression guard — no ?category param must call listBooks, not listBooksByCategory
        when(bookService.listBooks(eq(0), eq(12))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/books"))
            .andExpect(status().isOk());

        org.mockito.Mockito.verify(bookService).listBooks(0, 12);
        org.mockito.Mockito.verify(bookService, org.mockito.Mockito.never())
            .listBooksByCategory(org.mockito.ArgumentMatchers.any(),
                                 org.mockito.ArgumentMatchers.anyInt(),
                                 org.mockito.ArgumentMatchers.anyInt());
    }


    @Test
    void listBooks_returns400_whenPageNegative() throws Exception {
        // No mock setup — controller rejects BEFORE calling the service.
        mockMvc.perform(get("/api/books?page=-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("page must be >= 0"));
    }


    @Test
    void listBooks_returns400_whenSizeAboveMax() throws Exception {
        mockMvc.perform(get("/api/books?size=101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }


    @Test
    void listBooks_returns400_whenSizeBelowMin() throws Exception {
        mockMvc.perform(get("/api/books?size=0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }


    // ==================================================================
    // GET /api/books/{id}
    // ==================================================================

    @Test
    void getBookById_returns200_whenBookExists() throws Exception {
        BookDto dto = sampleDto(42L, "The Book");
        when(bookService.getBookById(42L)).thenReturn(dto);

        mockMvc.perform(get("/api/books/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.title").value("The Book"))
            .andExpect(jsonPath("$.availability").value("IN_STOCK"))
            .andExpect(jsonPath("$.stockQuantity").doesNotExist())
            .andExpect(jsonPath("$.createdAt").doesNotExist());
    }


    @Test
    void getBookById_returns404WithErrorBody_whenBookMissing() throws Exception {
        // GIVEN: the service throws — which is what happens in production
        // when the repo returns Optional.empty().
        when(bookService.getBookById(99L))
            .thenThrow(new BookNotFoundException(99L));

        mockMvc.perform(get("/api/books/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Book with id 99 was not found"))
            .andExpect(jsonPath("$.path").value("/api/books/99"))
            // timestamp is generated at runtime — just assert it exists
            .andExpect(jsonPath("$.timestamp").exists());
    }


    // ==================================================================
    // Test helpers
    // ==================================================================

    private BookDto sampleDto(Long id, String title) {
        BookDto dto = new BookDto();
        dto.setId(id);
        dto.setIsbn("9781234567890");
        dto.setTitle(title);
        dto.setAuthors(List.of("Author"));
        dto.setDescription("Description");
        dto.setCoverImageUrl("https://example.com/cover.jpg");
        dto.setLanguage("en");
        dto.setCategory("Fiction");
        dto.setPrice(new BigDecimal("299.00"));
        dto.setAvailability("IN_STOCK");
        return dto;
    }
}
