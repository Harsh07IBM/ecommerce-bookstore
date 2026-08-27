package com.harsh.bookstore.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.repository.BookRepository;
import com.harsh.bookstore.repository.CategoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BookSeedLoader — populates the `category` and `book` tables from
 * data/seed/books.json on every fresh startup (H2 is wiped on restart).
 *
 * ORDER MATTERS:
 *   1. Categories are saved first — each book holds a FK to category.id.
 *   2. Books are saved second, with their Category reference resolved.
 *
 * SLUG DERIVATION:
 *   Category name → slug: lowercase, non-alphanumeric runs replaced with "-".
 *   e.g. "Self-Help" → "self-help", "Technology" → "technology".
 *
 * WHY WE READ JSON AS Map<String,Object> FIRST (not Book directly):
 *   After FEAT-02, Book.category is a Category entity — not a String.
 *   Jackson cannot deserialise "category": "Fiction" straight into a
 *   Category object. We read the raw map, save Category rows ourselves,
 *   then wire up each Book to its Category by name lookup.
 */
@Component
public class BookSeedLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BookSeedLoader.class);

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    @Value("${bookstore.seed.file}")
    private String seedFilePath;

    public BookSeedLoader(BookRepository bookRepository,
                          CategoryRepository categoryRepository) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        // Idempotency check — H2 is wiped on restart so this is always 0
        // in development, but protects against accidental double-seeding
        // if we later switch to a persistent DB.
        if (bookRepository.count() > 0) {
            log.info("Books already present — skipping seed");
            return;
        }

        File file = new File(seedFilePath);
        if (!file.exists()) {
            log.warn("Seed file not found at {} — starting with empty catalogue",
                     file.getAbsolutePath());
            return;
        }

        // Read the JSON as a raw list of maps so we can control how
        // the "category" string field is resolved to a Category entity.
        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<Map<String, Object>> rawBooks = mapper.readValue(
            file, new TypeReference<List<Map<String, Object>>>() {}
        );

        // --- Step 1: collect distinct category names and save them ---
        // LinkedHashMap preserves insertion order — deterministic logs.
        Map<String, Category> categoryByName = new LinkedHashMap<>();
        for (Map<String, Object> raw : rawBooks) {
            String name = (String) raw.get("category");
            if (name != null && !categoryByName.containsKey(name)) {
                Category cat = new Category();
                cat.setName(name);
                cat.setSlug(toSlug(name));
                categoryByName.put(name, categoryRepository.save(cat));
            }
        }
        log.info("Seeded {} categories", categoryByName.size());

        // --- Step 2: build Book entities, resolve each category by name ---
        List<Book> books = new ArrayList<>();
        for (Map<String, Object> raw : rawBooks) {
            String catName = (String) raw.get("category");

            // Remove "category" from the raw map BEFORE convertValue so Jackson
            // never tries to deserialise a plain String into a Category entity.
            // We wire the Category reference ourselves below.
            raw.remove("category");

            Book book = mapper.convertValue(raw, Book.class);
            book.setCategory(categoryByName.get(catName));
            books.add(book);
        }

        // --- Step 3: bulk insert all books ---
        bookRepository.saveAll(books);
        log.info("Seeded {} books from {}", books.size(), file.getAbsolutePath());
    }

    /**
     * Derives a URL-safe slug from a category name.
     *   "Self-Help"  → "self-help"
     *   "Technology" → "technology"
     *   "Biography"  → "biography"
     */
    private String toSlug(String name) {
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9]+", "-")
                   .replaceAll("-+$", "");
    }
}
