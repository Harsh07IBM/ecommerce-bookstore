package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BookSpecificationSearchTest {

    @Autowired private BookRepository bookRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Book atheistBook;

    @BeforeEach
    void setUp() {
        Category cat = new Category();
        cat.setName("Philosophy");
        cat.setSlug("philosophy");
        categoryRepository.save(cat);

        atheistBook = new Book();
        atheistBook.setIsbn("9782291066842");
        atheistBook.setTitle("Why I am an atheist");
        atheistBook.setAuthors(List.of("Bhagat Singh"));
        atheistBook.setDescription("A pamphlet by Bhagat Singh.");
        atheistBook.setCoverImageUrl("https://example.com/cover.jpg");
        atheistBook.setLanguage("en");
        atheistBook.setCategory(cat);
        atheistBook.setPrice(new BigDecimal("244.00"));
        atheistBook.setStockQuantity(13);
        bookRepository.save(atheistBook);
    }

    @Test
    void search_byTitleKeyword_returnsMatch() {
        Specification<Book> spec = Specification.where(BookSpecification.hasKeyword("atheist"));
        Page<Book> result = bookRepository.findAll(spec, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Why I am an atheist");
    }

    @Test
    void search_byAuthorKeyword_returnsMatch() {
        Specification<Book> spec = Specification.where(BookSpecification.hasKeyword("Bhagat"));
        Page<Book> result = bookRepository.findAll(spec, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getAuthors()).contains("Bhagat Singh");
    }

    @Test
    void search_byUnknownKeyword_returnsEmpty() {
        Specification<Book> spec = Specification.where(BookSpecification.hasKeyword("xyznotexist"));
        Page<Book> result = bookRepository.findAll(spec, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
