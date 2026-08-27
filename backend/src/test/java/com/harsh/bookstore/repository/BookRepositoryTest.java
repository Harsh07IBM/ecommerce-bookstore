package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Integration tests for BookRepository.
 *
 * @DataJpaTest — uses an in-memory H2 DB, rolls back after each test.
 * BookSeedLoader does NOT run here — tests build their own data.
 */
@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CategoryRepository categoryRepository;


    // ==================================================================
    // Test cases
    // ==================================================================

    @Test
    void save_assignsIdAndAllowsLookup() {
        Book book = newSampleBook("9781234567890", "Test Book", 10);

        Book saved = bookRepository.save(book);

        assertThat(saved.getId()).isNotNull();
        assertThat(bookRepository.findById(saved.getId())).isPresent();
    }


    @Test
    void findById_returnsEmpty_whenBookDoesNotExist() {
        Optional<Book> result = bookRepository.findById(99_999L);
        assertThat(result).isEmpty();
    }


    @Test
    void findAll_returnsBooks_orderedByCreatedAtDesc() {
        // All three books share the same category — avoids unique-name constraint violations.
        Category sharedCat = categoryRepository.save(cat("Fiction", "fiction-ordered"));
        Book older  = newSampleBookWithCategory("1111111111111", "Older",  sharedCat);
        Book middle = newSampleBookWithCategory("2222222222222", "Middle", sharedCat);
        Book newer  = newSampleBookWithCategory("3333333333333", "Newer",  sharedCat);
        older.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        middle.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        newer.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        bookRepository.saveAll(List.of(older, middle, newer));

        Page<Book> page = bookRepository.findAll(
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
            .extracting(Book::getTitle)
            .containsExactly("Newer", "Middle", "Older");
    }


    @Test
    void save_persistsMultipleAuthors_viaElementCollection() {
        Book book = newSampleBook("9990000000001", "Multi Author", 5);
        book.setAuthors(List.of("Alice Author", "Bob Author"));

        Book saved = bookRepository.save(book);
        Book fetched = bookRepository.findById(saved.getId()).orElseThrow();

        assertThat(fetched.getAuthors())
            .containsExactly("Alice Author", "Bob Author");
    }


    @Test
    void findByCategory_returnsOnlyBooksInThatCategory() {
        Category fiction = categoryRepository.save(cat("Fiction", "fiction"));
        Category science = categoryRepository.save(cat("Science", "science"));

        Book f1 = newSampleBookWithCategory("1111111111110", "Fiction Book 1", fiction);
        Book f2 = newSampleBookWithCategory("2222222222220", "Fiction Book 2", fiction);
        Book s1 = newSampleBookWithCategory("3333333333330", "Science Book 1", science);
        bookRepository.saveAll(List.of(f1, f2, s1));

        Page<Book> result = bookRepository.findByCategory(
            fiction, PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
            .extracting(Book::getTitle)
            .containsExactlyInAnyOrder("Fiction Book 1", "Fiction Book 2");
    }


    // ==================================================================
    // Test helpers
    // ==================================================================

    /**
     * Creates a category, saves it, and returns a Book wired to that category.
     * Used by tests that only care about book fields, not category specifics.
     */
    private Book newSampleBook(String isbn, String title, int stockQuantity) {
        Category cat = categoryRepository.save(cat("Fiction", "fiction-" + isbn));
        return newSampleBookWithCategory(isbn, title, cat, stockQuantity);
    }

    private Book newSampleBookWithCategory(String isbn, String title, Category category) {
        return newSampleBookWithCategory(isbn, title, category, 10);
    }

    private Book newSampleBookWithCategory(String isbn, String title, Category category, int stockQuantity) {
        Book b = new Book();
        b.setIsbn(isbn);
        b.setTitle(title);
        b.setAuthors(List.of("Sample Author"));
        b.setDescription("A sample description.");
        b.setCoverImageUrl("https://example.com/cover.jpg");
        b.setLanguage("en");
        b.setCategory(category);
        b.setPrice(new BigDecimal("299.00"));
        b.setStockQuantity(stockQuantity);
        return b;
    }

    private Category cat(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return c;
    }
}
