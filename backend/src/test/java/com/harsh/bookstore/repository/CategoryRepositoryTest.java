package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CategoryRepository.
 * @DataJpaTest — uses an in-memory H2 DB, rolls back after each test.
 */
@DataJpaTest
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BookRepository bookRepository;

    private Category fiction;
    private Category science;

    @BeforeEach
    void seedCategories() {
        fiction = categoryRepository.save(category("Fiction", "fiction"));
        science = categoryRepository.save(category("Science", "science"));
    }

    // --- findBySlugIgnoreCase ---

    @Test
    void findBySlugIgnoreCase_returnsCategory_forExactSlug() {
        assertThat(categoryRepository.findBySlugIgnoreCase("fiction")).isPresent();
    }

    @Test
    void findBySlugIgnoreCase_isCaseInsensitive() {
        assertThat(categoryRepository.findBySlugIgnoreCase("FICTION")).isPresent();
        assertThat(categoryRepository.findBySlugIgnoreCase("Fiction")).isPresent();
    }

    @Test
    void findBySlugIgnoreCase_returnsEmpty_forUnknownSlug() {
        assertThat(categoryRepository.findBySlugIgnoreCase("unknown")).isEmpty();
    }

    // --- findAllWithBookCount ---

    @Test
    void findAllWithBookCount_returnsCorrectCounts() {
        bookRepository.save(book("1111111111111", fiction));
        bookRepository.save(book("2222222222222", fiction));
        bookRepository.save(book("3333333333333", science));

        List<Object[]> rows = categoryRepository.findAllWithBookCount();

        // Ordered alphabetically — Fiction before Science
        assertThat(rows).hasSize(2);
        Category first = (Category) rows.get(0)[0];
        Long firstCount = (Long) rows.get(0)[1];
        assertThat(first.getSlug()).isEqualTo("fiction");
        assertThat(firstCount).isEqualTo(2L);

        Category second = (Category) rows.get(1)[0];
        Long secondCount = (Long) rows.get(1)[1];
        assertThat(second.getSlug()).isEqualTo("science");
        assertThat(secondCount).isEqualTo(1L);
    }

    @Test
    void findAllWithBookCount_returnsZero_forCategoryWithNoBooks() {
        List<Object[]> rows = categoryRepository.findAllWithBookCount();
        // Both categories seeded but no books saved in this test
        boolean allZero = rows.stream()
            .allMatch(row -> ((Long) row[1]) == 0L);
        assertThat(allZero).isTrue();
    }

    // --- helpers ---

    private Category category(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return c;
    }

    private Book book(String isbn, Category category) {
        Book b = new Book();
        b.setIsbn(isbn);
        b.setTitle("Title " + isbn);
        b.setAuthors(List.of("Author"));
        b.setDescription("Description");
        b.setCoverImageUrl("https://example.com/cover.jpg");
        b.setLanguage("en");
        b.setCategory(category);
        b.setPrice(new BigDecimal("299.00"));
        b.setStockQuantity(10);
        return b;
    }
}
