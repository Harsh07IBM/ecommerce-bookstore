package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Book;

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
 * ANNOTATIONS EXPLAINED:
 *
 *   @DataJpaTest — Spring's "JPA slice" testing annotation. What it does:
 *     - Configures an embedded H2 database automatically for this test,
 *       distinct from what production uses (though for us both are H2).
 *     - Sets up JPA + Hibernate.
 *     - Wraps EACH test method in a transaction that ROLLS BACK at the
 *       end — so tests never pollute each other.
 *     - Does NOT load @Service, @Component, @Controller beans (that's what
 *       "slice" means: only JPA-related beans). This makes the test
 *       start ~10x faster than a full @SpringBootTest.
 *     - Because @Component beans are not loaded, our BookSeedLoader does
 *       not run here — each test starts with an EMPTY database. Perfect.
 *
 * WHAT WE'RE ACTUALLY TESTING:
 *   BookRepository is an empty interface, so we're really verifying:
 *     1. Our @Entity + @Column annotations on Book map correctly to a schema
 *        (a wrong annotation would make save() throw)
 *     2. Spring Data JPA's inherited methods (save, findById, findAll,
 *        saveAll) actually work with our entity
 *     3. The @ElementCollection on `authors` persists round-trip
 *     4. Pagination + sort by "createdAt" works end-to-end
 *
 *   If a future refactor breaks any of these mappings — say, a typo in a
 *   @Column name, or removing a required setter — these tests catch it
 *   before production.
 *
 * TEST-NAMING CONVENTION:
 *   methodUnderTest_scenario_expectedResult().
 *   Reads like an English sentence when you scan the file.
 */
@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;


    // ==================================================================
    // Test cases
    // ==================================================================

    @Test
    void save_assignsIdAndAllowsLookup() {
        // GIVEN: a fresh Book with no id
        Book book = newSampleBook("9781234567890", "Test Book", 10);

        // WHEN: we save it
        Book saved = bookRepository.save(book);

        // THEN: the DB assigned an id, and we can look it up
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
        // GIVEN: three books with distinct, controlled createdAt values.
        // Setting createdAt BEFORE save is fine — the @PrePersist hook on
        // Book only fills it in when it's null, so an explicit value is
        // preserved. That lets us assert on order deterministically.
        Book older  = newSampleBook("1111111111111", "Older",  10);
        Book middle = newSampleBook("2222222222222", "Middle", 10);
        Book newer  = newSampleBook("3333333333333", "Newer",  10);
        older.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        middle.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        newer.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        bookRepository.saveAll(List.of(older, middle, newer));

        // WHEN: we ask for a page sorted by createdAt DESC
        Page<Book> page = bookRepository.findAll(
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // THEN: newer comes first, older last
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
            .extracting(Book::getTitle)
            .containsExactly("Newer", "Middle", "Older");
    }


    @Test
    void save_persistsMultipleAuthors_viaElementCollection() {
        // GIVEN: a book with two authors
        Book book = newSampleBook("9990000000001", "Multi Author", 5);
        book.setAuthors(List.of("Alice Author", "Bob Author"));

        // WHEN: save and re-load — this forces the round trip through
        // the DB, so we're really testing the @ElementCollection mapping.
        Book saved = bookRepository.save(book);
        Book fetched = bookRepository.findById(saved.getId()).orElseThrow();

        // THEN: both authors come back, in the order they were saved
        assertThat(fetched.getAuthors())
            .containsExactly("Alice Author", "Bob Author");
    }


    // ==================================================================
    // Test helpers
    // ==================================================================

    /**
     * Build a valid Book with sensible defaults. Each test only sets the
     * fields that matter to its scenario, so the test bodies stay short.
     */
    private Book newSampleBook(String isbn, String title, int stockQuantity) {
        Book b = new Book();
        b.setIsbn(isbn);
        b.setTitle(title);
        b.setAuthors(List.of("Sample Author"));
        b.setDescription("A sample description.");
        b.setCoverImageUrl("https://example.com/cover.jpg");
        b.setLanguage("en");
        b.setCategory("Fiction");
        b.setPrice(new BigDecimal("299.00"));
        b.setStockQuantity(stockQuantity);
        // createdAt intentionally left null — @PrePersist will set it to
        // "now" unless a test overrides it (see findAll_returnsBooks...).
        return b;
    }
}
